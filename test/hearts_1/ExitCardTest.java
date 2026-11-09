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
 * Committed regression coverage for AICardScorer's exit-card preservation,
 * added 2026-08-15 (TODO.md's "keep a guaranteed-losing card late in the
 * hand to avoid being endplayed" item). Covers isReserveCandidate() and
 * reserveCardCount() directly, plus one pickLead() integration test proving
 * the mechanism actually changes the winning card, not just the scorer's
 * isolated math.
 */
public final class ExitCardTest {

	private ExitCardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		aceIsNeverAReserveCandidate();
		lowCardIsAReserveCandidateWhileHigherRanksAreOutstanding();
		lowCardStopsBeingAReserveCandidateOnceEverythingAboveItIsAccountedFor();
		reserveCardCountCountsAcrossSuits();
		pickLeadAvoidsTheLastReserveCardWhenABossAlternativeExists();
		pickLeadDoesNotAvoidAReserveCardWhenAnotherReserveRemains();
		return TestKit.summarize();
	}

	private static void aceIsNeverAReserveCandidate() {
		TestKit.suite("aceIsNeverAReserveCandidate");
		Table_Cards tc = emptyTable();
		Card ace = card(CardValue.ACE, Suit.DIAMOND);
		List<Card> options = cards(ace, card(CardValue.THREE, Suit.SPADE));
		TestKit.check("nothing outranks the Ace, so it is never a reserve candidate",
				!AICardScorer.isReserveCandidate(tc, options, ace));
	}

	private static void lowCardIsAReserveCandidateWhileHigherRanksAreOutstanding() {
		TestKit.suite("lowCardIsAReserveCandidateWhileHigherRanksAreOutstanding");
		Table_Cards tc = emptyTable();
		Card three = card(CardValue.THREE, Suit.SPADE);
		List<Card> options = cards(three, card(CardValue.ACE, Suit.CLUB));
		TestKit.check("a low Spade is a reserve candidate while higher Spades are still out there",
				AICardScorer.isReserveCandidate(tc, options, three));
	}

	private static void lowCardStopsBeingAReserveCandidateOnceEverythingAboveItIsAccountedFor() {
		TestKit.suite("lowCardStopsBeingAReserveCandidateOnceEverythingAboveItIsAccountedFor");
		Table_Cards tc = emptyTable();
		Card king = card(CardValue.KING, Suit.SPADE);
		// My own hand accounts for every Spade above the King (the Ace) --
		// nothing left out there can beat it.
		List<Card> options = cards(king, card(CardValue.ACE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("a King is the provable boss once my own hand holds the only card above it",
				!AICardScorer.isReserveCandidate(tc, options, king));
	}

	private static void reserveCardCountCountsAcrossSuits() {
		TestKit.suite("reserveCardCountCountsAcrossSuits");
		Table_Cards tc = emptyTable();
		List<Card> options = cards(
				card(CardValue.ACE, Suit.DIAMOND), // boss -- not a reserve
				card(CardValue.THREE, Suit.SPADE), // reserve
				card(CardValue.FOUR, Suit.CLUB)); // reserve
		TestKit.checkEquals("two of the three options are reserve candidates",
				2, AICardScorer.reserveCardCount(tc, options));
	}

	private static void pickLeadAvoidsTheLastReserveCardWhenABossAlternativeExists() {
		TestKit.suite("pickLeadAvoidsTheLastReserveCardWhenABossAlternativeExists");
		// Clubs, deliberately, not Diamonds -- Diamonds carry their own
		// protect-the-Jack-capture-weapon lead penalty (AI.shouldProtectDiamondSuit)
		// that would confound this scenario. Clubs carry no special card.
		Table_Cards tc = emptyTable();
		Card lowSpade = card(CardValue.THREE, Suit.SPADE); // this hand's only reserve candidate
		Card bossClub = card(CardValue.ACE, Suit.CLUB); // provably unbeatable
		List<Card> options = cards(lowSpade, bossClub);
		Player me = playerOf(PLAYER.SOUTH);
		Card chosen = AICardScorer.pickLead(options, tc, me);
		TestKit.checkEquals("with only one reserve candidate available, the boss card leads instead",
				bossClub, chosen);
	}

	private static void pickLeadDoesNotAvoidAReserveCardWhenAnotherReserveRemains() {
		TestKit.suite("pickLeadDoesNotAvoidAReserveCardWhenAnotherReserveRemains");
		// Same shape, but now TWO reserve candidates are available (a low
		// Spade and a low Club) -- leading one still leaves a reserve behind,
		// so the ordinary "lead low" preference should win as normal.
		Table_Cards tc = emptyTable();
		Card lowSpade = card(CardValue.THREE, Suit.SPADE);
		Card lowClub = card(CardValue.FOUR, Suit.CLUB);
		List<Card> options = cards(lowSpade, lowClub);
		Player me = playerOf(PLAYER.SOUTH);
		Card chosen = AICardScorer.pickLead(options, tc, me);
		TestKit.checkEquals("with a reserve left over either way, the ordinary lowest-rank lead wins",
				lowSpade, chosen);
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

	private static Player playerOf(PLAYER seat) {
		Player p = new Player();
		p.tbl_player = seat;
		return p;
	}

	/** Four seats with empty won piles and no known voids -- the neutral
	 * baseline every scorer that reads tc.players/tc.void_suits needs to run
	 * without NPEs. */
	private static Table_Cards emptyTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			players.put(p, playerOf(p));
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.players = players;
		tc.void_suits = voidSuits;
		tc.current_player = PLAYER.SOUTH;
		tc.hearts_broken = true;
		return tc;
	}
}
