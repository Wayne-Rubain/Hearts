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
 * 2b" scorer, added 2026-08-16 -- replays the exact real hand that surfaced
 * this gap. WEST held 3-4-7-J of Hearts against SOUTH (visibly threatening,
 * standings North=63 East=78 South=45 West=22 going into the hand) running
 * Q-K-A-10-9. On the Queen lead (trick 3, unbeatable regardless of which
 * Heart WEST played), the ordinary highest-first duck preference picked the
 * Jack -- exactly matching what actually happened in the game log -- when
 * WEST held three cheaper Hearts (3, 4, 7) that lose the trick just as
 * safely and would have let the Jack survive to beat SOUTH's Ten three
 * tricks later, breaking the shot.
 *
 * Deliberately a single-card RESERVE, not a full lowest-first reversal (see
 * MOON_DENY_FOLLOW_HEART_RESERVE's doc in AICardScorer.java for why) -- the
 * second check here confirms the ordinary highest-first order is still
 * used among the non-reserved cards, not silently inverted for all of them.
 */
public final class MoonFollowReserveTest {

	private MoonFollowReserveTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		reservesTheBestHeartAgainstAThreateningShooter();
		stillPicksHighestAmongTheNonReservedCards();
		noReservationWithoutAThreateningShooter();
		noReservationWithOnlyOneHeartLeft();
		return TestKit.summarize();
	}

	private static void reservesTheBestHeartAgainstAThreateningShooter() {
		TestKit.suite("reservesTheBestHeartAgainstAThreateningShooter");
		Table_Cards tc = queenLedTable();
		List<Card> options = westHearts();
		Player west = playerOf(PLAYER.WEST, 22);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.check("the Jack (WEST's best Heart) is NOT played while a cheaper Heart is available",
				chosen.cardValue != CardValue.JACK);
	}

	private static void stillPicksHighestAmongTheNonReservedCards() {
		TestKit.suite("stillPicksHighestAmongTheNonReservedCards");
		Table_Cards tc = queenLedTable();
		List<Card> options = westHearts(); // 3, 4, 7, J
		Player west = playerOf(PLAYER.WEST, 22);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("among the non-reserved cards, ordinary highest-first duck order still applies (7, not 3 or 4)",
				CardValue.SEVEN, chosen.cardValue);
	}

	private static void noReservationWithoutAThreateningShooter() {
		TestKit.suite("noReservationWithoutAThreateningShooter");
		Table_Cards tc = queenLedTable();
		tc.players.get(PLAYER.SOUTH).won_cards = new Hand(); // no captured points -- no shooter visible
		List<Card> options = westHearts();
		Player west = playerOf(PLAYER.WEST, 22);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("with no visible shooter, ordinary duck preference plays the Jack as usual",
				CardValue.JACK, chosen.cardValue);
	}

	private static void noReservationWithOnlyOneHeartLeft() {
		TestKit.suite("noReservationWithOnlyOneHeartLeft");
		Table_Cards tc = queenLedTable();
		List<Card> options = cards(card(CardValue.JACK, Suit.HEART)); // WEST's only remaining Heart
		Player west = playerOf(PLAYER.WEST, 22);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("with no alternative, the reserved card is played anyway -- forced",
				CardValue.JACK, chosen.cardValue);
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static List<Card> westHearts() {
		return cards(card(CardValue.THREE, Suit.HEART), card(CardValue.FOUR, Suit.HEART),
				card(CardValue.SEVEN, Suit.HEART), card(CardValue.JACK, Suit.HEART));
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

	/** SOUTH leads the Queen of Hearts (unbeatable by WEST's 3/4/7/J
	 * regardless of choice); WEST is about to play into position 2.
	 * Standings match the real hand: South=45 (visibly threatening, has
	 * already captured a Heart), West=22 (currently leading overall, but
	 * NOT safely ahead of what South's total would become on a successful
	 * moon -- see AI.safelyAheadOfShooter). */
	private static Table_Cards queenLedTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, playerOf(PLAYER.NORTH, 63));
		players.put(PLAYER.EAST, playerOf(PLAYER.EAST, 78));
		Player south = playerOf(PLAYER.SOUTH, 45);
		south.won_cards.addX(card(CardValue.SIX, Suit.HEART)); // the sole captured point card so far
		players.put(PLAYER.SOUTH, south);
		players.put(PLAYER.WEST, playerOf(PLAYER.WEST, 22));
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.QUEEN, Suit.HEART);
		tc.hearts_broken = true;
		return tc;
	}
}
