package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;
import hearts_1.roster.Roster;
import hearts_1.roster.StatsRecorder;
import hearts_1.roster.StatsStore;

/**
 * Committed regression coverage for the event->counter recording layer
 * (hearts_1.roster.StatsRecorder), added 2026-08-16 with the feature.
 * Drives a synthetic hand and two synthetic game endings through
 * onEvent() directly (not via the bus, so nothing else subscribed in the
 * same JVM can interfere) and checks every counter family: pairs
 * (Queen/Jack/pass/moon-stop attribution), per-hand aggregates (perfect
 * and slam hands, best/worst), placements, streaks, and the
 * shooterHearts filter that separates a real stopped moon from the
 * routine first-vs-second point capture MOON_BROKEN otherwise fires on.
 */
public final class StatsRecorderTest {

	private StatsRecorderTest() { }

	private static final String ME = Roster.HUMAN_ID;

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			fullSyntheticHandAndTwoGames();
		} catch (IOException e) {
			TestKit.check("unexpected IOException: " + e.getMessage(), false);
		}
		return TestKit.summarize();
	}

	private static void fullSyntheticHandAndTwoGames() throws IOException {
		TestKit.suite("fullSyntheticHandAndTwoGames");
		File dir = Files.createTempDirectory("hearts2_recorder_test").toFile();
		dir.deleteOnExit();
		StatsStore store = new StatsStore();
		StatsRecorder recorder = new StatsRecorder(store, dir);
		recorder.setSeatId(PLAYER.NORTH, "AI_01");
		recorder.setSeatId(PLAYER.EAST, "AI_02");
		recorder.setSeatId(PLAYER.SOUTH, ME);
		recorder.setSeatId(PLAYER.WEST, "AI_03");

		// --- one hand's worth of trick-level events ---
		// 13 tricks: NORTH 6, EAST 4, WEST 3, SOUTH 0 (a slam hand for SOUTH).
		for (int i = 0; i < 6; i++) trickWon(recorder, PLAYER.NORTH);
		for (int i = 0; i < 4; i++) trickWon(recorder, PLAYER.EAST);
		for (int i = 0; i < 3; i++) trickWon(recorder, PLAYER.WEST);

		recorder.onEvent(GameEvent.QUEEN_DROPPED_ON, GameEventBus.facts()
				.with("trick", 5).with("winner", PLAYER.NORTH).with("droppedBySeat", PLAYER.SOUTH).build());
		recorder.onEvent(GameEvent.JACK_CAPTURED, GameEventBus.facts()
				.with("trick", 9).with("winner", PLAYER.EAST).with("playedBySeat", PLAYER.WEST).build());
		recorder.onEvent(GameEvent.MOON_COMMITTED, GameEventBus.facts()
				.with("seat", PLAYER.WEST).build());
		// A REAL stopped moon (7 Hearts banked) ...
		recorder.onEvent(GameEvent.MOON_BROKEN, GameEventBus.facts()
				.with("trick", 10).with("formerShooter", PLAYER.NORTH)
				.with("breaker", PLAYER.SOUTH).with("shooterHearts", Integer.valueOf(7)).build());
		// ... and the routine noise case (2 Hearts) that must NOT count.
		recorder.onEvent(GameEvent.MOON_BROKEN, GameEventBus.facts()
				.with("trick", 3).with("formerShooter", PLAYER.EAST)
				.with("breaker", PLAYER.WEST).with("shooterHearts", Integer.valueOf(2)).build());
		recorder.onEvent(GameEvent.PASS_RECEIVED, GameEventBus.facts()
				.with("recipient", PLAYER.SOUTH).with("from", PLAYER.NORTH)
				.with("cards", "QUEEN SPADE, TWO HEART, THREE CLUB").build());

		TestKit.checkEquals("Queen drop attributed HUMAN->AI_01",
				1, store.getPair(ME, "AI_01", "queenDroppedOn"));
		TestKit.checkEquals("Jack capture credited to AI_02", 1, store.get("AI_02", "jacksCaptured"));
		TestKit.checkEquals("Jack giveaway attributed AI_03->AI_02",
				1, store.getPair("AI_03", "AI_02", "jackGivenTo"));
		TestKit.checkEquals("moon commitment credited to AI_03", 1, store.get("AI_03", "moonCommitted"));
		TestKit.checkEquals("real stopped moon attributed HUMAN->AI_01",
				1, store.getPair(ME, "AI_01", "moonStopped"));
		TestKit.checkEquals("2-Heart break filtered out as routine noise",
				0, store.getPair("AI_03", "AI_02", "moonStopped"));
		TestKit.checkEquals("Queen pass attributed AI_01->HUMAN",
				1, store.getPair("AI_01", ME, "queenPassedTo"));

		// --- hand end: N=+20 E=-10 S=0 W=+16 ---
		Map<PLAYER, Integer> scores = new EnumMap<PLAYER, Integer>(PLAYER.class);
		scores.put(PLAYER.NORTH, 20);
		scores.put(PLAYER.EAST, -10);
		scores.put(PLAYER.SOUTH, 0);
		scores.put(PLAYER.WEST, 16);
		recorder.onEvent(GameEvent.HAND_ENDED, GameEventBus.facts()
				.with("handNumber", 1).with("scores", scores).build());

		TestKit.checkEquals("hand counted for the human", 1, store.get(ME, "handsPlayed"));
		TestKit.checkEquals("score 0 counts as a perfect hand", 1, store.get(ME, "perfectHands"));
		TestKit.checkEquals("zero tricks counts as a slam hand", 1, store.get(ME, "slamHands"));
		TestKit.checkEquals("EAST's -10 is perfect too", 1, store.get("AI_02", "perfectHands"));
		TestKit.checkEquals("EAST won tricks, so no slam", 0, store.get("AI_02", "slamHands"));
		TestKit.checkEquals("NORTH's +20 is not perfect", 0, store.get("AI_01", "perfectHands"));
		TestKit.checkEquals("best hand tracked", 0, store.get(ME, "bestHandScore"));
		TestKit.checkEquals("hands-together pair recorded", 1, store.getPair(ME, "AI_01", "handsTogether"));
		TestKit.check("history.txt written at hand end", new File(dir, "history.txt").isFile());

		// --- game 1: S=20 E=30 N=40 W=50 -> HUMAN 1st, AI_03 4th ---
		recorder.onEvent(GameEvent.GAME_ENDED, GameEventBus.facts()
				.with("winners", "SOUTH").with("lowestTotal", 20)
				.with("totals", totals(40, 30, 20, 50)).build());
		TestKit.checkEquals("human game won", 1, store.get(ME, "gamesWon"));
		TestKit.checkEquals("human placed 1st", 1, store.get(ME, "place1"));
		TestKit.checkEquals("AI_03 placed 4th", 1, store.get("AI_03", "place4"));
		TestKit.checkEquals("win streak started", 1, store.get(ME, "currentWinStreak"));
		TestKit.checkEquals("human beat AI_01 pairwise", 1, store.getPair(ME, "AI_01", "gamesBeaten"));
		TestKit.checkEquals("AI_01 did not beat the human", 0, store.getPair("AI_01", ME, "gamesBeaten"));

		// --- game 2: N=10 E=20 W=30 S=90 -> HUMAN 4th, streak resets ---
		recorder.onEvent(GameEvent.GAME_ENDED, GameEventBus.facts()
				.with("winners", "NORTH").with("lowestTotal", 10)
				.with("totals", totals(10, 20, 90, 30)).build());
		TestKit.checkEquals("streak reset on the loss", 0, store.get(ME, "currentWinStreak"));
		TestKit.checkEquals("longest streak preserved", 1, store.get(ME, "longestWinStreak"));
		TestKit.checkEquals("two games played total", 2, store.get(ME, "gamesPlayed"));
		TestKit.checkEquals("AI_01 won game 2", 1, store.get("AI_01", "gamesWon"));
		TestKit.checkEquals("games-together accumulated", 2, store.getPair(ME, "AI_02", "gamesTogether"));
	}

	private static void trickWon(StatsRecorder recorder, PLAYER winner) {
		recorder.onEvent(GameEvent.TRICK_WON, GameEventBus.facts()
				.with("trick", 1).with("winner", winner).with("leadSuit", Suit.CLUB).build());
	}

	private static Map<PLAYER, Integer> totals(int north, int east, int south, int west) {
		Map<PLAYER, Integer> t = new EnumMap<PLAYER, Integer>(PLAYER.class);
		t.put(PLAYER.NORTH, north);
		t.put(PLAYER.EAST, east);
		t.put(PLAYER.SOUTH, south);
		t.put(PLAYER.WEST, west);
		return t;
	}
}
