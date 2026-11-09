package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for EndGame.java, added 2026-08-15 alongside
 * the class itself (TODO.md's "end-of-game awareness" item). Covers the
 * danger-zone math and, in particular, isCurrentLeader() -- added after a
 * user question caught the first version of the "push the leader over"
 * scorer targeting the seat closest to TARGET_SCORE unconditionally, which
 * is backwards for any seat NOT currently in 1st: hastening the game's end
 * only helps whoever is winning right now, and a trailing seat with a live
 * shot at 1st gains nothing by helping end the game early.
 */
public final class EndGameTest {

	private EndGameTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		dangerLevelRampsAcrossTheZone();
		isCurrentLeaderRequiresLowestOrTiedTotal();
		mostEndangeredOtherPicksClosestToTargetExcludingSelfAndSafeSeats();
		currentTrickWinnerSeatTracksTheHighestCardOfTheLeadSuitSoFar();
		return TestKit.summarize();
	}

	private static void dangerLevelRampsAcrossTheZone() {
		TestKit.suite("dangerLevelRampsAcrossTheZone");
		Table_Cards atEdge = tableWithTotals(75, 0, 0, 0); // margin exactly 25
		TestKit.checkEquals("margin exactly 25 (the zone's edge) is zero danger",
				Double.valueOf(0.0), Double.valueOf(EndGame.dangerLevel(atEdge, PLAYER.NORTH)));

		Table_Cards atTarget = tableWithTotals(100, 0, 0, 0);
		TestKit.checkEquals("a seat exactly at TARGET_SCORE reads as maximum danger",
				Double.valueOf(1.0), Double.valueOf(EndGame.dangerLevel(atTarget, PLAYER.NORTH)));

		Table_Cards midZone = tableWithTotals(90, 0, 0, 0);
		// margin = 100 - 90 = 10; (25 - 10) / 25 = 0.6
		TestKit.checkEquals("margin 10 of a 25-point zone is danger 0.6",
				Double.valueOf(0.6), Double.valueOf(EndGame.dangerLevel(midZone, PLAYER.NORTH)));

		Table_Cards farOut = tableWithTotals(10, 0, 0, 0);
		TestKit.checkEquals("a seat far from the target is zero danger",
				Double.valueOf(0.0), Double.valueOf(EndGame.dangerLevel(farOut, PLAYER.NORTH)));
	}

	private static void isCurrentLeaderRequiresLowestOrTiedTotal() {
		TestKit.suite("isCurrentLeaderRequiresLowestOrTiedTotal");
		Table_Cards tc = tableWithTotals(10, 40, 40, 40); // NORTH clearly lowest
		TestKit.check("the seat with the strictly lowest total is the leader",
				EndGame.isCurrentLeader(tc, PLAYER.NORTH));
		TestKit.check("a seat behind the leader is not the leader",
				!EndGame.isCurrentLeader(tc, PLAYER.EAST));

		Table_Cards tied = tableWithTotals(10, 10, 40, 40); // NORTH and EAST tied lowest
		TestKit.check("a seat tied for lowest counts as leader",
				EndGame.isCurrentLeader(tied, PLAYER.NORTH));
		TestKit.check("its tied partner also counts as leader",
				EndGame.isCurrentLeader(tied, PLAYER.EAST));
		TestKit.check("a seat behind a tie is still not the leader",
				!EndGame.isCurrentLeader(tied, PLAYER.SOUTH));

		Table_Cards allLevel = tableWithTotals(0, 0, 0, 0);
		TestKit.check("at game start every seat is tied for the lead",
				EndGame.isCurrentLeader(allLevel, PLAYER.WEST));
	}

	private static void mostEndangeredOtherPicksClosestToTargetExcludingSelfAndSafeSeats() {
		TestKit.suite("mostEndangeredOtherPicksClosestToTargetExcludingSelfAndSafeSeats");
		// NORTH=10 (self, ignored even though irrelevant here), EAST=95
		// (deep in the zone), SOUTH=85 (in the zone but less deep), WEST=0
		// (safe).
		Table_Cards tc = tableWithTotals(10, 95, 85, 0);
		TestKit.checkEquals("the seat closest to the target wins, not just any seat in the zone",
				PLAYER.EAST, EndGame.mostEndangeredOther(tc, PLAYER.NORTH));

		Table_Cards nobodyInDanger = tableWithTotals(10, 20, 20, 20);
		TestKit.check("null when no other seat is inside the danger zone",
				EndGame.mostEndangeredOther(nobodyInDanger, PLAYER.NORTH) == null);

		Table_Cards selfInDanger = tableWithTotals(99, 0, 0, 0);
		TestKit.check("a self deep in the zone is never returned as the target -- only OTHER seats count",
				EndGame.mostEndangeredOther(selfInDanger, PLAYER.NORTH) == null);
	}

	private static void currentTrickWinnerSeatTracksTheHighestCardOfTheLeadSuitSoFar() {
		TestKit.suite("currentTrickWinnerSeatTracksTheHighestCardOfTheLeadSuitSoFar");
		Table_Cards tc = new Table_Cards();
		tc.who_leads = PLAYER.EAST;
		tc.p1_card = card(CardValue.FIVE, Suit.CLUB); // EAST leads
		tc.p2_card = card(CardValue.KING, Suit.CLUB); // SOUTH beats it
		TestKit.checkEquals("winner after 2 plays is whoever holds the highest card of the led suit so far",
				PLAYER.SOUTH, EndGame.currentTrickWinnerSeat(tc, 3));

		tc.p3_card = card(CardValue.TWO, Suit.HEART); // WEST discards off-suit, cannot win
		TestKit.checkEquals("an off-suit discard never changes the current winner",
				PLAYER.SOUTH, EndGame.currentTrickWinnerSeat(tc, 4));

		TestKit.checkEquals("evaluated after only the lead has been played, the leader is winning by default",
				PLAYER.EAST, EndGame.currentTrickWinnerSeat(tc, 2));
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	/** NORTH/EAST/SOUTH/WEST totals, in that fixed seat order -- simpler than
	 * MoonThresholdTest's "me + 3 others" shape since these tests care about
	 * absolute seat identity, not a single acting player's point of view. */
	private static Table_Cards tableWithTotals(int north, int east, int south, int west) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, playerOf(PLAYER.NORTH, north));
		players.put(PLAYER.EAST, playerOf(PLAYER.EAST, east));
		players.put(PLAYER.SOUTH, playerOf(PLAYER.SOUTH, south));
		players.put(PLAYER.WEST, playerOf(PLAYER.WEST, west));
		tc.players = players;
		return tc;
	}

	private static Player playerOf(PLAYER seat, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.p_total_points = totalPoints;
		return p;
	}
}
