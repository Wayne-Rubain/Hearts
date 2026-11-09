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
 * Committed regression coverage for the Aggressive lead scorer's
 * point-safety guard, added 2026-08-16 -- replays the real hand that
 * surfaced it: an all-Hearts Aggressive EAST, already holding 13 points
 * from a forced self-eaten Queen, led its boss ACE of Hearts (guaranteed
 * self-capture, +3 on the trick) purely because bare rank-hunger (+600)
 * outvoted the halved BASIC_SAFETY boss-heart penalty (-200). The same
 * arithmetic would volunteer the Queen of Spades or an Ace of Spades
 * into a live Queen. Trick-hungry is not point-hungry: the rank bonus
 * now skips any candidate whose lead invites points onto a trick it's
 * about to win, mirroring the follow-side Aggressive take guard.
 */
public final class AggressiveLeadGuardTest {

	private AggressiveLeadGuardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		allHeartsLeadAvoidsTheBossAce();
		queenOfSpadesIsNeverVolunteered();
		highSpadeIntoALiveQueenGetsNoRankBonus();
		ordinaryHighCardStillLedAggressively();
		return TestKit.summarize();
	}

	private static void allHeartsLeadAvoidsTheBossAce() {
		TestKit.suite("allHeartsLeadAvoidsTheBossAce");
		// The real position: 2-6-8-9-A of Hearts, nothing else -- the
		// all-Hearts fallback makes Hearts legal despite being unbroken.
		Table_Cards tc = aggressiveTable();
		List<Card> hearts = cards(
				card(CardValue.TWO, Suit.HEART), card(CardValue.SIX, Suit.HEART),
				card(CardValue.EIGHT, Suit.HEART), card(CardValue.NINE, Suit.HEART),
				card(CardValue.ACE, Suit.HEART));
		Card chosen = AICardScorer.pickLead(hearts, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("the boss Ace (guaranteed self-capture) is not the aggressive lead",
				chosen.cardValue != CardValue.ACE);
		TestKit.checkEquals("the highest LOSABLE Heart is led instead -- still aggressive",
				CardValue.NINE, chosen.cardValue);
	}

	private static void queenOfSpadesIsNeverVolunteered() {
		TestKit.suite("queenOfSpadesIsNeverVolunteered");
		Table_Cards tc = aggressiveTable();
		List<Card> options = cards(
				card(CardValue.QUEEN, Suit.SPADE),
				card(CardValue.FOUR, Suit.CLUB), card(CardValue.SIX, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(options, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("Aggressive never volunteers the Queen of Spades for rank alone",
				!Rules.isQueenOfSpades(chosen));
	}

	private static void highSpadeIntoALiveQueenGetsNoRankBonus() {
		TestKit.suite("highSpadeIntoALiveQueenGetsNoRankBonus");
		Table_Cards tc = aggressiveTable();
		List<Card> options = cards(
				card(CardValue.ACE, Suit.SPADE),
				card(CardValue.JACK, Suit.CLUB), card(CardValue.FOUR, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(options, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("with the Queen live and no shooter, the Ace of Spades is not rank-led",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.ACE));
	}

	private static void ordinaryHighCardStillLedAggressively() {
		TestKit.suite("ordinaryHighCardStillLedAggressively");
		// The guard must not neuter the personality: a high CLUB carries no
		// point risk and should still be the aggressive pick over low cards.
		Table_Cards tc = aggressiveTable();
		List<Card> options = cards(
				card(CardValue.ACE, Suit.CLUB),
				card(CardValue.THREE, Suit.CLUB), card(CardValue.FIVE, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(options, tc, tc.players.get(PLAYER.EAST));
		TestKit.checkEquals("a pointless-suit high card is still the aggressive lead",
				CardValue.ACE, chosen.cardValue);
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

	/** EAST is Aggressive and leading; the Queen of Spades is still live
	 * and no moon shooter is in progress. */
	private static Table_Cards aggressiveTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.EAST).Set_Player(Player_Type.AGGRESSIVE);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.who_leads = PLAYER.EAST;
		tc.current_player = PLAYER.EAST;
		tc.hearts_broken = true;
		return tc;
	}
}
