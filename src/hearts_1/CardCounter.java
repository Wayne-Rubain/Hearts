package hearts_1;

import java.util.List;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Card counting, added 2026-08-07. Answers "which cards of a suit are still
 * unaccounted for?" -- the deduction the advanced tactics need and that
 * VoidTracker deliberately doesn't do (VoidTracker only records who is
 * PROVABLY void in a suit; it has no notion of which individual ranks remain
 * outstanding).
 *
 * WHAT COUNTS AS KNOWN. Only information a player could legitimately have at
 * the table:
 *   - every card in a completed trick (Play_Hand.resolveTrick() puts all four
 *     into the winner's won_cards, so the union across all seats is exactly
 *     the set of cards already played),
 *   - the cards face up in the trick currently in progress,
 *   - the player's own cards.
 * Anything left over is, by elimination, in an opponent's hand. No peeking at
 * anyone's hand -- that's the whole point.
 *
 * A NOTE ON "my own cards": callers pass their legal-options list rather than
 * a full hand, because that's all AI.java is ever given. That is sound for the
 * suits these tactics ask about: Rules.legalLeads() only ever filters out
 * Hearts (or forces the 2 of Clubs on the first trick), so a lead's options
 * always contain every Spade and Diamond held; and when following, the options
 * are exactly the player's cards of the led suit. So whenever a caller asks
 * about the suit it's actually reasoning over, that suit is fully represented.
 * Asking about a suit outside that guarantee would silently undercount, so
 * don't.
 *
 * Pure logic, no Swing dependency -- same pattern as Rules.java/Scoring.java/
 * VoidTracker.java/HandEvaluator.java/MoonThreat.java.
 */
public final class CardCounter {

	private CardCounter() { }

	/**
	 * Which ranks of `suit` are already accounted for, indexed by
	 * CardValue.ordinal() (TWO=0 .. ACE=12). A false entry means that card is
	 * still out there in somebody else's hand.
	 *
	 * `playPosition` is the position the acting player is about to play into,
	 * so positions 1..playPosition-1 are the cards already face up this trick.
	 * Pass 1 when leading (nothing is on the table yet).
	 */
	public static boolean[] accountedRanks(Table_Cards tc, List<Card> myCards, int playPosition, Suit suit) {
		boolean[] seen = new boolean[CardValue.values().length];

		// Cards already won in completed tricks.
		for (PLAYER p : PLAYER.values()) {
			Player player = tc.players.get(p);
			if (player == null || player.won_cards == null) {
				continue;
			}
			for (Card c : player.won_cards.hand) {
				if (c != null && c.suit == suit) {
					seen[c.cardValue.ordinal()] = true;
				}
			}
		}

		// Cards face up in the trick in progress.
		Card[] onTable = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		for (int i = 0; i < playPosition - 1 && i < onTable.length; i++) {
			Card c = onTable[i];
			if (c != null && c.suit == suit) {
				seen[c.cardValue.ordinal()] = true;
			}
		}

		// My own cards.
		for (Card c : myCards) {
			if (c != null && c.suit == suit) {
				seen[c.cardValue.ordinal()] = true;
			}
		}

		return seen;
	}

	/** True if every rank of `suit` strictly below `below` is accounted for --
	 * i.e. no opponent still holds a card of that suit that could duck under
	 * `below`. */
	public static boolean allBelowAccountedFor(Table_Cards tc, List<Card> myCards, int playPosition,
			Suit suit, CardValue below) {
		boolean[] seen = accountedRanks(tc, myCards, playPosition, suit);
		for (CardValue v : CardValue.values()) {
			if (v.compareTo(below) < 0 && !seen[v.ordinal()]) {
				return false;
			}
		}
		return true;
	}

	/** True if at least one rank of `suit` strictly above `above` is still
	 * unaccounted for -- i.e. somebody out there can still beat it. */
	public static boolean anyAboveUnaccounted(Table_Cards tc, List<Card> myCards, int playPosition,
			Suit suit, CardValue above) {
		boolean[] seen = accountedRanks(tc, myCards, playPosition, suit);
		for (CardValue v : CardValue.values()) {
			if (v.compareTo(above) > 0 && !seen[v.ordinal()]) {
				return true;
			}
		}
		return false;
	}

	/** True if this exact card has already been played in a COMPLETED trick.
	 * Distinct from "accounted for": a card in my own hand, or face up in the
	 * trick in progress, is accounted for but has not been permanently
	 * disposed of. */
	public static boolean alreadyWonBySomeone(Table_Cards tc, CardValue value, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			Player player = tc.players.get(p);
			if (player == null || player.won_cards == null) {
				continue;
			}
			for (Card c : player.won_cards.hand) {
				if (c != null && c.suit == suit && c.cardValue == value) {
					return true;
				}
			}
		}
		return false;
	}

	/** True if this exact card is face up in the trick in progress. */
	public static boolean onTableThisTrick(Table_Cards tc, int playPosition, CardValue value, Suit suit) {
		Card[] onTable = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		for (int i = 0; i < playPosition - 1 && i < onTable.length; i++) {
			Card c = onTable[i];
			if (c != null && c.suit == suit && c.cardValue == value) {
				return true;
			}
		}
		return false;
	}

	public static boolean holds(List<Card> myCards, CardValue value, Suit suit) {
		for (Card c : myCards) {
			if (c != null && c.suit == suit && c.cardValue == value) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if every seat that still has to play into this trick is provably
	 * void in `suit`.
	 *
	 * For the Queen of Spades this is a strong deduction and the basis of the
	 * safe-unload tactic: a seat void in SPADES cannot be holding the Queen,
	 * because the Queen is a Spade. So if nobody left to act holds a Spade at
	 * all, the Queen cannot land on this trick.
	 *
	 * `playPosition` is the acting player's position; tc.current_player is the
	 * seat about to act, so walking Next_Dealer() forward enumerates exactly
	 * the seats still to come.
	 */
	public static boolean allLaterSeatsVoidIn(Table_Cards tc, int playPosition, Suit suit) {
		PLAYER seat = tc.current_player;
		for (int i = playPosition; i < 4; i++) {
			seat = seat.Next_Dealer();
			if (!VoidTracker.isVoid(tc, seat, suit)) {
				return false;
			}
		}
		// Playing last: there are no later seats, so nothing can come after
		// me at all. Vacuously true, and genuinely the safest position there
		// is -- everything is already on the table. An earlier version
		// returned false here for want of anyone to check, which wrongly
		// suppressed both the high-Spade unload and the Jack-of-Diamonds
		// capture in exactly the spot where they are most certain.
		return true;
	}

	/** Same question for a LEAD: are all three opponents provably void in
	 * `suit`? */
	public static boolean allOpponentsVoidIn(Table_Cards tc, PLAYER me, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			if (p != me && !VoidTracker.isVoid(tc, p, suit)) {
				return false;
			}
		}
		return true;
	}
}
