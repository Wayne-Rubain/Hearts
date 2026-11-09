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
 * Committed regression coverage for the Avoids_Queen passing nuance, added
 * 2026-08-16 for TODO.md's "passing-craft per-type nuances" ("Avoids_Queen
 * should up-weight the Queen-specific passing rules specifically").
 *
 * The behaviour is narrow on purpose, and the reason is worth keeping with
 * the tests: a general "scale the Queen-shaped scores by 1.5" emphasis was
 * built first and measured as a total no-op (an unprotected Queen already
 * scores 15-25, at or above the best Heart at 20, so she is already first
 * pick and re-scaling a first-place score re-ranks nothing). The single
 * case a multiplier cannot reach is the PROTECTED Queen, scored a flat 0
 * ("safe to hold") unless the direction is ideal -- so that is where the
 * type now differs. See Deal_Hand.trustsQueenProtection().
 */
public final class AvoidsQueenPassTest {

	private AvoidsQueenPassTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		avoidsQueenShipsAProtectedQueenAnyway();
		everyOtherTypeStillHoldsAProtectedQueen();
		anUnprotectedQueenIsPassedByBothTypesAlike();
		trustFlagMatchesTheTypeTable();
		return TestKit.summarize();
	}

	/**
	 * The whole nuance in one check. Five low Spades is comfortably
	 * "protected" (hasSpadeProtection wants 4), and WEST is the across
	 * seat -- neither the safe direction nor the dangerous one -- so the
	 * ordinary rule scores her 0 and keeps her.
	 */
	private static void avoidsQueenShipsAProtectedQueenAnyway() {
		TestKit.suite("avoidsQueenShipsAProtectedQueenAnyway");
		List<Card> passed = passProtectedQueenHand(Player_Type.AVOIDS_QUEEN);
		TestKit.check("an Avoids_Queen seat refuses to hold even a well-protected Queen",
				contains(passed, CardValue.QUEEN, Suit.SPADE));
	}

	private static void everyOtherTypeStillHoldsAProtectedQueen() {
		TestKit.suite("everyOtherTypeStillHoldsAProtectedQueen");
		Player_Type[] unchanged = { Player_Type.NORMAL, Player_Type.CAUTIOUS,
				Player_Type.AGGRESSIVE, Player_Type.MOON_SHOOTER };
		for (Player_Type t : unchanged) {
			List<Card> passed = passProtectedQueenHand(t);
			TestKit.check(t + " still keeps a protected Queen (unchanged behaviour)",
					!contains(passed, CardValue.QUEEN, Suit.SPADE));
		}
	}

	/**
	 * The no-regression half: with the Queen UNPROTECTED there was never a
	 * disagreement to create, and there still isn't -- both types ship her.
	 * This is what proves the change is confined to the protected case.
	 */
	private static void anUnprotectedQueenIsPassedByBothTypesAlike() {
		TestKit.suite("anUnprotectedQueenIsPassedByBothTypesAlike");
		TestKit.check("Normal passes an unprotected Queen",
				contains(passUnprotectedQueenHand(Player_Type.NORMAL), CardValue.QUEEN, Suit.SPADE));
		TestKit.check("Avoids_Queen passes an unprotected Queen too -- no divergence here",
				contains(passUnprotectedQueenHand(Player_Type.AVOIDS_QUEEN), CardValue.QUEEN, Suit.SPADE));
	}

	private static void trustFlagMatchesTheTypeTable() {
		TestKit.suite("trustFlagMatchesTheTypeTable");
		TestKit.check("Normal trusts Spade protection",
				Deal_Hand.trustsQueenProtection(playerOf(Player_Type.NORMAL)));
		TestKit.check("Moon_Shooter trusts Spade protection",
				Deal_Hand.trustsQueenProtection(playerOf(Player_Type.MOON_SHOOTER)));
		TestKit.check("Avoids_Queen does not",
				!Deal_Hand.trustsQueenProtection(playerOf(Player_Type.AVOIDS_QUEEN)));
		TestKit.check("the Aggressive+Avoids_Queen combo inherits the distrust",
				!Deal_Hand.trustsQueenProtection(playerOf(Player_Type.AGGRESSIVE_AVOIDS_QUEEN)));
	}

	// ------------------------------------------------------------------

	/** Queen with five low Spades behind her -- protected by any measure --
	 * and passed ACROSS, the direction that earns no positional bonus. */
	private static List<Card> passProtectedQueenHand(Player_Type type) {
		Hand hand = handOf(
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.SPADE),
				card(CardValue.THREE, Suit.SPADE), card(CardValue.FOUR, Suit.SPADE),
				card(CardValue.FIVE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.HEART), card(CardValue.SIX, Suit.HEART),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.CLUB),
				card(CardValue.FOUR, Suit.CLUB),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FOUR, Suit.DIAMOND));
		return passOf(hand, type);
	}

	/** Same shape, but only two low Spades -- below the protection bar. */
	private static List<Card> passUnprotectedQueenHand(Player_Type type) {
		Hand hand = handOf(
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.SPADE),
				card(CardValue.THREE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.HEART), card(CardValue.SIX, Suit.HEART),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.CLUB),
				card(CardValue.FOUR, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FOUR, Suit.DIAMOND), card(CardValue.FIVE, Suit.DIAMOND));
		return passOf(hand, type);
	}

	private static List<Card> passOf(Hand hand, Player_Type type) {
		Table_Cards tc = tableFor(type);
		return Deal_Hand.chooseCardsToPass(hand, tc, tc.players.get(PLAYER.SOUTH), PLAYER.WEST);
	}

	private static boolean contains(List<Card> cards, CardValue value, Suit suit) {
		for (Card c : cards) {
			if (c.cardValue == value && c.suit == suit) {
				return true;
			}
		}
		return false;
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static Hand handOf(Card... cards) {
		Hand h = new Hand();
		for (Card c : cards) {
			h.addX(c);
		}
		return h;
	}

	private static Player playerOf(Player_Type type) {
		Player p = new Player();
		p.tbl_player = PLAYER.SOUTH;
		p.Set_Player(type);
		return p;
	}

	private static Table_Cards tableFor(Player_Type type) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Player_Type.NORMAL);
			players.put(p, pl);
		}
		Player me = players.get(PLAYER.SOUTH);
		me.Set_Player(type);
		me.tbl_player = PLAYER.SOUTH;
		tc.players = players;
		return tc;
	}
}
