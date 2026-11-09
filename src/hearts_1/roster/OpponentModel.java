package hearts_1.roster;

import java.util.Map;

import hearts_1.Hearts_1.PLAYER;

/**
 * The AI's read side of the shared StatsStore, added 2026-10-10 -- the
 * second of the store's two planned consumers (StatsReport was the first).
 * Answers one question: how likely is this opponent to be shooting the moon
 * in the situation they are in RIGHT NOW, compared with an ordinary player?
 *
 * NO AI DECISION READS IT YET -- only the stats screen does. Two hooks were
 * built and taken back out the same day (2026-10-10):
 *   - Hook B (never pass a likely shooter a top Heart) measured clearly
 *     HARMFUL in DuplicateTest: +0.20 points/hand (9 se) for the seat using
 *     it, +2.3 in the hands it changed. Keeping an Ace/King of Hearts wins
 *     Heart tricks; even the sim's most extreme shooter tries a moon in only
 *     ~3% of hands, so the insurance costs far more than it saves.
 *   - Hook A (accept the Queen a Heart or two earlier against a likely
 *     shooter) could not be measured -- single-hand duplicate deals start
 *     every seat level, where the standings check always refuses -- and was
 *     removed rather than shipped blind on a 13-point decision.
 * The lesson for any future consumer: the score identifies shooters well;
 * acting EARLY on a rare event is what costs.
 *
 * SITUATIONAL, per Wayne (2026-10-10): moon appetite depends on standings.
 * A leader rarely shoots -- the downside outweighs the upside and every
 * other player is watching them -- while a last-place player shoots more,
 * because the table is busy pulling down the players ahead. And how FAR
 * behind matters: last by 5 and last by 40 are different situations. So
 * every hand is recorded against the player's start-of-hand Place (lead /
 * middle / last) and distance band (points behind the leader), and the
 * prediction is made for the subject's current Place and band.
 *
 * Estimate, from thin data, in layers -- each layer leans on the one above
 * it until it has enough hands of its own:
 *   1. table rate overall (every player's attempts / hands);
 *   2. table rate at this Place, and at this Place+band (all players
 *      pooled -- this is where the distance effect is learned);
 *   3. the subject's own overall rate, shrunk toward the table's;
 *   4. the subject's own rate at this Place, shrunk toward (their overall
 *      x the table's Place shape);
 *   5. times the table's distance effect within that Place.
 * The result is compared with the OTHER players' overall rate -- so a typical
 * player who is last by 40 already reads as more dangerous than the same
 * player leading, and a habitual shooter reads higher still. RATIO_FOR_TOP
 * times the table rate is the top of the 0..1 scale.
 *
 * A relative yardstick on purpose: an absolute "3% is ordinary" bar was
 * tried first and failed -- in simulation even the Moon_Shooter type
 * attempts only ~3% of hands (Normal ~0.4%), so nothing ever registered.
 *
 * Measure of an attempt (Wayne's call): a completed moon, or a STOPPED one
 * (StatsRecorder.STOPPED_MOON_MIN_HEARTS banked first). Same for human and
 * AI -- MOON_COMMITTED never fires for the human.
 *
 * NEUTRAL BY DEFAULT: no store, no identity, or no history gives exactly 0.
 */
public final class OpponentModel {

	public enum Place { LEAD, MIDDLE, LAST }

	/** Pseudo-identity holding the table-wide (all players pooled)
	 * situation counters. Starts with '_' so no roster id can collide. */
	public static final String TABLE_ID = "_TABLE";
	/** Distance band i covers "points behind the leader >= floor[i]". */
	static final int[] BAND_FLOORS = { 0, 1, 10, 25 };
	static final String[] BAND_NAMES = { "level", "1-9 behind", "10-24 behind", "25+ behind" };

	/** Table rate assumed before any history exists. */
	static final double DEFAULT_RATE = 0.02;
	/** Shrinkage weights, in "imaginary hands" of the layer above. Large on
	 * purpose: at a ~2% base rate ONE moon is worth about 50 hands of
	 * evidence, so a weight of 20-40 (the first version) let a single lucky
	 * moon max out the scale. The band layer is table-wide and accumulates
	 * fastest, so it can afford the heaviest shrink. */
	static final double K_OVERALL = 150;
	static final double K_PLACE = 100;
	static final double K_BAND = 300;
	/** A predicted rate this many times the table's overall rate is the top
	 * of the scale; equal to the table rate is 0. */
	static final double RATIO_FOR_TOP = 4;

	private OpponentModel() { }

	// ---- situation ------------------------------------------------------

	/** Lead / middle / last by running total (lower is better). Ties for
	 * the lead count as LEAD, ties for last as LAST, and a table where
	 * everyone is level (every game's first hand) as MIDDLE. */
	public static Place placeOf(PLAYER seat, Map<PLAYER, Integer> totals) {
		if (totals == null || totals.get(seat) == null) {
			return Place.MIDDLE;
		}
		int me = totals.get(seat).intValue();
		boolean anyLower = false, anyHigher = false;
		for (Map.Entry<PLAYER, Integer> e : totals.entrySet()) {
			if (e.getKey() == seat || e.getValue() == null) {
				continue;
			}
			anyLower |= e.getValue().intValue() < me;
			anyHigher |= e.getValue().intValue() > me;
		}
		if (!anyLower && !anyHigher) {
			return Place.MIDDLE;
		}
		if (!anyLower) {
			return Place.LEAD;
		}
		return anyHigher ? Place.MIDDLE : Place.LAST;
	}

	/** Index into BAND_FLOORS for how far `seat` trails the leader. */
	public static int bandOf(PLAYER seat, Map<PLAYER, Integer> totals) {
		if (totals == null || totals.get(seat) == null) {
			return 0;
		}
		int lowest = Integer.MAX_VALUE;
		for (Integer t : totals.values()) {
			if (t != null) {
				lowest = Math.min(lowest, t.intValue());
			}
		}
		int behind = totals.get(seat).intValue() - lowest;
		int band = 0;
		for (int i = 0; i < BAND_FLOORS.length; i++) {
			if (behind >= BAND_FLOORS[i]) {
				band = i;
			}
		}
		return band;
	}

	// ---- recording (called by StatsRecorder, and by DuplicateTest's
	// calibration so the simulated profile uses the identical keys) ------

	public static void recordHand(StatsStore s, String id, Place place, int band, boolean attempted) {
		String p = place.name();
		String pb = p + "_" + band;
		s.add(id, "hands_" + p, 1);
		s.add(TABLE_ID, "hands_" + p, 1);
		s.add(TABLE_ID, "hands_" + pb, 1);
		if (attempted) {
			s.add(id, "moons_" + p, 1);
			s.add(TABLE_ID, "moons_" + p, 1);
			s.add(TABLE_ID, "moons_" + pb, 1);
		}
	}

	// ---- the read side -------------------------------------------------

	/** 0..1: how much of a moon threat `id` is in the given situation,
	 * relative to the other players (shown on the stats screen). */
	public static double moonScoreOf(StatsStore s, String id, Place place, int band) {
		double ratio = predictedRate(s, id, place, band) / tableRate(s, id);
		return clamp01((ratio - 1) / (RATIO_FOR_TOP - 1));
	}

	/** The subject's predicted attempt rate per hand in this situation.
	 *
	 * The subject's baseline (and the yardstick moonScoreOf divides by) is
	 * the OTHER players' rate: one habitual shooter at a four-seat table
	 * drags the all-players average up enough to hide itself otherwise.
	 * The Place shape and distance factor use everyone's POOLED counters --
	 * they describe how situations move moon appetite in general, and the
	 * two pooled layers stay consistent with each other. */
	static double predictedRate(StatsStore s, String id, Place place, int band) {
		String p = place.name();
		double others = tableRate(s, id);
		double pooled = tableRate(s, null);
		double pooledPlace = shrink(s.get(TABLE_ID, "moons_" + p), s.get(TABLE_ID, "hands_" + p), pooled, K_PLACE);
		double pooledBand = shrink(s.get(TABLE_ID, "moons_" + p + "_" + band),
				s.get(TABLE_ID, "hands_" + p + "_" + band), pooledPlace, K_BAND);
		double mine = shrink(attempts(s, id), s.get(id, "handsPlayed"), others, K_OVERALL);
		double minePlace = shrink(s.get(id, "moons_" + p), s.get(id, "hands_" + p),
				mine * pooledPlace / pooled, K_PLACE);
		return minePlace * pooledBand / pooledPlace;
	}

	/** Every real player's attempts over every hand played, leaving out
	 * `excludeId` (null = everyone). */
	static double tableRate(StatsStore s, String excludeId) {
		int hands = 0, att = 0;
		for (String id : s.knownPlayerIds()) {
			if (id.startsWith("_") || id.equals(excludeId)) {
				continue;
			}
			hands += s.get(id, "handsPlayed");
			att += attempts(s, id);
		}
		return shrink(att, hands, DEFAULT_RATE, K_OVERALL);
	}

	private static int attempts(StatsStore s, String id) {
		return s.get(id, "moonSuccesses") + s.get(id, "moonsStoppedOnMe");
	}

	private static double shrink(int count, int hands, double prior, double k) {
		return (count + prior * k) / (hands + k);
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}
}
