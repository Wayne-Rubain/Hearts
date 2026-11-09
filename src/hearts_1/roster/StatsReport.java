package hearts_1.roster;

/**
 * Renders the human-facing stats screen text from the shared StatsStore,
 * added 2026-08-16 -- the first of the store's two planned consumers (the
 * second, AI opponent-modelling, reads the same counters later). Pure
 * text, no Swing: Hearts_JFrame drops the result into a monospace
 * dialog, and keeping the formatting here means a future fancier screen
 * (or a console dump for debugging) reuses the same source.
 */
public final class StatsReport {

	private StatsReport() { }

	public static String build(StatsStore store, Roster roster) {
		StringBuilder sb = new StringBuilder();
		String me = Roster.HUMAN_ID;

		sb.append("=== ").append(roster.humanName()).append(" ===\n");
		int games = store.get(me, "gamesPlayed");
		int wins = store.get(me, "gamesWon");
		sb.append(line("Games played", games));
		sb.append(line("Games won", wins + pct(wins, games)));
		sb.append(line("Placements 1st/2nd/3rd/4th",
				store.get(me, "place1") + " / " + store.get(me, "place2") + " / "
				+ store.get(me, "place3") + " / " + store.get(me, "place4")));
		sb.append(line("Win streak (current / longest)",
				store.get(me, "currentWinStreak") + " / " + store.get(me, "longestWinStreak")));
		int hands = store.get(me, "handsPlayed");
		sb.append(line("Hands played", hands));
		if (hands > 0) {
			sb.append(line("Avg points per hand",
					String.format("%.1f", store.get(me, "totalPointsTaken") / (double) hands)));
			sb.append(line("Best / worst hand",
					fmtScore(store.get(me, "bestHandScore")) + " / " + fmtScore(store.get(me, "worstHandScore"))));
		}
		sb.append(line("Perfect hands (0 or better)", store.get(me, "perfectHands")));
		sb.append(line("Slam hands (no tricks taken)", store.get(me, "slamHands")));
		sb.append(line("Jacks of Diamonds captured", store.get(me, "jacksCaptured")));
		sb.append(line("Times ate own Queen", store.get(me, "selfAteQueen")));
		sb.append(line("Moons shot", store.get(me, "moonSuccesses")));
		sb.append(line("My moons stopped", store.get(me, "moonsStoppedOnMe")));
		sb.append(line("Moon tries lead/middle/last", moonTries(store, me)));
		sb.append(line("AI's read lead/mid/last-by-25+", moonRep(store, me)));
		sb.append(line("Rating (ELO)", StatsRecorder.eloOf(store, me)));
		int chances = store.get(me, "comebackChances");
		sb.append(line("Comebacks (last at 50, won)",
				store.get(me, "comebacksWon") + " of " + chances + pct(store.get(me, "comebacksWon"), chances)));

		sb.append('\n').append("--- Passing ---\n");
		int given = store.get(me, "passPointsGiven");
		sb.append(line("Penalty points passed", given));
		if (given > 0) {
			sb.append(line("  landed on the recipient", store.get(me, "passPointsLanded")
					+ pct(store.get(me, "passPointsLanded"), given)));
			sb.append(line("  came back to me", store.get(me, "passPointsBackfired")
					+ pct(store.get(me, "passPointsBackfired"), given)));
			sb.append(line("  went to someone else", store.get(me, "passPointsElsewhere")
					+ pct(store.get(me, "passPointsElsewhere"), given)));
		}

		sb.append('\n').append("--- Leads (clean = took no points) ---\n");
		int leads = store.get(me, "leads");
		sb.append(line("All leads", store.get(me, "leadsClean") + " of " + leads
				+ pct(store.get(me, "leadsClean"), leads)));
		for (String suit : new String[] { "CLUB", "DIAMOND", "SPADE", "HEART" }) {
			int n = store.get(me, "leads_" + suit);
			if (n > 0) {
				int clean = store.get(me, "leadsClean_" + suit);
				sb.append(line("  " + suit.charAt(0) + suit.substring(1).toLowerCase() + "s",
						clean + " of " + n + pct(clean, n)));
			}
		}

		for (RosterPlayer p : roster.players()) {
			if (store.getPair(me, p.id, "handsTogether") == 0
					&& store.getPair(p.id, me, "handsTogether") == 0) {
				continue; // never played them -- keep the report short
			}
			sb.append('\n').append("=== vs ").append(p.displayName)
					.append(" (").append(p.type).append(", ELO ")
					.append(StatsRecorder.eloOf(store, p.id)).append(") ===\n");
			int together = store.getPair(me, p.id, "gamesTogether");
			int beat = store.getPair(me, p.id, "gamesBeaten");
			sb.append(line("Games together", together));
			sb.append(line("Finished ahead of them", beat + pct(beat, together)));
			sb.append(line("Hands together", store.getPair(me, p.id, "handsTogether")));
			sb.append(line("Queens I dropped on them", store.getPair(me, p.id, "queenDroppedOn")));
			sb.append(line("Queens they dropped on me", store.getPair(p.id, me, "queenDroppedOn")));
			sb.append(line("Queens I passed them", store.getPair(me, p.id, "queenPassedTo")));
			sb.append(line("Queens they passed me", store.getPair(p.id, me, "queenPassedTo")));
			sb.append(line("Jacks I gave them", store.getPair(me, p.id, "jackGivenTo")));
			sb.append(line("Jacks they gave me", store.getPair(p.id, me, "jackGivenTo")));
			sb.append(line("Their moons I stopped", store.getPair(me, p.id, "moonStopped")));
			sb.append(line("My moons they stopped", store.getPair(p.id, me, "moonStopped")));
			sb.append(line("Their moons shot (total)", store.get(p.id, "moonSuccesses")));
			sb.append(line("Their moon tries lead/mid/last", moonTries(store, p.id)));
			sb.append(line("Moon read lead/mid/last-by-25+", moonRep(store, p.id)));
			sb.append(line("Pass points I landed on them", store.getPair(me, p.id, "passPointsLandedOn")));
			sb.append(line("Pass points they landed on me", store.getPair(p.id, me, "passPointsLandedOn")));
		}
		return sb.toString();
	}

	/** The exact 0..1 moon-threat score OpponentModel feeds the AI, in three
	 * representative situations: leading, middle (a few points back), and
	 * last by 25+ -- so the human can see how the table reads them. */
	private static String moonRep(StatsStore store, String id) {
		return String.format("%.0f%% / %.0f%% / %.0f%%",
				100 * OpponentModel.moonScoreOf(store, id, OpponentModel.Place.LEAD, 0),
				100 * OpponentModel.moonScoreOf(store, id, OpponentModel.Place.MIDDLE, 1),
				100 * OpponentModel.moonScoreOf(store, id, OpponentModel.Place.LAST, 3));
	}

	/** Raw moon attempts / hands started in each Place. */
	private static String moonTries(StatsStore store, String id) {
		StringBuilder sb = new StringBuilder();
		for (OpponentModel.Place p : OpponentModel.Place.values()) {
			if (sb.length() > 0) {
				sb.append(" / ");
			}
			sb.append(store.get(id, "moons_" + p)).append(" of ").append(store.get(id, "hands_" + p));
		}
		return sb.toString();
	}

	private static String line(String label, Object value) {
		return String.format("%-34s %s%n", label + ":", value);
	}

	private static String pct(int part, int whole) {
		return whole <= 0 ? "" : String.format("  (%.0f%%)", 100.0 * part / whole);
	}

	private static String fmtScore(int s) {
		return s > 0 ? "+" + s : Integer.toString(s);
	}
}
