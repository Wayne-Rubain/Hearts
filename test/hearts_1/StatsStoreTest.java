package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import hearts_1.roster.StatsStore;

/**
 * Committed regression coverage for the shared cross-game counter store
 * (hearts_1.roster.StatsStore), added 2026-08-16 with the feature. The
 * store is deliberately schema-free -- counters are just named integers --
 * so the properties under test are the mechanical ones: increments,
 * ordered-pair asymmetry, lossless round-trips, and the forgiving parser.
 */
public final class StatsStoreTest {

	private StatsStoreTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			countersAccumulateAndPairsAreOrdered();
			roundTripPreservesEveryCounter();
			forgivingParserSkipsGarbage();
			missingFileStartsEmpty();
		} catch (IOException e) {
			TestKit.check("unexpected IOException: " + e.getMessage(), false);
		}
		return TestKit.summarize();
	}

	private static File tempDir() throws IOException {
		File dir = Files.createTempDirectory("hearts2_stats_test").toFile();
		dir.deleteOnExit();
		return dir;
	}

	private static void countersAccumulateAndPairsAreOrdered() {
		TestKit.suite("countersAccumulateAndPairsAreOrdered");
		StatsStore store = new StatsStore();
		TestKit.checkEquals("unset counter reads 0", 0, store.get("HUMAN", "gamesWon"));
		store.add("HUMAN", "gamesWon", 1);
		store.add("HUMAN", "gamesWon", 1);
		TestKit.checkEquals("two increments read 2", 2, store.get("HUMAN", "gamesWon"));
		store.put("HUMAN", "currentWinStreak", 5);
		store.put("HUMAN", "currentWinStreak", 0);
		TestKit.checkEquals("put overwrites (streak reset)", 0, store.get("HUMAN", "currentWinStreak"));
		store.addPair("HUMAN", "AI_01", "queenDroppedOn", 1);
		TestKit.checkEquals("ordered pair A>B records", 1, store.getPair("HUMAN", "AI_01", "queenDroppedOn"));
		TestKit.checkEquals("the reverse pair B>A stays untouched -- asymmetric by construction",
				0, store.getPair("AI_01", "HUMAN", "queenDroppedOn"));
	}

	private static void roundTripPreservesEveryCounter() throws IOException {
		TestKit.suite("roundTripPreservesEveryCounter");
		File dir = tempDir();
		StatsStore store = new StatsStore();
		store.add("HUMAN", "gamesPlayed", 7);
		store.add("HUMAN", "gamesWon", 3);
		store.put("HUMAN", "bestHandScore", -14); // negatives must survive
		store.add("AI_04", "moonSuccesses", 2);
		store.addPair("HUMAN", "AI_04", "queenDroppedOn", 4);
		store.addPair("AI_04", "HUMAN", "queenDroppedOn", 1);
		store.save(dir);

		StatsStore back = StatsStore.loadOrCreate(dir);
		TestKit.checkEquals("player counter survives", 7, back.get("HUMAN", "gamesPlayed"));
		TestKit.checkEquals("negative value survives", -14, back.get("HUMAN", "bestHandScore"));
		TestKit.checkEquals("second player's counter survives", 2, back.get("AI_04", "moonSuccesses"));
		TestKit.checkEquals("pair A>B survives", 4, back.getPair("HUMAN", "AI_04", "queenDroppedOn"));
		TestKit.checkEquals("pair B>A survives separately", 1, back.getPair("AI_04", "HUMAN", "queenDroppedOn"));
	}

	private static void forgivingParserSkipsGarbage() throws IOException {
		TestKit.suite("forgivingParserSkipsGarbage");
		File dir = tempDir();
		String content = "version = 1\n"
				+ "[player HUMAN]\n"
				+ "gamesPlayed = 3\n"
				+ "gamesWon = notanumber\n" // non-numeric -- skip
				+ "no equals here\n" // garbage -- skip
				+ "[weirdsection]\n" // unknown section -- skip its contents
				+ "something = 5\n"
				+ "[pair HUMAN>AI_02]\n"
				+ "queenDroppedOn = 2\n";
		Files.write(new File(dir, "history.txt").toPath(), content.getBytes(StandardCharsets.UTF_8));
		StatsStore store = StatsStore.loadOrCreate(dir);
		TestKit.checkEquals("good counter loads", 3, store.get("HUMAN", "gamesPlayed"));
		TestKit.checkEquals("non-numeric value skipped, reads 0", 0, store.get("HUMAN", "gamesWon"));
		TestKit.checkEquals("pair loads past the garbage", 2, store.getPair("HUMAN", "AI_02", "queenDroppedOn"));
	}

	private static void missingFileStartsEmpty() throws IOException {
		TestKit.suite("missingFileStartsEmpty");
		StatsStore store = StatsStore.loadOrCreate(tempDir());
		TestKit.checkEquals("no file means an empty store, not an error",
				0, store.knownPlayerIds().size());
	}
}
