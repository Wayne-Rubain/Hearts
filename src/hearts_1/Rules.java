package hearts_1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Pure Hearts rules logic: follow-suit, hearts-broken, and the standard
 * first-trick restrictions (must lead the 2 of Clubs; no Hearts or Queen of
 * Spades on the first trick unless it's genuinely all you have). Added
 * 2026-07-30. Deliberately has zero Swing/GUI dependencies -- it only reads
 * Card/Hand/Table_Cards -- so it's cheap to unit test directly without
 * standing up any part of the UI.
 *
 * A note on "legal move" lists here: every method below is guaranteed to
 * return a non-empty list as long as the input hand is non-empty. Where a
 * rule (e.g. "no hearts on the first trick") would otherwise filter a hand
 * down to nothing, the restriction is dropped for that call instead --
 * a player must always have SOMETHING legal to play.
 */
public final class Rules {

	private Rules() { }

	public static boolean isTwoOfClubs(Card c) {
		return c.suit == Suit.CLUB && c.cardValue == CardValue.TWO;
	}

	public static boolean isQueenOfSpades(Card c) {
		return c.suit == Suit.SPADE && c.cardValue == CardValue.QUEEN;
	}

	public static boolean isHeartOrQueenOfSpades(Card c) {
		return c.suit == Suit.HEART || isQueenOfSpades(c);
	}

	/** True for every trick before the first one has resolved. */
	public static boolean isFirstTrick(Table_Cards tc) {
		return tc.trick_nbr == 0;
	}

	/** Cards in `hand` that are legal to LEAD a trick with. */
	public static List<Card> legalLeads(Hand hand, boolean heartsBroken, boolean isFirstTrick) {
		if (isFirstTrick) {
			List<Card> twoOfClubs = filtered(hand, Rules::isTwoOfClubs);
			if (!twoOfClubs.isEmpty()) {
				return twoOfClubs; // must lead the 2 of Clubs if you hold it
			}
			// Whoever was asked to lead the first trick doesn't hold 2C --
			// stay defensive rather than assuming that can't happen.
			return fallback(hand, filtered(hand, c -> !isHeartOrQueenOfSpades(c)));
		}
		if (heartsBroken) {
			return new ArrayList<Card>(hand.hand);
		}
		return fallback(hand, filtered(hand, c -> c.suit != Suit.HEART));
	}

	/** Cards in `hand` that are legal to play when following `leadSuit`. */
	public static List<Card> legalFollows(Hand hand, Suit leadSuit, boolean isFirstTrick) {
		List<Card> sameSuit = filtered(hand, c -> c.suit == leadSuit);
		if (!sameSuit.isEmpty()) {
			return sameSuit; // must follow suit if able
		}
		if (isFirstTrick) {
			return fallback(hand, filtered(hand, c -> !isHeartOrQueenOfSpades(c)));
		}
		return new ArrayList<Card>(hand.hand); // void in lead suit -- this is how hearts get broken
	}

	/** Call once a card has actually been placed on the table -- updates
	 * hearts-broken if that card was a Heart. Safe/idempotent to call for
	 * every play, lead or follow, human or computer. */
	public static void recordPlay(Table_Cards tc, Card played) {
		if (played.suit == Suit.HEART) {
			tc.hearts_broken = true;
		}
	}

	private static List<Card> filtered(Hand hand, Predicate<Card> p) {
		return hand.hand.stream().filter(p).collect(Collectors.toList());
	}

	private static List<Card> fallback(Hand hand, List<Card> filteredResult) {
		return filteredResult.isEmpty() ? new ArrayList<Card>(hand.hand) : filteredResult;
	}
}
