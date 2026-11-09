package hearts_1;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/** Scratch: round robin of a roster -- every 4-player combination, seats
 * rotated, whole seeded games. Args: rosterDir gamesPerCombo shard shards [nogamble|gamble] [nointerest].
 * Prints per player: games, wins (ties split), total final points, then
 * (added 2026-11-02) hands, moon tries (committed after the pass), moons made
 * by those tries (-26 or better), Queens of Spades eaten, Jacks of Diamonds taken. */
public final class Lineup {
	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		int perCombo = Integer.parseInt(args[1]);
		int shard = Integer.parseInt(args[2]), shards = Integer.parseInt(args[3]);
		// Optional 5th arg "nogamble" reproduces the old behavior (gamble rate 0).
		boolean noGamble = args.length > 4 && "nogamble".equals(args[4]);
		// Optional 6th arg "nointerest" turns the moon-interest roll off (2026-10-17).
		boolean noInterest = args.length > 5 && "nointerest".equals(args[5]);
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		List<RosterPlayer> ps = Roster.loadOrCreate(dir).players();
		int n = ps.size();
		double[] wins = new double[n], pts = new double[n];
		int[] games = new int[n];
		final long[] hands = new long[n], tries = new long[n], made = new long[n], queens = new long[n], jacks = new long[n];
		final int[] seatOf = new int[4];
		HeadlessSimulator.observer = new HeadlessSimulator.HandObserver() {
			@Override public void hand(boolean[] att, boolean[] gam, int[] tot, int[] hs) {
				for (int s = 0; s < 4; s++) {
					hands[seatOf[s]]++;
					if (att[s]) { tries[seatOf[s]]++; if (hs[s] <= -26) made[seatOf[s]]++; }
				}
			}
			@Override public void captures(boolean[] queen, boolean[] jack) {
				for (int s = 0; s < 4; s++) {
					if (queen[s]) queens[seatOf[s]]++;
					if (jack[s]) jacks[seatOf[s]]++;
				}
			}
		};
		List<int[]> combos = new ArrayList<int[]>();
		for (int a = 0; a < n; a++)
			for (int b = a + 1; b < n; b++)
				for (int c = b + 1; c < n; c++)
					for (int d = c + 1; d < n; d++)
						combos.add(new int[] { a, b, c, d });
		for (int ci = shard; ci < combos.size(); ci += shards) {
			int[] combo = combos.get(ci);
			for (int g = 0; g < perCombo; g++) {
				Profile[] prof = new Profile[4];
				int[] counts = new int[4];
				int[] gambles = new int[4];
				int[] interests = new int[4];
				for (int s = 0; s < 4; s++) {
					seatOf[s] = combo[(s + g) % 4];
					prof[s] = ps.get(seatOf[s]).profile;
					counts[s] = ps.get(seatOf[s]).countCardsPercent;
					gambles[s] = noGamble ? 0 : ps.get(seatOf[s]).moonGamblePercent;
					interests[s] = noInterest ? 0 : ps.get(seatOf[s]).moonInterestPercent;
				}
				HeadlessSimulator.SeededGame r = HeadlessSimulator.playSeededGame(
						7_000_000L + ci * 1000L + g, prof, counts, gambles, interests);
				int best = Integer.MAX_VALUE, nBest = 0;
				for (int f : r.finals) {
					if (f < best) { best = f; nBest = 1; } else if (f == best) { nBest++; }
				}
				for (int s = 0; s < 4; s++) {
					games[seatOf[s]]++;
					pts[seatOf[s]] += r.finals[s];
					if (r.finals[s] == best) {
						wins[seatOf[s]] += 1.0 / nBest;
					}
				}
			}
		}
		System.setOut(out);
		for (int i = 0; i < n; i++) {
			System.out.printf("%s\t%s\t%d\t%.1f\t%.0f\t%d\t%d\t%d\t%d\t%d%n", ps.get(i).id, ps.get(i).displayName,
					games[i], wins[i], pts[i], hands[i], tries[i], made[i], queens[i], jacks[i]);
			if (perCombo == 0) {
				System.out.println("   " + ps.get(i).profile + " count=" + ps.get(i).countCardsPercent);
			}
		}
	}
}
