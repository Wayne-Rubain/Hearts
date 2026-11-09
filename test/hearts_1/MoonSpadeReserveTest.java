package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for AICardScorer's "MOON-STOPPING, tactic
 * 2c" scorer, added 2026-08-16 (user request) -- the Spade counterpart of
 * tactic 2b (MoonFollowReserveTest): a defender following an unwinnable
 * Spade lead (the Ace already on the table) should reserve its King rather
 * than shedding it via ordinary duck preference, since the Queen of Spades
 * hasn't necessarily surfaced yet and the King may be what catches her
 * later. Gated on MoonThreat.queenDenialWorthIt() -- the user's own caveat
 * ("unless they are really concerned about getting the Queen") -- so a
 * defender doesn't bother reserving when capturing her wouldn't actually be
 * worth it.
 */
public final class MoonSpadeReserveTest {

	private MoonSpadeReserveTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		reservesTheKingWhenCapturingTheQueenWouldBeWorthIt();
		noReservationWhenCapturingTheQueenWouldNotBeWorthIt();
		noReservationWithoutAThreateningShooter();
		noReservationWhenAlreadySafeToUnload();
		return TestKit.summarize();
	}

	private static void reservesTheKingWhenCapturingTheQueenWouldBeWorthIt() {
		TestKit.suite("reservesTheKingWhenCapturingTheQueenWouldBeWorthIt");
		// West leading at 30, South (45, 7 Hearts) shooting: the moon puts South
		// at 19, ahead of West; taking the Queen keeps West in 1st at 43 vs 52
		// -- worth it (keep-my-lead rule, 2026-11-09).
		Table_Cards tc = aceLedTable(60, 60, 30);
		List<Card> options = kingAndLowSpade();
		Player west = playerOf(PLAYER.WEST, 30);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("the King is reserved -- the low Spade goes instead",
				CardValue.THREE, chosen.cardValue);
	}

	private static void noReservationWhenCapturingTheQueenWouldNotBeWorthIt() {
		TestKit.suite("noReservationWhenCapturingTheQueenWouldNotBeWorthIt");
		// West level with North here -- swallowing the Queen later would
		// drop West behind North, so queenDenialWorthIt refuses, and this
		// scorer should stand down and let ordinary duck preference (shed
		// the King, the higher card) apply as usual.
		Table_Cards tc = aceLedTable(50, 20, 50);
		List<Card> options = kingAndLowSpade();
		Player west = playerOf(PLAYER.WEST, 50);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("no reservation -- ordinary duck preference sheds the King as usual",
				CardValue.KING, chosen.cardValue);
	}

	private static void noReservationWithoutAThreateningShooter() {
		TestKit.suite("noReservationWithoutAThreateningShooter");
		Table_Cards tc = aceLedTable(20, 20, 80);
		tc.players.get(PLAYER.SOUTH).won_cards = new Hand(); // no captured points -- no shooter visible
		List<Card> options = kingAndLowSpade();
		Player west = playerOf(PLAYER.WEST, 80);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("with no visible shooter, ordinary duck preference sheds the King",
				CardValue.KING, chosen.cardValue);
	}

	private static void noReservationWhenAlreadySafeToUnload() {
		TestKit.suite("noReservationWhenAlreadySafeToUnload");
		Table_Cards tc = aceLedTable(20, 20, 80);
		// The Queen is already in a won pile -- safeToUnloadHighSpade()
		// says shedding the King is fine regardless of any shooter.
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		List<Card> options = kingAndLowSpade();
		Player west = playerOf(PLAYER.WEST, 80);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("Queen already gone -- safe to shed the King, no reservation needed",
				CardValue.KING, chosen.cardValue);
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static List<Card> kingAndLowSpade() {
		return cards(card(CardValue.KING, Suit.SPADE), card(CardValue.THREE, Suit.SPADE));
	}

	private static List<Card> cards(Card... cs) {
		List<Card> list = new ArrayList<Card>();
		for (Card c : cs) {
			list.add(c);
		}
		return list;
	}

	private static Hand handOf(List<Card> cs) {
		Hand h = new Hand();
		for (Card c : cs) {
			h.addX(c);
		}
		return h;
	}

	private static Player playerOf(PLAYER seat, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.p_total_points = totalPoints;
		return p;
	}

	/** SOUTH leads the Ace of Spades (unbeatable by WEST's King regardless
	 * of choice); WEST is about to follow into position 2. SOUTH has
	 * already captured a majority of the Hearts (7), clearing
	 * queenDenialWorthIt's urgency gate on its own. `northTotal`/
	 * `eastTotal`/`westTotal` let each test control whether the standings
	 * check inside queenDenialWorthIt allows or refuses. */
	private static Table_Cards aceLedTable(int northTotal, int eastTotal, int westTotal) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, playerOf(PLAYER.NORTH, northTotal));
		players.put(PLAYER.EAST, playerOf(PLAYER.EAST, eastTotal));
		Player south = playerOf(PLAYER.SOUTH, 45);
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR,
				CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT }) { // 7 Hearts
			south.won_cards.addX(card(v, Suit.HEART));
		}
		players.put(PLAYER.SOUTH, south);
		players.put(PLAYER.WEST, playerOf(PLAYER.WEST, westTotal));
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.ACE, Suit.SPADE);
		tc.hearts_broken = true;
		return tc;
	}
}
