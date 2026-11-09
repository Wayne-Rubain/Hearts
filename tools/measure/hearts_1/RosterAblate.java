package hearts_1;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import hearts_1.Player.Player_Type;
import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/** Scratch: one roster player (optionally with edited settings) against three
 * plain Normals, whole seeded games, seat rotated. Args: rosterDir id games
 * [edits], where edits is "key=value;key=value" applied to that player's
 * [player] block (replaces the line or adds it), e.g. "template=NORMAL" or
 * "style.Aggressive=0;payback=off". Prints win % and average final score. */
public final class RosterAblate {
	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		String id = args[1];
		int games = Integer.parseInt(args[2]);
		String edits = args.length > 3 ? args[3] : "";
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		RosterPlayer rp = load(dir, id, edits);
		Profile normal = Profile.of(Player_Type.NORMAL);
		double wins = 0, pts = 0;
		for (int g = 0; g < games; g++) {
			int seat = g % 4;
			Profile[] seats = { normal, normal, normal, normal };
			seats[seat] = rp.profile;
			int[] counts = { 50, 50, 50, 50 };
			counts[seat] = rp.countCardsPercent;
			HeadlessSimulator.SeededGame r = HeadlessSimulator.playSeededGame(900_000 + g, seats, counts);
			int best = Integer.MAX_VALUE, nBest = 0;
			for (int f : r.finals) {
				if (f < best) { best = f; nBest = 1; } else if (f == best) { nBest++; }
			}
			if (r.finals[seat] == best) {
				wins += 1.0 / nBest;
			}
			pts += r.finals[seat];
		}
		double p = wins / games;
		System.setOut(out);
		System.out.printf("%-10s %-48s games=%d won=%.1f%% (se %.1f) avgFinal=%.1f%n", id,
				edits.isEmpty() ? "(as drafted)" : edits, games, 100 * p,
				100 * Math.sqrt(p * (1 - p) / games), pts / games);
	}

	/** Copies roster.txt to a temp dir with the edits applied to `id`'s block. */
	static RosterPlayer load(File dir, String id, String edits) throws Exception {
		List<String> lines = Files.readAllLines(new File(dir, "roster.txt").toPath());
		List<String> outLines = new ArrayList<String>();
		boolean inBlock = false, done = false;
		for (String line : lines) {
			String t = line.trim();
			if (t.equals("[player]")) {
				if (inBlock && !done) { appendMissing(outLines, edits); done = true; }
				inBlock = false;
			} else if (t.startsWith("id") && t.contains("=")) {
				inBlock = t.substring(t.indexOf('=') + 1).trim().equals(id);
			}
			if (inBlock && !edits.isEmpty()) {
				String replaced = replace(line, edits);
				if (replaced != null) { outLines.add(replaced); continue; }
			}
			outLines.add(line);
		}
		if (inBlock && !done) { appendMissing(outLines, edits); }
		File tmp = Files.createTempDirectory("rosterablate").toFile();
		Files.write(new File(tmp, "roster.txt").toPath(), outLines);
		for (RosterPlayer p : Roster.loadOrCreate(tmp).players()) {
			if (p.id.equals(id)) { return p; }
		}
		throw new IllegalArgumentException("no player " + id);
	}

	private static String replace(String line, String edits) {
		int eq = line.indexOf('=');
		if (eq < 0) { return null; }
		String key = line.substring(0, eq).trim();
		for (String e : edits.split(";")) {
			String[] kv = e.split("=", 2);
			if (kv[0].trim().equals(key)) { return key + " = " + kv[1].trim(); }
		}
		return null;
	}

	/** Edits whose key wasn't already a line in the block get added at its end. */
	private static void appendMissing(List<String> outLines, String edits) {
		for (String e : edits.split(";")) {
			if (e.isEmpty()) { continue; }
			String[] kv = e.split("=", 2);
			boolean seen = false;
			for (int i = outLines.size() - 1; i >= 0 && !outLines.get(i).trim().equals("[player]"); i--) {
				if (outLines.get(i).contains("=") && outLines.get(i).substring(0, outLines.get(i).indexOf('=')).trim().equals(kv[0].trim())) { seen = true; break; }
			}
			if (!seen) { outLines.add(kv[0].trim() + " = " + kv[1].trim()); }
		}
	}
}
