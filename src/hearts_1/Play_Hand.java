package hearts_1;



import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import hearts_1.Deck;
import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.*;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;

// Extracted from Hearts_JFrame.java (2026-07-30) -- Play_Hand_1, Play_Hand_2,
// and Get_Lead_Card used to live directly on Hearts_JFrame. This class was
// clearly meant to hold them (see the imports above, which were already set up
// for exactly this before the extraction happened), it just never got filled
// in. Holds a reference back to the owning frame for the GUI-facing bits
// (labels, shared game state) rather than fully decoupling from Swing -- a
// bigger separation than this move needs; see the project notes on why this
// was kept mechanical rather than a full redesign.
public class Play_Hand {

	private final Hearts_JFrame frame;

	public Play_Hand(Hearts_JFrame frame) {
		this.frame = frame;
	}

	public void Play_Hand_1(Table_Cards tc) {

		Hand h_hand;


		if (tc.play_position == 4 && tc.current_player == PLAYER.SOUTH) {
			if (tc.south_status == South_Hand_Status.na || tc.south_status == South_Hand_Status.first) {
				frame.My_Turn = true;
				System.out.println("******  South 4th positon 1st time   ***************");
				tc.south_status = South_Hand_Status.second;
				frame.lblInfoStatus.setText("    Play Card");
				frame.Update_South_Hand_Legality();
				return;  }
			else {
				tc.s_card = Play_Hand_2(tc, tc.play_position, tc.s_card, PLAYER.SOUTH);
				frame.My_Turn = false;
				tc.play_position = 0;
				resolveTrick(tc);
				frame.onTrickComplete();
				frame.lblInfoStatus.setText("   ");
				System.out.println("******  South 4th positon 2nd time   ***************");
				if (tc.trick_nbr == 13) {
					frame.End_Of_Hand(tc);
				}
				return;
			}

		}

		 if (tc.current_player == PLAYER.SOUTH) {
			 if (tc.south_status == South_Hand_Status.na || tc.south_status == South_Hand_Status.first) {
				frame.My_Turn = true;
				tc.south_status = South_Hand_Status.second;
				System.out.println("******  Player South Turn 1st time  ***************");
				frame.lblInfoStatus.setText("    Play Card");
				frame.Update_South_Hand_Legality();
				return;  }
			 else {
			    // BUG FIX (2026-07-30, found via live playthrough -- South led
			    // Spades on trick 2, but Scoring.determineTrickWinner() still
			    // used Clubs as the lead suit and gave the trick to a Club
			    // discard instead): this branch handles South playing in any
			    // position OTHER than last (leading, or following early), and
			    // used to just advance play_position/current_player without
			    // ever calling Play_Hand_2 for South's own card. That meant
			    // Place_Card_on_Table() -- which sets tc.p1_card/p2_card/p3_card,
			    // the position-based fields Scoring.determineTrickWinner() and
			    // Rules.legalFollows() actually read for the lead suit -- never
			    // ran for South's play here, so it silently kept whatever suit
			    // was led in the PREVIOUS trick. Also meant Rules.recordPlay()
			    // (hearts-broken tracking) never saw South's card in this branch
			    // either. The South-is-last branch above already calls
			    // Play_Hand_2 correctly; this mirrors it.
			    tc.s_card = Play_Hand_2(tc, tc.play_position, tc.s_card, PLAYER.SOUTH);
			    frame.My_Turn = false;
			    tc.south_status = South_Hand_Status.na;
				System.out.println("******  Player South Turn 1st time  ***************");
				frame.lblInfoStatus.setText("    ");
				tc.play_position++;
				tc.current_player= tc.current_player.Next_Dealer();
				Play_Hand_1(tc);
				return;
			    }

			 }


			switch (tc.current_player) {
			case  NORTH:
				h_hand = frame.all_hands.n_hand;
				tc.n_card = Play_Hand_2(tc, tc.play_position, h_hand, frame.table_player_n);
				frame.lblNorth.setIcon(tc.n_card.getIcon());
				h_hand.pull_card(tc.n_card);
				break;
			case  EAST:
				h_hand = frame.all_hands.e_hand;
				tc.e_card = Play_Hand_2(tc, tc.play_position, h_hand, frame.table_player_e);
				frame.lblEast.setIcon(tc.e_card.getIcon());
				h_hand.pull_card(tc.e_card);
				break;
			case  WEST:
				h_hand = frame.all_hands.w_hand;
				tc.w_card = Play_Hand_2(tc, tc.play_position, h_hand, frame.table_player_w);
				frame.lblWest.setIcon(tc.w_card.getIcon());
				h_hand.pull_card(tc.w_card);
				break;
			default:
				System.out.println("****** $$$$$  South should not be here $$$$$ 1st ***************");
				break;	}




			if (tc.play_position == 4) {

					tc.play_position = 0;
					resolveTrick(tc);
					frame.onTrickComplete();
					System.out.println("******  End Play Hand_1 " + tc.who_leads.toString()  + "   ***************");
					if (tc.trick_nbr == 13) {
						frame.End_Of_Hand(tc);
					}
			}
			else {
					tc.play_position++;
					tc.current_player= tc.current_player.Next_Dealer();
					Play_Hand_1(tc);  }

	}

	// BUG FIX (2026-07-30): both trick-resolution branches above used to set
	// tc.who_won via "tc.who_leads.Next_Dealer()" -- a simple seat rotation
	// with no regard for what was actually played, so the "winner" was
	// really just whoever happened to sit next in turn order. Real trick
	// winners are determined by Scoring.determineTrickWinner() (highest
	// card of the led suit), and the winner's Player.won_cards now actually
	// accumulates the 4 cards played this trick -- needed for
	// Scoring.computeHandScores() at hand-end. The winner leads the next
	// trick, same as the old code's intent (tc.who_leads = tc.who_won), just
	// now based on who genuinely won rather than an arbitrary rotation.
	private void resolveTrick(Table_Cards tc) {
		// Added 2026-08-15 for the GameEventBus MOON_SUSPECTED/MOON_BROKEN
		// events -- snapshot the in-progress shooter (if any) BEFORE this
		// trick's cards land in anyone's won_cards, so the before/after
		// comparison below reflects only what THIS trick changed.
		PLAYER shooterBefore = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(tc));

		PLAYER leader = tc.who_leads; // captured before who_leads moves to the winner
		// 2026-11-05: counting the trick, the winner, payback and moon reads,
		// the won cards and the next leader all live in GameEngine now, shared
		// with HeadlessSimulator. It prints the same two TRICK lines as before.
		GameEngine.resolveTrick(tc, true);

		publishTrickEvents(tc, shooterBefore, leader);
	}

	/** Added 2026-08-15 -- GameEventBus emission for this trick, split out
	 * of resolveTrick() so the scoring/state logic above stays exactly as
	 * it was (this method only reads tc, never mutates it). See
	 * GameEvent.java for what each published event means and who it's
	 * for (trashTalk / the roster's tendency counters / the stats screen
	 * -- none of which exist yet; ConsoleEventLogger is the only current
	 * listener, installed to prove this actually fires). */
	private void publishTrickEvents(Table_Cards tc, PLAYER shooterBefore, PLAYER leader) {
		int trick = tc.trick_nbr;

		Card[] played = { tc.n_card, tc.e_card, tc.s_card, tc.w_card };
		PLAYER[] seats = { PLAYER.NORTH, PLAYER.EAST, PLAYER.SOUTH, PLAYER.WEST };
		// cards/leader added 2026-10-10 for the passing-outcome and
		// lead-success stats -- see GameEvent.TRICK_WON.
		Map<PLAYER, Card> cardsBySeat = new EnumMap<PLAYER, Card>(PLAYER.class);
		for (int i = 0; i < played.length; i++) {
			cardsBySeat.put(seats[i], played[i]);
		}
		GameEventBus.publish(GameEvent.TRICK_WON, GameEventBus.facts()
				.with("trick", trick).with("winner", tc.who_won).with("leadSuit", tc.p1_card.suit)
				.with("leader", leader).with("cards", cardsBySeat).build());

		for (int i = 0; i < played.length; i++) {
			if (Rules.isQueenOfSpades(played[i])) {
				GameEventBus.publish(GameEvent.QUEEN_DROPPED_ON, GameEventBus.facts()
						.with("trick", trick).with("winner", tc.who_won).with("droppedBySeat", seats[i]).build());
				if (seats[i] == tc.who_won) {
					GameEventBus.publish(GameEvent.SELF_ATE_QUEEN, GameEventBus.facts()
							.with("trick", trick).with("seat", tc.who_won).build());
				}
			}
			if (played[i].suit == Suit.DIAMOND && played[i].cardValue == CardValue.JACK) {
				GameEventBus.publish(GameEvent.JACK_CAPTURED, GameEventBus.facts()
						.with("trick", trick).with("winner", tc.who_won)
						.with("playedBySeat", seats[i]).build());
			}
		}

		PLAYER shooterAfter = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(tc));
		if (shooterBefore == null && shooterAfter != null) {
			GameEventBus.publish(GameEvent.MOON_SUSPECTED, GameEventBus.facts()
					.with("trick", trick).with("shooter", shooterAfter).build());
		} else if (shooterBefore != null && shooterAfter == null) {
			// The breaker is this trick's winner: a lone claim only ends when
			// somebody ELSE captures a point card, and whoever captured this
			// trick's points is by definition its winner. shooterHearts counts
			// what the former shooter had banked BEFORE this trick resolved
			// (their pile can't have grown this trick -- they didn't win it).
			GameEventBus.publish(GameEvent.MOON_BROKEN, GameEventBus.facts()
					.with("trick", trick).with("formerShooter", shooterBefore)
					.with("breaker", tc.who_won)
					.with("shooterHearts", Integer.valueOf(
							Scoring.countHearts(playerFor(shooterBefore).won_cards)))
					.build());
		}
	}

	private Player playerFor(PLAYER seat) {
		switch (seat) {
			case NORTH: return frame.table_player_n;
			case EAST:  return frame.table_player_e;
			case SOUTH: return frame.table_player_s;
			case WEST:  return frame.table_player_w;
			default: throw new IllegalArgumentException("Unknown seat: " + seat);
		}
	}

	// BUG FIX (2026-07-30, found during extraction): both overloads below used
	// to declare "Card h_c = null;" and "return h_c;" on the non-leading path --
	// h_c was never assigned, so every non-leading play returned null instead
	// of the card actually played. Fixed to return the real card.
	// BUG FIX (2026-07-30, found via live playthrough -- East was forced to
	// lead 2 of Clubs by Rules.legalLeads/AI.chooseLead, but actually led a 7
	// of Hearts): Play_Hand_1's NORTH/EAST/WEST switch cases used to ignore
	// the card Play_Hand_2 returned and instead re-fetch "get_it(0)" -- index
	// 0 of the hand's internal list, i.e. an arbitrary leftover card -- for
	// the icon, tc.n_card/e_card/w_card, AND which card got pulled from hand.
	// That silently discarded the Rules/AI-selected card, played whatever was
	// first in the ArrayList instead, and left the real chosen card
	// uncollected in the hand. Fixed by using the already-correct return
	// value from Play_Hand_2 directly (Card.getCard() just returns "this",
	// and n_hand_get/e_hand_get/w_hand_get just return the same live Hand
	// field h_hand already holds, so no re-fetch was ever needed here).
	// Added 2026-07-30: computer players pick from the RULES-LEGAL subset of
	// their hand (follow suit if able; hearts-broken; first-trick
	// restrictions -- see Rules.java), and WHICH of those legal cards gets
	// picked is now real strategy via AI.java (reads Player.p_traits, so seats
	// actually play differently from each other now instead of all using the
	// same "first legal option" placeholder). Each seat's traits come from
	// its dropdown as of 2026-08-06 (Player.Player_Type), reloaded at the
	// start of every deal.
	public  Card Play_Hand_2(Table_Cards tc, int play_position, Hand in_hand, Player in_p) {
		Card h_card;

		// 2026-11-05: choosing and placing go through GameEngine, shared with
		// HeadlessSimulator.
		h_card = GameEngine.chooseCard(tc, play_position, in_hand, in_p);
		GameEngine.placeCard(tc, play_position, in_p.tbl_player, h_card);
		return h_card;
	}
	// The human (South) already chose in_card via a button Update_South_Hand_Legality()
	// disabled ahead of time, so it's guaranteed legal by construction here --
	// this overload just places it and updates hearts-broken tracking.
	public  Card Play_Hand_2(Table_Cards tc, int play_position, Card in_card, PLAYER in_player) {
		Card  h_card;

		h_card = in_card;
		GameEngine.placeCard(tc, play_position, in_player, h_card);
		return h_card;
	}

	public Card Get_Lead_Card(Table_Cards tc, Hand in1, Player in_pp) {
		return GameEngine.chooseCard(tc, 1, in1, in_pp);
	}

}
