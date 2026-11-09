package hearts_1;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * End-of-game awareness, added 2026-08-15 (TODO.md: "near the 100-point game
 * end, play changes -- stay under, push the leader over, time the hand you
 * eat points in"). Unblocked since 2026-08-09, when TARGET_SCORE and
 * checkForGameOver() gave the game an actual end to be aware of; before that
 * there was nothing here to react to.
 *
 * Distinct from Scoring.standingDelta(), which measures RELATIVE position
 * against the field average and already feeds the moon-shot threshold and
 * defensive passing. This class measures ABSOLUTE proximity to
 * Hearts_JFrame.TARGET_SCORE -- a seat can be comfortably ahead of the field
 * average and still be one bad trick from ending the game, which
 * standingDelta alone can't see.
 *
 * Pure logic, no Swing dependency -- same pattern as
 * Rules.java/Scoring.java/MoonThreat.java/VoidTracker.java.
 */
public final class EndGame {

	private EndGame() { }

	/** How close to Hearts_JFrame.TARGET_SCORE turns on end-of-game caution.
	 * A seat with this many points or fewer remaining is "in the danger
	 * zone"; dangerLevel() ramps from 0 at the edge of the zone to 1 at the
	 * target itself. Sized well above a single trick's worst case (13, the
	 * Queen of Spades) so caution phases in before one bad trick can end the
	 * game outright, not just as it happens. */
	private static final int DANGER_ZONE = 25;

	/** Points remaining before `seat`'s running total reaches
	 * Hearts_JFrame.TARGET_SCORE. Can be zero or negative only mid-hand,
	 * between a seat crossing the target and checkForGameOver() ending the
	 * game at the hand boundary -- callers should treat that as maximum
	 * danger, not a special case. */
	public static int marginToTarget(Table_Cards tc, PLAYER seat) {
		return Hearts_JFrame.TARGET_SCORE - tc.players.get(seat).p_total_points;
	}

	/** 0.0 while `seat` has DANGER_ZONE points or more of headroom, ramping
	 * linearly to 1.0 as the margin shrinks to zero. Clamped so a margin at
	 * or past the target still reads as exactly 1.0, not higher. */
	public static double dangerLevel(Table_Cards tc, PLAYER seat) {
		int margin = marginToTarget(tc, seat);
		if (margin >= DANGER_ZONE) {
			return 0.0;
		}
		if (margin <= 0) {
			return 1.0;
		}
		return (DANGER_ZONE - margin) / (double) DANGER_ZONE;
	}

	/** The seat other than `self` with the least margin remaining -- i.e.
	 * closest to ending the game -- or null if no other seat is inside the
	 * danger zone at all. Ties broken by PLAYER enum order (NORTH, EAST,
	 * SOUTH, WEST), which never matters in practice since ties only occur at
	 * dangerLevel() 0 (nobody in the zone). */
	public static PLAYER mostEndangeredOther(Table_Cards tc, PLAYER self) {
		PLAYER worst = null;
		int worstMargin = Integer.MAX_VALUE;
		for (PLAYER p : PLAYER.values()) {
			if (p == self) {
				continue;
			}
			int margin = marginToTarget(tc, p);
			if (margin < DANGER_ZONE && margin < worstMargin) {
				worst = p;
				worstMargin = margin;
			}
		}
		return worst;
	}

	/** Is `seat` currently tied for the lowest running total -- i.e. would
	 * they win (or share the win) if the game ended on the spot? Added
	 * 2026-08-15 after a user question: hastening the game's end (see the
	 * "push the leader over" scorer in AICardScorer.java) only benefits
	 * whoever is CURRENTLY winning. A seat sitting in 2nd or 3rd who still
	 * has a mathematically live game ahead of them gains nothing by ending
	 * it early -- doing so simply locks in a placement they might otherwise
	 * still climb out of. Ties count as leader, matching how
	 * Hearts_JFrame.checkForGameOver() itself collects every seat at the
	 * lowest total as a winner rather than picking just one. */
	public static boolean isCurrentLeader(Table_Cards tc, PLAYER seat) {
		int myTotal = tc.players.get(seat).p_total_points;
		for (PLAYER p : PLAYER.values()) {
			if (p != seat && tc.players.get(p).p_total_points < myTotal) {
				return false;
			}
		}
		return true;
	}

	/** Which seat plays in `position` (1-4) of the trick in progress, derived
	 * from who led it. Duplicated from MoonThreat.seatAtPosition() rather
	 * than shared -- same call this project has made for CardCounter/
	 * VoidTracker's small position helpers, keeping each pure-logic class
	 * self-contained. */
	private static PLAYER seatAtPosition(Table_Cards tc, int position) {
		PLAYER seat = tc.who_leads;
		for (int i = 1; i < position; i++) {
			seat = seat.Next_Dealer();
		}
		return seat;
	}

	private static Card cardPlayedAt(Table_Cards tc, int position) {
		switch (position) {
			case 1: return tc.p1_card;
			case 2: return tc.p2_card;
			case 3: return tc.p3_card;
			case 4: return tc.p4_card;
			default: return null;
		}
	}

	/** Which seat is currently winning the trick in progress, considering
	 * only the cards already played (positions 1 through uptoPosition - 1).
	 * Used to decide whether a void discard is about to land on the most
	 * endangered seat's pile or somebody else's. */
	public static PLAYER currentTrickWinnerSeat(Table_Cards tc, int uptoPosition) {
		Suit leadSuit = tc.p1_card.suit;
		PLAYER winner = tc.who_leads;
		Card winningCard = tc.p1_card;
		for (int pos = 2; pos < uptoPosition; pos++) {
			Card c = cardPlayedAt(tc, pos);
			if (c != null && c.suit == leadSuit
					&& c.cardValue.compareTo(winningCard.cardValue) > 0) {
				winningCard = c;
				winner = seatAtPosition(tc, pos);
			}
		}
		return winner;
	}
}
