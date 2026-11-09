package hearts_1;

import java.util.List;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * The game's play steps in one place (2026-11-05, project review item 4). The
 * real game (Play_Hand) and the measurement simulator (HeadlessSimulator)
 * both play every card and resolve every trick through here, so the two can
 * no longer drift apart -- they did once, when the simulator went without
 * moonGamblePercent until 2026-10-14. It is also the seam a new UI will sit
 * on: the screen draws, this class plays.
 *
 * Hand setup and scoring were already shared (Deal_Hand.beginHand(),
 * Deal_Hand.passCards(), Scoring.computeHandScores()). What both callers used
 * to copy was the part below: choosing a computer player's card, putting a
 * card on the table with the tracking every card needs, and resolving a
 * completed trick.
 *
 * Pure logic, no Swing -- same pattern as Rules.java and Scoring.java.
 */
public final class GameEngine {

	private GameEngine() { }

	/** A computer player's card for position `playPosition` (1 = leading),
	 * chosen from the legal cards of `hand` by the AI. Does not play it. */
	public static Card chooseCard(Table_Cards tc, int playPosition, Hand hand, Player player) {
		if (playPosition == 1) {
			List<Card> legal = Rules.legalLeads(hand, tc.hearts_broken, Rules.isFirstTrick(tc));
			return AI.chooseLead(legal, hand, tc, player);
		}
		List<Card> legal = Rules.legalFollows(hand, tc.p1_card.suit, Rules.isFirstTrick(tc));
		return AI.chooseFollow(legal, hand, tc, playPosition, player);
	}

	/** Puts `card` on the table for `seat` at `playPosition`, with the tracking
	 * every played card needs: Hearts broken and proven voids. Does not remove
	 * it from the seat's hand -- the caller owns the hand (the UI's South hand
	 * is its buttons). */
	public static void placeCard(Table_Cards tc, int playPosition, PLAYER seat, Card card) {
		switch (playPosition) {
			case 1: tc.p1_card = card; break;
			case 2: tc.p2_card = card; break;
			case 3: tc.p3_card = card; break;
			case 4: tc.p4_card = card; break;
			default: throw new IllegalArgumentException("Unknown play position: " + playPosition);
		}
		switch (seat) {
			case NORTH: tc.n_card = card; break;
			case EAST: tc.e_card = card; break;
			case SOUTH: tc.s_card = card; break;
			case WEST: tc.w_card = card; break;
			default: throw new IllegalArgumentException("Unknown seat: " + seat);
		}
		Rules.recordPlay(tc, card);
		VoidTracker.recordPlay(tc, seat, card, playPosition);
	}

	/** Resolves the four cards on the table as trick number tc.trick_nbr + 1:
	 * counts the trick, finds the winner, updates the Queen-payback and moon
	 * reads, gives the winner the cards, and makes the winner the next leader.
	 * With `log`, prints the two TRICK lines the game log (and LogReplayer)
	 * read, in the same place as before. Returns the winner. */
	public static PLAYER resolveTrick(Table_Cards tc, boolean log) {
		tc.trick_nbr++;
		if (log) {
			System.out.println("****** TRICK " + tc.trick_nbr + " lead=" + tc.p1_card.cardValue + " " + tc.p1_card.suit
					+ " | N=" + tc.n_card.cardValue + " " + tc.n_card.suit
					+ " E=" + tc.e_card.cardValue + " " + tc.e_card.suit
					+ " S=" + tc.s_card.cardValue + " " + tc.s_card.suit
					+ " W=" + tc.w_card.cardValue + " " + tc.w_card.suit + " ******");
		}
		tc.who_won = Scoring.determineTrickWinner(tc);
		if (log) {
			System.out.println("****** TRICK " + tc.trick_nbr + " winner=" + tc.who_won + " ******");
		}
		Payback.recordTrick(tc);
		MoonRead.recordTrick(tc);
		Hand pile = tc.players.get(tc.who_won).won_cards;
		pile.addX(tc.n_card);
		pile.addX(tc.e_card);
		pile.addX(tc.s_card);
		pile.addX(tc.w_card);
		tc.who_leads = tc.who_won;
		tc.current_player = tc.who_won;
		return tc.who_won;
	}
}
