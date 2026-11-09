package hearts_1;

import java.util.List;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Reading a moon shot from the table, added 2026-10-11 for the MOON_ALARM
 * strategy component (see Strategy.MoonAlarm). Decides WHEN a lone
 * point-taker counts as a real threat for the two moon-defense rules that
 * cost games when they fire too early: don't feed the shooter, and take a
 * point trick to break the shot (AICardScorer, MOON-STOPPING tactics 2 and
 * 3). FIRST_HEART is the old behaviour (any lone point-taker); the default
 * since 2026-10-11 is SHOOTER_READ.
 *
 * The user's read (2026-10-11): "If a person got a heart because someone was
 * out of a suit and then lead a winner on purpose in that same suit, you can
 * usually tell the person is trying to shoot the moon" -- nobody wants the
 * lead, taking tricks, while the Queen is still out and Hearts are broken.
 * Winning a trick by accident is not the signal; LEADING a sure winner is.
 *
 * Everything here is public information: cards already played (the four won
 * piles), who led and who won. Per-hand state lives on Player and is cleared
 * by startHand().
 */
public final class MoonRead {

	private MoonRead() { }

	/** Start of a hand. */
	public static void startHand(Player p) {
		p.heartDiscardSuit = null;
		p.ledSureWinner = false;
		p.ledSureWinnerSameSuit = false;
		p.ledQueenOfSpades = false;
	}

	/** Called as each trick resolves (Play_Hand and the headless simulator):
	 * the winner is known, the four cards are still on the table and not yet
	 * in anyone's pile, and who_leads is still this trick's leader. */
	public static void recordTrick(Table_Cards tc) {
		Player leader = tc.players.get(tc.who_leads);
		Player winner = tc.players.get(tc.who_won);
		Card led = tc.p1_card;
		if (leader == null || winner == null || led == null) {
			return;
		}
		if (Rules.isQueenOfSpades(led)) {
			leader.ledQueenOfSpades = true;
		}
		if (tc.who_won == tc.who_leads && hasHeart(leader.won_cards.hand) && !queenPlayed(tc)
				&& isTopOutstanding(tc, led)) {
			leader.ledSureWinner = true;
			if (led.suit == leader.heartDiscardSuit) {
				leader.ledSureWinnerSameSuit = true;
			}
		}
		Card[] four = { tc.n_card, tc.e_card, tc.s_card, tc.w_card };
		boolean heartInTrick = false;
		for (Card c : four) {
			heartInTrick |= c != null && c.suit == Suit.HEART;
		}
		if (heartInTrick && led.suit != Suit.HEART && winner.heartDiscardSuit == null
				&& !hasHeart(winner.won_cards.hand)) {
			winner.heartDiscardSuit = led.suit; // their first Heart was dumped on them
		}
	}

	/** Should `me` treat `shooter` (the lone point-taker) as a real moon
	 * threat yet, by me's MOON_ALARM option? */
	public static boolean alarmed(Table_Cards tc, Player me, PLAYER shooter) {
		Strategy.MoonAlarm alarm = (Strategy.MoonAlarm) me.strategy(Strategy.MOON_ALARM);
		if (alarm == Strategy.MoonAlarm.FIRST_HEART) {
			return true;
		}
		Player s = tc.players.get(shooter);
		if (s == null) {
			return true;
		}
		// 2026-11-08 (Wayne): leading the Queen of Spades yourself is a moon
		// warning -- on the trick it is led (before it lands in their pile) and
		// for the rest of the hand. Whether to act on it is MoonThreat's
		// standings question (a far-ahead leader may let it pass for now).
		if (s.ledQueenOfSpades || (tc.who_leads == shooter && tc.p1_card != null && Rules.isQueenOfSpades(tc.p1_card))) {
			return true;
		}
		int hearts = Scoring.countHearts(s.won_cards);
		boolean queenAndHeart = Scoring.hasQueenOfSpades(s.won_cards) && hearts >= 1;
		boolean fourHearts = hearts >= FOUR_HEARTS;
		switch (alarm) {
			case FOUR_HEARTS: return fourHearts;
			case QUEEN_AND_HEART: return queenAndHeart;
			case FOUR_OR_QUEEN: return fourHearts || queenAndHeart;
			case SURE_WINNER_SAME: return s.ledSureWinnerSameSuit || queenAndHeart;
			case SURE_WINNER_ANY: return s.ledSureWinner || queenAndHeart;
			case KEPT_LEAD: return keptLead(tc, s) || queenAndHeart;
			case SHOOTER_READ: return s.ledSureWinnerSameSuit || queenAndHeart || fourHearts;
			default: return true;
		}
	}

	private static final int FOUR_HEARTS = 4;

	/** Won another trick after the one that brought their first Heart, with
	 * the Queen still out. A pile holds its tricks in order, four cards
	 * each. */
	private static boolean keptLead(Table_Cards tc, Player s) {
		List<Card> won = s.won_cards.hand;
		for (int i = 0; i < won.size(); i++) {
			if (won.get(i).suit == Suit.HEART) {
				return won.size() / 4 - 1 > i / 4 && !queenPlayed(tc);
			}
		}
		return false;
	}

	private static boolean hasHeart(List<Card> cards) {
		for (Card c : cards) {
			if (c.suit == Suit.HEART) {
				return true;
			}
		}
		return false;
	}

	/** The Queen of Spades is in somebody's pile already. */
	private static boolean queenPlayed(Table_Cards tc) {
		for (Player p : tc.players.values()) {
			if (Scoring.hasQueenOfSpades(p.won_cards)) {
				return true;
			}
		}
		return false;
	}

	/** No higher card of `led`'s suit is still unplayed (completed tricks
	 * only) -- a sure winner, as anyone at the table could see. */
	private static boolean isTopOutstanding(Table_Cards tc, Card led) {
		for (CardValue v : CardValue.values()) {
			if (v.compareTo(led.cardValue) > 0 && !played(tc, v, led.suit)) {
				return false;
			}
		}
		return true;
	}

	private static boolean played(Table_Cards tc, CardValue v, Suit suit) {
		for (Player p : tc.players.values()) {
			for (Card c : p.won_cards.hand) {
				if (c.suit == suit && c.cardValue == v) {
					return true;
				}
			}
		}
		return false;
	}
}
