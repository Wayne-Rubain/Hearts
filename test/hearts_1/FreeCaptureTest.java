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
 * Committed regression coverage for the COUNTED FREE CAPTURE scorer,
 * added 2026-08-16 -- replays the real hand that surfaced it: a Cautious
 * WEST, playing LAST over a 2-J-Q Diamond trick with the Ace and 7 of
 * Diamonds in hand, ducked with the 7 and gifted the human a free -10.
 * Root cause: the existing capture-a-played-Jack scorer is gated by
 * JACK_HUNT, which Cautious weighs at ZERO -- correct for speculative
 * hunting, wrong for a PROVEN net-gain trick. The proven case now fires
 * under COUNTED_CERTAINTIES, which every counting-capable type keeps.
 */
public final class FreeCaptureTest {

	private FreeCaptureTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		cautiousTakesTheProvenFreeJack();
		unprovenWinStaysHuntTerritoryForCautious();
		noCaptureWhenTheTrickIsNotFreeMoney();
		return TestKit.summarize();
	}

	private static void cautiousTakesTheProvenFreeJack() {
		TestKit.suite("cautiousTakesTheProvenFreeJack");
		Table_Cards tc = jackOnTableTrick();
		List<Card> legal = cards(card(CardValue.SEVEN, Suit.DIAMOND), card(CardValue.ACE, Suit.DIAMOND));
		Player west = tc.players.get(PLAYER.WEST);
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 4, west);
		TestKit.checkEquals("playing last over 2-J-Q, Cautious takes the free -10 with the Ace",
				CardValue.ACE, chosen.cardValue);
	}

	private static void unprovenWinStaysHuntTerritoryForCautious() {
		TestKit.suite("unprovenWinStaysHuntTerritoryForCautious");
		// WEST holds the KING (not the Ace) and plays at position 3 -- one
		// seat still to act, not provably void, and the ACE of Diamonds is
		// unaccounted for, so the King beating the table proves nothing.
		// The proven-only scorer must stand down, and with JACK_HUNT at
		// zero Cautious goes back to ducking -- the deliberate personality
		// behavior. (An ACE in this spot is a different story: nothing can
		// ever outrank it, so it is proven at ANY position -- which is why
		// this scenario must use the King.)
		Table_Cards tc = jackOnTableTrick();
		tc.p3_card = null;
		List<Card> legal = cards(card(CardValue.SEVEN, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND));
		Player west = tc.players.get(PLAYER.WEST);
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 3, west);
		TestKit.checkEquals("King with the Ace still out and a seat to act: unproven, Cautious ducks",
				CardValue.SEVEN, chosen.cardValue);
	}

	private static void noCaptureWhenTheTrickIsNotFreeMoney() {
		TestKit.suite("noCaptureWhenTheTrickIsNotFreeMoney");
		// Jack on the table but the Queen of Spades rides along: net
		// -10 + 13 = +3, not a gain -- duck as usual.
		Table_Cards tc = jackOnTableTrick();
		tc.p3_card = card(CardValue.QUEEN, Suit.SPADE); // discarded onto the trick
		List<Card> legal = cards(card(CardValue.SEVEN, Suit.DIAMOND), card(CardValue.ACE, Suit.DIAMOND));
		Player west = tc.players.get(PLAYER.WEST);
		Card chosen = AICardScorer.pickFollow(legal, handOf(legal), tc, 4, west);
		TestKit.checkEquals("a Queen riding along makes the trick net-positive -- duck, don't capture",
				CardValue.SEVEN, chosen.cardValue);
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

	/** NORTH leads the 2 of Diamonds, EAST plays the Jack, SOUTH the
	 * Queen; WEST (Cautious) is about to act. Exactly the real game-2
	 * hand-9 trick-3 position. */
	private static Table_Cards jackOnTableTrick() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		players.get(PLAYER.WEST).Set_Player(Player_Type.CAUTIOUS);
		// This suite is about the free-capture TACTIC, not the 2026-08-16
		// counts-cards gate (see CountsCardsGateTest for that) -- assume
		// counting is active so the tactic itself gets exercised.
		players.get(PLAYER.WEST).countsCardsThisHand = true;
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.TWO, Suit.DIAMOND);
		tc.p2_card = card(CardValue.JACK, Suit.DIAMOND);
		tc.p3_card = card(CardValue.QUEEN, Suit.DIAMOND);
		tc.hearts_broken = false;
		return tc;
	}
}
