package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Committed coverage for EndgameSolver (2026-10-08). Every scenario builds a
 * genuine late-hand table: all 52 cards are accounted for (hands, won piles,
 * the trick in progress), because the solver refuses to run when its card
 * bookkeeping doesn't add up -- and a test that only passed because the
 * solver quietly returned null would prove nothing.
 */
public final class EndgameSolverTest {

	private EndgameSolverTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		handScoresMatchScoring();
		utilityRanksStandingsBeforeMargin();
		queenHolderKeepsHerOnlyWhenItKeepsHimAheadOfTheShooter();
		queenDumpTimingIsSolved();
		pickLeadFollowsTheSolverWhenCountingCards();
		noBonusWhenNotCountingCards();
		followingMidTrickSolves();
		refusesWhenBookkeepingIsWrong();
		refusesWhenTheHandIsTooBig();
		headlessGamesRunCleanWithTheSolverActive();
		return TestKit.summarize();
	}

	// ------------------------------------------------------------------

	private static void handScoresMatchScoring() {
		TestKit.suite("handScoresMatchScoring");
		Random rng = new Random(7);
		int mismatches = 0;
		for (int trial = 0; trial < 2000; trial++) {
			Map<PLAYER, Hand> piles = new EnumMap<PLAYER, Hand>(PLAYER.class);
			for (PLAYER p : PLAYER.values()) {
				piles.put(p, new Hand());
			}
			int[] hearts = new int[4];
			boolean[] queen = new boolean[4];
			boolean[] jack = new boolean[4];
			// Every point card goes to a random seat; a quarter of the time all
			// of them go to one seat, so moons get exercised too.
			int moonSeat = rng.nextInt(4) == 0 ? rng.nextInt(4) : -1;
			for (CardValue v : CardValue.values()) {
				int s = moonSeat >= 0 ? moonSeat : rng.nextInt(4);
				piles.get(PLAYER.values()[s]).addX(card(v, Suit.HEART));
				hearts[s]++;
			}
			int qs = moonSeat >= 0 ? moonSeat : rng.nextInt(4);
			piles.get(PLAYER.values()[qs]).addX(card(CardValue.QUEEN, Suit.SPADE));
			queen[qs] = true;
			int js = rng.nextInt(4);
			piles.get(PLAYER.values()[js]).addX(card(CardValue.JACK, Suit.DIAMOND));
			jack[js] = true;

			Map<PLAYER, Integer> real = Scoring.computeHandScores(piles);
			int[] mine = EndgameSolver.handScores(hearts, queen, jack);
			for (PLAYER p : PLAYER.values()) {
				if (real.get(p).intValue() != mine[p.ordinal()]) {
					mismatches++;
				}
			}
		}
		TestKit.checkEquals("the solver's scoring agrees with Scoring.computeHandScores on 2000 random hands",
				0, mismatches);
	}

	private static void utilityRanksStandingsBeforeMargin() {
		TestKit.suite("utilityRanksStandingsBeforeMargin");
		// Seat 0 finishing first by 1 point beats finishing second by 40.
		int first = EndgameSolver.utility(new int[] { 50, 51, 90, 90 }, 0);
		int second = EndgameSolver.utility(new int[] { 10, 9, 90, 90 }, 0);
		TestKit.check("first place by a hair is better than second place by a mile", first < second);
		// Same place: the smaller margin wins.
		int fewer = EndgameSolver.utility(new int[] { 10, 20, 20, 20 }, 0);
		int more = EndgameSolver.utility(new int[] { 15, 20, 20, 20 }, 0);
		TestKit.check("in the same place, fewer points is better", fewer < more);
	}

	/** The user's rule (2026-10-08): a Queen holder eats her to stop a moon
	 * only if the 13 points still leave it ahead of the would-be shooter. */
	private static void queenHolderKeepsHerOnlyWhenItKeepsHimAheadOfTheShooter() {
		TestKit.suite("queenHolderKeepsHerOnlyWhenItKeepsHimAheadOfTheShooter");
		// Seat 1 holds the Queen, seat 0 is shooting with 12 Hearts banked.
		// Eating her: seat 1 +13, the shooter keeps its 12 Hearts.
		// Letting the moon through: shooter -26, seat 1 +0.
		// 1. Holder 10, shooter 30: the moon would put the shooter (4) ahead of
		//    the holder; eating her (23 vs 42) keeps the holder in front.
		TestKit.check("eating the Queen keeps it ahead of the shooter, so it eats her",
				eatQueen(30, 10) < letMoon(30, 10));
		// 2. Holder 10, shooter 40: even after a moon the shooter (14) stays
		//    behind, so the 13 points buy nothing -- dump her.
		TestKit.check("still ahead after the moon anyway, so it dumps her", letMoon(40, 10) < eatQueen(40, 10));
		// 3. Holder 30, shooter 20: behind the shooter whether it eats her
		//    (43 vs 32) or not -- dump her.
		TestKit.check("behind the shooter either way, so it dumps her", letMoon(20, 30) < eatQueen(20, 30));
	}

	/** Holder's utility (seat 1) if it eats the Queen: +13 to it, the shooter
	 * (seat 0) keeps its 12 Hearts. Seats 2 and 3 sit well back at 60. */
	private static int eatQueen(int shooterTotal, int holderTotal) {
		return EndgameSolver.utility(new int[] { shooterTotal + 12, holderTotal + 13, 60, 60 }, 1);
	}

	/** ...and if it lets the moon through: shooter -26, everyone else +0. */
	private static int letMoon(int shooterTotal, int holderTotal) {
		return EndgameSolver.utility(new int[] { shooterTotal - 26, holderTotal, 60, 60 }, 1);
	}

	/** South leads, 2 cards each, holding the Queen of Spades and the 2 of
	 * Hearts. Every other Spade is gone. Leading the Queen wins her outright;
	 * leading the Heart loses the lead to North's 5, after which a Club lead
	 * lets South discard the Queen onto someone else. */
	private static void queenDumpTimingIsSolved() {
		TestKit.suite("queenDumpTimingIsSolved");
		Scenario s = queenScenario();
		EndgameSolver.Result r = EndgameSolver.solve(s.tc, s.me, s.myHand.hand, s.myHand.hand, 1);
		TestKit.check("the solver runs on this position", r != null);
		if (r == null) {
			return;
		}
		int queenLead = r.worst.get(s.queen).intValue();
		int heartLead = r.worst.get(s.heart).intValue();
		TestKit.check("leading the Heart is better in the worst case than leading the Queen",
				heartLead < queenLead);
		Map<Card, Double> bonus = AICardScorer.endgameBonus(s.tc, s.me, s.myHand.hand, s.myHand.hand, 1);
		TestKit.check("the Heart lead gets the worst-case bonus", bonus.containsKey(s.heart));
		TestKit.check("the Queen lead gets nothing", !bonus.containsKey(s.queen));
	}

	private static void pickLeadFollowsTheSolverWhenCountingCards() {
		TestKit.suite("pickLeadFollowsTheSolverWhenCountingCards");
		Scenario s = queenScenario();
		Card chosen = AICardScorer.pickLead(s.myHand.hand, s.myHand, s.tc, s.me);
		TestKit.checkEquals("a counting seat leads the Heart and keeps the Queen to discard", s.heart, chosen);
	}

	private static void noBonusWhenNotCountingCards() {
		TestKit.suite("noBonusWhenNotCountingCards");
		Scenario s = queenScenario();
		s.me.countsCardsThisHand = false;
		Map<Card, Double> bonus = AICardScorer.endgameBonus(s.tc, s.me, s.myHand.hand, s.myHand.hand, 1);
		TestKit.check("a seat not counting cards this hand gets no solver bonus", bonus.isEmpty());
	}

	/** Same deal, but West has led a Club and South is following, void. */
	private static void followingMidTrickSolves() {
		TestKit.suite("followingMidTrickSolves");
		Scenario s = queenScenario();
		// West leads 9C out of its hand, North 4C, East 7C; South to play 4th.
		s.tc.who_leads = PLAYER.WEST;
		Card w = s.take(PLAYER.WEST, CardValue.NINE, Suit.CLUB);
		Card n = s.take(PLAYER.NORTH, CardValue.FOUR, Suit.CLUB);
		Card e = s.take(PLAYER.EAST, CardValue.SEVEN, Suit.CLUB);
		s.tc.p1_card = w;
		s.tc.p2_card = n;
		s.tc.p3_card = e;
		s.tc.current_player = PLAYER.SOUTH;
		EndgameSolver.Result r = EndgameSolver.solve(s.tc, s.me, s.myHand.hand, s.myHand.hand, 4);
		TestKit.check("the solver runs when following mid-trick", r != null);
		if (r == null) {
			return;
		}
		// Every Spade but mine is gone and I'm void in Clubs, so the Queen
		// lands on somebody else whichever card I discard first -- the solver
		// must see that, and award nothing.
		TestKit.checkEquals("both discards are scored", 2, r.worst.size());
		TestKit.checkEquals("both discards come out the same",
				r.worst.get(s.queen), r.worst.get(s.heart));
		Map<Card, Double> bonus = AICardScorer.endgameBonus(s.tc, s.me, s.myHand.hand, s.myHand.hand, 4);
		TestKit.check("no bonus when nothing separates the cards", bonus.isEmpty());
	}

	private static void refusesWhenBookkeepingIsWrong() {
		TestKit.suite("refusesWhenBookkeepingIsWrong");
		Scenario s = queenScenario();
		// Lose a card from a won pile: 51 cards accounted for, not 52.
		s.tc.players.get(PLAYER.EAST).won_cards.hand.remove(0);
		TestKit.check("a missing card makes the solver refuse rather than guess",
				EndgameSolver.solve(s.tc, s.me, s.myHand.hand, s.myHand.hand, 1) == null);
	}

	private static void refusesWhenTheHandIsTooBig() {
		TestKit.suite("refusesWhenTheHandIsTooBig");
		Table_Cards tc = baseTable();
		List<Card> six = new ArrayList<Card>();
		for (int i = 0; i < 6; i++) {
			six.add(card(CardValue.values()[i], Suit.CLUB));
		}
		Player me = tc.players.get(PLAYER.SOUTH);
		TestKit.check("six cards in hand is outside the budget",
				EndgameSolver.solve(tc, me, six, six, 1) == null);
	}

	/** Full AI-vs-AI games with the solver live on every seat: no crash, no
	 * illegal play (HeadlessSimulator uses the real Rules), and points still
	 * conserved per hand. */
	private static void headlessGamesRunCleanWithTheSolverActive() {
		TestKit.suite("headlessGamesRunCleanWithTheSolverActive");
		Player_Type[] types = { Player_Type.NORMAL, Player_Type.AGGRESSIVE, Player_Type.NORMAL,
				Player_Type.AVOIDS_QUEEN };
		java.io.PrintStream out = System.out;
		boolean crashed = false;
		long before = EndgameSolver.solveCount;
		try {
			System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
			// 100% counting on every seat -- at the default 0% the solver
			// never runs and this check would pass without testing anything.
			HeadlessSimulator.playHeadlessGame(40, types, new int[] { 100, 100, 100, 100 });
		} catch (RuntimeException ex) {
			crashed = true;
			out.println("headless run threw: " + ex);
		} finally {
			System.setOut(out);
		}
		TestKit.check("40 full hands play through with the solver active", !crashed);
		TestKit.check("the solver actually ran during those hands", EndgameSolver.solveCount > before);
	}

	// ------------------------------------------------------------------
	// Fixtures
	// ------------------------------------------------------------------

	private static final class Scenario {
		Table_Cards tc;
		Player me;
		Hand myHand;
		Map<PLAYER, Hand> hands = new EnumMap<PLAYER, Hand>(PLAYER.class);
		Card queen;
		Card heart;

		Card take(PLAYER seat, CardValue v, Suit s) {
			for (Card c : hands.get(seat).hand) {
				if (c.cardValue == v && c.suit == s) {
					hands.get(seat).hand.remove(c);
					return c;
				}
			}
			throw new IllegalStateException("fixture: " + seat + " lacks " + v + " " + s);
		}
	}

	/** Trick 12 of 13: two cards each, Hearts broken, nobody on 0 points
	 * shooting. South: QS 2H. North: 5H 4C. East: 6C 7C. West: 8C 9C.
	 * Proven voids pin the deal down: East and West have shown out of Hearts
	 * and Spades, North and South out of Clubs... except North, who still has
	 * a Club -- so North is void only in Spades and Diamonds. */
	private static Scenario queenScenario() {
		Scenario s = new Scenario();
		s.tc = baseTable();
		s.me = s.tc.players.get(PLAYER.SOUTH);
		s.me.p_type = Player_Type.NORMAL;
		s.me.countsCardsThisHand = true;

		s.queen = card(CardValue.QUEEN, Suit.SPADE);
		s.heart = card(CardValue.TWO, Suit.HEART);
		s.myHand = hand(s.queen, s.heart);
		s.hands.put(PLAYER.SOUTH, s.myHand);
		s.hands.put(PLAYER.NORTH, hand(card(CardValue.FIVE, Suit.HEART), card(CardValue.FOUR, Suit.CLUB)));
		s.hands.put(PLAYER.EAST, hand(card(CardValue.SIX, Suit.CLUB), card(CardValue.SEVEN, Suit.CLUB)));
		s.hands.put(PLAYER.WEST, hand(card(CardValue.EIGHT, Suit.CLUB), card(CardValue.NINE, Suit.CLUB)));

		// Every other card is in a won pile. Hearts split East 6 / West 5 so
		// no moon is on; the Jack of Diamonds sits with North.
		List<Card> rest = new ArrayList<Card>();
		for (Suit su : Suit.values()) {
			for (CardValue v : CardValue.values()) {
				if (!inAnyHand(s, v, su)) {
					rest.add(card(v, su));
				}
			}
		}
		int heartsToEast = 6;
		for (Card c : rest) {
			PLAYER owner;
			if (c.suit == Suit.HEART) {
				owner = heartsToEast-- > 0 ? PLAYER.EAST : PLAYER.WEST;
			} else if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
				owner = PLAYER.NORTH;
			} else {
				owner = PLAYER.values()[(c.cardValue.ordinal() + c.suit.ordinal()) % 4];
			}
			s.tc.players.get(owner).won_cards.addX(c);
		}

		s.tc.trick_nbr = 11;
		s.tc.hearts_broken = true;
		s.tc.current_player = PLAYER.SOUTH;
		s.tc.who_leads = PLAYER.SOUTH;
		voids(s.tc, PLAYER.EAST, Suit.HEART, Suit.SPADE, Suit.DIAMOND);
		voids(s.tc, PLAYER.WEST, Suit.HEART, Suit.SPADE, Suit.DIAMOND);
		voids(s.tc, PLAYER.NORTH, Suit.SPADE, Suit.DIAMOND);
		voids(s.tc, PLAYER.SOUTH, Suit.CLUB, Suit.DIAMOND);
		return s;
	}

	private static boolean inAnyHand(Scenario s, CardValue v, Suit su) {
		for (Hand h : s.hands.values()) {
			for (Card c : h.hand) {
				if (c.cardValue == v && c.suit == su) {
					return true;
				}
			}
		}
		return false;
	}

	private static void voids(Table_Cards tc, PLAYER p, Suit... suits) {
		for (Suit su : suits) {
			tc.void_suits.get(p).add(su);
		}
	}

	private static Table_Cards baseTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			players.put(p, pl);
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.players = players;
		tc.void_suits = voidSuits;
		tc.current_player = PLAYER.SOUTH;
		return tc;
	}

	private static Hand hand(Card... cs) {
		Hand h = new Hand();
		for (Card c : cs) {
			h.addX(c);
		}
		return h;
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}
}
