package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for Deal_Hand.effectiveMoonGamblePercent(),
 * added 2026-08-16 alongside the Ace-of-Hearts flush lead (see
 * AceOfHeartsFlushTest). Per the user's explicit instruction: "we
 * definitely need to factor in when a person is trailing far behind that
 * they would take these type of chances" -- a seat's roster-assigned
 * moonGamblePercent is boosted upward the further it trails the field,
 * bounded the same deliberate way HandEvaluator's own standings term is
 * bounded (see that file's MAX_STANDING_DISCOUNT comment for the
 * self-reinforcing-spiral failure mode an earlier, unbounded version
 * produced).
 */
public final class MoonGambleTest {

	private MoonGambleTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		levelStandingsLeaveTheBaseRateUnchanged();
		leadingNeverReducesTheBaseRate();
		trailingBoostsTheEffectiveRate();
		theBoostIsCapped();
		zeroBaseIsNeverOverriddenByStandings();
		effectiveRateNeverExceeds100();
		simulatorSeatsCarryTheRateIntoTheHandRoll();
		return TestKit.summarize();
	}

	/** The headless simulator used to leave moonGamblePercent at 0, so the
	 * trait was unmeasurable (fixed 2026-10-14). */
	private static void simulatorSeatsCarryTheRateIntoTheHandRoll() {
		TestKit.suite("simulatorSeatsCarryTheRateIntoTheHandRoll");
		Profile[] profiles = HeadlessSimulator.profilesOf(new Player.Player_Type[] {
				Player.Player_Type.NORMAL, Player.Player_Type.NORMAL,
				Player.Player_Type.NORMAL, Player.Player_Type.NORMAL });
		Map<PLAYER, Player> players = HeadlessSimulator.newPlayers(profiles,
				new int[] { 0, 0, 0, 0 }, new int[] { 100, 0, 100, 0 });
		TestKit.checkEquals("NORTH carries its rate", Integer.valueOf(100),
				Integer.valueOf(players.get(PLAYER.NORTH).moonGamblePercent));
		TestKit.checkEquals("EAST stays at zero", Integer.valueOf(0),
				Integer.valueOf(players.get(PLAYER.EAST).moonGamblePercent));
		Table_Cards tc = new Table_Cards();
		tc.players = players;
		Deck deck = shuffled();
		All_Hands hands = deck.deal(PLAYER.NORTH, deck);
		Deal_Hand.beginHand(hands, tc, null);
		TestKit.check("a 100% seat gambles this hand", players.get(PLAYER.NORTH).takesMoonGambleThisHand);
		TestKit.check("a 0% seat never gambles", !players.get(PLAYER.EAST).takesMoonGambleThisHand);
	}

	private static Deck shuffled() {
		Deck d = new Deck();
		d.shuffle_now(new java.util.Random(42));
		return d;
	}

	private static void levelStandingsLeaveTheBaseRateUnchanged() {
		TestKit.suite("levelStandingsLeaveTheBaseRateUnchanged");
		Table_Cards tc = tableWithStandings(0, 0, 0, 0);
		TestKit.checkEquals("dead level -- effective rate equals the roster base",
				Integer.valueOf(40), Integer.valueOf(
						Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 40))));
	}

	private static void leadingNeverReducesTheBaseRate() {
		TestKit.suite("leadingNeverReducesTheBaseRate");
		// SOUTH is comfortably AHEAD of the field (lower is better in Hearts).
		Table_Cards tc = tableWithStandings(40, 40, 0, 40);
		TestKit.checkEquals("ahead of the field -- still just the base rate, never discounted",
				Integer.valueOf(40), Integer.valueOf(
						Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 40))));
	}

	private static void trailingBoostsTheEffectiveRate() {
		TestKit.suite("trailingBoostsTheEffectiveRate");
		// SOUTH trailing the other three by 40 each -- a real, not marginal, gap.
		Table_Cards tc = tableWithStandings(0, 0, 40, 0);
		int effective = Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 40));
		TestKit.check("trailing badly raises the effective rate above the roster base",
				effective > 40);
	}

	private static void theBoostIsCapped() {
		TestKit.suite("theBoostIsCapped");
		// An extreme, blown-out gap -- the boost must not run away unbounded.
		Table_Cards tc = tableWithStandings(0, 0, 300, 0);
		int effective = Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 40));
		TestKit.check("even a huge deficit caps the boost rather than scaling forever",
				effective <= 40 + 30);
	}

	private static void zeroBaseIsNeverOverriddenByStandings() {
		TestKit.suite("zeroBaseIsNeverOverriddenByStandings");
		Table_Cards tc = tableWithStandings(0, 0, 60, 0);
		TestKit.checkEquals("a roster base of 0 (the explicit \"No\") stays 0 no matter how far behind",
				Integer.valueOf(0), Integer.valueOf(
						Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 0))));
	}

	private static void effectiveRateNeverExceeds100() {
		TestKit.suite("effectiveRateNeverExceeds100");
		Table_Cards tc = tableWithStandings(0, 0, 50, 0);
		int effective = Deal_Hand.effectiveMoonGamblePercent(tc, playerOf(tc, PLAYER.SOUTH, 90));
		TestKit.check("a high base plus a full boost is still clamped at 100",
				effective <= 100);
	}

	// ------------------------------------------------------------------

	private static Player playerOf(Table_Cards tc, PLAYER seat, int moonGamblePercent) {
		Player p = tc.players.get(seat);
		p.moonGamblePercent = moonGamblePercent;
		return p;
	}

	/** NORTH/EAST/SOUTH/WEST totals, in that fixed seat order -- same shape
	 * as QueenDenialTest/HighSpadeLeadTest's helper of the same name. */
	private static Table_Cards tableWithStandings(int north, int east, int south, int west) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, freshPlayer(PLAYER.NORTH, north));
		players.put(PLAYER.EAST, freshPlayer(PLAYER.EAST, east));
		players.put(PLAYER.SOUTH, freshPlayer(PLAYER.SOUTH, south));
		players.put(PLAYER.WEST, freshPlayer(PLAYER.WEST, west));
		tc.players = players;
		return tc;
	}

	private static Player freshPlayer(PLAYER seat, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.p_total_points = totalPoints;
		return p;
	}
}
