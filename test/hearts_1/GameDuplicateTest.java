package hearts_1;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.Random;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Player.Player_Type;

/**
 * The whole-GAME duplicate test, added 2026-10-10. DuplicateTest replays
 * single hands that all start from a 0-0-0-0 game, so anything driven by
 * the standings never fires there (the Queen-to-stop-a-moon decision always
 * refuses when everyone is level; the near-100 end game never happens).
 * This one replays entire seeded games instead -- real standings, hand after
 * hand, until someone reaches 100 -- and judges a tactic by what actually
 * matters in this game: GAMES WON (Wayne, 2026-10-10: "it only matters to
 * win"). A tied win is shared.
 *
 * Each game is played 5 ways from the same seeds: baseline (tactic off for
 * every seat) and on for exactly one seat at a time. The tool self-checks
 * that a baseline replays identically before reporting anything.
 *
 * Usage: java hearts_1.GameDuplicateTest <tactic> <games> [types] [countsCardsPercent] [firstGame]
 *   tactic: a key of DuplicateTest.TOGGLES (e.g. endgame, bleed)
 *   types:  MIXED (default -- every game draws four types from a pool,
 *           seeded), one Player_Type for all seats, or four comma-separated
 *   countsCardsPercent: 0-100, all seats (default 100)
 */
public final class GameDuplicateTest {

	private GameDuplicateTest() { }

	private static final Player_Type[] MIXED_POOL = { Player_Type.NORMAL, Player_Type.AGGRESSIVE,
			Player_Type.CAUTIOUS, Player_Type.AVOIDS_QUEEN, Player_Type.MOON_SHOOTER };
	private static final int SELF_CHECK_GAMES = 5;

	public static void main(String[] args) {
		if (args.length < 2 || !DuplicateTest.TOGGLES.containsKey(args[0])) {
			System.err.println("usage: GameDuplicateTest <" + String.join("|", DuplicateTest.TOGGLES.keySet())
					+ "> <games> [MIXED|type|t1,t2,t3,t4] [countsCardsPercent]");
			System.exit(2);
		}
		EnumSet<PLAYER> disabled = DuplicateTest.TOGGLES.get(args[0]);
		int games = Integer.parseInt(args[1]);
		String typeArg = args.length > 2 ? args[2] : "MIXED";
		int pct = args.length > 3 ? Integer.parseInt(args[3]) : 100;
		int[] pcts = { pct, pct, pct, pct };
		// Optional first game seed, so long measurements can run as several
		// parallel processes over disjoint game ranges.
		int first = args.length > 4 ? Integer.parseInt(args[4]) : 0;

		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		try {
			for (int g = 0; g < SELF_CHECK_GAMES; g++) {
				Player_Type[] types = typesFor(typeArg, g);
				String a = play(disabled, null, g, types, pcts).plays;
				String b = play(disabled, null, g, types, pcts).plays;
				if (!a.equals(b)) {
					System.setOut(out);
					System.out.println("SELF-CHECK FAILED on game " + g
							+ ": the same seeds produced different play. No result would be trustworthy.");
					System.exit(1);
				}
			}

			long n = 0, changed = 0;
			double winSum = 0, winSq = 0, ptsSum = 0, ptsSq = 0, baseWins = 0;
			long t0 = System.currentTimeMillis();
			for (int g = first; g < first + games; g++) {
				Player_Type[] types = typesFor(typeArg, g);
				HeadlessSimulator.SeededGame base = play(disabled, null, g, types, pcts);
				for (PLAYER seat : PLAYER.values()) {
					HeadlessSimulator.SeededGame on = play(disabled, seat, g, types, pcts);
					double dw = winShare(on.finals, seat.ordinal()) - winShare(base.finals, seat.ordinal());
					double dp = on.finals[seat.ordinal()] - base.finals[seat.ordinal()];
					n++;
					winSum += dw;
					winSq += dw * dw;
					ptsSum += dp;
					ptsSq += dp * dp;
					baseWins += winShare(base.finals, seat.ordinal());
					if (!on.plays.equals(base.plays)) {
						changed++;
					}
				}
			}
			long ms = System.currentTimeMillis() - t0;
			disabled.clear();

			double mw = winSum / n, sw = Math.sqrt((winSq / n - mw * mw) / n);
			double mp = ptsSum / n, sp = Math.sqrt((ptsSq / n - mp * mp) / n);
			System.setOut(out);
			System.out.printf("GAME DUPLICATE TEST  tactic=%s  types=%s  counting=%d%%  games=%d  (%d seat-games, %.0fs)%n",
					args[0], typeArg, pct, games, n, ms / 1000.0);
			System.out.printf("  baseline win rate per seat: %.1f%%%n", 100 * baseWins / n);
			System.out.printf("  WIN RATE change for the seat using it: %+.2f points of percentage  (+/- %.2f se, %.1f se)%n",
					100 * mw, 100 * sw, sw == 0 ? 0 : Math.abs(mw) / sw);
			System.out.printf("  final game total change: %+.2f points  (+/- %.2f se)%n", mp, sp);
			System.out.printf("  game play changed in %d of %d seat-games (%.1f%%)%n", changed, n, 100.0 * changed / n);
			System.out.println("  (positive win-rate change = the tactic helps; 2+ se is the bar for 'real')");
		} finally {
			disabled.clear();
			System.setOut(out);
		}
	}

	/** 1 for the lowest final total, shared on a tie, else 0. */
	static double winShare(int[] finals, int seat) {
		int low = Integer.MAX_VALUE;
		for (int f : finals) {
			low = Math.min(low, f);
		}
		if (finals[seat] != low) {
			return 0;
		}
		int tied = 0;
		for (int f : finals) {
			if (f == low) {
				tied++;
			}
		}
		return 1.0 / tied;
	}

	private static Player_Type[] typesFor(String arg, long game) {
		Player_Type[] types = new Player_Type[4];
		if ("MIXED".equals(arg)) {
			Random r = new Random(game * 31L + 7);
			for (int i = 0; i < 4; i++) {
				types[i] = MIXED_POOL[r.nextInt(MIXED_POOL.length)];
			}
			return types;
		}
		String[] parts = arg.split(",");
		for (int i = 0; i < 4; i++) {
			types[i] = Player_Type.valueOf(parts.length == 4 ? parts[i] : parts[0]);
		}
		return types;
	}

	/** One seeded game with the tactic on only for `onSeat` (null = nobody). */
	private static HeadlessSimulator.SeededGame play(EnumSet<PLAYER> disabled, PLAYER onSeat, long game,
			Player_Type[] types, int[] pcts) {
		disabled.clear();
		for (PLAYER p : PLAYER.values()) {
			if (p != onSeat) {
				disabled.add(p);
			}
		}
		return HeadlessSimulator.playSeededGame(game, types, pcts);
	}
}
