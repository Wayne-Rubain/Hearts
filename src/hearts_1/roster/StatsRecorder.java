package hearts_1.roster;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hearts_1.Card;
import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;

/**
 * The GameEventBus listener that turns seat-based game events into
 * identity-based counters in the StatsStore, added 2026-08-16. This is
 * the single recording layer both planned consumers share (the stats
 * screen now, AI opponent-modelling later) -- the whole reason the event
 * bus was built ahead of either (see GameEvent.java's header).
 *
 * Seats are transient, identities are not: every event arrives keyed by
 * PLAYER (NORTH/EAST/SOUTH/WEST), and this class translates through the
 * current seat assignment (set by Hearts_JFrame whenever the roster
 * dropdowns are applied) before recording. If a seat has no assigned
 * identity -- pre-GUI unit tests, or events fired before the first deal
 * -- that seat's updates are silently skipped rather than misattributed.
 *
 * Persists the store at HAND_ENDED and GAME_ENDED (not on exit), per the
 * design doc's write-timing rule: a force-closed Swing app should never
 * lose more than the hand in progress.
 */
public final class StatsRecorder implements GameEventBus.Listener {

	/** A broken moon only counts as a STOPPED moon (rather than the
	 * routine first-vs-second point capture of an ordinary hand) when the
	 * former shooter had already banked at least this many Hearts --
	 * MOON_BROKEN fires in nearly every hand, so without this filter the
	 * "times I stopped a moon" stat would be meaningless noise. */
	static final int STOPPED_MOON_MIN_HEARTS = 5;

	/** Comeback stat (2026-10-10): the standings are snapshotted the first
	 * time any seat's running total reaches this -- half of TARGET_SCORE,
	 * Wayne's chosen "midpoint". Whoever is LAST (highest total) at that
	 * moment gets a comeback chance; winning the game converts it. */
	static final int COMEBACK_MIDPOINT = 50;
	/** ELO (2026-10-10): every identity starts here. */
	static final int ELO_START = 1500;
	/** Total rating at stake per game, split evenly across the three
	 * head-to-head comparisons a 4-player game contains -- a standard
	 * multiplayer-ELO adaptation, so one game moves a rating about as far
	 * as one ordinary two-player game would. */
	static final double ELO_K_PER_GAME = 32;

	private final StatsStore store;
	private final File saveDir;
	private final Map<PLAYER, String> seatIds = new EnumMap<PLAYER, String>(PLAYER.class);
	private final Map<PLAYER, Integer> tricksWonThisHand = new EnumMap<PLAYER, Integer>(PLAYER.class);
	/** Point cards passed this hand, by card, awaiting the trick that
	 * captures them (2026-10-10 passing-outcome stat). Seat-keyed like the
	 * events themselves; translated to identities when recorded. */
	private final Map<String, PassedCard> passedPointCards = new HashMap<String, PassedCard>();
	private boolean midpointTaken;
	private final Set<String> lastAtMidpoint = new HashSet<String>();
	/** Running totals as of the end of the previous hand = the standings
	 * this hand STARTED from (OpponentModel's situational moon counters).
	 * Null before the first hand of a session; reset on a new game. */
	private Map<PLAYER, Integer> startTotals;
	/** Seats whose moon was stopped this hand (5+ Hearts banked first). */
	private final Set<PLAYER> stoppedThisHand = new HashSet<PLAYER>();

	/** A hand score this low can only be a completed moon (-26, or -36
	 * with the Jack): no ordinary hand goes below the Jack's -10. Lets
	 * HAND_ENDED see the moon without waiting for MOON_SUCCEEDED, which is
	 * published after it. */
	private static final int MOON_HAND_SCORE = -26;

	private static final class PassedCard {
		final PLAYER from;
		final PLAYER to;
		final int points;

		PassedCard(PLAYER from, PLAYER to, int points) {
			this.from = from;
			this.to = to;
			this.points = points;
		}
	}

	public StatsRecorder(StatsStore store, File saveDir) {
		this.store = store;
		this.saveDir = saveDir;
	}

	/** Called by Hearts_JFrame whenever the seat dropdowns are applied
	 * (start of every deal). SOUTH is always Roster.HUMAN_ID. */
	public void setSeatId(PLAYER seat, String id) {
		seatIds.put(seat, id);
	}

	/** The roster identity currently seated at `seat` (null before the first deal). */
	public String seatId(PLAYER seat) {
		return seatIds.get(seat);
	}

	private String idOf(Object seatFact) {
		return (seatFact instanceof PLAYER) ? seatIds.get((PLAYER) seatFact) : null;
	}

	@Override
	public void onEvent(GameEvent event, Map<String, Object> facts) {
		switch (event) {
			case TRICK_WON: {
				PLAYER winner = (PLAYER) facts.get("winner");
				Integer sofar = tricksWonThisHand.get(winner);
				tricksWonThisHand.put(winner, Integer.valueOf(sofar == null ? 1 : sofar.intValue() + 1));
				@SuppressWarnings("unchecked")
				Map<PLAYER, Card> cards = (Map<PLAYER, Card>) facts.get("cards");
				if (cards != null) {
					recordPassOutcomes(winner, cards);
					recordLead((PLAYER) facts.get("leader"), winner, cards);
				}
				break;
			}
			case QUEEN_DROPPED_ON: {
				String dropper = idOf(facts.get("droppedBySeat"));
				String winner = idOf(facts.get("winner"));
				if (dropper != null && winner != null && !dropper.equals(winner)) {
					store.addPair(dropper, winner, "queenDroppedOn", 1);
				}
				break;
			}
			case SELF_ATE_QUEEN: {
				String seat = idOf(facts.get("seat"));
				if (seat != null) {
					store.add(seat, "selfAteQueen", 1);
				}
				break;
			}
			case JACK_CAPTURED: {
				String winner = idOf(facts.get("winner"));
				String player = idOf(facts.get("playedBySeat"));
				if (winner != null) {
					store.add(winner, "jacksCaptured", 1);
				}
				if (winner != null && player != null && !player.equals(winner)) {
					store.addPair(player, winner, "jackGivenTo", 1);
				}
				break;
			}
			case MOON_COMMITTED: {
				String seat = idOf(facts.get("seat"));
				if (seat != null) {
					store.add(seat, "moonCommitted", 1);
				}
				break;
			}
			case MOON_SUCCEEDED: {
				String shooter = idOf(facts.get("shooter"));
				if (shooter != null) {
					store.add(shooter, "moonSuccesses", 1);
				}
				break;
			}
			case MOON_BROKEN: {
				Integer hearts = (Integer) facts.get("shooterHearts");
				if (hearts == null || hearts.intValue() < STOPPED_MOON_MIN_HEARTS) {
					break; // routine first-vs-second capture, not a stopped moon
				}
				String breaker = idOf(facts.get("breaker"));
				String shooter = idOf(facts.get("formerShooter"));
				if (breaker != null && shooter != null) {
					store.addPair(breaker, shooter, "moonStopped", 1);
					store.add(shooter, "moonsStoppedOnMe", 1);
				}
				Object formerSeat = facts.get("formerShooter");
				if (formerSeat instanceof PLAYER) {
					stoppedThisHand.add((PLAYER) formerSeat);
				}
				break;
			}
			case PASS_RECEIVED: {
				rememberPassedPointCards(facts);
				Object cards = facts.get("cards");
				if (cards != null && cards.toString().contains("QUEEN SPADE")) {
					String from = idOf(facts.get("from"));
					String to = idOf(facts.get("recipient"));
					if (from != null && to != null) {
						store.addPair(from, to, "queenPassedTo", 1);
					}
				}
				break;
			}
			case HAND_ENDED:
				onHandEnded(facts);
				break;
			case GAME_ENDED:
				onGameEnded(facts);
				break;
			default:
				break;
		}
	}

	private void onHandEnded(Map<String, Object> facts) {
		@SuppressWarnings("unchecked")
		Map<PLAYER, Integer> scores = (Map<PLAYER, Integer>) facts.get("scores");
		if (scores == null) {
			tricksWonThisHand.clear();
			passedPointCards.clear();
			stoppedThisHand.clear();
			return;
		}
		@SuppressWarnings("unchecked")
		Map<PLAYER, Integer> totals = (Map<PLAYER, Integer>) facts.get("totals");
		boolean firstHandOfGame = isFirstHandOfGame(scores, totals);
		recordMoonSituations(scores, firstHandOfGame);
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			Integer scoreBox = scores.get(seat);
			if (id == null || scoreBox == null) {
				continue;
			}
			int score = scoreBox.intValue();
			boolean firstHand = store.get(id, "handsPlayed") == 0;
			store.add(id, "handsPlayed", 1);
			store.add(id, "totalPointsTaken", score);
			if (score <= 0) {
				store.add(id, "perfectHands", 1); // zero penalty points taken (negative = Jack bonus)
			}
			Integer tricks = tricksWonThisHand.get(seat);
			if (tricks == null || tricks.intValue() == 0) {
				store.add(id, "slamHands", 1); // took no tricks at all
			}
			if (firstHand || score < store.get(id, "bestHandScore")) {
				store.put(id, "bestHandScore", score);
			}
			if (firstHand || score > store.get(id, "worstHandScore")) {
				store.put(id, "worstHandScore", score);
			}
		}
		forEachOrderedPair("handsTogether");
		tricksWonThisHand.clear();
		passedPointCards.clear();
		stoppedThisHand.clear();
		snapshotMidpoint(totals, firstHandOfGame);
		if (totals != null) {
			startTotals = new EnumMap<PLAYER, Integer>(totals);
		}
		store.save(saveDir);
	}

	/** A game's first hand: every running total equals that hand's score.
	 * Also recovers cleanly if a game was abandoned without a GAME_ENDED. */
	private static boolean isFirstHandOfGame(Map<PLAYER, Integer> scores, Map<PLAYER, Integer> totals) {
		if (totals == null) {
			return false;
		}
		for (PLAYER seat : PLAYER.values()) {
			Integer t = totals.get(seat);
			Integer s = scores.get(seat);
			if (t == null || s == null || !t.equals(s)) {
				return false;
			}
		}
		return true;
	}

	/** OpponentModel's situational counters: for every seat, the Place and
	 * distance band it STARTED this hand in, and whether it made a moon
	 * attempt (completed, or stopped after 5+ Hearts). */
	private void recordMoonSituations(Map<PLAYER, Integer> scores, boolean firstHandOfGame) {
		Map<PLAYER, Integer> start = (firstHandOfGame || startTotals == null) ? null : startTotals;
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			Integer score = scores.get(seat);
			if (id == null || score == null) {
				continue;
			}
			boolean attempted = score.intValue() <= MOON_HAND_SCORE || stoppedThisHand.contains(seat);
			// No start standings (a game's first hand) = everyone level.
			OpponentModel.Place place = OpponentModel.placeOf(seat, start);
			int band = OpponentModel.bandOf(seat, start);
			OpponentModel.recordHand(store, id, place, band, attempted);
		}
	}

	/** Comeback stat: on a game's first hand start fresh; then, the first
	 * time any total reaches COMEBACK_MIDPOINT, remember who is last. */
	private void snapshotMidpoint(Map<PLAYER, Integer> totals, boolean firstHandOfGame) {
		if (totals == null) {
			return;
		}
		int highest = Integer.MIN_VALUE;
		for (PLAYER seat : PLAYER.values()) {
			Integer t = totals.get(seat);
			if (t == null) {
				return;
			}
			highest = Math.max(highest, t.intValue());
		}
		if (firstHandOfGame) {
			midpointTaken = false;
			lastAtMidpoint.clear();
		}
		if (midpointTaken || highest < COMEBACK_MIDPOINT) {
			return;
		}
		midpointTaken = true;
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			if (id != null && totals.get(seat).intValue() == highest) {
				lastAtMidpoint.add(id); // ties for last all get the chance
			}
		}
	}

	/** Penalty value of one card under this ruleset -- raw, before any
	 * moon-shot inversion (the inversion is a whole-hand fact, not a
	 * per-trick one). */
	private static int pointsOf(Card c) {
		if (c.suit == Suit.HEART) {
			return 1;
		}
		if (c.suit == Suit.SPADE && c.cardValue == CardValue.QUEEN) {
			return 13;
		}
		if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
			return -10;
		}
		return 0;
	}

	private static String key(Card c) {
		return c.cardValue + " " + c.suit;
	}

	/** Passing outcome: remember every PENALTY card (Hearts, the Queen)
	 * passed this hand so the trick that captures it can be attributed. */
	private void rememberPassedPointCards(Map<String, Object> facts) {
		@SuppressWarnings("unchecked")
		List<Card> passed = (List<Card>) facts.get("passedCards");
		PLAYER from = (PLAYER) facts.get("from");
		PLAYER to = (PLAYER) facts.get("recipient");
		if (passed == null || from == null || to == null) {
			return;
		}
		String fromId = seatIds.get(from);
		for (Card c : passed) {
			int pts = pointsOf(c);
			if (pts <= 0) {
				continue;
			}
			passedPointCards.put(key(c), new PassedCard(from, to, pts));
			if (fromId != null) {
				store.add(fromId, "passPointsGiven", pts);
			}
		}
	}

	/** Where did my passed point cards end up: on the recipient (the pass
	 * did its job), back in my own pile (it backfired), or with a third
	 * seat? */
	private void recordPassOutcomes(PLAYER winner, Map<PLAYER, Card> cards) {
		for (Card c : cards.values()) {
			if (c == null) {
				continue;
			}
			PassedCard p = passedPointCards.remove(key(c));
			if (p == null) {
				continue;
			}
			String fromId = seatIds.get(p.from);
			if (fromId == null) {
				continue;
			}
			if (winner == p.to) {
				store.add(fromId, "passPointsLanded", p.points);
				String toId = seatIds.get(p.to);
				if (toId != null) {
					store.addPair(fromId, toId, "passPointsLandedOn", p.points);
				}
			} else if (winner == p.from) {
				store.add(fromId, "passPointsBackfired", p.points);
			} else {
				store.add(fromId, "passPointsElsewhere", p.points);
			}
		}
	}

	/** Lead success: a led trick counts as CLEAN when the leader came away
	 * with no penalty points from it -- either somebody else won it, or the
	 * leader won it for zero or better (the Jack's -10 counts as clean). */
	private void recordLead(PLAYER leader, PLAYER winner, Map<PLAYER, Card> cards) {
		String id = (leader == null) ? null : seatIds.get(leader);
		Card led = (leader == null) ? null : cards.get(leader);
		if (id == null || led == null) {
			return;
		}
		int trickPoints = 0;
		for (Card c : cards.values()) {
			if (c != null) {
				trickPoints += pointsOf(c);
			}
		}
		boolean clean = winner != leader || trickPoints <= 0;
		String suit = led.suit.name();
		store.add(id, "leads", 1);
		store.add(id, "leads_" + suit, 1);
		if (clean) {
			store.add(id, "leadsClean", 1);
			store.add(id, "leadsClean_" + suit, 1);
		}
	}

	private void onGameEnded(Map<String, Object> facts) {
		@SuppressWarnings("unchecked")
		Map<PLAYER, Integer> totals = (Map<PLAYER, Integer>) facts.get("totals");
		if (totals == null) {
			return;
		}
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			Integer myTotal = totals.get(seat);
			if (id == null || myTotal == null) {
				continue;
			}
			// Placement = 1 + how many seats finished strictly ahead (lower
			// total). Ties share the better placement, matching how
			// checkForGameOver() itself treats tied winners.
			int placement = 1;
			for (PLAYER other : PLAYER.values()) {
				Integer theirs = totals.get(other);
				if (other != seat && theirs != null && theirs.intValue() < myTotal.intValue()) {
					placement++;
				}
			}
			store.add(id, "gamesPlayed", 1);
			store.add(id, "place" + placement, 1);
			if (placement == 1) {
				store.add(id, "gamesWon", 1);
				int streak = store.get(id, "currentWinStreak") + 1;
				store.put(id, "currentWinStreak", streak);
				if (streak > store.get(id, "longestWinStreak")) {
					store.put(id, "longestWinStreak", streak);
				}
			} else {
				store.put(id, "currentWinStreak", 0);
			}
			// Pairwise finished-ahead-of, for the per-opponent win ratio.
			for (PLAYER other : PLAYER.values()) {
				String otherId = seatIds.get(other);
				Integer theirs = totals.get(other);
				if (other == seat || otherId == null || theirs == null) {
					continue;
				}
				if (myTotal.intValue() < theirs.intValue()) {
					store.addPair(id, otherId, "gamesBeaten", 1);
				}
			}
		}
		forEachOrderedPair("gamesTogether");
		recordComebacks(totals);
		updateElo(totals);
		startTotals = null; // the next hand starts a new game, everyone level
		store.save(saveDir);
	}

	private void recordComebacks(Map<PLAYER, Integer> totals) {
		int lowest = Integer.MAX_VALUE;
		for (Integer t : totals.values()) {
			if (t != null) {
				lowest = Math.min(lowest, t.intValue());
			}
		}
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			Integer t = totals.get(seat);
			if (id == null || t == null || !lastAtMidpoint.contains(id)) {
				continue;
			}
			store.add(id, "comebackChances", 1);
			if (t.intValue() == lowest) {
				store.add(id, "comebacksWon", 1);
			}
		}
		midpointTaken = false;
		lastAtMidpoint.clear();
	}

	/** Current rating, ELO_START for an identity that has never finished a
	 * game. */
	public static int eloOf(StatsStore store, String id) {
		return store.get(id, "eloGames") == 0 ? ELO_START : store.get(id, "elo");
	}

	/** ELO update: each seat is scored head-to-head against each other
	 * seat (finished lower = 1, tie = 0.5, higher = 0), against the
	 * expectation from PRE-game ratings, so the result never depends on
	 * the order seats are processed. */
	private void updateElo(Map<PLAYER, Integer> totals) {
		Map<PLAYER, Integer> before = new EnumMap<PLAYER, Integer>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			String id = seatIds.get(seat);
			if (id != null && totals.get(seat) != null) {
				before.put(seat, Integer.valueOf(eloOf(store, id)));
			}
		}
		int opponents = before.size() - 1;
		if (opponents < 1) {
			return;
		}
		double kPerPair = ELO_K_PER_GAME / opponents;
		for (PLAYER me : before.keySet()) {
			double rMe = before.get(me).intValue();
			int myTotal = totals.get(me).intValue();
			double delta = 0;
			for (PLAYER other : before.keySet()) {
				if (other == me) {
					continue;
				}
				double rOther = before.get(other).intValue();
				int theirTotal = totals.get(other).intValue();
				double actual = myTotal < theirTotal ? 1 : (myTotal == theirTotal ? 0.5 : 0);
				double expected = 1 / (1 + Math.pow(10, (rOther - rMe) / 400));
				delta += kPerPair * (actual - expected);
			}
			String id = seatIds.get(me);
			store.put(id, "elo", (int) Math.round(rMe + delta));
			store.add(id, "eloGames", 1);
		}
	}

	private void forEachOrderedPair(String counter) {
		for (PLAYER a : PLAYER.values()) {
			String aId = seatIds.get(a);
			if (aId == null) {
				continue;
			}
			for (PLAYER b : PLAYER.values()) {
				String bId = seatIds.get(b);
				if (a != b && bId != null) {
					store.addPair(aId, bId, counter, 1);
				}
			}
		}
	}
}
