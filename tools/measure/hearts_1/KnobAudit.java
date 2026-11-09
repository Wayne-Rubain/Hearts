package hearts_1;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import hearts_1.Player.Player_Type;
import hearts_1.roster.RosterPlayer;

/** Settings audit (2026-11-05): does each roster setting change play at all?
 * For one roster player, every setting is changed in turn (to a value that
 * differs from the drafted one) and the seeded games where any card played
 * changes are counted, against three plain Normals.
 *
 * Args: rosterDir id games [minChanged]. With minChanged, exits 1 when any
 * setting changes fewer games than that, so it can guard against settings
 * that look like knobs but do nothing. The list of settings is built from
 * Strategy and TacticWeights, so new options are audited automatically. */
public final class KnobAudit {
	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		String id = args[1];
		int games = Integer.parseInt(args[2]);
		int minChanged = args.length > 3 ? Integer.parseInt(args[3]) : -1;
		Map<String, String> drafted = block(dir, id);
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		RosterPlayer base = RosterAblate.load(dir, id, "");
		List<String> edits = edits(drafted, base.profile);
		String[] basePlays = new String[games];
		for (int g = 0; g < games; g++) {
			basePlays[g] = play(base, g);
		}
		List<String> report = new ArrayList<String>();
		int below = 0;
		for (String e : edits) {
			RosterPlayer v = RosterAblate.load(dir, id, e);
			int changed = 0;
			for (int g = 0; g < games; g++) {
				if (!play(v, g).equals(basePlays[g])) {
					changed++;
				}
			}
			boolean flag = minChanged >= 0 && changed < minChanged;
			below += flag ? 1 : 0;
			report.add(String.format("%3d of %d  %s%s", changed, games, e, flag ? "   <-- no real effect" : ""));
		}
		System.setOut(out);
		System.out.println("Settings audit for " + id + " (" + base.displayName + "), " + games
				+ " seeded games against three Normals: games where any card changed");
		for (String r : report) {
			System.out.println(r);
		}
		if (minChanged >= 0) {
			System.out.println(below + " setting(s) changed fewer than " + minChanged + " games");
			System.exit(below > 0 ? 1 : 0);
		}
	}

	private static String play(RosterPlayer rp, int g) {
		int s = g % 4;
		Profile n = Profile.of(Player_Type.NORMAL);
		Profile[] seats = { n, n, n, n };
		seats[s] = rp.profile;
		int[] counts = { 50, 50, 50, 50 }, gambles = new int[4], interests = { 3, 3, 3, 3 };
		counts[s] = rp.countCardsPercent;
		gambles[s] = rp.moonGamblePercent;
		interests[s] = rp.moonInterestPercent;
		return HeadlessSimulator.playSeededGame(900_000 + g, seats, counts, gambles, interests).plays;
	}

	/** The player's [player] block as key -> value. */
	static Map<String, String> block(File dir, String id) throws Exception {
		Map<String, String> m = new LinkedHashMap<String, String>();
		boolean in = false;
		for (String line : Files.readAllLines(new File(dir, "roster.txt").toPath())) {
			String t = line.split("#", 2)[0].trim();
			if (t.equals("[player]")) {
				in = false;
				continue;
			}
			int eq = t.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String k = t.substring(0, eq).trim(), v = t.substring(eq + 1).trim();
			if (k.equals("id")) {
				in = v.equals(id);
			}
			if (in) {
				m.put(k, v);
			}
		}
		if (m.isEmpty()) {
			throw new IllegalArgumentException("no player " + id);
		}
		return m;
	}

	/** One edit per setting, each to a value that differs from the drafted one. */
	static List<String> edits(Map<String, String> d, Profile p) {
		List<String> e = new ArrayList<String>();
		for (TacticWeights.Tactic t : TacticWeights.Tactic.values()) {
			if (t == TacticWeights.Tactic.CORE) {
				continue;
			}
			double w = p.weight(t);
			e.add("weight." + t + "=" + (w == 1.5 ? "1" : "1.5"));
			e.add("weight." + t + "=" + (w == 0.5 ? "1" : "0.5"));
		}
		for (Profile.Skill s : Profile.Skill.values()) {
			e.add("reliable." + s + "=" + other(d.get("reliable." + s), "50", "100"));
		}
		for (String style : new String[] { "Aggressive", "Avoids_Queen", "Shooting_the_Moon" }) {
			e.add("style." + style + "=" + other(d.get("style." + style), "50", "0"));
		}
		for (Strategy c : Strategy.values()) {
			String current = d.containsKey("strategy." + c) ? d.get("strategy." + c) : c.defaultOption.name();
			for (Enum<?> o : c.options()) {
				if (!o.name().equalsIgnoreCase(current)) {
					e.add("strategy." + c + "=" + o.name());
				}
			}
		}
		e.add("level=" + other(d.get("level"), "1", "5"));
		e.add("blunder=" + other(d.get("blunder"), "10", "0"));
		e.add("payback=" + ("off".equalsIgnoreCase(d.get("payback")) ? "on" : "off"));
		e.add("countCardsPercent=" + other(d.get("countCardsPercent"), "90", "0"));
		e.add("moonGamblePercent=" + other(d.get("moonGamblePercent"), "50", "0"));
		e.add("moonInterestPercent=" + other(d.get("moonInterestPercent"), "20", "0"));
		e.add("wildness=" + other(d.get("wildness"), "40", "0"));
		e.add("wildness.opening=" + other(d.get("wildness.opening"), "40", "0"));
		e.add("wildness.leading=" + other(d.get("wildness.leading"), "200", "50"));
		e.add("wildness.losing=" + other(d.get("wildness.losing"), "300", "100"));
		return e;
	}

	private static String other(String current, String first, String second) {
		return first.equals(current) ? second : first;
	}
}
