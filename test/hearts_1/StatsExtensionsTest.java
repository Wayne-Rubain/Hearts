package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;
import hearts_1.roster.OpponentModel;
import hearts_1.roster.Roster;
import hearts_1.roster.StatsRecorder;
import hearts_1.roster.StatsReport;
import hearts_1.roster.StatsStore;

/**
 * Committed coverage for the 2026-10-10 stats extensions in
 * hearts_1.roster.StatsRecorder: lead success (clean = the leader took no
 * penalty points from the trick), passing outcomes (where each passed
 * penalty card ended up), the comeback stat (last when anyone first
 * reaches 50, then won), and the multiplayer ELO update. Drives synthetic
 * events straight into onEvent(), same as StatsRecorderTest.
 */
public final class StatsExtensionsTest {

	private StatsExtensionsTest() { }

	private static final String ME = Roster.HUMAN_ID;

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			leadSuccess();
			passOutcomes();
			comebacksAndElo();
			situationalMoonCounters();
		} catch (IOException e) {
			TestKit.check("unexpected IOException: " + e.getMessage(), false);
		}
		return TestKit.summarize();
	}

	private static void leadSuccess() throws IOException {
		TestKit.suite("leadSuccess");
		StatsStore store = new StatsStore();
		StatsRecorder r = recorder(store);

		// I lead a low Club and NORTH wins it -- clean.
		trick(r, PLAYER.SOUTH, PLAYER.NORTH,
				card(CardValue.THREE, Suit.CLUB), card(CardValue.FOUR, Suit.CLUB),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB));
		// I lead the Ace of Hearts and win my own Hearts back -- not clean.
		trick(r, PLAYER.SOUTH, PLAYER.SOUTH,
				card(CardValue.TWO, Suit.HEART), card(CardValue.THREE, Suit.HEART),
				card(CardValue.ACE, Suit.HEART), card(CardValue.FOUR, Suit.HEART));
		// I lead the Ace of Diamonds and win the Jack -- -10, counts as clean.
		trick(r, PLAYER.SOUTH, PLAYER.SOUTH,
				card(CardValue.JACK, Suit.DIAMOND), card(CardValue.TWO, Suit.DIAMOND),
				card(CardValue.ACE, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND));

		TestKit.checkEquals("three leads recorded", Integer.valueOf(3), Integer.valueOf(store.get(ME, "leads")));
		TestKit.checkEquals("two of them clean", Integer.valueOf(2), Integer.valueOf(store.get(ME, "leadsClean")));
		TestKit.checkEquals("one Heart lead", Integer.valueOf(1), Integer.valueOf(store.get(ME, "leads_HEART")));
		TestKit.checkEquals("... and it was not clean",
				Integer.valueOf(0), Integer.valueOf(store.get(ME, "leadsClean_HEART")));
		TestKit.checkEquals("the Jack-winning Diamond lead is clean",
				Integer.valueOf(1), Integer.valueOf(store.get(ME, "leadsClean_DIAMOND")));
		TestKit.checkEquals("NORTH led nothing", Integer.valueOf(0), Integer.valueOf(store.get("AI_01", "leads")));
	}

	private static void passOutcomes() throws IOException {
		TestKit.suite("passOutcomes");
		StatsStore store = new StatsStore();
		StatsRecorder r = recorder(store);

		r.onEvent(GameEvent.PASS_RECEIVED, GameEventBus.facts()
				.with("from", PLAYER.SOUTH).with("recipient", PLAYER.WEST)
				.with("cards", "QUEEN SPADE, FIVE HEART, TWO CLUB")
				.with("passedCards", Arrays.asList(card(CardValue.QUEEN, Suit.SPADE),
						card(CardValue.FIVE, Suit.HEART), card(CardValue.TWO, Suit.CLUB)))
				.build());
		TestKit.checkEquals("14 penalty points passed (Queen + one Heart; the Club is free)",
				Integer.valueOf(14), Integer.valueOf(store.get(ME, "passPointsGiven")));

		// WEST ends up eating the Queen I passed -- the pass landed.
		trick(r, PLAYER.NORTH, PLAYER.WEST,
				card(CardValue.TWO, Suit.SPADE), card(CardValue.THREE, Suit.SPADE),
				card(CardValue.FOUR, Suit.SPADE), card(CardValue.QUEEN, Suit.SPADE));
		// The Five of Hearts comes back to me -- backfired.
		trick(r, PLAYER.NORTH, PLAYER.SOUTH,
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.FIVE, Suit.HEART),
				card(CardValue.ACE, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND));

		TestKit.checkEquals("13 points landed on the recipient",
				Integer.valueOf(13), Integer.valueOf(store.get(ME, "passPointsLanded")));
		TestKit.checkEquals("... attributed to the pair me>WEST",
				Integer.valueOf(13), Integer.valueOf(store.getPair(ME, "AI_03", "passPointsLandedOn")));
		TestKit.checkEquals("1 point came back to me",
				Integer.valueOf(1), Integer.valueOf(store.get(ME, "passPointsBackfired")));
		TestKit.checkEquals("nothing went elsewhere",
				Integer.valueOf(0), Integer.valueOf(store.get(ME, "passPointsElsewhere")));
	}

	private static void comebacksAndElo() throws IOException {
		TestKit.suite("comebacksAndElo");
		StatsStore store = new StatsStore();
		StatsRecorder r = recorder(store);

		TestKit.checkEquals("a new identity starts at 1500",
				Integer.valueOf(1500), Integer.valueOf(StatsRecorder.eloOf(store, ME)));

		// Game 1. First hand: totals == scores (marks a new game).
		handEnded(r, totals(10, 5, 20, 0), totals(10, 5, 20, 0));
		// Second hand: SOUTH reaches 60 -- the midpoint, and SOUTH is last.
		handEnded(r, totals(45, 25, 40, 10), totals(55, 30, 60, 10));
		// Later NORTH crosses 50 again etc. -- no second snapshot.
		handEnded(r, totals(50, 40, 0, 40), totals(105, 70, 60, 50));
		gameEnded(r, totals(105, 70, 40, 50));
		TestKit.checkEquals("SOUTH was last at the midpoint and won -- one chance",
				Integer.valueOf(1), Integer.valueOf(store.get(ME, "comebackChances")));
		TestKit.checkEquals("... converted", Integer.valueOf(1), Integer.valueOf(store.get(ME, "comebacksWon")));
		TestKit.checkEquals("NORTH was not last at the midpoint",
				Integer.valueOf(0), Integer.valueOf(store.get("AI_01", "comebackChances")));

		// ELO from all-1500: SOUTH beat all three (+16), NORTH lost to all
		// three (-16), EAST beat only NORTH (-5), WEST beat two (+5).
		TestKit.checkEquals("winner gains 16", Integer.valueOf(1516), Integer.valueOf(StatsRecorder.eloOf(store, ME)));
		TestKit.checkEquals("last place loses 16", Integer.valueOf(1484), Integer.valueOf(StatsRecorder.eloOf(store, "AI_01")));
		TestKit.checkEquals("third place loses 5", Integer.valueOf(1495), Integer.valueOf(StatsRecorder.eloOf(store, "AI_02")));
		TestKit.checkEquals("second place gains 5", Integer.valueOf(1505), Integer.valueOf(StatsRecorder.eloOf(store, "AI_03")));

		// Game 2: a fresh first hand resets the midpoint; NORTH is last at
		// the midpoint and does NOT win.
		handEnded(r, totals(20, 0, 0, 5), totals(20, 0, 0, 5));
		handEnded(r, totals(32, 10, 5, 0), totals(52, 10, 5, 5));
		handEnded(r, totals(50, 0, 26, 0), totals(102, 10, 31, 5));
		gameEnded(r, totals(102, 10, 31, 5));
		TestKit.checkEquals("NORTH got a comeback chance in game 2",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_01", "comebackChances")));
		TestKit.checkEquals("... and did not convert it",
				Integer.valueOf(0), Integer.valueOf(store.get("AI_01", "comebacksWon")));
		TestKit.checkEquals("SOUTH had no new chance in game 2",
				Integer.valueOf(1), Integer.valueOf(store.get(ME, "comebackChances")));
		TestKit.checkEquals("two rated games on record",
				Integer.valueOf(2), Integer.valueOf(store.get(ME, "eloGames")));

		String report = StatsReport.build(store, Roster.loadOrCreate(tempDir()));
		TestKit.check("stats screen shows the rating", report.contains("Rating (ELO)"));
		TestKit.check("stats screen shows comebacks", report.contains("Comebacks"));
	}

	/** OpponentModel's per-situation counters, recorded at HAND_ENDED from
	 * the standings each seat STARTED the hand in. */
	private static void situationalMoonCounters() throws IOException {
		TestKit.suite("situationalMoonCounters");
		StatsStore store = new StatsStore();
		StatsRecorder r = recorder(store);

		// Hand 1 of a game: everyone starts level -> MIDDLE.
		handEnded(r, totals(0, 10, 5, 20), totals(0, 10, 5, 20));
		TestKit.checkEquals("first hand counts as MIDDLE for everyone",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_03", "hands_MIDDLE")));

		// Hand 2: WEST started last, 20 behind (band 2), and shoots the moon.
		handEnded(r, totals(26, 26, 26, -26), totals(26, 36, 31, -6));
		TestKit.checkEquals("WEST: one hand started in LAST",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_03", "hands_LAST")));
		TestKit.checkEquals("... and it was a moon attempt (-26)",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_03", "moons_LAST")));
		TestKit.checkEquals("table counter for LAST, band 2",
				Integer.valueOf(1), Integer.valueOf(store.get(OpponentModel.TABLE_ID, "moons_LAST_2")));
		TestKit.checkEquals("NORTH started this hand leading",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_01", "hands_LEAD")));

		// Hand 3: EAST's moon is STOPPED after 6 Hearts -- also an attempt.
		// EAST started last (36 vs leader -6: 42 behind, band 3).
		r.onEvent(GameEvent.MOON_BROKEN, GameEventBus.facts()
				.with("trick", Integer.valueOf(9)).with("formerShooter", PLAYER.EAST)
				.with("breaker", PLAYER.SOUTH).with("shooterHearts", Integer.valueOf(6)).build());
		handEnded(r, totals(0, 20, 6, 0), totals(26, 56, 37, -6));
		TestKit.checkEquals("EAST's stopped moon counts as an attempt in LAST",
				Integer.valueOf(1), Integer.valueOf(store.get("AI_02", "moons_LAST")));
		TestKit.checkEquals("... at band 3 on the table",
				Integer.valueOf(1), Integer.valueOf(store.get(OpponentModel.TABLE_ID, "moons_LAST_3")));
		TestKit.checkEquals("SOUTH started all three hands in the middle (level, 5 of 20, 31 of 36)",
				Integer.valueOf(3), Integer.valueOf(store.get(ME, "hands_MIDDLE")));
		TestKit.checkEquals("hands recorded for the table: 4 per hand",
				Integer.valueOf(12), Integer.valueOf(store.get(OpponentModel.TABLE_ID, "hands_LEAD")
						+ store.get(OpponentModel.TABLE_ID, "hands_MIDDLE")
						+ store.get(OpponentModel.TABLE_ID, "hands_LAST")));
	}

	// ------------------------------------------------------------------

	private static File tempDir() throws IOException {
		File dir = Files.createTempDirectory("hearts2_statsext_test").toFile();
		dir.deleteOnExit();
		return dir;
	}

	private static StatsRecorder recorder(StatsStore store) throws IOException {
		StatsRecorder r = new StatsRecorder(store, tempDir());
		r.setSeatId(PLAYER.NORTH, "AI_01");
		r.setSeatId(PLAYER.EAST, "AI_02");
		r.setSeatId(PLAYER.SOUTH, ME);
		r.setSeatId(PLAYER.WEST, "AI_03");
		return r;
	}

	/** Cards in fixed N/E/S/W order. */
	private static void trick(StatsRecorder r, PLAYER leader, PLAYER winner,
			Card n, Card e, Card s, Card w) {
		Map<PLAYER, Card> cards = new EnumMap<PLAYER, Card>(PLAYER.class);
		cards.put(PLAYER.NORTH, n);
		cards.put(PLAYER.EAST, e);
		cards.put(PLAYER.SOUTH, s);
		cards.put(PLAYER.WEST, w);
		r.onEvent(GameEvent.TRICK_WON, GameEventBus.facts()
				.with("trick", Integer.valueOf(1)).with("winner", winner)
				.with("leadSuit", cards.get(leader).suit).with("leader", leader)
				.with("cards", cards).build());
	}

	private static void handEnded(StatsRecorder r, Map<PLAYER, Integer> scores, Map<PLAYER, Integer> totals) {
		r.onEvent(GameEvent.HAND_ENDED, GameEventBus.facts()
				.with("handNumber", Integer.valueOf(1)).with("scores", scores).with("totals", totals).build());
	}

	private static void gameEnded(StatsRecorder r, Map<PLAYER, Integer> totals) {
		r.onEvent(GameEvent.GAME_ENDED, GameEventBus.facts()
				.with("winners", "").with("lowestTotal", Integer.valueOf(0)).with("totals", totals).build());
	}

	private static Map<PLAYER, Integer> totals(int n, int e, int s, int w) {
		Map<PLAYER, Integer> m = new EnumMap<PLAYER, Integer>(PLAYER.class);
		m.put(PLAYER.NORTH, Integer.valueOf(n));
		m.put(PLAYER.EAST, Integer.valueOf(e));
		m.put(PLAYER.SOUTH, Integer.valueOf(s));
		m.put(PLAYER.WEST, Integer.valueOf(w));
		return m;
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}
}
