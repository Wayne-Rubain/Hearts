package hearts_1;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/** Scratch (2026-11-02): how often the lead blunder guard (AI.isSuicidalLead)
 * changes a blunder, per 1000 hands, by player level. Random 4-player tables
 * from the roster, whole seeded games, each player at their own rates.
 * Args: rosterDir games [seedBase]. */
public final class LeadVeto {
	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		int games = Integer.parseInt(args[1]);
		long seedBase = args.length > 2 ? Long.parseLong(args[2]) : 11_000_000L;
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		List<RosterPlayer> ps = Roster.loadOrCreate(dir).players();
		int n = ps.size();
		final long[] handsByLevel = new long[6], vetoesByLevel = new long[6];
		final long[] handsOf = new long[n], vetoesOf = new long[n];
		final int[] seatPlayer = new int[4];
		HeadlessSimulator.observer = (att, gam, tot, hs) -> {
			for (int s = 0; s < 4; s++) {
				handsOf[seatPlayer[s]]++;
				handsByLevel[ps.get(seatPlayer[s]).profile.level]++;
			}
		};
		AI.leadVetoListener = p -> {
			int i = seatPlayer[p.tbl_player.ordinal()];
			vetoesOf[i]++;
			vetoesByLevel[ps.get(i).profile.level]++;
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
			}
			HeadlessSimulator.playSeededGame(seedBase + g, prof, counts, gamble, interest);
		}
		System.setOut(out);
		System.out.println("games=" + games + "  (veto = the guard changed a lead blunder)");
		for (int i = 0; i < n; i++) {
			System.out.printf("%-16s lvl %d  blunder %2d%%  hands %7d  vetoes %5d  per 1000 hands %.2f%n",
					ps.get(i).displayName, ps.get(i).profile.level, ps.get(i).profile.blunderPercent,
					handsOf[i], vetoesOf[i], 1000.0 * vetoesOf[i] / Math.max(1, handsOf[i]));
		}
		for (int L = 1; L <= 5; L++) {
			if (handsByLevel[L] > 0) {
				System.out.printf("level %d: %d hands, %d vetoes, %.2f per 1000 hands%n", L, handsByLevel[L],
						vetoesByLevel[L], 1000.0 * vetoesByLevel[L] / handsByLevel[L]);
			}
		}
	}
}
