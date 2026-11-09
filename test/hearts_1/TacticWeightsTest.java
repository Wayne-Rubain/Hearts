package hearts_1;

import hearts_1.Player.Player_Type;
import hearts_1.TacticWeights.Tactic;

/**
 * Committed regression coverage for TacticWeights.java's per-type matrix,
 * added 2026-08-15 -- pins down the specific numbers from the design table
 * in CHANGELOG.md's 2026-08-07 entry so a future edit to the matrix has to
 * change this test deliberately, not by accident. Replaces the old
 * uncommitted WeightCheck (10 scenarios).
 */
public final class TacticWeightsTest {

	private TacticWeightsTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		coreIsAlwaysFullWeightForEveryType();
		bradDeadAndBadKnowNothingBeyondCore();
		normalIsUndereagerAboutMoonOffense();
		aggressiveHuntsTheJackHarderThanNormal();
		avoidsQueenOvervaluesQueenFlush();
		moonShooterIsEagerButIgnoresQueenFlush();
		comboTypeTakesMaxOfBothParents();
		return TestKit.summarize();
	}

	private static void coreIsAlwaysFullWeightForEveryType() {
		TestKit.suite("coreIsAlwaysFullWeightForEveryType");
		for (Player_Type type : Player_Type.values()) {
			Player p = playerOf(type);
			TestKit.checkEquals("CORE is always weight 1 for " + type,
					Double.valueOf(1.0), Double.valueOf(TacticWeights.weight(p, Tactic.CORE)));
		}
	}

	private static void bradDeadAndBadKnowNothingBeyondCore() {
		TestKit.suite("bradDeadAndBadKnowNothingBeyondCore");
		Player brainDead = playerOf(Player_Type.BRAIN_DEAD);
		Player bad = playerOf(Player_Type.BAD);
		for (Tactic t : new Tactic[] { Tactic.VOID_AWARENESS, Tactic.JACK_GUARD, Tactic.JACK_HUNT,
				Tactic.COUNTED_CERTAINTIES, Tactic.QUEEN_FLUSH, Tactic.MOON_OFFENSE, Tactic.MOON_DEFENSE,
				Tactic.PASSING_CRAFT }) {
			TestKit.checkEquals("BRAIN_DEAD's " + t + " row is 0", Double.valueOf(0.0),
					Double.valueOf(TacticWeights.weight(brainDead, t)));
		}
		TestKit.checkEquals("BAD keeps BASIC_SAFETY", Double.valueOf(1.0),
				Double.valueOf(TacticWeights.weight(bad, Tactic.BASIC_SAFETY)));
		TestKit.checkEquals("BAD has zero JACK_HUNT", Double.valueOf(0.0),
				Double.valueOf(TacticWeights.weight(bad, Tactic.JACK_HUNT)));
		TestKit.checkEquals("BAD passes naively (0 PASSING_CRAFT)", Double.valueOf(0.0),
				Double.valueOf(TacticWeights.weight(bad, Tactic.PASSING_CRAFT)));
	}

	private static void normalIsUndereagerAboutMoonOffense() {
		TestKit.suite("normalIsUndereagerAboutMoonOffense");
		Player normal = playerOf(Player_Type.NORMAL);
		TestKit.checkEquals("NORMAL's MOON_OFFENSE is 0.5 (rare, not eager)", Double.valueOf(0.5),
				Double.valueOf(TacticWeights.weight(normal, Tactic.MOON_OFFENSE)));
	}

	private static void aggressiveHuntsTheJackHarderThanNormal() {
		TestKit.suite("aggressiveHuntsTheJackHarderThanNormal");
		Player normal = playerOf(Player_Type.NORMAL);
		Player aggressive = playerOf(Player_Type.AGGRESSIVE);
		double normalHunt = TacticWeights.weight(normal, Tactic.JACK_HUNT);
		double aggressiveHunt = TacticWeights.weight(aggressive, Tactic.JACK_HUNT);
		TestKit.check("AGGRESSIVE (" + aggressiveHunt + ") outhunts NORMAL (" + normalHunt + ") for the Jack",
				aggressiveHunt > normalHunt);
		TestKit.check("AGGRESSIVE ducks less than NORMAL on basic safety",
				TacticWeights.weight(aggressive, Tactic.BASIC_SAFETY) < TacticWeights.weight(normal, Tactic.BASIC_SAFETY));
	}

	private static void avoidsQueenOvervaluesQueenFlush() {
		TestKit.suite("avoidsQueenOvervaluesQueenFlush");
		Player avoidsQueen = playerOf(Player_Type.AVOIDS_QUEEN);
		TestKit.checkEquals("AVOIDS_QUEEN overvalues the Queen flush at 1.5", Double.valueOf(1.5),
				Double.valueOf(TacticWeights.weight(avoidsQueen, Tactic.QUEEN_FLUSH)));
		TestKit.checkEquals("AVOIDS_QUEEN never attempts a moon (MOON_OFFENSE 0)", Double.valueOf(0.0),
				Double.valueOf(TacticWeights.weight(avoidsQueen, Tactic.MOON_OFFENSE)));
	}

	private static void moonShooterIsEagerButIgnoresQueenFlush() {
		TestKit.suite("moonShooterIsEagerButIgnoresQueenFlush");
		Player moonShooter = playerOf(Player_Type.MOON_SHOOTER);
		TestKit.checkEquals("MOON_SHOOTER overvalues MOON_OFFENSE at 1.5", Double.valueOf(1.5),
				Double.valueOf(TacticWeights.weight(moonShooter, Tactic.MOON_OFFENSE)));
		TestKit.checkEquals("MOON_SHOOTER has zero interest in the Queen flush (would rather collect it)",
				Double.valueOf(0.0), Double.valueOf(TacticWeights.weight(moonShooter, Tactic.QUEEN_FLUSH)));
	}

	private static void comboTypeTakesMaxOfBothParents() {
		TestKit.suite("comboTypeTakesMaxOfBothParents");
		Player aggressive = playerOf(Player_Type.AGGRESSIVE);
		Player avoidsQueen = playerOf(Player_Type.AVOIDS_QUEEN);
		Player combo = playerOf(Player_Type.AGGRESSIVE_AVOIDS_QUEEN);
		for (Tactic t : Tactic.values()) {
			if (t == Tactic.CORE) {
				continue;
			}
			double expected = Math.max(TacticWeights.weight(aggressive, t), TacticWeights.weight(avoidsQueen, t));
			TestKit.checkEquals("combo type's " + t + " weight is max(AGGRESSIVE, AVOIDS_QUEEN)",
					Double.valueOf(expected), Double.valueOf(TacticWeights.weight(combo, t)));
		}
	}

	// ------------------------------------------------------------------

	private static Player playerOf(Player_Type type) {
		Player p = new Player();
		p.tbl_player = hearts_1.Hearts_1.PLAYER.SOUTH;
		p.Set_Player(type);
		return p;
	}
}
