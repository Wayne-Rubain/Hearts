package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Trick-winner determination and Omnibus Hearts scoring, added 2026-07-30.
 * Pure logic, no Swing dependency -- same pattern as Rules.java/AI.java.
 *
 * Replaces the old placeholder in Play_Hand.java
 * (`tc.who_won = tc.who_leads.Next_Dealer()`, a simple rotation with no
 * regard for what was actually played) with real trick-winner logic:
 * whoever played the highest card of the LED suit wins, full stop --
 * cards of other suits (void-discards) never win regardless of rank.
 *
 * Scoring follows Omnibus Hearts specifically (see the project's TODO.md
 * for the full ruleset writeup), not standard Hearts:
 *   - Each Heart captured is worth +1.
 *   - The Queen of Spades captured is worth +13.
 *   - The Jack of Diamonds captured is worth -10 -- independent of
 *     everything else, including shooting the moon (stacks with it).
 *   - Shooting the moon (capturing all 13 Hearts AND the Queen of Spades)
 *     replaces the normal Hearts/Queen-of-Spades scoring for that hand:
 *     the shooter gets -26 instead of +26, everyone else gets 0 for
 *     Hearts/Queen (they took none of those points -- the shooter took
 *     them all). The Jack of Diamonds bonus is untouched by this and
 *     still applies to whoever actually captured it, even if that's the
 *     moon-shooter themselves (-26 + -10 = -36) or someone else entirely
 *     (a "tragic moon shot" consolation -- they still couldn't stop the
 *     moon, but banked -10 for grabbing the Jack away from the shooter).
 */
public final class Scoring {

	private Scoring() { }

	// Package-private (were private) since 2026-10-08 so EndgameSolver scores
	// its look-ahead with these exact values rather than its own copies.
	static final int HEARTS_IN_DECK = 13;
	static final int QUEEN_OF_SPADES_POINTS = 13;
	static final int JACK_OF_DIAMONDS_POINTS = -10;
	static final int SHOOT_THE_MOON_POINTS = -26;

	/** Whoever played the highest card of the trick's lead suit (tc.p1_card's
	 * suit) among n_card/e_card/s_card/w_card. All 4 must already be set --
	 * call this only once a full trick (play_position reached 4) has been
	 * played. */
	public static PLAYER determineTrickWinner(Table_Cards tc) {
		Suit leadSuit = tc.p1_card.suit;

		Map<PLAYER, Card> played = new EnumMap<PLAYER, Card>(PLAYER.class);
		played.put(PLAYER.NORTH, tc.n_card);
		played.put(PLAYER.EAST, tc.e_card);
		played.put(PLAYER.SOUTH, tc.s_card);
		played.put(PLAYER.WEST, tc.w_card);

		PLAYER winner = null;
		Card winningCard = null;
		for (Map.Entry<PLAYER, Card> entry : played.entrySet()) {
			Card c = entry.getValue();
			if (c != null && c.suit == leadSuit
					&& (winningCard == null || c.cardValue.compareTo(winningCard.cardValue) > 0)) {
				winningCard = c;
				winner = entry.getKey();
			}
		}
		return winner;
	}

	public static int countHearts(Hand wonCards) {
		int count = 0;
		for (Card c : wonCards.hand) {
			if (c.suit == Suit.HEART) {
				count++;
			}
		}
		return count;
	}

	public static boolean hasQueenOfSpades(Hand wonCards) {
		for (Card c : wonCards.hand) {
			if (Rules.isQueenOfSpades(c)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasJackOfDiamonds(Hand wonCards) {
		for (Card c : wonCards.hand) {
			if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
				return true;
			}
		}
		return false;
	}

	/** True if any seat has already captured the Jack of Diamonds this hand
	 * (added 2026-08-02 for AI.java's Jack-hunt abandon logic). If the Jack
	 * is already taken, protecting/hunting it is pointless -- abandon that
	 * strategy and discard Ace/King of Diamonds freely. */
	public static boolean hasJackBeenCaptured(Map<PLAYER, Hand> wonCardsByPlayer) {
		for (Hand won : wonCardsByPlayer.values()) {
			if (hasJackOfDiamonds(won)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isShootingTheMoon(Hand wonCards) {
		return countHearts(wonCards) == HEARTS_IN_DECK && hasQueenOfSpades(wonCards);
	}

	/** Whichever seat is CURRENTLY on track to shoot the moon mid-hand --
	 * i.e. has captured at least one Heart or the Queen of Spades so far,
	 * while every other seat has captured zero Hearts and not the Queen --
	 * or null if there's no such single seat (nobody's captured a point card
	 * yet, or more than one seat already has). This is a real, currently-true
	 * read of who-took-what so far, not a prediction of who WILL shoot the
	 * moon -- added 2026-07-31 for AI.java's Aggressive moon-stopping logic. */
	public static PLAYER inProgressMoonShooter(Map<PLAYER, Hand> wonCardsByPlayer) {
		PLAYER candidate = null;
		for (Map.Entry<PLAYER, Hand> entry : wonCardsByPlayer.entrySet()) {
			Hand won = entry.getValue();
			if (countHearts(won) > 0 || hasQueenOfSpades(won)) {
				if (candidate != null) {
					return null; // more than one seat already has point cards -- no single shooter
				}
				candidate = entry.getKey();
			}
		}
		return candidate;
	}

	/** Full hand-end scoring for all 4 players at once -- needed as a single
	 * call (rather than per-player) because the moon-shooting rule changes
	 * every OTHER player's score too, not just the shooter's. */
	public static Map<PLAYER, Integer> computeHandScores(Map<PLAYER, Hand> wonCardsByPlayer) {
		PLAYER moonShooter = null;
		for (Map.Entry<PLAYER, Hand> entry : wonCardsByPlayer.entrySet()) {
			if (isShootingTheMoon(entry.getValue())) {
				moonShooter = entry.getKey();
				break;
			}
		}

		Map<PLAYER, Integer> scores = new EnumMap<PLAYER, Integer>(PLAYER.class);
		for (Map.Entry<PLAYER, Hand> entry : wonCardsByPlayer.entrySet()) {
			PLAYER p = entry.getKey();
			Hand won = entry.getValue();

			int score;
			if (moonShooter != null) {
				score = (p == moonShooter) ? SHOOT_THE_MOON_POINTS : 0;
			} else {
				score = countHearts(won) + (hasQueenOfSpades(won) ? QUEEN_OF_SPADES_POINTS : 0);
			}
			if (hasJackOfDiamonds(won)) {
				score += JACK_OF_DIAMONDS_POINTS; // independent bonus -- stacks with everything above
			}
			scores.put(p, score);
		}
		return scores;
	}

	/** How much better or worse off `me` is than the rest of the field, in
	 * running game totals -- positive means worse off (my total is HIGHER
	 * than the other three's average, since lower is better in Hearts) and
	 * therefore behind; negative means better off and ahead. Added
	 * 2026-07-31 for HandEvaluator.java's moon-shot-attempt threshold and
	 * Deal_Hand.java's Jack-of-Diamonds pass/keep decision -- both use this
	 * as the single "how badly do I need to catch up" signal, scaled
	 * differently by each caller rather than duplicating the comparison. */
	public static double standingDelta(Table_Cards tc, PLAYER me) {
		int myTotal = tc.players.get(me).p_total_points;
		int sumOthers = 0;
		for (PLAYER p : PLAYER.values()) {
			if (p != me) {
				sumOthers += tc.players.get(p).p_total_points;
			}
		}
		return myTotal - (sumOthers / 3.0);
	}
}
