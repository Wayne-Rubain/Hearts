package hearts_1;

import java.io.OutputStream;
import java.io.PrintStream;

import hearts_1.Player.Player_Traits;
import hearts_1.Player.Player_Type;

/** Scratch: one variant profile vs three plain Normals, whole seeded games,
 * variant seat rotated. Reports games won and average final points. */
public final class DialSim {
	public static void main(String[] args) throws Exception {
		String variant = args[0];
		int games = Integer.parseInt(args[1]);
		Profile v = variant(variant);
		// Optional 4th arg gamble=N: the variant seat's moon-gamble rate.
		int gamble = args.length > 3 && args[3].startsWith("gamble=") ? Integer.parseInt(args[3].substring(7)) : 0;
		Profile normal = args.length > 2 ? variant(args[2]) : Profile.of(Player_Type.NORMAL);
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		double wins = 0, pts = 0;
		for (int g = 0; g < games; g++) {
			int seat = g % 4;
			Profile[] seats = { normal, normal, normal, normal };
			seats[seat] = v;
			int[] gambles = new int[4];
			gambles[seat] = gamble;
			HeadlessSimulator.SeededGame r = HeadlessSimulator.playSeededGame(900_000 + g, seats, new int[] { 50, 50, 50, 50 }, gambles);
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
		System.out.printf("%-22s vs %-12s games=%d won=%.1f%% (se %.1f) avgFinal=%.1f%n", variant,
				args.length > 2 ? args[2] : "normal", games, 100 * p,
				100 * Math.sqrt(p * (1 - p) / games), pts / games);
	}


	static Profile variant(String name) {
		Profile n = Profile.of(Player_Type.NORMAL);
		if (name.startsWith("w:")) {
			return n.withWildness(Integer.parseInt(name.substring(2)));
		}
		if (name.startsWith("s:")) {
			for (String kv : name.substring(2).split("[+]")) {
				String[] p = kv.split("=");
				n = n.withSetting("strategy." + p[0], p[1]);
			}
			return n;
		}
		switch (name) {
			case "normal": return n;
			case "reliable75": return allSkills(n, 75);
			case "reliable50": return allSkills(n, 50);
			case "reliable0": return allSkills(n, 0);
			case "blunder10": return n.withBlunder(10);
			case "blunder33": return n.withBlunder(33);
			case "aggrMood50": return n.withStylePercent(Player_Traits.Aggressive, 50);
			case "payback": return n.withPayback(new int[] { 100, 60, 25 });
			case "paybackStrong": return n.withPayback(new int[] { 100, 100, 100 });
			case "bad":return Profile.of(Player_Type.BAD);
			case "braindead": return Profile.of(Player_Type.BRAIN_DEAD);
			default:
				if (name.startsWith("famoff_")) {
					return n.withReliability(hearts_1.TacticWeights.Tactic.valueOf(name.substring(7)), 0);
				}
				if (name.startsWith("off_")) {
					return n.withReliability(Profile.Skill.valueOf(name.substring(4)), 0);
				}
				throw new IllegalArgumentException(name);
		}
	}

	static Profile allSkills(Profile p, int pct) {
		for (Profile.Skill s : Profile.Skill.values()) {
			p = p.withReliability(s, pct);
		}
		return p;
	}
}
