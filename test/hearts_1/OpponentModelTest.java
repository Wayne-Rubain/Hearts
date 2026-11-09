package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.roster.OpponentModel;
import hearts_1.roster.OpponentModel.Place;
import hearts_1.roster.StatsStore;

/**
 * Committed coverage for hearts_1.roster.OpponentModel, added 2026-10-10:
 * the standings classification (Place and distance band), exact neutrality
 * without history, that the score follows SITUATION -- Wayne's point that a
 * leader rarely shoots and a player last by a lot shoots more -- and that
 * shrinkage stops one early moon from branding a newcomer. (Its two AI
 * hooks were measured and removed the same day; see the class doc.)
 */
public final class OpponentModelTest {

	private OpponentModelTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		neutralWithoutHistory();
		placeAndBandClassification();
		scoreFollowsTheSituation();
		distanceBehindRaisesTheRead();
		oneEarlyMoonIsNotAReputation();
		aHabitualShooterReadsHigh();
		return TestKit.summarize();
	}

	private static void neutralWithoutHistory() {
		TestKit.suite("neutralWithoutHistory");
		for (Place p : Place.values()) {
			TestKit.checkEquals("empty store, " + p + " -- exactly 0",
					Double.valueOf(0), Double.valueOf(OpponentModel.moonScoreOf(new StatsStore(), "AI_09", p, 3)));
		}
	}

	private static void placeAndBandClassification() {
		TestKit.suite("placeAndBandClassification");
		Map<PLAYER, Integer> level = totals(10, 10, 10, 10);
		TestKit.checkEquals("everyone level -- MIDDLE", Place.MIDDLE, OpponentModel.placeOf(PLAYER.SOUTH, level));
		Map<PLAYER, Integer> t = totals(20, 20, 50, 80); // N/E tied lead, S middle, W last
		TestKit.checkEquals("tied for the lead -- LEAD", Place.LEAD, OpponentModel.placeOf(PLAYER.NORTH, t));
		TestKit.checkEquals("in between -- MIDDLE", Place.MIDDLE, OpponentModel.placeOf(PLAYER.SOUTH, t));
		TestKit.checkEquals("highest total -- LAST", Place.LAST, OpponentModel.placeOf(PLAYER.WEST, t));
		TestKit.checkEquals("leader is band 0", Integer.valueOf(0), Integer.valueOf(OpponentModel.bandOf(PLAYER.NORTH, t)));
		TestKit.checkEquals("30 behind is band 3 (25+)", Integer.valueOf(3), Integer.valueOf(OpponentModel.bandOf(PLAYER.SOUTH, t)));
		Map<PLAYER, Integer> close = totals(20, 25, 34, 44);
		TestKit.checkEquals("5 behind is band 1", Integer.valueOf(1), Integer.valueOf(OpponentModel.bandOf(PLAYER.EAST, close)));
		TestKit.checkEquals("14 behind is band 2", Integer.valueOf(2), Integer.valueOf(OpponentModel.bandOf(PLAYER.SOUTH, close)));
		TestKit.checkEquals("24 behind is still band 2", Integer.valueOf(2), Integer.valueOf(OpponentModel.bandOf(PLAYER.WEST, close)));
		TestKit.checkEquals("no standings at all -- MIDDLE", Place.MIDDLE, OpponentModel.placeOf(PLAYER.WEST, null));
	}

	/** Wayne's own pattern: never shoots while leading, does when last.
	 * The model must read him low when he leads and high when he trails. */
	private static void scoreFollowsTheSituation() {
		TestKit.suite("scoreFollowsTheSituation");
		StatsStore s = ordinaryTable();
		hands(s, "WAYNE", Place.LEAD, 0, 200, 0);
		hands(s, "WAYNE", Place.LAST, 3, 40, 6);
		double leading = OpponentModel.moonScoreOf(s, "WAYNE", Place.LEAD, 0);
		double lastFar = OpponentModel.moonScoreOf(s, "WAYNE", Place.LAST, 3);
		double ordinaryLast = OpponentModel.moonScoreOf(s, "N1", Place.LAST, 3);
		TestKit.checkEquals("leading -- read as no threat", Double.valueOf(0), Double.valueOf(leading));
		TestKit.check("last by 25+ -- read as a real threat (" + lastFar + ")", lastFar >= 0.5);
		TestKit.check("... and more of one than an ordinary player in the same spot (" + ordinaryLast + ")",
				lastFar > ordinaryLast);
	}

	/** The table-wide distance effect: an ordinary player last by 25+ is a
	 * bigger moon risk than the same player last by a few points. */
	private static void distanceBehindRaisesTheRead() {
		TestKit.suite("distanceBehindRaisesTheRead");
		StatsStore s = ordinaryTable();
		double near = OpponentModel.moonScoreOf(s, "N1", Place.LAST, 1);
		double far = OpponentModel.moonScoreOf(s, "N1", Place.LAST, 3);
		TestKit.check("last by 25+ (" + far + ") reads higher than last by 1-9 (" + near + ")", far > near);
		TestKit.checkEquals("an ordinary player leading is no threat",
				Double.valueOf(0), Double.valueOf(OpponentModel.moonScoreOf(s, "N1", Place.LEAD, 0)));
	}

	private static void oneEarlyMoonIsNotAReputation() {
		TestKit.suite("oneEarlyMoonIsNotAReputation");
		StatsStore s = ordinaryTable();
		hands(s, "NEWBIE", Place.MIDDLE, 0, 2, 1);
		double newbie = OpponentModel.moonScoreOf(s, "NEWBIE", Place.MIDDLE, 0);
		TestKit.check("one moon in two hands stays well off the top of the scale (" + newbie + ")", newbie < 0.5);
	}

	/** Compared against the OTHER players, a habitual shooter at a four-seat
	 * table must not hide itself by dragging the average up. */
	private static void aHabitualShooterReadsHigh() {
		TestKit.suite("aHabitualShooterReadsHigh");
		StatsStore s = new StatsStore();
		for (String id : new String[] { "N1", "N2", "N3" }) {
			hands(s, id, Place.MIDDLE, 0, 300, 4);
		}
		hands(s, "SHOOTER", Place.MIDDLE, 0, 300, 30);
		double top = OpponentModel.moonScoreOf(s, "SHOOTER", Place.MIDDLE, 0);
		TestKit.check("30 tries in 300 hands vs others' 4 reads near the top (" + top + ")", top >= 0.75);
	}

	// ------------------------------------------------------------------

	/** Records `count` hands for `id` in one situation, `attempts` of them
	 * moon attempts, keeping the overall counters consistent. */
	private static void hands(StatsStore s, String id, Place place, int band, int count, int attempts) {
		for (int i = 0; i < count; i++) {
			OpponentModel.recordHand(s, id, place, band, i < attempts);
		}
		s.add(id, "handsPlayed", count);
		s.add(id, "moonSuccesses", attempts);
	}

	/** Three ordinary players with the generic pattern: rare moons while
	 * leading, more when last, most when last by a lot. */
	private static StatsStore ordinaryTable() {
		StatsStore s = new StatsStore();
		for (String id : new String[] { "N1", "N2", "N3" }) {
			hands(s, id, Place.LEAD, 0, 100, 0);
			hands(s, id, Place.MIDDLE, 1, 100, 1);
			hands(s, id, Place.LAST, 1, 100, 1);
			hands(s, id, Place.LAST, 3, 100, 6);
		}
		return s;
	}

	private static Map<PLAYER, Integer> totals(int n, int e, int s, int w) {
		Map<PLAYER, Integer> m = new EnumMap<PLAYER, Integer>(PLAYER.class);
		m.put(PLAYER.NORTH, Integer.valueOf(n));
		m.put(PLAYER.EAST, Integer.valueOf(e));
		m.put(PLAYER.SOUTH, Integer.valueOf(s));
		m.put(PLAYER.WEST, Integer.valueOf(w));
		return m;
	}
}
