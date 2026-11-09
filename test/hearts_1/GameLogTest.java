package hearts_1;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import hearts_1.events.GameLog;

/**
 * Regression coverage for GameLog (2026-10-17, user request): the console
 * play-by-play of a whole game is kept on disk, a "NEW GAME" line starts a
 * fresh file and archives the old one, old archives are pruned, and kept_*
 * files are never touched.
 */
public final class GameLogTest {

	private GameLogTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			wholeGameIsKeptAndNewGameRotates();
		} catch (IOException e) {
			TestKit.check("no IOException: " + e, false);
		}
		return TestKit.summarize();
	}

	private static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
	}

	private static void wholeGameIsKeptAndNewGameRotates() throws IOException {
		TestKit.suite("wholeGameIsKeptAndNewGameRotates");
		File dir = Files.createTempDirectory("gamelogtest").toFile();
		File keep = new File(dir, "kept_review.log");
		Files.write(keep.toPath(), "keep me".getBytes(StandardCharsets.ISO_8859_1));
		PrintStream real = System.out;
		try {
			GameLog.install(dir);
			System.out.println("****** DEAL NORTH hand = x ******");
			System.out.println("****** TRICK 1 lead=TWO CLUB | N=A E=B S=C W=D ******");
			File cur = new File(dir, "game_current.log");
			TestKit.check("current file exists", cur.exists());
			String first = read(cur);
			TestKit.check("current holds both lines",
					first.contains("DEAL NORTH") && first.contains("TRICK 1"));

			System.out.println("****** NEW GAME -- totals reset ******");
			System.out.println("****** DEAL NORTH hand = y ******");
			String second = read(cur);
			TestKit.check("new game starts a fresh file",
					second.startsWith("****** NEW GAME") && !second.contains("TRICK 1"));
			File[] archived = dir.listFiles(new java.io.FilenameFilter() {
				public boolean accept(File d, String n) { return n.startsWith("game_2"); }
			});
			TestKit.checkEquals("one archived game", 1, archived == null ? 0 : archived.length);
			TestKit.check("archive holds the old game", archived != null && archived.length == 1
					&& read(archived[0]).contains("TRICK 1"));
			TestKit.check("kept file untouched", keep.exists() && read(keep).equals("keep me"));
		} finally {
			GameLog.uninstall(real);
		}
	}
}
