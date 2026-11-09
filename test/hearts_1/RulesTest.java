package hearts_1;

import java.util.List;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.Suit;

/**
 * Committed regression coverage for Rules.java, added 2026-08-15 -- the
 * follow-suit / hearts-broken / first-trick restrictions the 2026-08-09
 * rules audit verified against a real session log (124/124 correct, zero
 * violations across 98 tricks -- see CHANGELOG.md), pinned down as
 * standalone scenarios so a future change to Rules.java gets caught before
 * the next live playthrough rather than by one.
 */
public final class RulesTest {

	private RulesTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		firstTrickMustLeadTwoOfClubsIfHeld();
		firstTrickForbidsHeartsAndQueenUnlessForced();
		mustFollowSuitIfAble();
		voidInLeadSuitMayDiscardAnything();
		cannotLeadHeartsUntilBroken();
		heartsMayBeLedOnceBroken();
		heartsMayBeLedWhenHandIsAllHearts();
		return TestKit.summarize();
	}

	private static void firstTrickMustLeadTwoOfClubsIfHeld() {
		TestKit.suite("firstTrickMustLeadTwoOfClubsIfHeld");
		Hand hand = handOf(card(CardValue.TWO, Suit.CLUB), card(CardValue.KING, Suit.SPADE),
				card(CardValue.FIVE, Suit.HEART));
		List<Card> legal = Rules.legalLeads(hand, false, true);
		TestKit.checkEquals("only the 2 of Clubs is a legal opening lead when held", 1, legal.size());
		TestKit.check("the legal lead is specifically the 2 of Clubs", Rules.isTwoOfClubs(legal.get(0)));
	}

	private static void firstTrickForbidsHeartsAndQueenUnlessForced() {
		TestKit.suite("firstTrickForbidsHeartsAndQueenUnlessForced");
		Hand hand = handOf(card(CardValue.FIVE, Suit.CLUB), card(CardValue.QUEEN, Suit.SPADE),
				card(CardValue.FIVE, Suit.HEART));
		List<Card> legal = Rules.legalLeads(hand, false, true);
		TestKit.check("Queen of Spades excluded from first-trick lead options when a safe card exists",
				!containsCard(legal, CardValue.QUEEN, Suit.SPADE));
		TestKit.check("Hearts excluded from first-trick lead options when a safe card exists",
				!containsCard(legal, CardValue.FIVE, Suit.HEART));

		Hand allDanger = handOf(card(CardValue.QUEEN, Suit.SPADE), card(CardValue.FIVE, Suit.HEART));
		List<Card> forced = Rules.legalLeads(allDanger, false, true);
		TestKit.checkEquals("forced to lead a dangerous card when it's genuinely all you have", 2, forced.size());
	}

	private static void mustFollowSuitIfAble() {
		TestKit.suite("mustFollowSuitIfAble");
		Hand hand = handOf(card(CardValue.THREE, Suit.CLUB), card(CardValue.KING, Suit.CLUB),
				card(CardValue.FIVE, Suit.HEART));
		List<Card> legal = Rules.legalFollows(hand, Suit.CLUB, false);
		TestKit.checkEquals("only the 2 Clubs are legal follows when Clubs are led and held", 2, legal.size());
		TestKit.check("the Heart is excluded while Clubs can be followed",
				!containsCard(legal, CardValue.FIVE, Suit.HEART));
	}

	private static void voidInLeadSuitMayDiscardAnything() {
		TestKit.suite("voidInLeadSuitMayDiscardAnything");
		Hand hand = handOf(card(CardValue.QUEEN, Suit.SPADE), card(CardValue.FIVE, Suit.HEART));
		List<Card> legal = Rules.legalFollows(hand, Suit.CLUB, false);
		TestKit.checkEquals("void in the led suit (post-trick-1) may discard the entire hand", 2, legal.size());
	}

	private static void cannotLeadHeartsUntilBroken() {
		TestKit.suite("cannotLeadHeartsUntilBroken");
		Hand hand = handOf(card(CardValue.FIVE, Suit.HEART), card(CardValue.THREE, Suit.CLUB));
		List<Card> legal = Rules.legalLeads(hand, false, false);
		TestKit.check("Hearts excluded from lead options while unbroken",
				!containsCard(legal, CardValue.FIVE, Suit.HEART));
	}

	private static void heartsMayBeLedOnceBroken() {
		TestKit.suite("heartsMayBeLedOnceBroken");
		Hand hand = handOf(card(CardValue.FIVE, Suit.HEART), card(CardValue.THREE, Suit.CLUB));
		List<Card> legal = Rules.legalLeads(hand, true, false);
		TestKit.check("Hearts become a legal lead once broken",
				containsCard(legal, CardValue.FIVE, Suit.HEART));
	}

	private static void heartsMayBeLedWhenHandIsAllHearts() {
		TestKit.suite("heartsMayBeLedWhenHandIsAllHearts");
		Hand hand = handOf(card(CardValue.FIVE, Suit.HEART), card(CardValue.SIX, Suit.HEART));
		List<Card> legal = Rules.legalLeads(hand, false, false);
		TestKit.checkEquals("a hand with nothing but Hearts falls back to the whole hand", 2, legal.size());
	}

	// ------------------------------------------------------------------

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

	private static boolean containsCard(List<Card> cards, CardValue value, Suit suit) {
		for (Card c : cards) {
			if (c.cardValue == value && c.suit == suit) {
				return true;
			}
		}
		return false;
	}
}
