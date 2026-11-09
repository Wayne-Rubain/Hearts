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
 * Committed regression coverage for the 2026-08-16 "remote moon chance"
 * guard on Aggressive's lead scorer -- replays the exact real hand that
 * surfaced it: WEST held Q/J/10 of Hearts (no Ace, no King, only 3 total
 * -- no realistic shot at a moon per HandEvaluator.hasMoonHeartControl)
 * and led the Queen purely because it out-ranked its other options,
 * losing it to the Ace. User's framing: Aggressive should chase Hearts
 * only with a genuine moon chance; otherwise it should prefer any other
 * suit, and if truly forced into Hearts, play the smallest one.
 */
public final class AggressiveHeartChanceTest {

	private AggressiveHeartChanceTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		noRemoteChancePrefersAnyOtherSuitOverHearts();
		noRemoteChanceForcedIntoHeartsPlaysTheLowest();
		realMoonChanceStillChasesTheHighHeart();
		return TestKit.summarize();
	}

	private static void noRemoteChancePrefersAnyOtherSuitOverHearts() {
		TestKit.suite("noRemoteChancePrefersAnyOtherSuitOverHearts");
		// The real hand: West's Q/J/10 of Hearts (no Ace/King -- no
		// control) alongside real alternatives in Spades/Diamonds/Clubs.
		Table_Cards tc = westTable();
		List<Card> options = cards(
				card(CardValue.THREE, Suit.SPADE), card(CardValue.FOUR, Suit.SPADE),
				card(CardValue.SEVEN, Suit.SPADE), card(CardValue.KING, Suit.SPADE),
				card(CardValue.FIVE, Suit.DIAMOND), card(CardValue.SIX, Suit.DIAMOND),
				card(CardValue.EIGHT, Suit.CLUB),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART));
		Card chosen = AICardScorer.pickLead(options, tc, tc.players.get(PLAYER.WEST));
		TestKit.check("with no remote moon chance and real alternatives, Aggressive avoids Hearts entirely",
				chosen.suit != Suit.HEART);
	}

	private static void noRemoteChanceForcedIntoHeartsPlaysTheLowest() {
		TestKit.suite("noRemoteChanceForcedIntoHeartsPlaysTheLowest");
		Table_Cards tc = westTable();
		List<Card> heartsOnly = cards(card(CardValue.QUEEN, Suit.HEART),
				card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART));
		Card chosen = AICardScorer.pickLead(heartsOnly, tc, tc.players.get(PLAYER.WEST));
		TestKit.checkEquals("forced into Hearts with nothing else, the smallest one is led, not the Queen",
				CardValue.TEN, chosen.cardValue);
	}

	private static void realMoonChanceStillChasesTheHighHeart() {
		TestKit.suite("realMoonChanceStillChasesTheHighHeart");
		// A hand that DOES pass hasMoonHeartControl (Ace + 5 Hearts) should
		// be unaffected by this guard -- Aggressive still runs its high
		// Hearts exactly as before (see AggressiveLeadGuardTest's sibling
		// coverage of the boss-Ace exception).
		Table_Cards tc = westTable();
		List<Card> options = cards(
				card(CardValue.TWO, Suit.HEART), card(CardValue.SIX, Suit.HEART),
				card(CardValue.EIGHT, Suit.HEART), card(CardValue.NINE, Suit.HEART),
				card(CardValue.ACE, Suit.HEART));
		Card chosen = AICardScorer.pickLead(options, tc, tc.players.get(PLAYER.WEST));
		TestKit.check("a hand with real moon control still runs its highest LOSABLE Heart, not the boss Ace",
				chosen.suit == Suit.HEART && chosen.cardValue != CardValue.ACE);
		TestKit.checkEquals("specifically the Nine -- the guard doesn't suppress a genuinely moon-capable hand",
				CardValue.NINE, chosen.cardValue);
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

	private static Table_Cards westTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.WEST).Set_Player(Player_Type.AGGRESSIVE);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.WEST;
		tc.current_player = PLAYER.WEST;
		return tc;
	}
}
