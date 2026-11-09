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
 * Committed regression coverage for the 2026-08-16 tactic-family correction:
 * the two "COUNTED-OUT JACK OF DIAMONDS" scorers (lead and follow) moved
 * from JACK_HUNT to JACK_GUARD.
 *
 * From a live-hand review: a Cautious seat held the Jack of Diamonds PLUS
 * the Ace, King and Queen -- total, guaranteed control of the entire suit,
 * with nothing left anywhere that could beat it -- and still led its
 * weakest Diamond instead of the Jack, losing the trick (and the lead) for
 * nothing. The cause was tactic tagging, not logic: both counted-out
 * scorers only ever fire once counting has already PROVEN the capture is
 * safe, which is protection being cashed in, not a hunt -- but they were
 * tagged JACK_HUNT, and Cautious's "no flashy plays" JACK_HUNT weight of 0
 * (correctly) blocking the genuinely risky "pull it out of someone else's
 * hand" tactics blocked this already-safe one too.
 *
 * JACK_GUARD is 1 (full) for every real strategic type -- NORMAL, CAUTIOUS,
 * AGGRESSIVE, AVOIDS_QUEEN, AGGRESSIVE_AVOIDS_QUEEN, MOON_SHOOTER -- and 0
 * only for BAD ("point-safety basics only, everything else unknown"),
 * exactly the seat that should NOT be executing a counted-out certainty.
 * That BAD case is the No-regression check here: the retag must not have
 * accidentally taught BAD a tactic it never had.
 */
public final class SafeJackCashInTest {

	private SafeJackCashInTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		cautiousNowCashesAJackItFullyControls();
		badStillNeverCashesTheJack();
		cautiousCapturesAProvablySafeJackOnTheFollowSideToo();
		cautiousStillDeclinesTheGenuinelyRiskyPullLead();
		return TestKit.summarize();
	}

	/** The exact scenario that prompted the fix, reconstructed from the
	 * real game: East (Cautious) holds every Diamond rank from the Jack
	 * up -- Jack, Queen, King, Ace -- so literally nothing left anywhere
	 * can beat it. Before the retag this led the Two of Diamonds instead. */
	private static void cautiousNowCashesAJackItFullyControls() {
		TestKit.suite("cautiousNowCashesAJackItFullyControls");
		Table_Cards tc = tableOf(Player_Type.CAUTIOUS);
		List<Card> hand = cards(
				card(CardValue.TWO, Suit.SPADE), card(CardValue.TEN, Suit.SPADE),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FIVE, Suit.DIAMOND), card(CardValue.SEVEN, Suit.DIAMOND),
				card(CardValue.TEN, Suit.DIAMOND), card(CardValue.JACK, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.ACE, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(hand, tc, tc.players.get(PLAYER.EAST));
		TestKit.checkEquals("Cautious with total Diamond control leads the Jack, not the Two",
				CardValue.JACK, chosen.cardValue);
		TestKit.checkEquals("", Suit.DIAMOND, chosen.suit);
	}

	/** BAD never picks up sophisticated Diamond tactics at all -- JACK_GUARD
	 * is 0 for BAD, same as JACK_HUNT was, so this must be unaffected by
	 * the retag. Uses the identical fixture as the Cautious case above,
	 * changing only the type, to isolate the tactic-weight difference. */
	private static void badStillNeverCashesTheJack() {
		TestKit.suite("badStillNeverCashesTheJack");
		Table_Cards tc = tableOf(Player_Type.BAD);
		List<Card> hand = cards(
				card(CardValue.TWO, Suit.SPADE), card(CardValue.TEN, Suit.SPADE),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FIVE, Suit.DIAMOND), card(CardValue.SEVEN, Suit.DIAMOND),
				card(CardValue.TEN, Suit.DIAMOND), card(CardValue.JACK, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.ACE, Suit.DIAMOND));
		Card chosen = AICardScorer.pickLead(hand, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("BAD still never targets the Jack specifically -- no regression from the retag",
				!(chosen.suit == Suit.DIAMOND && chosen.cardValue == CardValue.JACK));
	}

	/** Follow-side twin: Diamonds led by someone else, Cautious holds the
	 * Jack and counting already proves nothing left can beat it. */
	private static void cautiousCapturesAProvablySafeJackOnTheFollowSideToo() {
		TestKit.suite("cautiousCapturesAProvablySafeJackOnTheFollowSideToo");
		Table_Cards tc = tableOf(Player_Type.CAUTIOUS);
		tc.p1_card = card(CardValue.TWO, Suit.DIAMOND);
		// Queen/King/Ace already captured in earlier tricks this hand --
		// counting proves nothing above the Jack is left to beat it.
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.QUEEN, Suit.DIAMOND));
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.KING, Suit.DIAMOND));
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.ACE, Suit.DIAMOND));
		List<Card> legalOptions = cards(card(CardValue.JACK, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND));
		Hand myHand = handOf(legalOptions);
		Card chosen = AICardScorer.pickFollow(legalOptions, myHand, tc, 2, tc.players.get(PLAYER.EAST));
		TestKit.checkEquals("Cautious plays the provably-safe Jack rather than ducking under it",
				CardValue.JACK, chosen.cardValue);
	}

	/** The genuinely risky tactics -- forcing the Jack out of an OPPONENT's
	 * hand -- must still be off for Cautious. Holds the Ace of Diamonds
	 * (a guaranteed winner) but NOT the Jack itself, with the Jack still
	 * unaccounted for: PULL_THE_JACK_OUT would normally fire here. */
	private static void cautiousStillDeclinesTheGenuinelyRiskyPullLead() {
		TestKit.suite("cautiousStillDeclinesTheGenuinelyRiskyPullLead");
		Table_Cards tc = tableOf(Player_Type.CAUTIOUS);
		List<Card> hand = cards(
				card(CardValue.ACE, Suit.DIAMOND), card(CardValue.TWO, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(hand, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("Cautious still won't chase a Jack it doesn't hold -- JACK_HUNT stays off",
				chosen.suit != Suit.DIAMOND);
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

	private static Table_Cards tableOf(Player_Type eastType) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Player_Type.NORMAL);
			players.put(p, pl);
		}
		players.get(PLAYER.EAST).Set_Player(eastType);
		tc.players = players;

		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = false;
		tc.trick_nbr = 4;
		tc.who_leads = PLAYER.EAST;
		tc.current_player = PLAYER.EAST;
		return tc;
	}
}
