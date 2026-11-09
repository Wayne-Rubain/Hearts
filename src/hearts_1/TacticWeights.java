package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Player.Player_Type;

/**
 * The tactic-weighting matrix, added 2026-08-07 (designed in TODO.md's
 * "per-type tactic gating and weighting" section, requested by the user:
 * "all of these tactics will not be available to the players that aren't
 * that good; Aggressive and moon-shooter types will ignore some of the
 * tactics or not value them as much").
 *
 * Every scorer in AICardScorer.java declares which Tactic family it belongs
 * to, and its contribution is multiplied by that family's weight for the
 * acting player's type:
 *
 *   0.0  the player doesn't know this tactic exists
 *   0.5  knows it but undervalues it
 *   1.0  full use
 *   1.5  overvalues it (personality flavor)
 *
 * Because the whole AI is additive scoring, a weight is one multiplication
 * -- no branch surgery -- and this table is exactly the foundation the
 * future personality/roster system needs: a personality will simply be its
 * own weight row (a ranked mixture of the same tactics) instead of one of
 * these presets.
 *
 * Two tactics are consumed OUTSIDE the scorers: MOON_OFFENSE also gates the
 * attempt decision itself (HandEvaluator.shouldAttemptMoonShot -- a 0 never
 * commits, a 0.5 needs a stronger hand, a 1.5 tries on marginal ones), and
 * PASSING_CRAFT gates the whole strategic passing pipeline in Deal_Hand
 * (weight 0 passes naively). CORE is the pseudo-family for baseline
 * competence every non-bypassed player keeps -- suit-length preference,
 * lead-low, void-discard priority, the trait-gated Aggressive/Avoids_Queen
 * styles -- and always weighs 1.
 *
 * BRAIN_DEAD never reaches the scorers at all (AI.java's competence bypass
 * plays the first legal card), so its row only matters for PASSING_CRAFT.
 * BAD keeps basic point-safety (plus its existing 1-in-3 random blunder,
 * also applied before scoring) and knows nothing else.
 */
public final class TacticWeights {

	private TacticWeights() { }

	/** One concern-family per scorer. See AICardScorer.java for membership;
	 * the matrix lives in buildTable() below. */
	public enum Tactic {
		/** Baseline competence, never gated -- always weight 1. */
		CORE,
		/** Ducking and the graded lead dangers (Queen, high Spade, Hearts). */
		BASIC_SAFETY,
		/** Avoiding (and pricing) suits a seat is known void in. */
		VOID_AWARENESS,
		/** Never gifting the Jack of Diamonds, protecting Jack assets, AND
		 * cashing a Jack already held once it's provably safe (2026-08-16
		 * user correction: this is a guarded payoff, not a hunt -- see the
		 * two "COUNTED-OUT JACK OF DIAMONDS" scorers, moved here from
		 * JACK_HUNT). */
		JACK_GUARD,
		/** Actively winning the Jack away from ANOTHER seat: pull leads
		 * that force it out of hiding, opportunistic captures of a Jack
		 * someone else exposed. Genuinely risky -- unlike JACK_GUARD's
		 * counted-out lead/capture, these commit before the outcome is
		 * certain. */
		JACK_HUNT,
		/** Card-counting certainties: counted Queen handoff, safe unload. */
		COUNTED_CERTAINTIES,
		/** Drawing the Queen out with a low lead into a known void. */
		QUEEN_FLUSH,
		/** Shooting the moon: the attempt itself and moon-mode play. */
		MOON_OFFENSE,
		/** Stopping a shooter: redirect, withhold fuel, take to deny. */
		MOON_DEFENSE,
		/** The strategic passing pipeline in Deal_Hand.java. */
		PASSING_CRAFT,
		/** Absolute proximity to TARGET_SCORE (EndGame.java, 2026-08-15):
		 * extra reluctance to take points as my own total nears the target,
		 * and a bonus for dumping void-discard points onto whichever seat is
		 * closest to it. Distinct from the standings-delta terms already
		 * baked into MOON_OFFENSE/PASSING_CRAFT, which compare seats against
		 * each other rather than against the target itself. */
		END_GAME,
		/** Standings-vs-each-other tactics that apply throughout the whole
		 * game, not just near TARGET_SCORE (2026-08-15, TODO.md's "Targeted
		 * Queen placement" item, generalized to Hearts too on a user
		 * request): a trailing seat directing a void discard onto whichever
		 * trick the standings LEADER is currently winning, to close the gap.
		 * Distinct from END_GAME -- this fires from hand 1, regardless of
		 * anyone's absolute proximity to the target. */
		SOCIAL
	}

	private static final Map<Player_Type, Map<Tactic, Double>> TABLE = buildTable();

	/** The acting player's weight for a family -- read from the player's
	 * Profile (2026-10-10), which starts as one of the rows below and may
	 * override any of them. A family the player's reliability dice left out
	 * of this hand (Player.skippedThisHand, step 2) weighs 0. */
	public static double weight(Player player, Tactic tactic) {
		if (tactic == Tactic.MOON_OFFENSE && (player.moonInterestThisHand || player.moonLookThisHand)
				&& player.attempting_moon_shot) {
			// A moon-interest gamble must be played out (2026-10-17): the player
			// committed through the interest roll, so its template weight or a
			// skipped skill must not leave it holding a shot it never plays.
			return Math.max(0.5, player.profile.weight(tactic));
		}
		if (player.skippedThisHand.contains(tactic)) {
			return 0;
		}
		return player.profile.weight(tactic);
	}

	/** A copy of a preset's row -- the starting point Profile.of() builds
	 * from. */
	static Map<Tactic, Double> templateRow(Player_Type type) {
		Map<Tactic, Double> row = TABLE.get(type);
		return row == null ? new EnumMap<Tactic, Double>(Tactic.class) : new EnumMap<Tactic, Double>(row);
	}

	/** Row order: BASIC_SAFETY, VOID_AWARENESS, JACK_GUARD, JACK_HUNT,
	 * COUNTED_CERTAINTIES, QUEEN_FLUSH, MOON_OFFENSE, MOON_DEFENSE,
	 * PASSING_CRAFT, END_GAME, SOCIAL -- same order as the TODO.md design
	 * table, with END_GAME and SOCIAL appended 2026-08-15. */
	private static Map<Player_Type, Map<Tactic, Double>> buildTable() {
		Map<Player_Type, Map<Tactic, Double>> t =
				new EnumMap<Player_Type, Map<Tactic, Double>>(Player_Type.class);
		t.put(Player_Type.NORMAL, row(1, 1, 1, 1, 1, 1, 0.5, 1, 1, 1, 1));
		// Cautious: solid safety, never shoots, skips the flashy plays (no
		// Jack hunting, no flush) and only half-trusts the counted
		// certainties -- which in practice keeps the safe unload and makes
		// the counted Queen handoff a genuinely-last-resort play.
		t.put(Player_Type.CAUTIOUS, row(1, 1, 1, 0, 0.5, 0, 0, 1, 1, 1, 1));
		// Aggressive: ducks less, hunts the Jack harder, and stays aggressive
		// near the target too -- it doesn't back off just because it's close
		// to 100, same as it doesn't duck just because a trick has points.
		t.put(Player_Type.AGGRESSIVE, row(0.5, 1, 1, 1.5, 1, 1, 0.5, 1, 1, 0.5, 1));
		// Avoids_Queen: everything Queen-shaped is over-valued, the Jack
		// hunt is a distraction from the real enemy.
		t.put(Player_Type.AVOIDS_QUEEN, row(1, 1, 1, 0.5, 1, 1.5, 0, 1, 1, 1, 1));
		// Combo type: max of its two parent rows, per the design.
		t.put(Player_Type.AGGRESSIVE_AVOIDS_QUEEN, row(1, 1, 1, 1.5, 1, 1.5, 0.5, 1, 1, 1, 1));
		// Moon_Shooter: eager to attempt, uninterested in tactics that give
		// point cards away to other players (the flush hands out 13 points
		// it would rather collect; the Jack hunt is a half-interest). Its own
		// moon-specific restraint near the target is handled by
		// HandEvaluator's danger gate, not this row, so END_GAME stays full.
		// SOCIAL is halved -- focused on its own shot, less bandwidth for
		// targeting the leader on an ordinary hand.
		t.put(Player_Type.MOON_SHOOTER, row(1, 1, 1, 0.5, 1, 0, 1.5, 1, 1, 1, 0.5));
		// Bad: point-safety basics only, everything else unknown, passing
		// included. The 1-in-3 random blunder stays on top of this.
		t.put(Player_Type.BAD, row(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		// Brain_Dead: bypassed before scoring; only PASSING_CRAFT (0) is
		// ever actually read from this row.
		t.put(Player_Type.BRAIN_DEAD, row(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		return t;
	}

	private static Map<Tactic, Double> row(double basicSafety, double voidAwareness,
			double jackGuard, double jackHunt, double countedCertainties,
			double queenFlush, double moonOffense, double moonDefense,
			double passingCraft, double endGame, double social) {
		Map<Tactic, Double> row = new EnumMap<Tactic, Double>(Tactic.class);
		row.put(Tactic.BASIC_SAFETY, Double.valueOf(basicSafety));
		row.put(Tactic.VOID_AWARENESS, Double.valueOf(voidAwareness));
		row.put(Tactic.JACK_GUARD, Double.valueOf(jackGuard));
		row.put(Tactic.JACK_HUNT, Double.valueOf(jackHunt));
		row.put(Tactic.COUNTED_CERTAINTIES, Double.valueOf(countedCertainties));
		row.put(Tactic.QUEEN_FLUSH, Double.valueOf(queenFlush));
		row.put(Tactic.MOON_OFFENSE, Double.valueOf(moonOffense));
		row.put(Tactic.MOON_DEFENSE, Double.valueOf(moonDefense));
		row.put(Tactic.PASSING_CRAFT, Double.valueOf(passingCraft));
		row.put(Tactic.END_GAME, Double.valueOf(endGame));
		row.put(Tactic.SOCIAL, Double.valueOf(social));
		return row;
	}
}
