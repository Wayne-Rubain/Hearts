package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PASS_DIRECTION;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;

/**
 * The passing phase, added 2026-07-31 (this class was a completely empty
 * stub before now). Decides, for each seat, which 3 cards to pass, then
 * performs the actual 4-way exchange according to the rotating direction.
 *
 * Each seat's decision follows the design worked through in conversation:
 *
 * 1. Evaluate the dealt hand once for moon-shot viability
 *    (HandEvaluator.shouldAttemptMoonShot(), which is itself standings-aware
 *    via Scoring.standingDelta() -- behind the field lowers the effective
 *    bar, ahead of the field raises it). The result is stored on
 *    Player.attempting_moon_shot for (future) trick-play strategy to read --
 *    a one-time commitment for the hand, not re-rolled per trick.
 * 2. If going for it: pass OFFENSIVELY -- keep Hearts, Spade control, and
 *    the long suit; pass away whatever doesn't serve the attempt.
 * 3. Otherwise (the common case): pass DEFENSIVELY -- the standard danger
 *    list (Queen of Spades unless protected, high Hearts, unprotected high
 *    Spades), with the Jack of Diamonds special-cased via
 *    HandEvaluator.hasJackOfDiamondsProtection() rather than lumped in as an
 *    ordinary danger card (protected: keep it and chase the -10 yourself;
 *    unprotected: a mild, standings-sensitive candidate to pass, not an
 *    urgent one -- unlike Hearts/the Queen, holding it doesn't directly
 *    hurt you).
 * 4. Regardless of mode: never pass a combination that would hand the
 *    recipient a complete moon-shot toolkit -- Spade control (Ace/King)
 *    bundled with Heart/Queen exposure. This was originally scoped to
 *    "don't load the leader with the tools to shoot the moon while also
 *    loading them with points," but it's sound to apply whenever passing
 *    danger cards to anyone, not just a leader specifically, so it's
 *    implemented as a universal safety check on the defensive selection.
 *
 * Deliberately NOT yet implemented: a human passing UI for South. Deal_Hand
 * runs the exact same decision logic for all 4 seats, including South, as a
 * placeholder auto-pass -- see TODO.md.
 */
public class Deal_Hand {

	/** Moon fuel a single pass may hand over before a blocker is held back.
	 * Tuned so one high Heart (4) or the Queen of Spades alone (5) still
	 * passes normally -- both are ordinary, correct plays -- while two top
	 * Hearts (8), or a top Heart bundled with the Queen (9), do not. */
	private static final int MOON_FUEL_LIMIT = 8;

	/** Low Spades to keep in reserve. Three is enough to duck a Spade lead
	 * more than once, which is what makes an incoming Queen survivable. */
	private static final int SAFE_SPADE_FLOOR = 3;

	/** See effectiveMoonGamblePercent(). Bounded the same way
	 * HandEvaluator.MAX_STANDING_DISCOUNT is bounded, and for the same
	 * reason -- a trailing seat should gamble MORE, not have standings
	 * become the only thing that matters. */
	private static final int MOON_GAMBLE_MAX_STANDING_BONUS = 30;
	private static final double MOON_GAMBLE_STANDING_SCALE = 40.0;


	private Deal_Hand() { }

	/** Runs the full passing phase for one hand with every seat's cards
	 * decided by AI logic (including South) -- kept for testability and as
	 * the base case the human-aware overload below delegates to. */
	public static List<Card> passCards(All_Hands allHands, Table_Cards tc, PASS_DIRECTION direction) {
		return passCards(allHands, tc, direction, null, null);
	}

	/**
	 * The once-per-hand, pass-direction-INDEPENDENT start-of-hand work: log
	 * every seat's true dealt hand, and record each seat's one-time moon-shot
	 * commitment. Must run for every hand, KEEP hands included.
	 *
	 * BUG FIX (2026-08-09): both of these used to live inside passCards(),
	 * behind its `direction == KEEP` early return -- and on a KEEP hand
	 * Hearts_JFrame never calls passCards() at all (completePassingAndStartHand()
	 * only calls it when passing_selection_mode is true, which the KEEP path
	 * never sets). Player.java's comment claimed passCards() "unconditionally
	 * overwrites [attempting_moon_shot] for every seat at the start of every
	 * hand"; that invariant was false for every 4th hand. Two real consequences,
	 * both confirmed by auditing a 10-hand session log (only 8 DEAL blocks were
	 * present -- the two missing ones were exactly the KEEP hands):
	 *
	 *   1. No seat could ever NEWLY commit to a moon shot on a KEEP hand, so
	 *      moon offense was silently disabled on 25% of all hands.
	 *   2. A seat that committed on the PREVIOUS hand carried a stale `true`
	 *      into the KEEP hand, where AI.isMoonShotAlive() reads it as live (with
	 *      no point card captured yet, inProgressMoonShooter() returns null,
	 *      which means "still alive") -- putting it into MOON_SHOT PlayMode,
	 *      with its 1000-weight scorers, on a hand it had never evaluated.
	 *
	 * The DEAL logging added earlier the same day sat behind the same early
	 * return, so KEEP hands logged nothing at all about what anyone held.
	 *
	 * Called from Hearts_JFrame.beginPassingPhase(), which runs for EVERY hand
	 * and does so after applySelectedPlayerTypes(), so each seat's p_type is
	 * already loaded for the TacticWeights gate inside shouldAttemptMoonShot().
	 */
	public static void beginHand(All_Hands allHands, Table_Cards tc, PLAYER humanSeat) {
		Map<PLAYER, Hand> hands = handMap(allHands);

		// This is the one point in the hand where every seat's TRUE pre-exchange
		// hand is in scope at once -- passCards() mutates them in place, so
		// logging any later would already be showing post-pass state.
		for (PLAYER seat : PLAYER.values()) {
			System.out.println("****** DEAL " + seat + " hand = " + formatCards(hands.get(seat).hand) + " ******");
		}

		for (PLAYER seat : PLAYER.values()) {
			Player p = tc.players.get(seat);
			if (p == null) {
				continue; // players map not populated (direct unit-test call)
			}
			// Added 2026-10-10 -- grudges age one hand (see Payback.java).
			Payback.startHand(p);
			MoonRead.startHand(p);
			p.handsThisGame++; // 2026-10-11 -- wildness's opening hands
			// Added 2026-08-16 -- see Player.countsCardsThisHand's doc. Rolled
			// once per hand for every seat (harmless no-op for a 0% rate or
			// the human, who never consults AICardScorer anyway).
			p.countsCardsThisHand = AI.RNG.nextInt(100) < p.countCardsPercent;
			if (seat == humanSeat) {
				// A human's intent is unknowable, so there is nothing honest to
				// evaluate here -- but the flag must still be CLEARED every hand
				// rather than left holding the previous hand's value. Before
				// this, South's flag was never written at all: the human's cards
				// bypass chooseCardsToPass(), which was the only other writer.
				p.attempting_moon_shot = false;
				p.moonShotHasAceOfHearts = false;
				p.moonLookThisHand = false;
				p.moonHeartLastThisHand = true;
				p.moonByLook = false;
			} else {
				// Added 2026-10-10 (AI players step 2) -- see Profile.rollHand.
				// Before the moon decision below, which reads MOON_OFFENSE.
				p.rollHand(AI.RNG, tc);
				if (!p.skippedThisHand.isEmpty()) {
					System.out.println("****** SKILLS " + seat + " skipped this hand=" + p.skippedThisHand + " ******");
				}
				if (!p.strategyThisHand.isEmpty()) {
					System.out.println("****** STRATEGY " + seat + " strayed this hand=" + p.strategyThisHand + " ******");
				}
				// Added 2026-10-17 -- see Player.moonInterestThisHand. Draws a random
				// number only for a player with a nonzero rate, so seeded replays of
				// players without one are unchanged.
				int interestPercent = effectiveMoonInterestPercent(tc, p);
				p.moonInterestThisHand = interestPercent > 0 && AI.RNG.nextInt(100) < interestPercent;
				if (p.moonInterestPercent > 0) {
					System.out.println("****** MOON_INTEREST " + seat + " base=" + p.moonInterestPercent
							+ "% effective=" + interestPercent + "% active=" + p.moonInterestThisHand + " ******");
				}
				// Added 2026-11-08 -- see Profile.moonLookPercent. Draws only for a
				// rate strictly between 0 and 100, so rosters without it replay
				// exactly as before.
				int look = p.profile.moonLookPercent;
				p.moonLookThisHand = look >= 100 || (look > 0 && AI.RNG.nextInt(100) < look);
				// Added 2026-11-08 -- see Profile.moonHeartLast. Its own random
				// source, so the setting never changes the other random rolls.
				int last = p.profile.moonHeartLast;
				p.moonHeartLastThisHand = last >= 100 || (look > 0 && last > 0 && AI.HEART_LAST_RNG.nextInt(100) < last);
				if (look > 0) {
					List<Card> dealt = hands.get(seat).hand;
					System.out.println("****** MOON_LOOK " + seat + " rate=" + look + "% optimism="
							+ p.profile.moonOptimism + " active=" + p.moonLookThisHand
							+ " score=" + HandEvaluator.sideSuitMoonScore(dealt)
							+ " afterOwnPass=" + HandEvaluator.sideSuitBuildScore(dealt)
							+ " bar=" + HandEvaluator.sideSuitMoonBar(p.profile.moonOptimism)
							+ " buildBar=" + HandEvaluator.sideSuitBuildBar(p.profile.moonOptimism)
							+ " heartLast=" + p.profile.moonHeartLast + "%:" + p.moonHeartLastThisHand + " ******");
				}
				p.attempting_moon_shot = HandEvaluator.shouldAttemptMoonShot(hands.get(seat), tc, p);
				noteMoonPath(hands.get(seat), tc, p);
				// Correct as final on a KEEP hand (no exchange follows); on
				// any hand that DOES exchange cards, reevaluateMoonShotAfter
				// Exchange() overwrites this from the real final hand below.
				p.moonShotHasAceOfHearts = CardCounter.holds(
						hands.get(seat).hand, CardValue.ACE, Suit.HEART);
			}
			// Nothing ever logged the commitment before, which is precisely why
			// the stale-flag bug above was invisible in a game log.
			System.out.println("****** MOON " + seat + " attempting=" + p.attempting_moon_shot
					+ " quality=" + HandEvaluator.moonShotHandQuality(hands.get(seat)) + " ******");
			if (p.attempting_moon_shot) {
				// Added 2026-08-16 -- see GameEvent.MOON_COMMITTED for why
				// MOON_SUSPECTED can't serve as the "attempts" stat signal.
				GameEventBus.publish(GameEvent.MOON_COMMITTED,
						GameEventBus.facts().with("seat", seat).build());
			}
			if (p.countCardsPercent > 0) {
				System.out.println("****** COUNTS_CARDS " + seat + " rate=" + p.countCardsPercent
						+ "% active=" + p.countsCardsThisHand + " ******");
			}
			// Added 2026-08-16 -- see Player.moonGamblePercent's doc.
			int effectiveGamblePercent = effectiveMoonGamblePercent(tc, p);
			p.takesMoonGambleThisHand = AI.RNG.nextInt(100) < effectiveGamblePercent;
			if (p.moonGamblePercent > 0) {
				System.out.println("****** MOON_GAMBLE " + seat + " base=" + p.moonGamblePercent
						+ "% effective=" + effectiveGamblePercent
						+ "% active=" + p.takesMoonGambleThisHand + " ******");
			}
		}
	}

	/** How much harder an EXTRA push, on top of AICardScorer's ordinary
	 * MOON_OFFENSE weight, a moon-shooter is more comfortable being when
	 * this seat is trailing far behind (2026-08-16, user request from a
	 * live-hand review: "when a person is trailing far behind... they
	 * would take these type of chances"). Deliberately shaped like
	 * HandEvaluator's own standings term (bounded, never negative here) --
	 * see that file's MAX_STANDING_DISCOUNT comment for the exact failure
	 * mode an UNBOUNDED version produced in an earlier feature (a
	 * self-reinforcing spiral where falling behind made every subsequent
	 * decision worse). 0 stays 0 regardless of standings -- the roster's
	 * explicit "No" is not something a bad scoreboard should override, the
	 * same convention countCardsPercent already uses.
	 *
	 * Package-private for MoonGambleTest. */
	static int effectiveMoonGamblePercent(Table_Cards tc, Player me) {
		if (me.moonGamblePercent <= 0) {
			return 0;
		}
		double delta = Scoring.standingDelta(tc, me.tbl_player);
		if (delta <= 0) {
			return me.moonGamblePercent; // level or ahead -- no added daring
		}
		double bonus = MOON_GAMBLE_MAX_STANDING_BONUS
				* Math.min(delta / MOON_GAMBLE_STANDING_SCALE, 1.0);
		return Math.min(100, me.moonGamblePercent + (int) Math.round(bonus));
	}

	/** Points behind the leader at which the moon-interest chance has doubled. */
	static final double MOON_INTEREST_DOUBLES_AT = 30.0;

	/** The chance this hand that the player is "interested" in a moon shot
	 * (2026-10-17, user design): the roster rate, up to doubled as the seat
	 * trails the leader by 30 or more points. Uses only the public scores.
	 * 0 stays 0. Package-private for MoonInterestTest. */
	static int effectiveMoonInterestPercent(Table_Cards tc, Player me) {
		if (me.moonInterestPercent <= 0) {
			return 0;
		}
		int best = Integer.MAX_VALUE;
		for (PLAYER seat : PLAYER.values()) {
			Player other = tc.players.get(seat);
			if (other != null) {
				best = Math.min(best, other.p_total_points);
			}
		}
		double behind = Math.max(0, me.p_total_points - best);
		double scale = 1.0 + Math.min(behind / MOON_INTEREST_DOUBLES_AT, 1.0);
		return Math.min(100, (int) Math.round(me.moonInterestPercent * scale));
	}

	private static Map<PLAYER, Hand> handMap(All_Hands allHands) {
		Map<PLAYER, Hand> hands = new EnumMap<PLAYER, Hand>(PLAYER.class);
		hands.put(PLAYER.NORTH, allHands.n_hand);
		hands.put(PLAYER.EAST, allHands.e_hand);
		hands.put(PLAYER.SOUTH, allHands.s_hand);
		hands.put(PLAYER.WEST, allHands.w_hand);
		return hands;
	}

	/** Same as above, but `humanSeat`'s 3 cards come from `humanSelection`
	 * (already chosen via the GUI -- see Hearts_JFrame's
	 * completePassingAndStartHand()) instead of being computed by
	 * chooseCardsToPass(). Pass humanSeat=null to fall back to the fully
	 * automatic behavior. KEEP means no passing this hand at all. Must be
	 * called after Table_Cards.players is populated (needs standings/traits)
	 * and before Find_Lead() (passing can move the 2 of Clubs) -- see
	 * Hearts_JFrame's hand-start setup.
	 *
	 * Returns the 3 cards `humanSeat` actually RECEIVED (empty if humanSeat
	 * is null or direction is KEEP) -- added 2026-07-31 so Hearts_JFrame can
	 * highlight them in South's redisplayed hand the same way selecting them
	 * to pass away highlighted them, per the user's request. */
	public static List<Card> passCards(All_Hands allHands, Table_Cards tc, PASS_DIRECTION direction,
			PLAYER humanSeat, List<Card> humanSelection) {
		// KEEP means there is no exchange to perform. Nothing else may live
		// behind this return -- see beginHand() for the once-per-hand work
		// that used to be stranded here (2026-08-09 bug fix).
		if (direction == PASS_DIRECTION.KEEP) {
			return new ArrayList<Card>();
		}

		Map<PLAYER, Hand> hands = handMap(allHands);

		// Decide everyone's 3 cards from the original hands FIRST -- doing
		// this seat-by-seat interleaved with removal/redistribution would let
		// an earlier seat's incoming cards taint a later seat's own decision.
		Map<PLAYER, List<Card>> chosen = new EnumMap<PLAYER, List<Card>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			if (seat == humanSeat) {
				chosen.put(seat, humanSelection);
			} else {
				chosen.put(seat, chooseCardsToPass(hands.get(seat), tc, tc.players.get(seat),
						recipientFor(seat, direction)));
			}
		}

		for (PLAYER seat : PLAYER.values()) {
			PLAYER recipient = recipientFor(seat, direction);
			String cardsDesc = formatCards(chosen.get(seat));
			System.out.println("****** PASS " + seat + " -> " + recipient + " = " + cardsDesc + " ******");
			// Added 2026-10-10 for Queen payback: a passed Queen counts against
			// the passer only if the recipient ends up taking her.
			Player receiver = tc.players.get(recipient);
			if (receiver != null) {
				for (Card c : chosen.get(seat)) {
					if (Rules.isQueenOfSpades(c)) {
						receiver.queenPassedBy = seat;
					}
				}
			}
			// Added 2026-08-15 -- see GameEvent.PASS_RECEIVED. Published from
			// the passer's perspective (matches the PASS log line above) even
			// though the event name is receiver-framed; "recipient"/"from"
			// facts disambiguate either reading.
			GameEventBus.publish(GameEvent.PASS_RECEIVED, GameEventBus.facts()
					.with("recipient", recipient).with("from", seat).with("cards", cardsDesc)
					.with("passedCards", new ArrayList<Card>(chosen.get(seat))).build());
		}

		List<Card> receivedByHuman = new ArrayList<Card>();
		if (humanSeat != null) {
			for (PLAYER seat : PLAYER.values()) {
				if (recipientFor(seat, direction) == humanSeat) {
					receivedByHuman.addAll(chosen.get(seat));
					break;
				}
			}
		}

		for (PLAYER seat : PLAYER.values()) {
			for (Card c : chosen.get(seat)) {
				hands.get(seat).pull_card(c);
			}
		}

		for (PLAYER seat : PLAYER.values()) {
			PLAYER recipient = recipientFor(seat, direction);
			for (Card c : chosen.get(seat)) {
				hands.get(recipient).addX(c);
			}
		}

		// BUG FIX (2026-07-31, found via live playthrough -- "hearts are not
		// together" in South's displayed hand): Deck.deal() sorts every hand
		// by suit/rank via Hand.sortem2() right after dealing, but the 3
		// cards each seat RECEIVES here just get appended to the end of
		// their hand via addX() with no re-sort -- so passing silently
		// scattered whichever suits the incoming cards belonged to away from
		// the rest of that suit. Re-sort all 4 hands the same way Deck.deal()
		// does once the exchange is actually finished.
		allHands.n_hand = Hand.sortem2(hands.get(PLAYER.NORTH));
		allHands.e_hand = Hand.sortem2(hands.get(PLAYER.EAST));
		allHands.s_hand = Hand.sortem2(hands.get(PLAYER.SOUTH));
		allHands.w_hand = Hand.sortem2(hands.get(PLAYER.WEST));

		reevaluateMoonShotAfterExchange(allHands, tc, humanSeat);

		return receivedByHuman;
	}

	/**
	 * Second and final look at the moon-shot commitment, now that each seat
	 * holds the 13 cards it will actually play (2026-08-16, TODO.md's
	 * passing-craft item). Until this existed, `attempting_moon_shot` was
	 * decided in beginHand() from the PRE-exchange hand and never revisited:
	 * every seat committed (or declined) on the strength of three cards it
	 * was about to give away, and in ignorance of the three coming back. A
	 * seat could be dealt a hand one card short of a shot, receive exactly
	 * that card, and never notice.
	 *
	 * UPGRADE ONLY -- a seat that already committed keeps its commitment,
	 * and this can never turn one off. That asymmetry is deliberate, and is
	 * the difference between a safe change and a destabilising one:
	 *
	 *   - A committed seat has ALREADY passed via offensivePass(), which
	 *     deliberately keeps the Queen, the high Hearts and Spade control
	 *     and ships its three most useless cards instead. That pass is
	 *     irreversible. Un-committing here would strand the seat holding
	 *     exactly the danger kit it kept on purpose, with no plan for it --
	 *     strictly worse than either playing the shot out or never having
	 *     committed. Abandoning a shot that has genuinely died is a
	 *     separate, already-solved job belonging to trick play, where
	 *     AI.isMoonShotAlive() handles it against real captured evidence.
	 *   - A non-committed seat passed defensively, so nothing about its pass
	 *     assumed a shot. Letting it commit now costs nothing and strands
	 *     nothing -- and it still has to clear every existing bar
	 *     (shouldAttemptMoonShot's MOON_OFFENSE weight gate, the hard
	 *     hasMoonHeartControl() gate, and the standings/end-game-adjusted
	 *     quality threshold), all of which are now evaluated against the
	 *     real hand rather than a hypothetical one. A seat that gave its
	 *     high Hearts away simply fails hasMoonHeartControl() and stays
	 *     home, so this cannot manufacture a shot out of nothing.
	 *
	 * Not reached on a KEEP hand: passCards() returns before any of this,
	 * correctly -- with no exchange there is no new information, and
	 * beginHand()'s decision was already made on the final hand.
	 *
	 * The human seat is skipped for the same reason beginHand() skips it: a
	 * human's intent is unknowable, so there is nothing honest to evaluate.
	 *
	 * Also finalizes Player.moonShotHasAceOfHearts (2026-08-17) for every
	 * seat that ends up attempting, whether it committed here or already
	 * had beginHand()'s pre-exchange answer -- that answer can go stale
	 * the moment the exchange changes what the seat actually holds, same
	 * root problem as the commitment flag itself, just for which ROUTE
	 * rather than whether. A seat's own offensivePass() shields its high
	 * Hearts (see keepWorthiness()), so this rarely flips, but "rarely"
	 * is not "never" and there is no cost to being exact here.
	 */
	private static void reevaluateMoonShotAfterExchange(All_Hands allHands, Table_Cards tc,
			PLAYER humanSeat) {
		Map<PLAYER, Hand> hands = handMap(allHands);
		for (PLAYER seat : PLAYER.values()) {
			Player p = tc.players.get(seat);
			if (p == null || seat == humanSeat) {
				continue;
			}
			Hand finalHand = hands.get(seat);
			if (!p.attempting_moon_shot) {
				if (!HandEvaluator.shouldAttemptMoonShot(finalHand, tc, p)) {
					continue;
				}
				p.attempting_moon_shot = true;
				noteMoonPath(finalHand, tc, p);
				// Logged distinctly from beginHand()'s MOON line so a game log
				// shows plainly that the commitment came from the exchange --
				// the 2026-08-09 stale-flag bug was invisible for exactly as
				// long as nothing logged this decision.
				System.out.println("****** MOON " + seat + " attempting=true POST-EXCHANGE"
						+ " quality=" + HandEvaluator.moonShotHandQuality(finalHand)
						+ (HandEvaluator.attemptsByHeartRules(finalHand, tc, p) ? ""
								: " by=MOON_LOOK score=" + HandEvaluator.sideSuitMoonScore(finalHand.hand))
						+ " ******");
				// Same event beginHand() publishes, and no double-count risk:
				// this only ever fires for a seat that did NOT publish there.
				GameEventBus.publish(GameEvent.MOON_COMMITTED, GameEventBus.facts()
						.with("seat", seat).with("postExchange", Boolean.TRUE).build());
			}
			p.moonShotHasAceOfHearts = CardCounter.holds(finalHand.hand, CardValue.ACE, Suit.HEART);
		}
	}

	/** "TEN HEART, JACK HEART, QUEEN HEART" -- matches the card-string
	 * convention Play_Hand.java's existing TRICK log lines already use
	 * (`cardValue + " " + suit`), so DEAL/PASS lines read consistently with
	 * everything else in the console log. Added 2026-08-09 alongside the
	 * DEAL/PASS logging above. */
	private static String formatCards(List<Card> cards) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cards.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			Card c = cards.get(i);
			sb.append(c.cardValue).append(" ").append(c.suit);
		}
		return sb.toString();
	}

	private static PLAYER recipientFor(PLAYER passer, PASS_DIRECTION direction) {
		switch (direction) {
			case LEFT:
				return passer.Next_Dealer();
			case RIGHT:
				return passer.Next_Dealer().Next_Dealer().Next_Dealer();
			case ACROSS:
				return passer.Next_Dealer().Next_Dealer();
			default:
				return passer; // KEEP -- never actually reached, passCards() returns early
		}
	}

	/** Decides one seat's 3 cards to pass. Public so it can also serve as
	 * South's placeholder auto-pass until a real human passing UI exists. */
	public static List<Card> chooseCardsToPass(Hand hand, Table_Cards tc, Player me) {
		return chooseCardsToPass(hand, tc, me, null);
	}

	/** `recipient` is whoever will actually receive these three cards, which
	 * the fixed pass direction decides -- a passer cannot choose who they give
	 * to, only what they give. Null means "unknown", which just skips the
	 * recipient-aware adjustments. Added 2026-08-07 for the Jack of Diamonds
	 * dumping rule. */
	public static List<Card> chooseCardsToPass(Hand hand, Table_Cards tc, Player me, PLAYER recipient) {
		// Deliberately still decides (and records) this itself rather than just
		// reading me.attempting_moon_shot, even though beginHand() has already
		// set it for every seat by the time the GUI reaches here. Two reasons:
		// this method is public and is called directly by the scratch test
		// suites without a beginHand() first, and on a real hand the inputs are
		// provably identical anyway -- no card moves between beginHand() and the
		// exchange below, so this recomputes the same value it already holds.
		boolean goingForMoonShot = HandEvaluator.shouldAttemptMoonShot(hand, tc, me);
		me.attempting_moon_shot = goingForMoonShot;
		noteMoonPath(hand, tc, me);

		// Tactic-weighting gate (2026-08-07, see TacticWeights.java): a
		// player with no PASSING_CRAFT doesn't run the strategic pipeline at
		// all. Brain_Dead shoves over whatever sits first in its hand -- the
		// same "first legal option" non-thought it plays tricks with -- and
		// Bad does what every beginner does: gets rid of its biggest cards,
		// no regard for suit, protection, or who's receiving. (Note
		// shouldAttemptMoonShot() above already returned false for both --
		// their MOON_OFFENSE weight is 0 -- so this can't preempt a shot.)
		if (me.p_traits.contains(Player.Player_Traits.Brain_Dead)) {
			return new ArrayList<Card>(hand.hand.subList(0, Math.min(3, hand.hand.size())));
		}
		if (TacticWeights.weight(me, TacticWeights.Tactic.PASSING_CRAFT) == 0) {
			return naiveHighestCardsPass(hand);
		}

		if (goingForMoonShot) {
			// 2026-11-08: a shot that only the side-suit look committed to passes
			// the look's way (low Hearts first), not the heart-length way.
			if (!HandEvaluator.attemptsByHeartRules(hand, tc, me)) {
				System.out.println("****** MOON_LOOK " + me.tbl_player + " passes for a side-suit shot ******");
				return HandEvaluator.moonLookPass(hand.hand);
			}
			return offensivePass(hand, false);
		}
		// 2026-11-08 (user design): a player giving this hand the side-suit look
		// passes toward a shot when the hand left after its pass clears the
		// build bar; reevaluateMoonShotAfterExchange() commits if the cards that
		// come back make it.
		if (me.moonLookThisHand && buildsTowardSideSuitShot(hand.hand, me)) {
			System.out.println("****** MOON_LOOK " + me.tbl_player + " passes to build toward a side-suit shot ******");
			return HandEvaluator.moonLookPass(hand.hand);
		}
		// Added 2026-10-17 (user design: the pass matters for the moon). An
		// interested player whose hand does not yet qualify still passes to
		// BUILD toward a shot -- it keeps its Hearts and its long suit and
		// gives away the junk, hoping the three cards it receives complete a
		// hand with Heart control; reevaluateMoonShotAfterExchange() then
		// commits if they did. The more it trails the leader the more often it
		// is interested (effectiveMoonInterestPercent), so a far-behind player
		// builds more often.
		if (buildsTowardMoonShot(hand, me)) {
			System.out.println("****** MOON_BUILD " + me.tbl_player + " passes to build toward a moon shot ******");
			return offensivePass(hand, true);
		}
		return defensivePass(hand, tc, me, recipient);
	}

	/** Side-suit look, before the pass: the hand left after the look's own pass
	 * clears the build bar and holds Hearts the look can live with. */
	static boolean buildsTowardSideSuitShot(List<Card> cards, Player me) {
		List<Card> rest = new ArrayList<Card>(cards);
		rest.removeAll(HandEvaluator.moonLookPass(cards));
		return HandEvaluator.lookHeartsOk(rest)
				&& HandEvaluator.sideSuitMoonScore(rest) >= HandEvaluator.sideSuitBuildBar(me.profile.moonOptimism);
	}

	/** Records whether this seat's commitment came from the side-suit look only
	 * (Player.moonByLook), which the look's own play reads. */
	static void noteMoonPath(Hand hand, Table_Cards tc, Player p) {
		p.moonByLook = p.attempting_moon_shot && !HandEvaluator.attemptsByHeartRules(hand, tc, p);
	}

	/** A moon-interested player with a hand that is one good card away from
	 * Heart control: three or more Hearts including the Ace or King. Package-
	 * private for MoonInterestTest. */
	static boolean buildsTowardMoonShot(Hand hand, Player me) {
		if (!me.moonInterestThisHand) {
			return false;
		}
		int hearts = 0;
		boolean topHeart = false;
		for (Card c : hand.hand) {
			if (c.suit == Suit.HEART) {
				hearts++;
				topHeart |= c.cardValue == CardValue.ACE || c.cardValue == CardValue.KING;
			}
		}
		return hearts >= 3 && topHeart;
	}

	/** The beginner's pass: the three highest-ranked cards in the hand,
	 * suits ignored. Often accidentally reasonable (big cards do win
	 * tricks), frequently awful (strips low-Spade armour, hands a moon
	 * shooter exactly the top Hearts it wants). That's the point. */
	private static List<Card> naiveHighestCardsPass(Hand hand) {
		List<Card> pool = new ArrayList<Card>(hand.hand);
		List<Card> chosen = new ArrayList<Card>();
		for (int pick = 0; pick < 3 && !pool.isEmpty(); pick++) {
			Card biggest = pool.get(0);
			for (Card c : pool) {
				if (c.cardValue.compareTo(biggest.cardValue) > 0) {
					biggest = c;
				}
			}
			chosen.add(biggest);
			pool.remove(biggest);
		}
		return chosen;
	}

	// ------------------------------------------------------------------
	// Offensive passing -- going for the moon shot this hand.
	// ------------------------------------------------------------------

	private static List<Card> offensivePass(Hand hand, boolean building) {
		Suit longest = longestSuit(hand);
		List<Card> pool = new ArrayList<Card>(hand.hand);
		List<Card> chosen = new ArrayList<Card>();
		for (int pick = 0; pick < 3 && !pool.isEmpty(); pick++) {
			Card worst = pool.get(0);
			int worstScore = keepWorthiness(worst, longest, building);
			for (Card c : pool) {
				int score = keepWorthiness(c, longest, building);
				if (score < worstScore) {
					worst = c;
					worstScore = score;
				}
			}
			chosen.add(worst);
			pool.remove(worst);
		}
		return chosen;
	}

	/** Higher means "keep this, it serves the attempt." */
	private static int keepWorthiness(Card c, Suit longestSuit, boolean building) {
		if (isSpadeControl(c)) {
			return 12;
		}
		if (building && c.suit == Suit.HEART) {
			// Building a hand: Hearts are the scarce part, so they outrank the
			// off-suit junk and a short side suit, and the high ones are kept first.
			return 9 + c.cardValue.compareTo(CardValue.TWO) / 2;
		}
		if (c.suit == longestSuit) {
			return 10 + c.cardValue.compareTo(CardValue.TWO);
		}
		if (Rules.isQueenOfSpades(c)) {
			return 8;
		}
		if (c.suit == Suit.HEART) {
			return 6;
		}
		return c.cardValue.compareTo(CardValue.TWO); // low, disconnected cards -- worst to keep
	}

	// ------------------------------------------------------------------
	// Defensive passing -- the common case, not attempting this hand.
	// ------------------------------------------------------------------

	private static List<Card> defensivePass(Hand hand, Table_Cards tc, Player me, PLAYER recipient) {
		List<Card> chosen = topDangerCards(hand, tc, me, recipient, false);

		boolean hasHeartOrQueen = false;
		boolean hasSpadeControl = false;
		for (Card c : chosen) {
			if (c.suit == Suit.HEART || Rules.isQueenOfSpades(c)) {
				hasHeartOrQueen = true;
			}
			if (isSpadeControl(c)) {
				hasSpadeControl = true;
			}
		}
		if (hasHeartOrQueen && hasSpadeControl) {
			// Never hand over a complete moon-shot toolkit: Spade control
			// bundled with Heart/Queen exposure. Redo the selection with
			// Spade control off the table entirely rather than trying to
			// patch the chosen list in place.
			chosen = topDangerCards(hand, tc, me, recipient, true);
		}
		return withholdMoonFuel(chosen, hand, tc, me, recipient);
	}

	/**
	 * Keep back a blocker rather than handing the recipient everything they
	 * need to shoot the moon. Added 2026-08-07 after a hand was lost to
	 * exactly this: East passed South the King of Hearts, the Ace of Hearts
	 * AND the Queen of Spades. South already held the Queen of Hearts, so the
	 * pass completed the top three Hearts plus the Queen -- a moon-shot kit
	 * delivered intact -- and South shot it.
	 *
	 * Two separate failures let that through. The toolkit check above only
	 * recognises the ACE and KING of Spades as "Spade control", so passing
	 * the QUEEN of Spades alongside two high Hearts did not trip it. And
	 * dangerScore() ranks Hearts by height, so the highest Hearts are always
	 * the first ones passed -- precisely backwards, because the top Hearts
	 * are the only cards that can BREAK a moon shot.
	 *
	 * The fix is to price how much moon fuel a pass hands over and, when it
	 * is too much, hold back the highest Heart and send something harmless
	 * instead. Retaining a high Heart is cheap insurance: it is worth 1 point
	 * if it takes a trick, and it guarantees the ability to win a Heart trick
	 * and end any moon attempt. Preferring to keep Hearts over keeping the
	 * Queen of Spades is deliberate -- the Queen costs 13 to be stuck with,
	 * a Heart costs 1.
	 *
	 * Note this reasons ONLY about what is being given away, never about the
	 * recipient's hand, which a passer cannot see. That keeps it honest.
	 *
	 * REVISED 2026-10-10 (user rule, after whole-game measurement showed the
	 * hold-back costing games): the blocker does not have to be a kept-back
	 * high Heart. A Heart of 5 or lower SENT WITH the big cards is the
	 * blocker -- the receiver would have to win a trick with it, which is
	 * nearly impossible -- so "Ace and King of Hearts plus a low Heart", or
	 * "Queen of Spades, Ace or King of Hearts, plus a low Heart", may go.
	 * The pass must ALWAYS carry a blocker when it carries a kit (user:
	 * "you always have to pass a blocker"), whoever receives it -- the old
	 * far-behind-recipient exception is gone. Only with no low Heart to send
	 * does the old hold-back below still run.
	 */
	private static List<Card> withholdMoonFuel(List<Card> chosen, Hand hand, Table_Cards tc,
			Player me, PLAYER recipient) {
		// Strategy KIT_PASS (2026-10-11): FREE sends the kit as chosen;
		// HOLD_BACK skips the low-Heart blocker and goes straight to the
		// old hold-back below.
		if (me.uses(Strategy.KitPass.FREE)) {
			return chosen;
		}
		if (me.uses(Strategy.KitPass.BLOCKER)
				&& moonFuel(chosen) >= MOON_FUEL_LIMIT && includeLowHeartBlocker(chosen, hand)) {
			return chosen;
		}
		while (moonFuel(chosen) >= MOON_FUEL_LIMIT) {
			Card blocker = highestPassedHeart(chosen);
			if (blocker == null) {
				break; // nothing left worth holding back
			}
			Card replacement = bestHarmlessAlternative(hand, chosen, tc, me, recipient);
			if (replacement == null) {
				break; // no harmless card to send instead -- pass as-is
			}
			chosen.remove(blocker);
			chosen.add(replacement);
		}
		return chosen;
	}

	/** User rule (2026-10-10): a Heart of 5 or lower travelling with the
	 * big cards is itself the moon blocker -- the receiver would need to win
	 * a trick with it. Makes sure the pass carries one (swapping out the
	 * least moon-relevant card for my lowest such Heart). False if I have
	 * none to send. */
	private static boolean includeLowHeartBlocker(List<Card> chosen, Hand hand) {
		for (Card c : chosen) {
			if (isLowHeart(c)) {
				return true;
			}
		}
		Card low = null;
		for (Card c : hand.hand) {
			if (isLowHeart(c) && !chosen.contains(c) && (low == null || c.cardValue.compareTo(low.cardValue) < 0)) {
				low = c;
			}
		}
		if (low == null) {
			return false;
		}
		Card drop = null;
		for (Card c : chosen) {
			if (drop == null || moonFuel(c) < moonFuel(drop)
					|| (moonFuel(c) == moonFuel(drop) && c.cardValue.compareTo(drop.cardValue) < 0)) {
				drop = c;
			}
		}
		chosen.remove(drop);
		chosen.add(low);
		return true;
	}

	private static boolean isLowHeart(Card c) {
		return c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.FIVE) <= 0;
	}

	/** How much of a moon-shot kit these cards add up to. The Queen of Spades
	 * is required for a moon shot outright; the top Hearts are what actually
	 * win Heart tricks; Spade control lets a shooter hold the Queen until it
	 * suits them. */
	private static int moonFuel(List<Card> cards) {
		int fuel = 0;
		for (Card c : cards) {
			fuel += moonFuel(c);
		}
		return fuel;
	}

	private static int moonFuel(Card c) {
		if (Rules.isQueenOfSpades(c)) {
			return 5;
		}
		if (c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.QUEEN) >= 0) {
			return 4;
		}
		if (isSpadeControl(c)) {
			return 3;
		}
		return 0;
	}

	private static Card highestPassedHeart(List<Card> chosen) {
		Card best = null;
		for (Card c : chosen) {
			if (c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.QUEEN) >= 0
					&& (best == null || c.cardValue.compareTo(best.cardValue) > 0)) {
				best = c;
			}
		}
		return best;
	}

	/** The card most worth passing among those that carry no moon fuel at
	 * all, so swapping one in never re-creates the problem. */
	private static Card bestHarmlessAlternative(Hand hand, List<Card> chosen, Table_Cards tc,
			Player me, PLAYER recipient) {
		Card best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Card c : hand.hand) {
			if (chosen.contains(c) || moonFuel(c) > 0) {
				continue;
			}
			int score = dangerScore(c, hand, chosen, tc, me, recipient);
			if (best == null || score > bestScore) {
				best = c;
				bestScore = score;
			}
		}
		return best;
	}

	private static List<Card> topDangerCards(Hand hand, Table_Cards tc, Player me, PLAYER recipient, boolean excludeSpadeControl) {
		List<Card> pool = new ArrayList<Card>();
		for (Card c : hand.hand) {
			if (excludeSpadeControl && isSpadeControl(c)) {
				continue;
			}
			pool.add(c);
		}
		List<Card> chosen = new ArrayList<Card>();
		for (int pick = 0; pick < 3 && !pool.isEmpty(); pick++) {
			Card best = pool.get(0);
			int bestScore = dangerScore(best, hand, chosen, tc, me, recipient);
			for (Card c : pool) {
				int score = dangerScore(c, hand, chosen, tc, me, recipient);
				if (score > bestScore) {
					best = c;
					bestScore = score;
				}
			}
			chosen.add(best);
			pool.remove(best);
		}
		return chosen;
	}

	/** Higher means "more worth passing away." Queen of Spades and high
	 * Hearts dominate; the Jack of Diamonds is deliberately NOT treated like
	 * an ordinary danger card (see HandEvaluator.hasJackOfDiamondsProtection())
	 * -- protected, it's strongly kept; unprotected, it's only a mild,
	 * standings-sensitive candidate, since unlike Hearts/the Queen, holding
	 * it doesn't directly cost the holder anything. */
	private static int dangerScore(Card c, Hand fullHand, List<Card> alreadyChosen, Table_Cards tc, Player me, PLAYER recipient) {
		return baseDangerScore(c, fullHand, alreadyChosen, tc, me, recipient)
				- moonKitRetentionBias(c, me)
				+ paybackPassBias(c, fullHand, tc, me, recipient)
				+ signaturePassBias(c, me);
	}

	/** Signature passing (2026-11-05, decision 3 in docs/decisions_2026-11.md):
	 * Queen-phobic's PASS_HIGH_SPADES and Jack Hunter's KEEP_DIAMONDS. Zero
	 * for a player on the default (OFF), so ordinary passing is untouched.
	 * Sized in dangerScore() units (Hearts 8-20, an unprotected Queen 15-25):
	 * the Ace and King of Spades pass like a high Heart; the Ace, King and
	 * Queen of Diamonds are pushed well below anything worth passing. */
	static int signaturePassBias(Card c, Player me) {
		if (c.suit == Suit.SPADE && isSpadeControl(c) && Strategy.passesHighSpades(me)) {
			return SIGNATURE_PASS_HIGH_SPADE;
		}
		if (c.suit == Suit.DIAMOND && c.cardValue.compareTo(CardValue.QUEEN) >= 0 && Strategy.keepsJackWeapons(me)) {
			return -SIGNATURE_KEEP_DIAMOND;
		}
		return 0;
	}

	static final int SIGNATURE_PASS_HIGH_SPADE = 18;
	static final int SIGNATURE_KEEP_DIAMOND = 40;

	/**
	 * Queen payback on the passing side (2026-10-10, user design -- see
	 * Payback.java): pass so the grudge target is more likely to end up with
	 * the Queen. Zero for anyone without a live grudge, so ordinary passing is
	 * untouched.
	 *
	 *   Passing TO the target:
	 *     - the Queen goes to them -- but only when I'm NOT 1st or 2nd. From
	 *       up there the user's caution applies: they can drop her straight
	 *       back on me on an off-suit trick, and I have more to lose.
	 *     - the Ace/King of Spades go to them: high Spades are what win the
	 *       Spade trick the Queen lands on.
	 *     - low Spades stay home: they're the cover that lets a holder duck.
	 *   Passing to someone ELSE while a grudge is live:
	 *     - a protected Queen is kept, to drop on the target during play.
	 *
	 * Sized in dangerScore() units (Hearts 8-20, unprotected Queen 15-25) and
	 * scaled by the grudge strength, so a fading third-hand grudge only tips
	 * close calls.
	 */
	private static int paybackPassBias(Card c, Hand fullHand, Table_Cards tc, Player me, PLAYER recipient) {
		if (recipient == null || TacticWeights.weight(me, TacticWeights.Tactic.SOCIAL) == 0) {
			return 0;
		}
		Map<PLAYER, Double> targets = Payback.targets(tc, me);
		if (targets.isEmpty()) {
			return 0;
		}
		Double toTarget = targets.get(recipient);
		if (Rules.isQueenOfSpades(c)) {
			if (toTarget != null) {
				return Payback.inTopTwo(tc, me) ? 0 : (int) Math.round(PAYBACK_PASS_QUEEN * toTarget.doubleValue());
			}
			if (hasSpadeProtection(fullHand) && trustsQueenProtection(me)) {
				double strongest = 0;
				for (Double s : targets.values()) {
					strongest = Math.max(strongest, s.doubleValue());
				}
				return -(int) Math.round(PAYBACK_KEEP_QUEEN * strongest);
			}
			return 0;
		}
		if (toTarget == null || c.suit != Suit.SPADE) {
			return 0;
		}
		if (isSpadeControl(c)) {
			return (int) Math.round(PAYBACK_PASS_SPADE_CONTROL * toTarget.doubleValue());
		}
		return -(int) Math.round(PAYBACK_KEEP_LOW_SPADE * toTarget.doubleValue());
	}

	private static final int PAYBACK_PASS_QUEEN = 12;
	private static final int PAYBACK_KEEP_QUEEN = 10;
	private static final int PAYBACK_PASS_SPADE_CONTROL = 10;
	private static final int PAYBACK_KEEP_LOW_SPADE = 5;

	/**
	 * A Moon_Shooter-type seat's reluctance to pass away its own moon kit
	 * (2026-08-23, TODO.md's "Moon_Shooter keeps its own kit when not
	 * committed"). Zero for everybody else, and zero for a seat that HAS
	 * committed -- that seat never reaches defensivePass() at all, it went to
	 * offensivePass().
	 *
	 * WHY THIS SEAT AND NOT THE HAND. This only fires for a seat that looked
	 * at its pre-pass hand, decided a shot wasn't on, and is now passing
	 * defensively. Ordinary dangerScore() ranks exactly the moon kit -- the
	 * Queen, the top Hearts, Spade control -- as the FIRST cards to get rid
	 * of, which is right for a seat that will never shoot and precisely wrong
	 * for the one type whose whole identity is looking for the chance. Since
	 * Deal_Hand.reevaluateMoonShotAfterExchange() shipped (2026-08-16), a seat
	 * CAN newly commit once it sees its real thirteen cards -- so there is now
	 * a second look for a retained kit to be found by, which is what made this
	 * worth building. Before that it would have been pure liability with no
	 * upside path.
	 *
	 * DELIBERATELY MILD, and that is the design, not timidity. TODO.md's own
	 * analysis of this item is explicit that a non-committed seat which hoards
	 * the full kit and then STILL fails the second look is holding thirteen
	 * points of pure liability with nothing to show for it. So this is sized
	 * as a tie-breaking reluctance in dangerScore()'s own units (Hearts run
	 * 8-20, an unprotected Queen 15-25): enough to reorder genuinely close
	 * calls and to stop the kit being volunteered ahead of harmless filler,
	 * never enough to make a seat sit on the Queen of Spades when the pass is
	 * otherwise screaming to be rid of her. moonFuel() already scores exactly
	 * the right cards, so this reuses it rather than inventing a second,
	 * drifting definition of "moon kit".
	 */
	private static int moonKitRetentionBias(Card c, Player me) {
		if (!me.p_traits.contains(Player.Player_Traits.Shooting_the_Moon)) {
			return 0;
		}
		return MOON_KIT_RETENTION_PER_FUEL * moonFuel(c);
	}

	/** Per point of moonFuel() -- see moonKitRetentionBias() for the sizing
	 * argument. Queen of Spades 5 fuel -> 10, a top Heart 4 -> 8, Spade
	 * control 3 -> 6. */
	private static final int MOON_KIT_RETENTION_PER_FUEL = 2;

	private static int baseDangerScore(Card c, Hand fullHand, List<Card> alreadyChosen, Table_Cards tc, Player me, PLAYER recipient) {
		if (Rules.isQueenOfSpades(c)) {
			return queenOfSpadesPassScore(fullHand, me, recipient);
		}
		if (c.suit == Suit.HEART) {
			return heartPassScore(c, fullHand, alreadyChosen, me);
		}
		if (isSpadeControl(c)) {
			if (hasSpadeProtection(fullHand)) {
				return 0;
			}
			return spadeControlPassScore(tc, me, recipient);
		}
		if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
			return jackOfDiamondsPassScore(fullHand, tc, me, recipient);
		}
		if (c.suit == Suit.SPADE) {
			return lowSpadePassScore(fullHand, alreadyChosen);
		}
		return clubDiamondPassScore(c, fullHand, alreadyChosen, me);
	}

	/** A Heart in the pass, by the profile's LOW_HEARTS strategy (2026-10-11).
	 * Every option scores the Jack and up the same, 8 + rank (Ace 20). */
	private static int heartPassScore(Card c, Hand fullHand, List<Card> alreadyChosen, Player me) {
		int byRank = c.cardValue.compareTo(CardValue.TWO);
		if (me.uses(Strategy.LowHearts.KEEP_TWO)) {
			if (isDuckingHeart(c) && duckingHeartsLeft(fullHand, alreadyChosen, c) < DUCKING_HEART_FLOOR) {
				return -5; // below the harmless-filler baseline -- keep it to duck with
			}
		} else if (me.uses(Strategy.LowHearts.BY_HEIGHT)) {
			if (c.cardValue.compareTo(CardValue.JACK) < 0) {
				return byRank; // 2 = 0 ... 10 = 8: a high Club goes first
			}
		} else if (me.uses(Strategy.LowHearts.KEEP_LOW)) {
			if (isLowHeart(c)) {
				return -2; // 2-5 kept as blockers
			}
		}
		return 8 + byRank;
	}

	/**
	 * A Club or Diamond (the Jack of Diamonds is handled above). Reworked
	 * 2026-10-10 after whole-game measurement showed the old flat score --
	 * every card 1, or 5 in a suit of two or fewer -- costing games: an Ace
	 * of Clubs was no more worth passing than the 3, so the AI kept the high
	 * cards that later win point-laden tricks.
	 *
	 *   - HEIGHT: high cards are worth passing (Ace/King 6 ... Two 0), except
	 *     the Ace/King of Diamonds while I hold the Jack -- they're how I win
	 *     it.
	 *   - VOID (user rule): short on Spades -- three or fewer below the Queen
	 *     -- emptying a suit is a big bonus. The Queen may arrive in the pass
	 *     or be stuck with me, and a void is where she gets dumped the first
	 *     time that suit is led. Counts the cards already picked, so a three-
	 *     card suit can be emptied by the whole pass.
	 *
	 * Measured (one seat vs three old-rule Normals, 2400 whole games): void
	 * alone 30.8% won vs 24.8%, height alone 27.1%, all four 2026-10-10
	 * changes together 38-40%.
	 *
	 * Both are strategy components since 2026-10-11: SIDE_SUITS FLAT drops
	 * the height (the old rule), VOID_HUNT NEVER drops the void bonus.
	 */
	private static int clubDiamondPassScore(Card c, Hand fullHand, List<Card> alreadyChosen, Player me) {
		int score = countOfSuit(fullHand, c.suit) <= 2 ? 5 : 1;
		boolean jackWeapon = c.suit == Suit.DIAMOND && c.cardValue.compareTo(CardValue.KING) >= 0
				&& CardCounter.holds(fullHand.hand, CardValue.JACK, Suit.DIAMOND);
		if (jackWeapon) {
			// Below every filler card (and a spare low Spade at 0): with the
			// Jack in hand these are how I win it. 2026-10-11 -- before the
			// two-ducking-Hearts floor the low Hearts always outbid them, so
			// sharing filler's score of 1 was enough; it no longer is.
			return -1;
		}
		if (me.uses(Strategy.SideSuits.BY_HEIGHT)) {
			score = Math.max(score, (c.cardValue.compareTo(CardValue.TWO) + 1) / 2);
		}
		if (me.uses(Strategy.VoidHunt.WHEN_SHORT_ON_SPADES) && spadesBelowQueen(fullHand) <= SHORT_SPADES) {
			int left = countOfSuit(fullHand, c.suit) - countOfSuitIn(alreadyChosen, c.suit);
			int picksLeft = 3 - (alreadyChosen == null ? 0 : alreadyChosen.size());
			if (left <= picksLeft) {
				score += VOID_BONUS;
			}
		}
		return score;
	}

	/**
	 * User rule (2026-10-11): never pass yourself out of low Hearts. The
	 * object is to take no Hearts, and low Hearts are what let a seat duck a
	 * Heart lead. Strip them -- say passing 9-3-2 of Hearts -- then receive a
	 * high Heart, and the first Heart lead forces that seat to win the trick,
	 * maybe four Hearts at once. So a Heart below the 10 is not passed if it
	 * would leave fewer than two of them in hand; one can still go (the
	 * moon-kit blocker in includeLowHeartBlocker() runs after this and is
	 * unaffected).
	 *
	 * Measured (one seat vs three Normals without it, 2400 whole games):
	 * 33.6% won vs 26.1%, average final 53.2 vs 61.2. Re-scoring every Heart
	 * below the Jack by height was also tried: 31.9% alone, and no further
	 * gain on top of the floor, so it was left out.
	 */
	private static boolean isDuckingHeart(Card c) {
		return c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.TEN) < 0;
	}

	private static int duckingHeartsLeft(Hand fullHand, List<Card> alreadyChosen, Card passing) {
		int n = 0;
		for (Card h : fullHand.hand) {
			if (h != passing && isDuckingHeart(h) && (alreadyChosen == null || !alreadyChosen.contains(h))) {
				n++;
			}
		}
		return n;
	}

	/** See isDuckingHeart(). */
	private static final int DUCKING_HEART_FLOOR = 2;

	/** See clubDiamondPassScore(). */
	private static final int SHORT_SPADES = 3;
	private static final int VOID_BONUS = 12;

	private static int spadesBelowQueen(Hand hand) {
		int n = 0;
		for (Card c : hand.hand) {
			if (c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.QUEEN) < 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * How eager this seat should be to pass the Jack of Diamonds away.
	 * Higher means "pass it"; negative means "keep it and chase the -10".
	 * Reworked 2026-08-07.
	 *
	 * The Jack is unlike every other card in the danger list: holding it
	 * costs nothing, and capturing it PAYS 10. So the question is never "is
	 * this dangerous" but "can I realistically win it, and if not, who is
	 * least harmful to hand it to".
	 *
	 * Three inputs:
	 *
	 * 1. Can I protect it? HandEvaluator.hasJackOfDiamondsProtection() -- the
	 *    Ace/King of Diamonds to control the suit, or enough low Diamonds to
	 *    duck behind until they're gone. If so, keep it.
	 *
	 * 2. How thin is my Diamond holding? A bare Jack, or a Jack with one
	 *    other Diamond, is close to hopeless: the suit gets led, I have to
	 *    follow, and the Jack goes to whoever holds the high Diamonds. It's
	 *    worth far more to somebody who can actually protect it, so pass it.
	 *
	 * 3. Who receives it? The pass direction is fixed, so this is not a
	 *    choice of recipient -- only of whether to hand THIS recipient a
	 *    10-point gift. Giving it to the seat in last place costs little,
	 *    since they are the least likely to convert it into a win. Giving it
	 *    to the leader actively helps the player already ahead, which is
	 *    worth avoiding even at the cost of holding a Jack I probably can't
	 *    win.
	 */
	private static int jackOfDiamondsPassScore(Hand fullHand, Table_Cards tc, Player me, PLAYER recipient) {
		if (HandEvaluator.hasJackOfDiamondsProtection(fullHand)) {
			return -10; // strongly prefer to KEEP -- chase the -10 themselves
		}

		int diamonds = countOfSuit(fullHand, Suit.DIAMOND);
		// Behind the field, an unprotected Jack is still worth a long shot;
		// level or ahead, shed the variance instead.
		int score = Scoring.standingDelta(tc, me.tbl_player) > 0 ? 2 : 6;

		if (diamonds <= 2) {
			// A bare or near-bare Jack can't be defended -- this is the case
			// where passing it is clearly right rather than merely tempting.
			score += 8;
		}

		PLAYER leader = seatInFirstPlace(tc);
		PLAYER trailer = seatInLastPlace(tc);
		// Only meaningful once the scores have actually spread out. Level
		// totals (a fresh game) make "leader" and "trailer" the same
		// arbitrary seat, and adjusting on that would be noise.
		if (recipient != null && leader != trailer) {
			if (recipient == leader) {
				// Decisive, not a nudge: handing the Jack to whoever is
				// already winning is worth avoiding even though I probably
				// cannot win it myself. A mere penalty was not enough -- an
				// unwinnable Jack still outscored harmless filler and got
				// passed to the leader anyway.
				return -10;
			}
			if (recipient == trailer) {
				score += 6; // harmless home for a Jack I can't win myself
			}
		}
		return score;
	}

	/**
	 * Does this seat trust Spade protection enough to KEEP a protected Queen
	 * of Spades (2026-08-16, TODO.md's "passing-craft per-type nuances":
	 * "Avoids_Queen should up-weight the Queen-specific passing rules
	 * specifically")? Everyone but an Avoids_Queen seat does.
	 *
	 * This is the one lever on the passing side that actually changes an
	 * Avoids_Queen seat's behaviour, and finding that took a diagnostic
	 * pass worth recording. The obvious approach -- scale the Queen-shaped
	 * dangerScore() branches by a 1.5 emphasis, mirroring the type's
	 * QUEEN_FLUSH weight in TacticWeights -- was built first and proved to
	 * be a complete no-op on every hand tried. The reason is structural,
	 * not a tuning miss: an UNPROTECTED Queen already scores 15-25, at or
	 * above the highest Heart (the Ace, at 8 + 12 = 20), so she is already
	 * first pick and multiplying a first-place score cannot re-rank
	 * anything. Downstream, defensivePass()'s toolkit-exclusion rule and
	 * withholdMoonFuel() then reshape the chosen three anyway, absorbing
	 * what little the emphasis did do. A multiplier was simply the wrong
	 * mechanism.
	 *
	 * What genuinely separates the type is the PROTECTED Queen, the one
	 * case queenOfSpadesPassScore() scores at 0 ("safe to hold, keep and
	 * control her") unless the pass direction is ideal. That 0 is exactly
	 * what a 1.5 multiplier cannot touch. An Avoids_Queen seat by
	 * definition doesn't believe in holding her at all, so it declines to
	 * count protection, drops through to the ordinary unprotected branches,
	 * and ships her -- still preferring the safe direction, just no longer
	 * requiring it. Package-private for AvoidsQueenPassTest.
	 */
	static boolean trustsQueenProtection(Player me) {
		return !me.p_traits.contains(Player.Player_Traits.Avoids_Queen);
	}

	/** Worst running total -- in Hearts the HIGHEST score is last place. Ties
	 * resolve to the first seat found, which is good enough for a tie-break
	 * nobody can observe. */
	private static PLAYER seatInLastPlace(Table_Cards tc) {
		PLAYER worst = null;
		for (PLAYER p : PLAYER.values()) {
			Player player = tc.players.get(p);
			if (player == null) {
				continue;
			}
			if (worst == null || player.p_total_points > tc.players.get(worst).p_total_points) {
				worst = p;
			}
		}
		return worst;
	}

	/** Best running total -- the LOWEST score leads in Hearts. */
	private static PLAYER seatInFirstPlace(Table_Cards tc) {
		PLAYER best = null;
		for (PLAYER p : PLAYER.values()) {
			Player player = tc.players.get(p);
			if (player == null) {
				continue;
			}
			if (best == null || player.p_total_points < tc.players.get(best).p_total_points) {
				best = p;
			}
		}
		return best;
	}

	/** Second-best running total -- the runner-up, added 2026-08-09 for
	 * standings-aware passing (see spadeControlPassScore()). Simple second
	 * pass: find the leader, then find the best of everyone else. With only
	 * 4 seats this is cheap and reads more plainly than a full sort. */
	private static PLAYER seatInSecondPlace(Table_Cards tc) {
		PLAYER leader = seatInFirstPlace(tc);
		PLAYER best = null;
		for (PLAYER p : PLAYER.values()) {
			Player player = tc.players.get(p);
			if (player == null || p == leader) {
				continue;
			}
			if (best == null || player.p_total_points < tc.players.get(best).p_total_points) {
				best = p;
			}
		}
		return best;
	}

	/**
	 * How eager this seat should be to pass the Queen of Spades. Reworked
	 * 2026-08-07 to account for WHERE it lands in the play order.
	 *
	 * Seats act in a fixed cycle, so the recipient's position relative to the
	 * passer is the same in every trick:
	 *
	 *   - To the RIGHT (they play immediately BEFORE me): the safest place to
	 *     send it. Knowing they hold the Queen, and seeing their card before
	 *     committing mine, means they can never drop it on a high Spade I
	 *     have already played -- and once they show a Spade that isn't the
	 *     Queen, I can shed a King or Ace behind them.
	 *   - To the LEFT (they play immediately AFTER me): the worst place. They
	 *     can wait until my Ace or King is winning the trick and drop the
	 *     Queen straight onto it.
	 *   - ACROSS: neither, so no adjustment.
	 *
	 * The positional preference is what decides a PROTECTED Queen, which is
	 * otherwise a genuine toss-up -- with four or more low Spades to duck
	 * behind it is safe to keep and control, so the tie is broken by whether
	 * it can be sent somewhere it cannot come back at me. An UNPROTECTED
	 * Queen is 13 points of live risk and still goes regardless of direction;
	 * position only nudges the urgency there.
	 */
	private static int queenOfSpadesPassScore(Hand fullHand, Player me, PLAYER recipient) {
		// An Avoids_Queen seat never counts protection here -- see
		// trustsQueenProtection() for why this, rather than a score
		// multiplier, is what actually distinguishes the type.
		boolean protectedQueen = queenProtectedFor(fullHand, me) && trustsQueenProtection(me);
		if (recipient == null) {
			return protectedQueen ? 0 : 20;
		}
		boolean playsAfterMe = recipient == me.tbl_player.Next_Dealer();
		boolean playsBeforeMe = recipient == me.tbl_player.Next_Dealer().Next_Dealer().Next_Dealer();

		if (protectedQueen) {
			// Safe to hold, so only worth passing when it can be sent
			// somewhere it can never be played back at me.
			return playsBeforeMe ? 22 : 0;
		}
		if (playsBeforeMe) {
			return 25; // unprotected AND the safe direction -- send it
		}
		if (playsAfterMe) {
			return 15; // still going, but the least attractive direction
		}
		return 20;
	}

	/**
	 * How willing this seat should be to pass a LOW Spade (the Queen and the
	 * Ace/King are handled before this). Added 2026-08-07.
	 *
	 * Low Spades are armour, not clutter. They are what lets a player follow
	 * a Spade lead without winning it, and the whole reason a Queen can be
	 * held safely. Passing them away is dangerous in a way passing low cards
	 * of any other suit is not, because of what might arrive in return: the
	 * three cards coming back are unknown, and can perfectly well include the
	 * Queen, King or Ace of Spades. Strip the low Spades, receive a high one,
	 * and there is nothing left to duck behind -- the seat is now forced to
	 * win a Spade trick and the Queen lands on it.
	 *
	 * This corrects a real hazard in the old scoring, which had no Spade case
	 * at all: low Spades fell through to the generic "two or fewer of this
	 * suit, good candidate to complete a void" rule and were actively
	 * REWARDED for going short. Voiding a suit is normally excellent in
	 * Hearts; voiding Spades specifically is how a player ends up eating the
	 * Queen.
	 *
	 * `alreadyChosen` matters because the three picks are made one at a time
	 * against the same starting hand -- without it, each low Spade would be
	 * judged as though none of the others had been passed, and a hand could
	 * still be stripped three at a time.
	 *
	 * A seat going for the moon never reaches this: offensivePass() has its
	 * own logic and deliberately keeps Spade control.
	 */
	private static int lowSpadePassScore(Hand fullHand, List<Card> alreadyChosen) {
		int spadesLeft = countOfSuit(fullHand, Suit.SPADE) - countOfSuitIn(alreadyChosen, Suit.SPADE) - 1;
		if (spadesLeft < SAFE_SPADE_FLOOR) {
			return -5; // below the harmless-filler baseline -- actively hold on to it
		}
		return 0; // plenty spare, but still not a card worth spending a pass on
	}

	private static int countOfSuitIn(List<Card> cards, Suit suit) {
		int count = 0;
		if (cards == null) {
			return 0;
		}
		for (Card c : cards) {
			if (c.suit == suit) {
				count++;
			}
		}
		return count;
	}

	private static boolean hasSpadeProtection(Hand hand) {
		int lowSpades = 0;
		for (Card c : hand.hand) {
			if (c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.TEN) < 0) {
				lowSpades++;
			}
		}
		return lowSpades >= 4;
	}

	/** Enough low Spades to keep the Queen, by the profile's QUEEN_KEEP
	 * strategy (2026-10-11): 4 (the default, = hasSpadeProtection), 5, or
	 * never. */
	private static boolean queenProtectedFor(Hand hand, Player me) {
		if (me.uses(Strategy.QueenKeep.NEVER)) {
			return false;
		}
		if (me.uses(Strategy.QueenKeep.FIVE_LOW_SPADES)) {
			return lowSpadeCount(hand) >= 5;
		}
		return hasSpadeProtection(hand);
	}

	private static int lowSpadeCount(Hand hand) {
		int lowSpades = 0;
		for (Card c : hand.hand) {
			if (c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.TEN) < 0) {
				lowSpades++;
			}
		}
		return lowSpades;
	}

	private static boolean isSpadeControl(Card c) {
		return c.suit == Suit.SPADE && (c.cardValue == CardValue.ACE || c.cardValue == CardValue.KING);
	}

	/** How eager to pass an UNPROTECTED Ace/King of Spades, standings-aware.
	 *
	 * REVISED 2026-10-11 (user rule). The 2026-08-09 version was reluctant to
	 * pass these to the leader or runner-up (4, vs 12 to anyone else),
	 * treating them as tools that strengthen the receiver. That's backwards
	 * for a seat that isn't shooting: an Ace or King of Spades is how a seat
	 * gets stuck catching the Queen. And a leader plays defensively and
	 * rarely tries the moon, so handing them Spade control mostly hands them
	 * a 13-point risk. So the leader is now the BEST home for them (16), the
	 * runner-up next (14), everyone else 12. Same early-game guard as the
	 * Jack rule: no adjustment while scores haven't spread out.
	 *
	 * Measured (one seat vs three Normals, 2400 whole games): 26.7% won vs
	 * 26.1% -- within noise. The case is rare (protected Spades score 0
	 * before this, and defensivePass() drops Spade control from any pass
	 * carrying Hearts or the Queen), so it seldom decides a pass.
	 *
	 * Strategy SPADE_CONTROL (2026-10-11) keeps the alternatives: ANYONE (12
	 * to everybody) and SHIELD_LEADER (the 2026-08-09 rule, restored below).
	 */
	private static int spadeControlPassScore(Table_Cards tc, Player me, PLAYER recipient) {
		if (me.uses(Strategy.SpadeControl.ANYONE)) {
			return 12;
		}
		if (me.uses(Strategy.SpadeControl.SHIELD_LEADER)) {
			return shieldLeaderSpadeControlScore(tc, me, recipient);
		}
		PLAYER leader = seatInFirstPlace(tc);
		if (recipient == null || leader == seatInLastPlace(tc)) {
			return 12; // no recipient, or scores haven't spread out yet
		}
		if (recipient == leader) {
			return 16;
		}
		if (recipient == seatInSecondPlace(tc)) {
			return 14;
		}
		return 12;
	}

	/** SpadeControl.SHIELD_LEADER -- the 2026-08-09 rule: reluctant (4) to
	 * hand Spade control to the leader, the runner-up, or (when I'm leading)
	 * my closest rival, treating it as strength for the receiver; relaxed
	 * (14) toward last place; 12 otherwise. */
	private static int shieldLeaderSpadeControlScore(Table_Cards tc, Player me, PLAYER recipient) {
		if (recipient == null) {
			return 12;
		}
		PLAYER leader = seatInFirstPlace(tc);
		PLAYER trailer = seatInLastPlace(tc);
		if (leader == trailer) {
			return 12; // scores haven't spread out yet
		}
		if (recipient == leader || recipient == seatInSecondPlace(tc)
				|| (me.tbl_player == leader && recipient == closestRivalTo(tc, me))) {
			return 4;
		}
		return recipient == trailer ? 14 : 12;
	}

	/** The seat (excluding `me`) closest to `me` in running total. Ties
	 * resolve to the first seat found. */
	private static PLAYER closestRivalTo(Table_Cards tc, Player me) {
		PLAYER closest = null;
		int smallestGap = Integer.MAX_VALUE;
		for (PLAYER p : PLAYER.values()) {
			if (p == me.tbl_player) {
				continue;
			}
			Player player = tc.players.get(p);
			if (player == null) {
				continue;
			}
			int gap = Math.abs(player.p_total_points - me.p_total_points);
			if (gap < smallestGap) {
				smallestGap = gap;
				closest = p;
			}
		}
		return closest;
	}

	private static int countOfSuit(Hand hand, Suit suit) {
		int count = 0;
		for (Card c : hand.hand) {
			if (c.suit == suit) {
				count++;
			}
		}
		return count;
	}

	private static Suit longestSuit(Hand hand) {
		Suit best = hand.hand.get(0).suit;
		int bestCount = 0;
		for (Suit s : Suit.values()) {
			int count = countOfSuit(hand, s);
			if (count > bestCount) {
				bestCount = count;
				best = s;
			}
		}
		return best;
	}
}
