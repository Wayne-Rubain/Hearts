package hearts_1;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Endgame solver, added 2026-10-08 (TODO.md's "Endgame claim" item, redesigned
 * with the user before building).
 *
 * WHY NOT THE ORIGINAL "CLAIM" IDEA. The TODO line read "when counting proves
 * every remaining trick is won, sequence for maximum points swing." But Hearts
 * has no trumps: a seat that provably wins every remaining trick collects
 * every remaining card whatever order it plays them in, so sequencing can't
 * change its points. The order of play only matters while the outcome of the
 * remaining tricks is still open -- and late in a hand, card counting plus the
 * known voids usually narrow "who holds what" down to a handful of
 * possibilities. That is what this class exploits.
 *
 * WHAT IT DOES. With few cards left (see MAX_CARDS_IN_HAND), it:
 *   1. works out every card still unseen by this seat (the deck minus my hand,
 *      every won pile, and the cards face up on the current trick -- the same
 *      legitimate information CardCounter uses, no peeking);
 *   2. enumerates every way those cards could be split among the three
 *      opponents, consistent with how many cards each has left and every void
 *      VoidTracker has proven;
 *   3. for each of my legal cards and each possible split, plays the rest of
 *      the hand out exhaustively;
 *   4. scores each card by its WORST result over all the splits, so a card is
 *      only ever preferred when it is better no matter how the unseen cards
 *      actually lie.
 *
 * THE OPPONENT MODEL (user, 2026-10-08). Opponents are not assumed to be out
 * to get me -- that "paranoid" model would have, for example, an opponent
 * swallow the Queen of Spades just to cost me points, which nobody does. Each
 * seat instead plays to protect its own place in the STANDINGS first and its
 * points margin second (see utility()). That reproduces the user's rule
 * directly: a player holding the Queen dumps her the first chance it gets,
 * unless eating her to stop a moon shot keeps it ahead of the would-be shooter
 * -- if taking the 13 drops it behind that seat anyway, there's no point and
 * it dumps her. Where an opponent is indifferent between plays, the search
 * assumes it picks the one worst for me, which keeps the answer conservative.
 *
 * MY OBJECTIVE is the same utility, from my own seat: standings first, then my
 * points relative to the other three (the user picked "relative" over "my own
 * points only" -- otherwise a successful moon by someone else, which scores
 * the rest of the table 0 under Omnibus rules, would look harmless and the
 * solver would override the moon defense).
 *
 * BUDGETS. Exhaustive search grows fast: at 3 cards a seat it is at most a few
 * thousand splits of a few thousand leaves each, which is quick. At 4 cards it
 * runs only when the voids have already pinned the splits down to a few. A
 * hard leaf budget backstops both; if it is ever exceeded the solver returns
 * null and play falls through to the ordinary scorers, unchanged.
 *
 * Pure logic, no Swing dependency -- same pattern as Rules.java/Scoring.java/
 * CardCounter.java. Uses its own compact int/bitmask representation rather
 * than Card/Hand objects so millions of positions stay fast.
 */
public final class EndgameSolver {

	private EndgameSolver() { }

	/** Solve whenever my hand is this small or smaller. */
	static final int MAX_CARDS_IN_HAND = 3;
	/** At one card more than that, solve only if the voids have narrowed the
	 * possible splits down to at most this many. */
	static final int MAX_DEALS_AT_FOUR = 24;
	/** Hard ceiling on deals enumerated at all. */
	static final int MAX_DEALS = 4000;
	/** Hard ceiling on leaves searched per decision. */
	static final long MAX_LEAVES = 6_000_000L;

	/** Test/measurement hooks, not game settings. DISABLED_SEATS lets an A/B
	 * simulation switch the solver off for some seats only (so both arms play
	 * the same deals); solveCount lets a test prove the solver actually ran
	 * rather than passing because it never fired. */
	static final EnumSet<PLAYER> DISABLED_SEATS = EnumSet.noneOf(PLAYER.class);
	static long solveCount = 0;
	static long maxSolveNanos = 0;

	private static final int SEATS = 4;
	private static final int HEART = Suit.HEART.ordinal();
	private static final int SPADE = Suit.SPADE.ordinal();
	private static final int DIAMOND = Suit.DIAMOND.ordinal();
	private static final int QUEEN_OF_SPADES = id(SPADE, CardValue.QUEEN.ordinal());
	private static final int JACK_OF_DIAMONDS = id(DIAMOND, CardValue.JACK.ordinal());

	/** Standings rank dominates the points margin -- a margin swing can never
	 * buy back a lost place. Margins are kept in thirds (3*mine - sum of the
	 * others), which stay far below this. */
	private static final int RANK_WEIGHT = 100_000;

	/** Result of one solve: each legal card's worst-case and average utility
	 * for the acting seat (lower is better). */
	public static final class Result {
		public final Map<Card, Integer> worst = new HashMap<Card, Integer>();
		public final Map<Card, Double> average = new HashMap<Card, Double>();
		public int deals;
		public long leaves;
	}

	private static int id(int suit, int rank) {
		return suit * 13 + rank;
	}

	static int id(Card c) {
		return id(c.suit.ordinal(), c.cardValue.ordinal());
	}

	private static int suitOf(int card) {
		return card / 13;
	}

	private static int rankOf(int card) {
		return card % 13;
	}

	// ------------------------------------------------------------------
	// Entry point
	// ------------------------------------------------------------------

	/**
	 * Solve for `me` choosing among `legalOptions`, with `myHand` this seat's
	 * full remaining hand, about to play into position `playPosition` (1 =
	 * leading). Returns null whenever the position is outside the budget or
	 * the inputs don't add up -- callers treat null as "no opinion".
	 */
	public static Result solve(Table_Cards tc, Player me, List<Card> myHand, List<Card> legalOptions,
			int playPosition) {
		if (myHand == null || legalOptions == null || legalOptions.isEmpty() || tc.players == null
				|| DISABLED_SEATS.contains(me.tbl_player)) {
			return null;
		}
		int myCount = myHand.size();
		if (myCount > MAX_CARDS_IN_HAND + 1 || myCount == 0) {
			return null;
		}
		if (legalOptions.size() < 2) {
			return null; // nothing to choose between
		}

		long started = System.nanoTime();
		int meSeat = me.tbl_player.ordinal();

		// Seats in this trick, in play order from the leader.
		int leader = seatAtPosition(tc.current_player.ordinal(), playPosition, 1);
		int[] trickCards = new int[SEATS];
		int trickLen = playPosition - 1;
		Card[] onTable = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		for (int i = 0; i < trickLen; i++) {
			if (onTable[i] == null) {
				return null;
			}
			trickCards[i] = id(onTable[i]);
		}

		// Everything seen: won piles, the trick in progress, my hand.
		long seen = 0;
		int[] hearts = new int[SEATS];
		boolean[] queen = new boolean[SEATS];
		boolean[] jack = new boolean[SEATS];
		for (PLAYER p : PLAYER.values()) {
			Player pl = tc.players.get(p);
			if (pl == null || pl.won_cards == null) {
				continue;
			}
			for (Card c : pl.won_cards.hand) {
				int k = id(c);
				seen |= 1L << k;
				if (suitOf(k) == HEART) {
					hearts[p.ordinal()]++;
				}
				if (k == QUEEN_OF_SPADES) {
					queen[p.ordinal()] = true;
				}
				if (k == JACK_OF_DIAMONDS) {
					jack[p.ordinal()] = true;
				}
			}
		}
		for (int i = 0; i < trickLen; i++) {
			seen |= 1L << trickCards[i];
		}
		long mine = 0;
		for (Card c : myHand) {
			mine |= 1L << id(c);
		}
		seen |= mine;

		long unseen = ((1L << 52) - 1) & ~seen;

		// Cards each opponent still holds: everybody started this trick with
		// my count (plus one, if I've already played -- I haven't, I'm acting
		// now), minus one for each seat that has already played to it.
		int[] need = new int[SEATS];
		int total = 0;
		for (int s = 0; s < SEATS; s++) {
			if (s == meSeat) {
				continue;
			}
			int pos = positionOf(leader, s);
			need[s] = (pos < playPosition) ? myCount - 1 : myCount;
			total += need[s];
		}
		if (total != Long.bitCount(unseen)) {
			return null; // bookkeeping disagrees -- refuse rather than guess
		}

		// Proven voids.
		boolean[][] voidIn = new boolean[SEATS][4];
		for (PLAYER p : PLAYER.values()) {
			for (Suit su : Suit.values()) {
				voidIn[p.ordinal()][su.ordinal()] = VoidTracker.isVoid(tc, p, su);
			}
		}

		List<long[]> deals = new ArrayList<long[]>();
		int[] unseenCards = bits(unseen);
		long[] partial = new long[SEATS];
		partial[meSeat] = mine;
		int cap = (myCount > MAX_CARDS_IN_HAND) ? MAX_DEALS_AT_FOUR : MAX_DEALS;
		if (!enumerate(unseenCards, 0, need, voidIn, meSeat, partial, deals, cap)) {
			return null; // too many possibilities to be worth it
		}
		if (deals.isEmpty()) {
			return null; // voids contradict counts -- trust nothing
		}

		int[] totals = new int[SEATS];
		for (PLAYER p : PLAYER.values()) {
			totals[p.ordinal()] = tc.players.get(p).p_total_points;
		}

		Search search = new Search(meSeat, totals);
		Result result = new Result();
		result.deals = deals.size();
		for (Card option : legalOptions) {
			int opt = id(option);
			int worst = Integer.MIN_VALUE;
			double sum = 0;
			for (long[] deal : deals) {
				State st = new State(deal, leader, trickCards, trickLen, tc.hearts_broken, hearts, queen, jack);
				int[] u = search.play(st, opt);
				if (u == null) {
					return null; // leaf budget blown
				}
				worst = Math.max(worst, u[meSeat]);
				sum += u[meSeat];
			}
			result.worst.put(option, Integer.valueOf(worst));
			result.average.put(option, Double.valueOf(sum / deals.size()));
		}
		result.leaves = search.leaves;
		solveCount++;
		maxSolveNanos = Math.max(maxSolveNanos, System.nanoTime() - started);
		return result;
	}

	// ------------------------------------------------------------------
	// Deal enumeration
	// ------------------------------------------------------------------

	private static boolean enumerate(int[] cards, int idx, int[] need, boolean[][] voidIn, int meSeat,
			long[] partial, List<long[]> out, int cap) {
		if (idx == cards.length) {
			out.add(partial.clone());
			return out.size() <= cap;
		}
		int c = cards[idx];
		int suit = suitOf(c);
		for (int s = 0; s < SEATS; s++) {
			if (s == meSeat || need[s] == 0 || voidIn[s][suit]) {
				continue;
			}
			need[s]--;
			partial[s] |= 1L << c;
			boolean ok = enumerate(cards, idx + 1, need, voidIn, meSeat, partial, out, cap);
			partial[s] &= ~(1L << c);
			need[s]++;
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static int[] bits(long mask) {
		int[] out = new int[Long.bitCount(mask)];
		int i = 0;
		while (mask != 0) {
			int b = Long.numberOfTrailingZeros(mask);
			out[i++] = b;
			mask &= mask - 1;
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Seat arithmetic (PLAYER.Next_Dealer() order: N, E, S, W)
	// ------------------------------------------------------------------

	/** Seat sitting at `targetPos` in a trick, given the seat at `knownPos`. */
	private static int seatAtPosition(int knownSeat, int knownPos, int targetPos) {
		return ((knownSeat + (targetPos - knownPos)) % SEATS + SEATS) % SEATS;
	}

	/** 1-based position of `seat` in a trick led by `leader`. */
	private static int positionOf(int leader, int seat) {
		return ((seat - leader) % SEATS + SEATS) % SEATS + 1;
	}

	// ------------------------------------------------------------------
	// Game state and search
	// ------------------------------------------------------------------

	private static final class State {
		final long[] hands;
		int leader;
		final int[] trick = new int[SEATS];
		int trickLen;
		boolean heartsBroken;
		final int[] hearts;
		final boolean[] queen;
		final boolean[] jack;

		State(long[] deal, int leader, int[] trick, int trickLen, boolean heartsBroken,
				int[] hearts, boolean[] queen, boolean[] jack) {
			this.hands = deal.clone();
			this.leader = leader;
			System.arraycopy(trick, 0, this.trick, 0, trickLen);
			this.trickLen = trickLen;
			this.heartsBroken = heartsBroken;
			this.hearts = hearts.clone();
			this.queen = queen.clone();
			this.jack = jack.clone();
		}
	}

	private static final class Search {
		final int meSeat;
		final int[] totals;
		long leaves;

		Search(int meSeat, int[] totals) {
			this.meSeat = meSeat;
			this.totals = totals;
		}

		/** Play `firstCard` for the seat to act, then search the rest. */
		int[] play(State st, int firstCard) {
			int seat = (st.leader + st.trickLen) % SEATS;
			return apply(st, seat, firstCard);
		}

		private int[] apply(State st, int seat, int card) {
			st.hands[seat] &= ~(1L << card);
			st.trick[st.trickLen++] = card;
			boolean wasBroken = st.heartsBroken;
			if (suitOf(card) == HEART) {
				st.heartsBroken = true;
			}

			int[] result;
			if (st.trickLen < SEATS) {
				result = search(st);
			} else {
				// Resolve the trick.
				int leadSuit = suitOf(st.trick[0]);
				int winPos = 0;
				for (int i = 1; i < SEATS; i++) {
					if (suitOf(st.trick[i]) == leadSuit && rankOf(st.trick[i]) > rankOf(st.trick[winPos])) {
						winPos = i;
					}
				}
				int winner = (st.leader + winPos) % SEATS;
				int oldLeader = st.leader;
				int addHearts = 0;
				boolean addQueen = false;
				boolean addJack = false;
				for (int i = 0; i < SEATS; i++) {
					int c = st.trick[i];
					if (suitOf(c) == HEART) {
						addHearts++;
					}
					if (c == QUEEN_OF_SPADES) {
						addQueen = true;
					}
					if (c == JACK_OF_DIAMONDS) {
						addJack = true;
					}
				}
				boolean oldQ = st.queen[winner];
				boolean oldJ = st.jack[winner];
				int[] savedTrick = st.trick.clone();
				st.hearts[winner] += addHearts;
				st.queen[winner] |= addQueen;
				st.jack[winner] |= addJack;
				st.leader = winner;
				st.trickLen = 0;

				if (st.hands[winner] == 0) {
					result = leaf(st);
				} else {
					result = search(st);
				}

				st.hearts[winner] -= addHearts;
				st.queen[winner] = oldQ;
				st.jack[winner] = oldJ;
				st.leader = oldLeader;
				System.arraycopy(savedTrick, 0, st.trick, 0, SEATS);
				st.trickLen = SEATS;
			}

			st.trickLen--;
			st.heartsBroken = wasBroken;
			st.hands[seat] |= 1L << card;
			return result;
		}

		/** Seat to act picks the card best for ITSELF; among its ties, the one
		 * worst for me (conservative). */
		private int[] search(State st) {
			int seat = (st.leader + st.trickLen) % SEATS;
			long legal = legalMoves(st, seat);
			int[] best = null;
			while (legal != 0) {
				int card = Long.numberOfTrailingZeros(legal);
				legal &= legal - 1;
				int[] u = apply(st, seat, card);
				if (u == null) {
					return null;
				}
				if (best == null || u[seat] < best[seat]
						|| (u[seat] == best[seat] && seat != meSeat && u[meSeat] > best[meSeat])) {
					best = u;
				}
			}
			return best;
		}

		private long legalMoves(State st, int seat) {
			long hand = st.hands[seat];
			if (st.trickLen == 0) {
				if (st.heartsBroken) {
					return hand;
				}
				long nonHearts = hand & ~suitMask(HEART);
				return nonHearts != 0 ? nonHearts : hand;
			}
			long follow = hand & suitMask(suitOf(st.trick[0]));
			return follow != 0 ? follow : hand;
		}

		private int[] leaf(State st) {
			if (++leaves > MAX_LEAVES) {
				return null;
			}
			int[] handScore = handScores(st.hearts, st.queen, st.jack);
			int[] finals = new int[SEATS];
			for (int s = 0; s < SEATS; s++) {
				finals[s] = totals[s] + handScore[s];
			}
			int[] u = new int[SEATS];
			for (int s = 0; s < SEATS; s++) {
				u[s] = utility(finals, s);
			}
			return u;
		}
	}

	private static long suitMask(int suit) {
		return ((1L << 13) - 1) << (suit * 13);
	}

	/** Mirrors Scoring.computeHandScores() exactly, on counts instead of Hands.
	 * EndgameSolverTest cross-checks the two. */
	static int[] handScores(int[] hearts, boolean[] queen, boolean[] jack) {
		int shooter = -1;
		for (int s = 0; s < SEATS; s++) {
			if (hearts[s] == Scoring.HEARTS_IN_DECK && queen[s]) {
				shooter = s;
			}
		}
		int[] out = new int[SEATS];
		for (int s = 0; s < SEATS; s++) {
			int score;
			if (shooter >= 0) {
				score = (s == shooter) ? Scoring.SHOOT_THE_MOON_POINTS : 0;
			} else {
				score = hearts[s] + (queen[s] ? Scoring.QUEEN_OF_SPADES_POINTS : 0);
			}
			if (jack[s]) {
				score += Scoring.JACK_OF_DIAMONDS_POINTS;
			}
			out[s] = score;
		}
		return out;
	}

	/** Lower is better. Standings place first (how many seats would finish the
	 * hand with a strictly lower game total than me), then my margin against
	 * the other three's average, kept in thirds so it stays an exact int. */
	static int utility(int[] finals, int seat) {
		int behind = 0;
		int sumOthers = 0;
		for (int s = 0; s < SEATS; s++) {
			if (s == seat) {
				continue;
			}
			sumOthers += finals[s];
			if (finals[s] < finals[seat]) {
				behind++;
			}
		}
		return behind * RANK_WEIGHT + (3 * finals[seat] - sumOthers);
	}
}
