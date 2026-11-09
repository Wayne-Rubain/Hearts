package hearts_1;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import hearts_1.Player.Player_Type;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;

/**
 * Committed regression test, added 2026-08-15, tying together everything
 * built this session: HeadlessSimulator plays real, random, AI-vs-AI games;
 * their console output is captured and fed to LogReplayer, the same tool a
 * human would point at a real session log; and GameEventBus is checked to
 * confirm it actually fired for the hands played. This is the closest
 * committed equivalent to the old uncommitted 400,000-decision equivalence
 * harness (CHANGELOG.md, 2026-08-06 entry) -- not the same scale, but the
 * same idea: real random play, checked against the rules independently,
 * every run.
 *
 * If this test ever fails, the failure is telling you one of two things:
 * either a genuine rules regression (fix the engine), or the engine grew a
 * new log line shape that LogReplayer's parser doesn't understand yet (fix
 * the parser) -- see LogReplayer's class javadoc for the formats it expects.
 */
public final class LogReplayerRoundTripTest {

	private LogReplayerRoundTripTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		randomGamesReplayCleanForEveryPlayerTypeMix();
		gameEventBusFiresRealEventsDuringHeadlessPlay();
		return TestKit.summarize();
	}

	private static void randomGamesReplayCleanForEveryPlayerTypeMix() {
		TestKit.suite("randomGamesReplayCleanForEveryPlayerTypeMix");

		// A handful of distinct seat mixes -- deliberately includes
		// BRAIN_DEAD/BAD (competence bypass, no scorer path at all),
		// MOON_SHOOTER (exercises the moon-attempt/moon-mode-play code
		// path), and AGGRESSIVE/AVOIDS_QUEEN (the two flavor traits) --
		// rather than one mix repeated, since each exercises different code
		// in AI.java/AICardScorer.java. 3 hands per mix (about 4 dealer
		// rotations' worth of pass directions) keeps this fast enough to
		// run on every build while still touching all 4 pass directions.
		Player_Type[][] mixes = {
				{ Player_Type.NORMAL, Player_Type.NORMAL, Player_Type.NORMAL, Player_Type.NORMAL },
				{ Player_Type.BRAIN_DEAD, Player_Type.BAD, Player_Type.NORMAL, Player_Type.AGGRESSIVE },
				{ Player_Type.MOON_SHOOTER, Player_Type.MOON_SHOOTER, Player_Type.AVOIDS_QUEEN, Player_Type.NORMAL },
				{ Player_Type.AGGRESSIVE_AVOIDS_QUEEN, Player_Type.CAUTIOUS, Player_Type.BAD, Player_Type.MOON_SHOOTER },
		};

		for (Player_Type[] mix : mixes) {
			String label = Arrays.toString(mix);
			List<String> lines = captureHeadlessGame(4, mix);
			LogReplayer.Result result = LogReplayer.replay(lines);
			TestKit.checkEquals("mix " + label + ": all 4 hands processed", Integer.valueOf(4),
					Integer.valueOf(result.handsProcessed));
			TestKit.checkEquals("mix " + label + ": all 13*4 tricks verified", Integer.valueOf(52),
					Integer.valueOf(result.tricksVerified));
			TestKit.check("mix " + label + ": zero violations -- " + result.report(), result.isClean());
		}
	}

	/** HeadlessSimulator publishes HAND_ENDED itself (mirroring
	 * Hearts_JFrame.End_Of_Hand()), so that event is real, live traffic
	 * through GameEventBus, not a simulated one -- this confirms the bus
	 * fires exactly once per hand played. TRICK_WON/QUEEN_DROPPED_ON/
	 * JACK_CAPTURED/etc. are published from Play_Hand.resolveTrick()
	 * specifically (see Play_Hand.java), which HeadlessSimulator
	 * deliberately doesn't call (Swing coupling -- see its class comment),
	 * so this test can't exercise those from here without duplicating
	 * Play_Hand's logic into the simulator, which would test a copy rather
	 * than the real thing. */
	private static void gameEventBusFiresRealEventsDuringHeadlessPlay() {
		TestKit.suite("gameEventBusFiresRealEventsDuringHeadlessPlay");
		final AtomicInteger handEndedCount = new AtomicInteger(0);
		GameEventBus.Listener listener = new GameEventBus.Listener() {
			public void onEvent(GameEvent event, Map<String, Object> facts) {
				if (event == GameEvent.HAND_ENDED) {
					handEndedCount.incrementAndGet();
				}
			}
		};
		GameEventBus.subscribe(listener);
		try {
			captureHeadlessGame(2, new Player_Type[] {
					Player_Type.NORMAL, Player_Type.NORMAL, Player_Type.NORMAL, Player_Type.NORMAL });
		} finally {
			GameEventBus.unsubscribe(listener);
		}
		TestKit.checkEquals("HAND_ENDED fired once per hand played", Integer.valueOf(2),
				Integer.valueOf(handEndedCount.get()));
	}

	/** Runs a headless game with System.out captured, returning every
	 * printed line. Restores the real System.out afterward even if the
	 * game throws. */
	private static List<String> captureHeadlessGame(int numHands, Player_Type[] mix) {
		PrintStream realOut = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setOut(newUtf8PrintStream(buffer));
			HeadlessSimulator.playHeadlessGame(numHands, mix);
		} finally {
			System.setOut(realOut);
		}
		return Arrays.asList(bufferToString(buffer).split("\\r?\\n"));
	}

	private static PrintStream newUtf8PrintStream(ByteArrayOutputStream buffer) {
		try {
			return new PrintStream(buffer, true, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); // UTF-8 is always available -- unreachable
		}
	}

	private static String bufferToString(ByteArrayOutputStream buffer) {
		try {
			return buffer.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); // UTF-8 is always available -- unreachable
		}
	}
}
