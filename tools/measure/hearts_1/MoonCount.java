package hearts_1;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/** Scratch: how often each roster player commits to a moon shot, by level and
 * by how far behind the leader they are at the start of the hand.
 * Args: rosterDir games [seedBase]. Random 4-player tables, seats rotated,
 * each player at their own moonGamblePercent. */
public final class MoonCount {
	static final String[] BUCKET = { "leading/level", "1-14 behind", "15-29 behind", "30+ behind" };

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		int games = Integer.parseInt(args[1]);
		long seedBase = args.length > 2 ? Long.parseLong(args[2]) : 9_000_000L;
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		List<RosterPlayer> ps = Roster.loadOrCreate(dir).players();
		int n = ps.size();
		final int[][] hands = new int[n][4], tries = new int[n][4], made = new int[n][4], gambles = new int[n][4];
		final int[] gamesOf = new int[n];
		final int[] seatPlayer = new int[4];
		HeadlessSimulator.observer = (att, gam, tot, hs) -> {
			int best = Integer.MAX_VALUE;
			for (int t : tot) best = Math.min(best, t);
			for (int s = 0; s < 4; s++) {
				int behind = tot[s] - best;
				int b = behind <= 0 ? 0 : behind < 15 ? 1 : behind < 30 ? 2 : 3;
				int p = seatPlayer[s];
				hands[p][b]++;
				if (att[s]) { tries[p][b]++; if (hs[s] <= -26) made[p][b]++; }
				if (gam[s]) gambles[p][b]++;
			}
		};
		java.util.Random rnd = new java.util.Random(seedBase);
		for (int g = 0; g < games; g++) {
			int[] pick = new int[4];
			for (int k = 0; k < 4; k++) {
				int c;
				boolean dup;
				do { c = rnd.nextInt(n); dup = false; for (int j = 0; j < k; j++) dup |= pick[j] == c; } while (dup);
				pick[k] = c;
			}
			Profile[] prof = new Profile[4];
			int[] counts = new int[4], gamble = new int[4], interest = new int[4];
			for (int s = 0; s < 4; s++) {
				seatPlayer[s] = pick[s];
				prof[s] = ps.get(pick[s]).profile;
				counts[s] = ps.get(pick[s]).countCardsPercent;
				gamble[s] = ps.get(pick[s]).moonGamblePercent;
				interest[s] = ps.get(pick[s]).moonInterestPercent;
				gamesOf[pick[s]]++;
			}
			HeadlessSimulator.playSeededGame(seedBase + g, prof, counts, gamble, interest);
		}
		System.setOut(out);
		System.out.println("games=" + games + "  (tries = committed to a moon shot after the pass; made = scored -26 or better)");
		System.out.printf("%-16s %3s %6s %7s %7s %6s %7s%n", "player", "lvl", "games", "hands", "tries", "made", "tries/100h");
		int[][] lvl = new int[6][3];
		for (int i = 0; i < n; i++) {
			int h = 0, t = 0, m = 0;
			for (int b = 0; b < 4; b++) { h += hands[i][b]; t += tries[i][b]; m += made[i][b]; }
			int L = ps.get(i).profile.level;
			lvl[L][0] += h; lvl[L][1] += t; lvl[L][2] += m;
			System.out.printf("%-16s %3d %6d %7d %7d %6d %7.2f%n", ps.get(i).displayName, L, gamesOf[i], h, t, m, 100.0 * t / h);
		}
		System.out.println();
		for (int L = 1; L <= 5; L++) {
			if (lvl[L][0] > 0) System.out.printf("level %d: %d hands, %d tries (%.2f per 100 hands), %d made%n", L, lvl[L][0], lvl[L][1], 100.0 * lvl[L][1] / lvl[L][0], lvl[L][2]);
		}
		System.out.println();
		System.out.println("By standing at the start of the hand (all players):");
		for (int b = 0; b < 4; b++) {
			int h = 0, t = 0, m = 0, gm = 0;
			for (int i = 0; i < n; i++) { h += hands[i][b]; t += tries[i][b]; m += made[i][b]; gm += gambles[i][b]; }
			System.out.printf("%-14s %8d hands %6d tries (%.2f per 100)  %4d made  gamble-active hands %d%n", BUCKET[b], h, t, 100.0 * t / Math.max(1, h), m, gm);
		}
	}
}
