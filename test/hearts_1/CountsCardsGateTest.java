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
import hearts_1.Player.Player_Type;

/**
 * Committed regression coverage for the 2026-08-16 "counts cards" ability
 * (user request): a per-INDIVIDUAL-roster-player rate (0-100%), re-rolled
 * every hand (Player.countsCardsThisHand), that gates ONLY the
 * TacticWeights.COUNTED_CERTAINTIES tactic family -- separate from that
 * family's own per-Player_Type weight, and from every other tactic family
 * in this file, which must stay universal per the user's explicit "don't
 * lose those [moon-defense/exit-card/etc.] features" instruction.
 *
 * Also covers the new COUNTED_MINIMAL_WIN scorer this ability unlocks:
 * winning a follow trick with the cheapest sufficient card, once counting
 * proves nobody left to act can overtake it, instead of reflexively
 * spending the highest legal card the way Aggressive/moon-deny/free-
 * capture otherwise would.
 */
public final class CountsCardsGateTest {

	private CountsCardsGateTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		gateSuppressesCountedCertaintiesWhenOff();
		gateAllowsCountedCertaintiesWhenOn();
		nonCountedTacticsStayUniversalRegardlessOfTheGate();
		minimalWinSpendsTheCheapestSufficientCard();
		minimalWinStandsDownWhenAWinCouldStillBeOvertaken();
		return TestKit.summarize();
	}

	private static void gateSuppressesCountedCertaintiesWhenOff() {
		TestKit.suite("gateSuppressesCountedCertaintiesWhenOff");
		// The Queen of Spades is already gone (in NORTH's won pile), so the
		// King is provably safe to unload -- but ONLY if the counted-
		// certainty scorer that knows that is actually consulted. Not
		// counting this hand, WEST falls back to ordinary duck preference,
		// which ducks under with the harmless Four instead.
		Table_Cards tc = queenOnTheTableSpadeTrick();
		Player west = tc.players.get(PLAYER.WEST);
		west.countsCardsThisHand = false;
		List<Card> legal = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.FOUR, Suit.SPADE));
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 4, west);
		TestKit.checkEquals("not counting this hand -- ordinary duck preference wins, King stays home",
				CardValue.FOUR, chosen.cardValue);
	}

	private static void gateAllowsCountedCertaintiesWhenOn() {
		TestKit.suite("gateAllowsCountedCertaintiesWhenOn");
		Table_Cards tc = queenOnTheTableSpadeTrick();
		Player west = tc.players.get(PLAYER.WEST);
		west.countsCardsThisHand = true;
		List<Card> legal = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.FOUR, Suit.SPADE));
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 4, west);
		TestKit.checkEquals("counting this hand -- the Queen is provably gone, so the safe unload fires and dumps the King",
				CardValue.KING, chosen.cardValue);
	}

	private static void nonCountedTacticsStayUniversalRegardlessOfTheGate() {
		TestKit.suite("nonCountedTacticsStayUniversalRegardlessOfTheGate");
		// A threatening-shooter Heart reservation (MOON_DEFENSE family) --
		// must behave identically whether this seat counts cards or not.
		// See MoonFollowReserveTest for the full scenario this mirrors.
		Table_Cards tc = queenLedHeartTrick();
		List<Card> options = cards(card(CardValue.THREE, Suit.HEART), card(CardValue.FOUR, Suit.HEART),
				card(CardValue.SEVEN, Suit.HEART), card(CardValue.JACK, Suit.HEART));
		Player west = tc.players.get(PLAYER.WEST);

		west.countsCardsThisHand = false;
		Card chosenOff = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		west.countsCardsThisHand = true;
		Card chosenOn = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);

		TestKit.checkEquals("moon-defense reservation fires with counting off",
				CardValue.SEVEN, chosenOff.cardValue);
		TestKit.checkEquals("moon-defense reservation is IDENTICAL with counting on -- untouched by this gate",
				CardValue.SEVEN, chosenOn.cardValue);
	}

	private static void minimalWinSpendsTheCheapestSufficientCard() {
		TestKit.suite("minimalWinSpendsTheCheapestSufficientCard");
		// WEST holds 3-Q-A of Clubs against a 7-high table and plays LAST
		// (position 4, everyone else already played) -- the win is
		// provably uncontested. Ordinary duck preference alone would duck
		// with the harmless 3 (see the sibling "mid position" test below,
		// where that's exactly what happens); once the win is provable,
		// the minimal-win scorer instead takes it with the cheaper of the
		// two winners (Queen), not the Ace.
		Table_Cards tc = clubTrickAllOthersPlayed();
		Player west = tc.players.get(PLAYER.WEST);
		west.countsCardsThisHand = true;
		List<Card> legal = cards(card(CardValue.THREE, Suit.CLUB),
				card(CardValue.QUEEN, Suit.CLUB), card(CardValue.ACE, Suit.CLUB));
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 4, west);
		TestKit.checkEquals("a provably safe win is taken with the cheaper winner (Queen), not the Ace or a duck",
				CardValue.QUEEN, chosen.cardValue);
	}

	private static void minimalWinStandsDownWhenAWinCouldStillBeOvertaken() {
		TestKit.suite("minimalWinStandsDownWhenAWinCouldStillBeOvertaken");
		// Identical hand and led suit, but EAST acts at position 2 with
		// SOUTH still to play -- the win is NOT provably safe, so the
		// minimal-win scorer must stand down entirely and ordinary duck
		// preference (duck with the harmless 3 when a duck exists) governs
		// instead. Proves the scorer is genuinely gated on provable safety,
		// not just "any winning card beats a losing one."
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.EAST).Set_Player(Player_Type.NORMAL);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.EAST;
		tc.p1_card = card(CardValue.SEVEN, Suit.CLUB);
		Player east = tc.players.get(PLAYER.EAST);
		east.countsCardsThisHand = true;
		List<Card> legal = cards(card(CardValue.THREE, Suit.CLUB),
				card(CardValue.QUEEN, Suit.CLUB), card(CardValue.ACE, Suit.CLUB));
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 2, east);
		TestKit.checkEquals("with SOUTH still to act, the win isn't provable -- ordinary duck preference wins instead",
				CardValue.THREE, chosen.cardValue);
		TestKit.check("confirms the reason: allLaterSeatsVoidIn is false at this position",
				!CardCounter.allLaterSeatsVoidIn(tc, 2, Suit.CLUB));
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
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

	private static Table_Cards baseTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.WEST).Set_Player(Player_Type.NORMAL);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = true;
		return tc;
	}

	/** NORTH leads a Spade, the Queen of Spades already sits in NORTH's own
	 * won pile from an earlier trick (provably gone) -- WEST, holding
	 * King/Four of Spades, is about to act last. safeToUnloadHighSpade()
	 * should read this as safe once counting is consulted at all. */
	private static Table_Cards queenOnTheTableSpadeTrick() {
		Table_Cards tc = baseTable();
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.THREE, Suit.SPADE);
		tc.p2_card = card(CardValue.FIVE, Suit.SPADE);
		tc.p3_card = card(CardValue.SIX, Suit.SPADE);
		return tc;
	}

	/** The exact MoonFollowReserveTest scenario -- SOUTH (threatening
	 * shooter) leads the Queen of Hearts, WEST is about to follow at
	 * position 2. */
	private static Table_Cards queenLedHeartTrick() {
		Table_Cards tc = baseTable();
		Player south = tc.players.get(PLAYER.SOUTH);
		south.won_cards.addX(card(CardValue.SIX, Suit.HEART));
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.QUEEN, Suit.HEART);
		return tc;
	}

	/** NORTH leads a low Club, EAST and SOUTH have already followed with
	 * nothing dangerous; WEST holds the Queen and Ace of Clubs, both of
	 * which beat the table. */
	private static Table_Cards clubTrickAllOthersPlayed() {
		Table_Cards tc = baseTable();
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.TWO, Suit.CLUB);
		tc.p2_card = card(CardValue.FIVE, Suit.CLUB);
		tc.p3_card = card(CardValue.SEVEN, Suit.CLUB);
		return tc;
	}
}
