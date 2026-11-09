package hearts_1;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Player.Player_Type;

/**
 * Duplicate test: THE standard way to measure whether an AI tactic helps
 * (user, 2026-10-09: "we should always do that type of test, dup, whenever
 * we really want to see the impact in real life"). Named after duplicate
 * bridge, which removes the luck of the deal the same way.
 *
 * WHY. Hearts is so luck-driven that an ordinary A/B -- tactic on for some
 * seats, off for others, thousands of random hands -- can't see a narrow
 * tactic at all. The Queen bleed-out showed it: 10,000 unpaired hands gave
 * noise, while 6,000 duplicate deals found a clear -0.25 points a hand.
 *
 * HOW. Every deal is played from fixed seeds (deck and AI.RNG), so the same
 * deal replays card-for-card. Each deal is played five times:
 *   - BASELINE: the tactic off for every seat;
 *   - then four more, the tactic switched on for exactly ONE seat (N, E, S,
 *     W in turn) with the other three playing exactly as in the baseline.
 * The measure is that one seat's hand score with the tactic minus its score
 * in the baseline. Isolating one seat matters: with the tactic on for
 * everybody, each seat's result also depends on the others using it.
 *
 * A tactic joins by exposing a per-seat disable set (an EnumSet<PLAYER> test
 * hook -- see EndgameSolver.DISABLED_SEATS and
 * AICardScorer.QUEEN_BLEED_DISABLED_SEATS) and an entry in TOGGLES below.
 *
 * SELF-CHECK. Before measuring, the first deals' baselines are replayed and
 * must match card-for-card. If anything random is left unseeded the run
 * stops, because every number after that would be meaningless.
 *
 * Usage: java hearts_1.DuplicateTest <tactic> <deals> [type] [countsCardsPercent]
 *   tactic: endgame | bleed
 *   type:   a Player_Type name, all four seats (default NORMAL)
 *   countsCardsPercent: 0-100, all seats (default 100)
 *
 * A measuring tool, not part of AllTests -- a meaningful run takes minutes.
 */
public final class DuplicateTest {

	private DuplicateTest() { }

	static final Map<String, EnumSet<PLAYER>> TOGGLES = new LinkedHashMap<String, EnumSet<PLAYER>>();
	static {
		TOGGLES.put("endgame", EndgameSolver.DISABLED_SEATS);
		TOGGLES.put("bleed", AICardScorer.QUEEN_BLEED_DISABLED_SEATS);
	}

	private static final int SELF_CHECK_DEALS = 20;

	public static void main(String[] args) {
		if (args.length < 2 || !TOGGLES.containsKey(args[0])) {
			System.err.println("usage: DuplicateTest <" + String.join("|", TOGGLES.keySet())
					+ "> <deals> [type] [countsCardsPercent]");
			System.exit(2);
		}
		EnumSet<PLAYER> disabled = TOGGLES.get(args[0]);
		int deals = Integer.parseInt(args[1]);
		String typeArg = args.length > 2 ? args[2] : "NORMAL";
		Player_Type[] types = parseTypes(typeArg);
		int pct = args.length > 3 ? Integer.parseInt(args[3]) : 100;
		int[] pcts = { pct, pct, pct, pct };

		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		try {
			// Self-check: a baseline must replay identically.
			for (int d = 0; d < SELF_CHECK_DEALS; d++) {
				String a = play(disabled, null, d, types, pcts).plays;
				String b = play(disabled, null, d, types, pcts).plays;
				if (!a.equals(b)) {
					System.setOut(out);
					System.out.println("SELF-CHECK FAILED on deal " + d
							+ ": the same seeds produced different play. Something random is unseeded;"
							+ " no result would be trustworthy.");
					System.exit(1);
				}
			}

			long n = 0, changed = 0, better = 0, worse = 0;
			double sum = 0, sumSq = 0, sumChanged = 0, sumSqChanged = 0;
			Map<PLAYER, double[]> perSeat = new EnumMap<PLAYER, double[]>(PLAYER.class); // n, sum, sumSq, changed
			for (PLAYER p : PLAYER.values()) {
				perSeat.put(p, new double[4]);
			}
			long t0 = System.currentTimeMillis();
			for (int d = 0; d < deals; d++) {
				HeadlessSimulator.SeededHand base = play(disabled, null, d, types, pcts);
				for (PLAYER seat : PLAYER.values()) {
					HeadlessSimulator.SeededHand on = play(disabled, seat, d, types, pcts);
					int diff = on.scores.get(seat).intValue() - base.scores.get(seat).intValue();
					n++;
					sum += diff;
					sumSq += (double) diff * diff;
					double[] ps = perSeat.get(seat);
					ps[0]++;
					ps[1] += diff;
					ps[2] += (double) diff * diff;
					if (!on.plays.equals(base.plays)) {
						changed++;
						sumChanged += diff;
						sumSqChanged += (double) diff * diff;
						ps[3]++;
					}
					if (diff < 0) better++;
					if (diff > 0) worse++;
				}
			}
			long ms = System.currentTimeMillis() - t0;
			disabled.clear();

			double mean = sum / n;
			double se = Math.sqrt((sumSq / n - mean * mean) / n);
			System.setOut(out);
			System.out.printf("DUPLICATE TEST  tactic=%s  type=%s  counting=%d%%  deals=%d  (%d seat-hands, %.0fs)%n",
					args[0], typeArg, pct, deals, n, ms / 1000.0);
			System.out.printf("  points per hand for the seat using it: %+.3f  (+/- %.3f standard error, %.1f se)%n",
					mean, se, se == 0 ? 0 : Math.abs(mean) / se);
			double meanC = changed == 0 ? 0 : sumChanged / changed;
			double seC = changed < 2 ? 0 : Math.sqrt((sumSqChanged / changed - meanC * meanC) / changed);
			System.out.printf("  play changed in %d of %d seat-hands (%.1f%%); in those: %+.3f  (+/- %.3f se)%n",
					changed, n, 100.0 * changed / n, meanC, seC);
			System.out.printf("  better in %d, worse in %d, same in %d%n", better, worse, n - better - worse);
			for (PLAYER p : PLAYER.values()) {
				double[] ps = perSeat.get(p);
				double m = ps[1] / ps[0];
				double s = Math.sqrt((ps[2] / ps[0] - m * m) / ps[0]);
				System.out.printf("    %-5s (%s): %+.3f  (+/- %.3f se), play changed in %.1f%%%n",
						p, types[p.ordinal()], m, s, 100 * ps[3] / ps[0]);
			}
			System.out.println("  (negative = fewer points = the tactic helps; 2+ se is the bar for 'real')");
		} finally {
			disabled.clear();
			System.setOut(out);
		}
	}

	/** One type for every seat ("NORMAL"), or four comma-separated types
	 * in NORTH,EAST,SOUTH,WEST order ("MOON_SHOOTER,NORMAL,NORMAL,NORMAL"). */
	private static Player_Type[] parseTypes(String arg) {
		String[] parts = arg.split(",");
		Player_Type[] types = new Player_Type[4];
		for (int i = 0; i < 4; i++) {
			types[i] = Player_Type.valueOf(parts.length == 4 ? parts[i] : parts[0]);
		}
		return types;
	}

	/** One seeded hand with the tactic on only for `onSeat` (null = nobody). */
	private static HeadlessSimulator.SeededHand play(EnumSet<PLAYER> disabled, PLAYER onSeat, long deal,
			Player_Type[] types, int[] pcts) {
		disabled.clear();
		for (PLAYER p : PLAYER.values()) {
			if (p != onSeat) {
				disabled.add(p);
			}
		}
		return HeadlessSimulator.playSeededHand(deal, types, pcts);
	}
}
