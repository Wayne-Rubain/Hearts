package hearts_1.events;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Keeps the console play-by-play (DEAL / PASS / STRATEGY / TRICK / HAND lines,
 * the same text LogReplayer reads) on disk, one file per game, so a game can
 * be reviewed after the fact -- every hand from the first deal up to the game
 * reaching TARGET_SCORE, or up to wherever the game was stopped.
 *
 * Files live in {@code <dir>/gamelogs/}:
 * <ul>
 * <li>{@code game_current.log} -- the game in progress (flushed after every line).</li>
 * <li>{@code game_<yyyyMMdd_HHmmss>.log} -- finished or abandoned earlier games.
 * Only the newest {@link #KEEP_RECENT} are kept; older ones are deleted so the
 * folder resets itself.</li>
 * <li>{@code kept_*.log} -- games copied aside (by hand) for review; never
 * pruned.</li>
 * </ul>
 *
 * A game ends the current file when the console prints the "NEW GAME" line
 * (Hearts_JFrame.startNewGame) or when the program next starts. Output is
 * only teed; what the game prints to the console is unchanged, and nothing
 * here touches play. Not installed automatically -- Hearts_JFrame's
 * constructor calls install(), so tests stay quiet.
 */
public final class GameLog {

	/** Finished/abandoned games kept besides game_current.log. */
	public static final int KEEP_RECENT = 5;

	private static final String CURRENT = "game_current.log";
	private static final String NEW_GAME_MARKER = "****** NEW GAME";

	private static boolean installed = false;

	private GameLog() { }

	public static File defaultDirectory() {
		return new File(new File(System.getProperty("user.home"), ".hearts2"), "gamelogs");
	}

	public static synchronized void install() {
		install(defaultDirectory());
	}

	public static synchronized void install(File dir) {
		if (installed) {
			return;
		}
		try {
			dir.mkdirs();
			archiveCurrent(dir);
			System.setOut(new PrintStream(new TeeOutputStream(System.out, dir), true));
			installed = true;
		} catch (IOException e) {
			// Logging is a convenience; never stop the game over it.
			System.err.println("GameLog not installed: " + e);
		}
	}

	/** Puts the real console back (tests only) so install() can run again. */
	public static synchronized void uninstall(PrintStream original) {
		System.out.flush();
		System.setOut(original);
		installed = false;
	}

	/** Moves game_current.log (if it has content) to a timestamped name and prunes. */
	static void archiveCurrent(File dir) throws IOException {
		File cur = new File(dir, CURRENT);
		if (cur.exists()) {
			if (cur.length() > 0) {
				String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(cur.lastModified()));
				File target = new File(dir, "game_" + stamp + ".log");
				int n = 1;
				while (target.exists()) {
					target = new File(dir, "game_" + stamp + "_" + (n++) + ".log");
				}
				if (!cur.renameTo(target)) {
					throw new IOException("could not archive " + cur);
				}
			} else {
				cur.delete();
			}
		}
		prune(dir);
	}

	private static void prune(File dir) {
		File[] old = dir.listFiles(new FilenameFilter() {
			public boolean accept(File d, String name) {
				return name.startsWith("game_2") && name.endsWith(".log");
			}
		});
		if (old == null || old.length <= KEEP_RECENT) {
			return;
		}
		Arrays.sort(old, new Comparator<File>() {
			public int compare(File a, File b) {
				return Long.compare(b.lastModified(), a.lastModified());
			}
		});
		for (int i = KEEP_RECENT; i < old.length; i++) {
			old[i].delete();
		}
	}

	/** Passes every byte to the real console and copies it, line by line, to the file. */
	private static final class TeeOutputStream extends OutputStream {
		private final OutputStream console;
		private final File dir;
		private FileOutputStream out;
		private final ByteArrayOutputStream line = new ByteArrayOutputStream();

		TeeOutputStream(OutputStream console, File dir) throws IOException {
			this.console = console;
			this.dir = dir;
			open();
		}

		private void open() throws IOException {
			out = new FileOutputStream(new File(dir, CURRENT));
		}

		@Override
		public synchronized void write(int b) throws IOException {
			console.write(b);
			line.write(b);
			if (b == '\n') {
				endLine();
			}
		}

		@Override
		public synchronized void write(byte[] buf, int off, int len) throws IOException {
			console.write(buf, off, len);
			for (int i = off; i < off + len; i++) {
				line.write(buf[i]);
				if (buf[i] == '\n') {
					endLine();
				}
			}
		}

		@Override
		public synchronized void flush() throws IOException {
			console.flush();
		}

		private void endLine() {
			try {
				byte[] bytes = line.toByteArray();
				line.reset();
				int n = Math.min(bytes.length, NEW_GAME_MARKER.length());
				if (new String(bytes, 0, n, StandardCharsets.ISO_8859_1).equals(NEW_GAME_MARKER)) {
					out.close();
					archiveCurrent(dir);
					open();
				}
				out.write(bytes);
				out.flush();
			} catch (IOException e) {
				// Keep playing even if the disk write fails.
			}
		}
	}
}
