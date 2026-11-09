package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * The 2026-10-10 passing rules (user rules, measured in whole games):
 *   - a moon kit always travels with a BLOCKER -- a Heart of 5 or lower --
 *     and is held back only when there is no low Heart to send;
 *   - short on Spades (three or fewer below the Queen), empty a suit;
 *   - Clubs and Diamonds are passed by height, except the Diamond weapons
 *     while holding the Jack.
 * And the 2026-10-11 rules:
 *   - never pass yourself below two Hearts under the 10 (ducking cards);
 *   - the Ace/King of Spades go to the leader readily (they catch the Queen).
 */
public final class PassingRulesTest {

	private PassingRulesTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		try {
			aKitGoesWithALowHeart();
			noLowHeartMeansHoldBackTheTopHeart();
			shortOnSpadesEmptiesASuit();
			highClubsAndDiamondsGoFirst();
			theJackWeaponsStay();
			keepTwoHeartsToDuckWith();
			spadeControlGoesToTheLeader();
		} finally {
			System.setOut(out);
		}
		return TestKit.summarize();
	}

	/** Ace and King of Hearts plus the Queen of Spades are the three most
	 * dangerous cards -- but together they're a moon kit. With a 2 of Hearts
	 * in hand, the pass becomes two of them plus the 2. */
	private static void aKitGoesWithALowHeart() {
		TestKit.suite("aKitGoesWithALowHeart");
		List<Card> p = pass(
				c(CardValue.ACE, Suit.HEART), c(CardValue.KING, Suit.HEART), c(CardValue.TWO, Suit.HEART),
				c(CardValue.QUEEN, Suit.SPADE), c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.FOUR, Suit.SPADE), c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.TWO, Suit.CLUB), c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND));
		TestKit.check("the low Heart goes as the blocker " + p, has(p, CardValue.TWO, Suit.HEART));
		TestKit.check("never Ace + King of Hearts + Queen of Spades together " + p,
				!(has(p, CardValue.ACE, Suit.HEART) && has(p, CardValue.KING, Suit.HEART)
						&& has(p, CardValue.QUEEN, Suit.SPADE)));
		TestKit.check("the big cards still go with it " + p,
				has(p, CardValue.ACE, Suit.HEART) && has(p, CardValue.KING, Suit.HEART));
	}

	private static void noLowHeartMeansHoldBackTheTopHeart() {
		TestKit.suite("noLowHeartMeansHoldBackTheTopHeart");
		List<Card> p = pass(
				c(CardValue.ACE, Suit.HEART), c(CardValue.KING, Suit.HEART), c(CardValue.NINE, Suit.HEART),
				c(CardValue.QUEEN, Suit.SPADE), c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.FOUR, Suit.SPADE), c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.TWO, Suit.CLUB), c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND));
		int fuel = 0;
		fuel += has(p, CardValue.ACE, Suit.HEART) ? 1 : 0;
		fuel += has(p, CardValue.KING, Suit.HEART) ? 1 : 0;
		fuel += has(p, CardValue.QUEEN, Suit.SPADE) ? 1 : 0;
		TestKit.check("without a low Heart, at most one big card goes " + p, fuel <= 1);
	}

	/** Two low Spades only, and a two-card Club suit: the pass empties the
	 * Clubs ahead of the higher-scoring loose Diamonds. */
	private static void shortOnSpadesEmptiesASuit() {
		TestKit.suite("shortOnSpadesEmptiesASuit");
		List<Card> p = pass(
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.SIX, Suit.CLUB), c(CardValue.SEVEN, Suit.CLUB),
				c(CardValue.KING, Suit.DIAMOND), c(CardValue.TEN, Suit.DIAMOND), c(CardValue.NINE, Suit.DIAMOND),
				c(CardValue.EIGHT, Suit.DIAMOND), c(CardValue.SEVEN, Suit.DIAMOND),
				c(CardValue.TWO, Suit.HEART), c(CardValue.THREE, Suit.HEART), c(CardValue.FOUR, Suit.HEART),
				c(CardValue.FIVE, Suit.HEART));
		TestKit.check("both Clubs go, leaving a void " + p,
				has(p, CardValue.SIX, Suit.CLUB) && has(p, CardValue.SEVEN, Suit.CLUB));
	}

	/** Plenty of Spades, no Hearts worth passing, long Clubs: the Ace and
	 * King go, not the low cards. */
	private static void highClubsAndDiamondsGoFirst() {
		TestKit.suite("highClubsAndDiamondsGoFirst");
		List<Card> p = pass(
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE), c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.ACE, Suit.CLUB), c(CardValue.KING, Suit.CLUB), c(CardValue.THREE, Suit.CLUB),
				c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND),
				c(CardValue.SIX, Suit.SPADE), c(CardValue.SEVEN, Suit.SPADE));
		TestKit.check("Ace and King of Clubs go " + p,
				has(p, CardValue.ACE, Suit.CLUB) && has(p, CardValue.KING, Suit.CLUB));
	}

	private static void theJackWeaponsStay() {
		TestKit.suite("theJackWeaponsStay");
		List<Card> p = pass(
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE), c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.ACE, Suit.DIAMOND), c(CardValue.KING, Suit.DIAMOND), c(CardValue.JACK, Suit.DIAMOND),
				c(CardValue.FOUR, Suit.DIAMOND),
				c(CardValue.QUEEN, Suit.CLUB), c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.HEART), c(CardValue.THREE, Suit.HEART));
		TestKit.check("holding the Jack, the Ace and King of Diamonds stay " + p,
				!has(p, CardValue.ACE, Suit.DIAMOND) && !has(p, CardValue.KING, Suit.DIAMOND));
	}

	/** The user's example: passing 9-3-2 of Hearts strips the ducking cards,
	 * and a high Heart received in return then wins the first Heart lead.
	 * One Heart may go; two stay. */
	private static void keepTwoHeartsToDuckWith() {
		TestKit.suite("keepTwoHeartsToDuckWith");
		List<Card> p = pass(
				c(CardValue.ACE, Suit.CLUB), c(CardValue.KING, Suit.CLUB), c(CardValue.SEVEN, Suit.CLUB),
				c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.NINE, Suit.HEART), c(CardValue.THREE, Suit.HEART), c(CardValue.TWO, Suit.HEART),
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE), c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND));
		int hearts = 0;
		for (Card x : p) {
			hearts += x.suit == Suit.HEART ? 1 : 0;
		}
		TestKit.check("at most one Heart passed, two kept to duck with " + p, hearts <= 1);
	}

	/** Unprotected King of Spades (only two Spades below the 10), the
	 * recipient leading the game: it goes. The old rule scored it 4 to the
	 * leader -- below the short-Club filler -- and kept it. */
	private static void spadeControlGoesToTheLeader() {
		TestKit.suite("spadeControlGoesToTheLeader");
		Card[] hand = {
				c(CardValue.KING, Suit.SPADE), c(CardValue.JACK, Suit.SPADE), c(CardValue.TEN, Suit.SPADE),
				c(CardValue.THREE, Suit.SPADE), c(CardValue.TWO, Suit.SPADE),
				c(CardValue.SEVEN, Suit.CLUB), c(CardValue.EIGHT, Suit.CLUB),
				c(CardValue.ACE, Suit.DIAMOND), c(CardValue.KING, Suit.DIAMOND), c(CardValue.TWO, Suit.DIAMOND),
				c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND), c(CardValue.FIVE, Suit.DIAMOND) };
		List<Card> toLeader = pass(new int[] { 40, 50, 60, 10 }, hand);
		TestKit.check("King of Spades passed to the leader " + toLeader,
				has(toLeader, CardValue.KING, Suit.SPADE));
		List<Card> toThird = pass(new int[] { 10, 30, 60, 40 }, hand);
		TestKit.check("and to a seat in 3rd " + toThird, has(toThird, CardValue.KING, Suit.SPADE));
	}

	// ------------------------------------------------------------------

	private static List<Card> pass(Card... cards) {
		return pass(null, cards);
	}

	/** totals, when given, are indexed by PLAYER.values() order (N, E, S, W); SOUTH
	 * passes to WEST. */
	private static List<Card> pass(int[] totals, Card... cards) {
		Hand hand = new Hand();
		for (Card x : cards) {
			hand.addX(x);
		}
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER s : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = s;
			pl.won_cards = new Hand();
			// These are the user's 2026-10-10/11 rules, the BLOCKER kit pass
			// among them -- since 2026-10-11 an option, no longer the default.
			pl.Set_Player(Profile.of(Player_Type.NORMAL).withStrategy(Strategy.KitPass.BLOCKER));
			if (totals != null) {
				pl.p_total_points = totals[s.ordinal()];
			}
			players.put(s, pl);
		}
		tc.players = players;
		return Deal_Hand.chooseCardsToPass(hand, tc, players.get(PLAYER.SOUTH), PLAYER.WEST);
	}

	private static Card c(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static boolean has(List<Card> cards, CardValue v, Suit s) {
		for (Card x : cards) {
			if (x.cardValue == v && x.suit == s) {
				return true;
			}
		}
		return false;
	}
}
