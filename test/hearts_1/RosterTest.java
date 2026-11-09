package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import hearts_1.Player.Player_Type;
import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/**
 * Committed regression coverage for the persistent roster's load/save
 * layer (hearts_1.roster.Roster), added 2026-08-16 with the feature.
 * Exercises the three properties the design doc demands of persistence:
 * a starter roster on first run, lossless round-trips, and a FORGIVING
 * parser -- unknown keys and unknown Player_Type names are skipped with a
 * warning, never fatal, because this project's enums change constantly.
 */
public final class RosterTest {

	private RosterTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			starterRosterCreatedOnFirstRun();
			roundTripPreservesEverything();
			forgivingParserSkipsUnknownsAndSurvivesGarbage();
			emptyFileFallsBackToStarterRoster();
		} catch (IOException e) {
			TestKit.check("unexpected IOException: " + e.getMessage(), false);
		}
		return TestKit.summarize();
	}

	private static File tempDir() throws IOException {
		File dir = Files.createTempDirectory("hearts2_roster_test").toFile();
		dir.deleteOnExit();
		return dir;
	}

	private static void starterRosterCreatedOnFirstRun() throws IOException {
		TestKit.suite("starterRosterCreatedOnFirstRun");
		File dir = tempDir();
		Roster roster = Roster.loadOrCreate(dir);
		List<RosterPlayer> players = roster.players();
		TestKit.checkEquals("starter roster has exactly 10 players", 10, players.size());
		TestKit.checkEquals("ids run AI_01..AI_10", "AI_01", players.get(0).id);
		TestKit.checkEquals("last id is AI_10", "AI_10", players.get(9).id);
		TestKit.check("roster.txt was written for hand-editing",
				new File(dir, "roster.txt").isFile());
		TestKit.check("byId finds a member", roster.byId("AI_03") != null);
		TestKit.check("byId returns null for an unknown id", roster.byId("AI_99") == null);
		// 2026-08-16: the counts-cards ability is spread across the starter
		// roster, not uniformly on or off.
		int neverCounts = 0, alwaysCounts = 0;
		for (RosterPlayer p : players) {
			TestKit.check(p.id + "'s countCardsPercent is a valid 0-100 rate",
					p.countCardsPercent >= 0 && p.countCardsPercent <= 100);
			if (p.countCardsPercent == 0) {
				neverCounts++;
			}
			if (p.countCardsPercent == 100) {
				alwaysCounts++;
			}
		}
		TestKit.check("at least one starter player never counts cards", neverCounts > 0);
		TestKit.check("at least one starter player always counts cards", alwaysCounts > 0);
		TestKit.check("not every starter player has the same rate (a real spread, not uniform)",
				neverCounts < players.size() && alwaysCounts < players.size());

		// 2026-08-16: the moon-gamble ability is likewise spread, not uniform.
		int neverGambles = 0;
		boolean anyGambles = false;
		for (RosterPlayer p : players) {
			TestKit.check(p.id + "'s moonGamblePercent is a valid 0-100 rate",
					p.moonGamblePercent >= 0 && p.moonGamblePercent <= 100);
			if (p.moonGamblePercent == 0) {
				neverGambles++;
			} else {
				anyGambles = true;
			}
		}
		TestKit.check("at least one starter player never gambles", neverGambles > 0);
		TestKit.check("at least one starter player does gamble", anyGambles);
	}

	private static void roundTripPreservesEverything() throws IOException {
		TestKit.suite("roundTripPreservesEverything");
		File dir = tempDir();
		Roster first = Roster.loadOrCreate(dir); // creates + saves starter
		Roster second = Roster.loadOrCreate(dir); // must read it back
		List<RosterPlayer> a = first.players();
		List<RosterPlayer> b = second.players();
		TestKit.checkEquals("same player count after reload", a.size(), b.size());
		boolean allMatch = true;
		for (int i = 0; i < a.size(); i++) {
			if (!a.get(i).id.equals(b.get(i).id) || a.get(i).type != b.get(i).type
					|| a.get(i).countCardsPercent != b.get(i).countCardsPercent
					|| a.get(i).moonGamblePercent != b.get(i).moonGamblePercent) {
				allMatch = false;
			}
		}
		TestKit.check("every id, type, countCardsPercent, and moonGamblePercent survives the round trip", allMatch);
		TestKit.checkEquals("human name survives too", first.humanName(), second.humanName());
	}

	private static void forgivingParserSkipsUnknownsAndSurvivesGarbage() throws IOException {
		TestKit.suite("forgivingParserSkipsUnknownsAndSurvivesGarbage");
		File dir = tempDir();
		String content = "version = 1\n"
				+ "[human]\n"
				+ "name = Tester\n"
				+ "favoriteColor = blue\n" // unknown key -- skip, don't die
				+ "[player]\n"
				+ "id = AI_01\n"
				+ "name = AI_01\n"
				+ "type = SOME_FUTURE_TYPE\n" // unknown enum name -- NORMAL fallback
				+ "[player]\n"
				+ "id = AI_02\n"
				+ "type = AGGRESSIVE\n"
				+ "this line has no equals sign\n" // garbage -- skip
				+ "[player]\n"
				+ "id = AI_03\n"
				+ "type = MOON_SHOOTER\n";
		Files.write(new File(dir, "roster.txt").toPath(), content.getBytes(StandardCharsets.UTF_8));
		Roster roster = Roster.loadOrCreate(dir);
		TestKit.checkEquals("all three recoverable players load", 3, roster.players().size());
		TestKit.checkEquals("human name loaded despite the unknown key", "Tester", roster.humanName());
		TestKit.checkEquals("unknown type falls back to NORMAL, player kept",
				Player_Type.NORMAL, roster.byId("AI_01").type);
		TestKit.checkEquals("known types parse normally",
				Player_Type.AGGRESSIVE, roster.byId("AI_02").type);
		TestKit.checkEquals("a player with no name line uses its id",
				"AI_02", roster.byId("AI_02").displayName);
	}

	private static void emptyFileFallsBackToStarterRoster() throws IOException {
		TestKit.suite("emptyFileFallsBackToStarterRoster");
		File dir = tempDir();
		Files.write(new File(dir, "roster.txt").toPath(), "# nothing here\n".getBytes(StandardCharsets.UTF_8));
		Roster roster = Roster.loadOrCreate(dir);
		TestKit.checkEquals("a roster file with zero players falls back to the starter 10",
				10, roster.players().size());
	}
}
