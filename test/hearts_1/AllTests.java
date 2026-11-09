package hearts_1;

/**
 * Runs every committed test class in this folder in one JVM and reports a
 * combined summary. Added 2026-08-15 alongside the tests themselves -- see
 * CHANGELOG.md's 2026-08-15 tooling note for why these are committed
 * instead of the project's old scratch-and-delete convention.
 *
 * Each XxxTest class exposes a static run() (resets TestKit, performs its
 * checks, prints its own summary, returns whether they all passed)
 * separately from its own main() (which calls run() then System.exit(), for
 * running one suite standalone). run() is what gets called here, since
 * calling each class's main() directly would exit the JVM after the first
 * suite.
 *
 * Usage: {@code java hearts_1.AllTests} -- exit code 0 iff every suite
 * passed.
 */
public final class AllTests {

	private AllTests() { }

	public static void main(String[] args) {
		boolean allPassed = true;
		int suiteCount = 0;
		int suitesFailed = 0;

		suiteCount++;
		System.out.println("--- ScoringTest ---");
		if (!ScoringTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- RulesTest ---");
		if (!RulesTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonThresholdTest ---");
		if (!MoonThresholdTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- TacticWeightsTest ---");
		if (!TacticWeightsTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- KeepHandTest ---");
		if (!KeepHandTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- LogReplayerRoundTripTest ---");
		if (!LogReplayerRoundTripTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- EndGameTest ---");
		if (!EndGameTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonStopperTest ---");
		if (!MoonStopperTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- ExitCardTest ---");
		if (!ExitCardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- HighSpadeLeadTest ---");
		if (!HighSpadeLeadTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- QueenDenialTest ---");
		if (!QueenDenialTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonFollowReserveTest ---");
		if (!MoonFollowReserveTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonSpadeReserveTest ---");
		if (!MoonSpadeReserveTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- RosterTest ---");
		if (!RosterTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- StatsStoreTest ---");
		if (!StatsStoreTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- StatsRecorderTest ---");
		if (!StatsRecorderTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- FreeCaptureTest ---");
		if (!FreeCaptureTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AggressiveLeadGuardTest ---");
		if (!AggressiveLeadGuardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- CountsCardsGateTest ---");
		if (!CountsCardsGateTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AggressiveHeartChanceTest ---");
		if (!AggressiveHeartChanceTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- JackPullGuardTest ---");
		if (!JackPullGuardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AvoidsQueenPassTest ---");
		if (!AvoidsQueenPassTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- PostExchangeMoonTest ---");
		if (!PostExchangeMoonTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AceOfHeartsFlushTest ---");
		if (!AceOfHeartsFlushTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonInterestTest ---");
		if (!MoonInterestTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonGambleTest ---");
		if (!MoonGambleTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- SafeJackCashInTest ---");
		if (!SafeJackCashInTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- BorderlineMoonSpadeHoldTest ---");
		if (!BorderlineMoonSpadeHoldTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- HighSpadeTempoTest ---");
		if (!HighSpadeTempoTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonKitRetentionTest ---");
		if (!MoonKitRetentionTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonQueenFollowTest ---");
		if (!MoonQueenFollowTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- EndgameSolverTest ---");
		if (!EndgameSolverTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AggressiveQueenGuardTest ---");
		if (!AggressiveQueenGuardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- QueenBleedTest ---");
		if (!QueenBleedTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- OwnQueenWinTest ---");
		if (!OwnQueenWinTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- StatsExtensionsTest ---");
		if (!StatsExtensionsTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- OpponentModelTest ---");
		if (!OpponentModelTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- ProfileTest ---");
		if (!ProfileTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- PaybackTest ---");
		if (!PaybackTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- PassingRulesTest ---");
		if (!PassingRulesTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- StrategyTest ---");
		if (!StrategyTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- BlunderGuardTest ---");
		if (!BlunderGuardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- AbandonedMoonTest ---");
		if (!AbandonedMoonTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonLadderTest ---");
		if (!MoonLadderTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- ExpertLimitsTest ---");
		if (!ExpertLimitsTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- ForcedDealTest ---");
		if (!ForcedDealTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- GameLedgerTest ---");
		if (!GameLedgerTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- GameLogTest ---");
		if (!GameLogTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- LeadBlunderGuardTest ---");
		if (!LeadBlunderGuardTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- SimulatorObserverTest ---");
		if (!SimulatorObserverTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- SignatureOptionsTest ---");
		if (!SignatureOptionsTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- GameEngineTest ---");
		if (!GameEngineTest.run()) { allPassed = false; suitesFailed++; }

		suiteCount++;
		System.out.println("--- MoonLookTest ---");
		if (!MoonLookTest.run()) { allPassed = false; suitesFailed++; }

		System.out.println();
		System.out.println(suiteCount + " suite(s) run, " + suitesFailed + " failed");
		System.exit(allPassed ? 0 : 1);
	}
}
