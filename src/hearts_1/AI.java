package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Traits;

/**
 * Computer-player card selection, added 2026-07-30. Sits on top of
 * Rules.java's legal-move filtering -- Rules decides WHAT'S ALLOWED, AI
 * decides WHICH of the allowed cards to actually play. Reads
 * Player.p_traits to give each computer seat a genuinely different playing
 * style; those traits were already being assigned per-seat at deal-setup but
 * nothing ever read them before now. As of 2026-08-06 each seat's type is
 * chosen from a dropdown in Hearts_JFrame (see Player.Player_Type) and
 * reloaded at the start of every deal -- previously North was hardcoded
 * Brain_Dead and East hardcoded Bad, so West was the only seat that ever
 * reached any of the strategy below.
 *
 * The baseline ("Normal"/"Cautious") strategy is a standard, defensible
 * simple-Hearts heuristic:
 *   - Leading: prefer low cards from long suits (voiding a suit early pays
 *     off later -- once void you can freely dump danger whenever it's led),
 *     graded against per-card penalties for volunteering Hearts, the Queen
 *     of Spades, an uncaptured-Queen-era King/Ace of Spades, a suit a known
 *     void seat can dump on, or a protected Diamond. Since 2026-08-07 this
 *     is scored per CARD in AICardScorer.java -- there is no "pick a suit
 *     first" step anymore, see the lead-scorer block there for why.
 *   - Following, and you CAN'T win the trick regardless: dump your highest
 *     card of the required suit -- shedding danger for free.
 *   - Following, and you COULD win: play the highest card that still loses
 *     ("duck" as high as possible without taking the trick), or if every
 *     legal card would win, take it as cheaply as possible (lowest card).
 *   - Void in the lead suit (free discard): shed the Queen of Spades first,
 *     then the highest Heart, then just the highest card overall.
 *
 * Traits layered on top of that baseline:
 *   - Brain_Dead: ignores strategy entirely, always plays the first legal
 *     option (deliberately the "dumbest" tier).
 *   - Bad: uses the baseline, but has a 1-in-3 chance of playing a random
 *     legal card instead (occasionally makes a real mistake).
 *   - Aggressive: willing to fight for tricks -- plays to win when
 *     following suit instead of ducking (unless a seat that hasn't played
 *     yet this trick is already known void in the led suit -- see the
 *     2026-07-31 update below), and leads with its highest card instead of
 *     its lowest. Still avoids the Queen of Spades/hearts when void, same
 *     as everyone else -- "aggressive" means trick-hungry, not
 *     self-destructive (that's Shooting_the_Moon, deliberately not
 *     implemented here, see below).
 *   - Avoids_Queen: extra eager to shed the Queen of Spades -- will play it
 *     even while "ducking" a Spade-led trick (instead of the usual
 *     highest-safe-card choice) whenever doing so is still safe, rather
 *     than waiting for a void discard opportunity.
 *
 * Deliberately NOT implemented yet, and why: Prevents_Shooter, Vengeful,
 * Last_Place, First_Place, Dave, Payback_p1/2/3, Need_to_Stop_Shooter,
 * Need_to_Shoot, and Shooting_the_Moon all depend on game state that
 * doesn't exist yet -- a running score, who's ahead/behind, or a real
 * trick-winner history to actually deduce/count from. Wiring those in now
 * would mean building strategy on a foundation (scoring, correct
 * trick-winner determination -- still a known gap, see Play_Hand.java's
 * commit history) that isn't there yet. Cautious is folded into the same
 * path as Normal for the same reason: there's no meaningfully different,
 * well-justified "more cautious than the safe baseline" behavior available
 * without score-awareness to react to.
 *
 * Updated 2026-07-31: the baseline lead strategy is now void-aware, via
 * VoidTracker.java -- avoids leading a suit any OTHER seat is already known
 * void in (their free discard could load the trick with the Queen of
 * Spades or a Heart), falling back to the plain longest-suit choice only
 * if every suit present is equally risky. This deliberately is NOT gated
 * behind Counts_cards -- avoiding an obviously-known void is basic
 * competency (the same category as not leading a Heart before it's broken),
 * not an advanced skill worth a trait distinction. Counts_cards is left
 * reserved for a future, genuinely more sophisticated card-counting
 * enhancement (e.g. reasoning about likely hand compositions, or driving a
 * determinization/simulation layer -- see the project's "deep dive"
 * discussion on game-playing AI architecture), not this baseline deduction.
 *
 * Updated 2026-07-31 (same day): the follow-side duck-vs-take decision is
 * also void-aware now. Aggressive normally fights for any trick it can win,
 * but backs off to the usual duck logic if a seat that hasn't played yet
 * this trick is already known void in the led suit -- winning means
 * collecting whatever they freely discard too, which could be the Queen of
 * Spades or a Heart. This only changes Aggressive's behavior: everyone else
 * already ducks whenever legally possible, so there's no "more cautious"
 * state left for void-awareness to add for them.
 *
 * Updated 2026-07-31 (same day): Player.attempting_moon_shot -- decided once
 * during passing (Deal_Hand.java/HandEvaluator.java), not re-rolled per
 * trick -- now overrides BOTH chooseLead() and chooseFollow() for whichever
 * seat committed to it, ahead of Aggressive and the baseline strategy (but
 * still after Brain_Dead/Bad, which are about competence, not strategy).
 * Leading: run the top of the longest suit repeatedly to sustain control,
 * rather than the plain highest card, which could be a lone Ace that wins
 * once and then has nothing left to lead. Following: take every Hearts/Queen
 * trick unconditionally when possible, and when void, never discard the Queen
 * or a Heart -- giving it away as a free discard hands it to whoever wins
 * instead of preserving it to capture later, breaking the attempt outright.
 *
 * RESTRUCTURED 2026-08-06: everything described above is still exactly what
 * the AI does, but it is no longer expressed as an if/else chain inside
 * chooseLead()/chooseFollow(). Each strategy is now an independent scorer in
 * AICardScorer.java; every legal card is scored by every scorer and the
 * highest total wins. This class keeps the two public entry points, the
 * Brain_Dead/Bad competence exits (which deliberately bypass strategy rather
 * than participate in it), and all the game-state predicates the scorers read
 * -- so there is still exactly one definition of "is a later seat void in the
 * led suit", "is this a protected Jack-of-Diamonds asset", and so on. The
 * change was verified equivalent against the old logic across 400,000
 * randomly generated decisions before the old code was removed; see
 * hearts.md section 8. Add a new consideration by adding a scorer, not by
 * finding the right place in a branch chain.
 */
public final class AI {

	private AI() { }

	// Package-private (was private) -- Deal_Hand.java's per-hand
	// counts-cards roll (2026-08-16) shares this same source rather than
	// spinning up a second Random.
	static final Random RNG = new Random();

	/** Separate source for the per-hand moonHeartLast roll (2026-11-08, Wayne:
	 * "so testing is testing apples to apples"). Drawing it from RNG shifted
	 * every later random number, so changing the setting changed the other
	 * rolls (passes, blunders, moon looks). Seeded alongside RNG by the simulator. */
	static final Random HEART_LAST_RNG = new Random();

	/** Seeds the side roll sources for a seeded game or hand. */
	static void seedRolls(long seed) {
		HEART_LAST_RNG.setSeed(seed ^ 0x5DEECE66DL);
	}

	/**
	 * Refactored 2026-08-06 to the scored-candidate system (see
	 * AICardScorer.java). Brain_Dead and Bad stay ahead of it deliberately:
	 * they aren't strategic preferences that should be weighed against other
	 * concerns, they're competence tiers that bypass strategy altogether, so
	 * expressing them as scores would be dishonest about what they model.
	 * Everything after them is now a sum of independent scorers rather than
	 * a first-match-wins branch chain -- legacyChooseLead() below preserves
	 * the pre-refactor logic so the two can be compared directly.
	 */
	public static Card chooseLead(List<Card> legalOptions, Table_Cards tc, Player player) {
		return chooseLead(legalOptions, null, tc, player);
	}

	/** Same, with this seat's full remaining hand (2026-10-08) -- the endgame
	 * solver needs it, since before Hearts are broken legalOptions hides every
	 * Heart held. See AICardScorer.pickLead(List, Hand, ...). */
	public static Card chooseLead(List<Card> legalOptions, Hand myHand, Table_Cards tc, Player player) {
		if (player.p_traits.contains(Player_Traits.Brain_Dead)) {
			return legalOptions.get(0);
		}
		if (player.p_traits.contains(Player_Traits.Bad) && RNG.nextInt(3) == 0) {
			return legalOptions.get(RNG.nextInt(legalOptions.size()));
		}
		if (blundersNow(player, legalOptions)) {
			List<Card> ranked = AICardScorer.rankLead(legalOptions, myHand, tc, player);
			final Card best = ranked.get(0);
			final List<Card> mine = myHand != null ? myHand.hand : legalOptions;
			final Table_Cards table = tc;
			// The first test is always the runner-up the dice picked, so it
			// alone says whether the veto changed this blunder.
			final boolean[] first = { true };
			return nearMiss(ranked, player, c -> {
				boolean vetoed = isSuicidalLead(c, best, mine, table, player);
				if (first[0]) {
					first[0] = false;
					if (vetoed && leadVetoListener != null) {
						leadVetoListener.accept(player);
					}
				}
				return vetoed;
			});
		}
		return AICardScorer.pickLead(legalOptions, myHand, tc, player);
	}

	/**
	 * Refactored 2026-08-06 -- see chooseLead() above. legacyChooseFollow()
	 * preserves the pre-refactor logic for comparison.
	 */
	public static Card chooseFollow(List<Card> legalOptions, Hand myHand, Table_Cards tc, int playPosition, Player player) {
		if (player.p_traits.contains(Player_Traits.Brain_Dead)) {
			return legalOptions.get(0);
		}
		if (player.p_traits.contains(Player_Traits.Bad) && RNG.nextInt(3) == 0) {
			return legalOptions.get(RNG.nextInt(legalOptions.size()));
		}
		if (blundersNow(player, legalOptions)) {
			final int position = playPosition;
			final Table_Cards table = tc;
			List<Card> ranked = AICardScorer.rankFollow(legalOptions, myHand, tc, playPosition, player);
			final Card best = ranked.get(0);
			final int limit = player.profile.isExpert() ? EXPERT_BLUNDER_COST : SUICIDAL_BLUNDER_COST;
			return nearMiss(ranked, player, c -> isSuicidalFollow(c, best, table, position, limit));
		}
		return AICardScorer.pickFollow(legalOptions, myHand, tc, playPosition, player);
	}

	/** The per-card blunder roll (2026-10-10, AI players step 2 -- see
	 * Profile.blunderPercent). No dice at all for a 0 rate or a forced
	 * play, so seeded games without blunders replay exactly as before. */
	private static boolean blundersNow(Player player, List<Card> legalOptions) {
		int pct = player.profile.effectiveBlunderPercent();
		return pct > 0 && legalOptions.size() > 1 && RNG.nextInt(100) < pct;
	}

	/** A human mistake, not a wild one (user pick): the 2nd best card, or
	 * the 3rd best one time in three when there is a 3rd. */
	static Card nearMiss(List<Card> ranked, Player player) {
		return nearMiss(ranked, player, c -> false);
	}

	/** Same, but a blunder may never be a suicidal play (user rule, 2026-10-17:
	 * a mistake is fine, eating the Queen on purpose-by-accident is not).
	 * `suicidal` vetoes a candidate; the dice roll is unchanged, then the
	 * chosen runner-up -- or, if it is vetoed, the next allowed runner-up -- is
	 * played. With none allowed the best card is played and the blunder is
	 * cancelled. */
	static Card nearMiss(List<Card> ranked, Player player, java.util.function.Predicate<Card> suicidal) {
		int pick = ranked.size() >= 3 && RNG.nextInt(3) == 0 ? 2 : 1;
		if (player.profile.isExpert()) {
			pick = 1; // an expert's slip is only ever its 2nd-best card (2026-10-17)
		}
		if (suicidal.test(ranked.get(pick))) {
			pick = -1;
			for (int i = 1; i < ranked.size(); i++) {
				if (!suicidal.test(ranked.get(i))) {
					pick = i;
					break;
				}
			}
			if (pick < 0) {
				System.out.println("****** BLUNDER " + player.tbl_player + " avoided: every runner-up was suicidal ******");
				return ranked.get(0);
			}
		}
		Card c = ranked.get(pick);
		System.out.println("****** BLUNDER " + player.tbl_player + " played choice #" + (pick + 1) + " "
				+ c.cardValue + "-" + c.suit + " instead of " + ranked.get(0).cardValue + "-" + ranked.get(0).suit
				+ " ******");
		return c;
	}

	/** Penalty points that landing on the winner of this trick, counting every
	 * card already played: Hearts 1, Queen of Spades 13, Jack of Diamonds -10. */
	static int trickPointValue(Card c) {
		if (c == null) {
			return 0;
		}
		if (Rules.isQueenOfSpades(c)) {
			return 13;
		}
		if (c.suit == Suit.HEART) {
			return 1;
		}
		if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
			return -10;
		}
		return 0;
	}

	/** True when playing `candidate` into this trick is a suicidal blunder
	 * compared with `best` (the card the AI really wanted): either it plays the
	 * Queen of Spades onto a Spade trick where she is currently the winning card
	 * (any seat), or, as the LAST player, it takes the trick and costs 10 or
	 * more points more than `best` does. Leads: see isSuicidalLead. */
	/** Point swing (last seat) that makes a blunder unacceptable: anyone 10+,
	 * experts 4+ (2026-10-17, user rule -- an expert's slip stays small). */
	static final int SUICIDAL_BLUNDER_COST = 10;
	static final int EXPERT_BLUNDER_COST = 4;

	static boolean isSuicidalFollow(Card candidate, Card best, Table_Cards tc, int playPosition) {
		return isSuicidalFollow(candidate, best, tc, playPosition, SUICIDAL_BLUNDER_COST);
	}

	static boolean isSuicidalFollow(Card candidate, Card best, Table_Cards tc, int playPosition, int limit) {
		Suit lead = tc.p1_card.suit;
		Card top = highestOfSuitOnTable(tc, lead, playPosition);
		if (Rules.isQueenOfSpades(candidate) && lead == Suit.SPADE
				&& (top == null || candidate.cardValue.compareTo(top.cardValue) > 0)) {
			return true;
		}
		// Never blunder the Jack of Diamonds away to another seat (2026-11-09,
		// Wayne: a gift of -10 to whoever wins it, maybe the leader): a discard
		// of her, or her played under a higher Diamond already on the table.
		if (isJackOfDiamonds(candidate) && !isJackOfDiamonds(best)
				&& (lead != Suit.DIAMOND || (top != null && top.cardValue.compareTo(candidate.cardValue) > 0))) {
			return true;
		}
		if (playPosition == 4) {
			return lastSeatCost(candidate, tc, top, lead) - lastSeatCost(best, tc, top, lead) >= limit;
		}
		return false;
	}

	private static boolean isJackOfDiamonds(Card c) {
		return c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK;
	}

	/** Measurement hook (2026-11-02): called when the lead guard vetoes the
	 * runner-up a blunder picked. Null in the game. */
	static java.util.function.Consumer<Player> leadVetoListener = null;

	/** True when leading `candidate` is a suicidal blunder compared with `best`
	 * (2026-11-02, extends the user rule above to leads). Public information
	 * only: cards already played and the player's own cards (`mine`).
	 *   - the Queen of Spades while the Ace or King of Spades is still out;
	 *   - the Ace or King of Spades while the Queen is still out, when `best`
	 *     was not a Spade;
	 *   - a sure swing: `candidate` is certain to win (no higher card of its
	 *     suit is out) and its own points beat what `best` is certain to take
	 *     by the same limit the follow guard uses (10, experts 4). This covers
	 *     leading a sure-winning Queen (13) and giving up a sure Jack of
	 *     Diamonds (10), at every level (user decision, 2026-11-05);
	 *   - the Jack of Diamonds while a higher Diamond is still out (2026-11-09).
	 * The third rule counts only the led card itself, since what the others
	 * will play onto it is hidden. */
	static boolean isSuicidalLead(Card candidate, Card best, List<Card> mine, Table_Cards tc, Player player) {
		if (Rules.isQueenOfSpades(candidate)
				&& (isOut(tc, mine, CardValue.ACE, Suit.SPADE) || isOut(tc, mine, CardValue.KING, Suit.SPADE))) {
			return true;
		}
		if (candidate.suit == Suit.SPADE
				&& (candidate.cardValue == CardValue.ACE || candidate.cardValue == CardValue.KING)
				&& best.suit != Suit.SPADE
				&& isOut(tc, mine, CardValue.QUEEN, Suit.SPADE)) {
			return true;
		}
		// Leading the Jack of Diamonds while a higher Diamond is still out hands
		// her (-10) to whoever holds it (2026-11-09, Wayne's game hand 12: East
		// blundered the Jack into North's Ace, and North was leading the game).
		if (isJackOfDiamonds(candidate) && !isJackOfDiamonds(best) && !certainToWinLead(candidate, mine, tc)) {
			return true;
		}
		if (certainToWinLead(candidate, mine, tc)) {
			int limit = player.profile.isExpert() ? EXPERT_BLUNDER_COST : SUICIDAL_BLUNDER_COST;
			int bestTakes = certainToWinLead(best, mine, tc) ? trickPointValue(best) : 0;
			return trickPointValue(candidate) - bestTakes >= limit;
		}
		return false;
	}

	/** Not yet played and not in my own hand. */
	private static boolean isOut(Table_Cards tc, List<Card> mine, CardValue value, Suit suit) {
		return !CardCounter.accountedRanks(tc, mine, 1, suit)[value.ordinal()];
	}

	private static boolean certainToWinLead(Card c, List<Card> mine, Table_Cards tc) {
		return !CardCounter.anyAboveUnaccounted(tc, mine, 1, c.suit, c.cardValue);
	}

	/** Points the last player takes by playing `c` (0 when it loses the trick). */
	private static int lastSeatCost(Card c, Table_Cards tc, Card top, Suit lead) {
		boolean wins = c.suit == lead && (top == null || c.cardValue.compareTo(top.cardValue) > 0);
		if (!wins) {
			return 0;
		}
		return trickPointValue(tc.p1_card) + trickPointValue(tc.p2_card) + trickPointValue(tc.p3_card)
				+ trickPointValue(c);
	}

	/** True when this player committed to shooting the moon during passing
	 * AND the attempt is still mathematically alive -- nobody else has
	 * captured a Heart or the Queen of Spades yet. attempting_moon_shot is a
	 * one-time commitment that is never cleared mid-hand, so every consumer
	 * of it has to pair it with this check; the 2026-08-05 live-playthrough
	 * bug (North kept leading moon-shot plays long after East had taken a
	 * Heart) was exactly this check being present on the follow path and
	 * missing on the lead path. Centralised here so it can't drift again.
	 *
	 * BUG FIX (2026-08-07): inProgressMoonShooter() returns null while NO
	 * seat has captured a point card yet -- and this used to require it to
	 * equal the shooter's own seat, so a committed shooter spent the early
	 * tricks in BASELINE mode, actively AVOIDING the very point cards it
	 * needed, until one happened to land in its pile by accident. Null means
	 * the attempt is fully alive (every point card is still winnable), not
	 * dead. */
	static boolean isMoonShotAlive(Table_Cards tc, Player player) {
		if (!player.attempting_moon_shot) {
			return false;
		}
		// BUG FIX (2026-10-17, live hand 4, Shark): this used to be
		// `shooter == null || shooter == me`, but inProgressMoonShooter() is
		// ALSO null once point cards are split between two or more seats --
		// which is the moon's death, not "nobody has captured anything". After
		// South took the King of Hearts and North then took four more, Shark
		// still read its attempt as live and kept cashing boss Hearts into
		// the Queen. Alive means no OTHER seat holds a Heart or the Queen.
		for (Map.Entry<PLAYER, Hand> e : wonCardsByPlayer(tc).entrySet()) {
			if (e.getKey() != player.tbl_player
					&& (Scoring.countHearts(e.getValue()) > 0 || Scoring.hasQueenOfSpades(e.getValue()))) {
				return false;
			}
		}
		return true;
	}


	/** True when this seat committed to a moon shot at the deal and the attempt has
	 * since died (another seat captured a Heart or the Queen). From then on the
	 * only goal is to minimize the damage (user rule, 2026-10-17). */
	static boolean moonAttemptAbandoned(Table_Cards tc, Player player) {
		return player.attempting_moon_shot && !isMoonShotAlive(tc, player);
	}

	/** Is this card a Jack-of-Diamonds asset worth hanging onto, given the
	 * rest of the hand? Either the Jack itself while it's still protectable,
	 * or the Ace/King of Diamonds while they're serving as that protection
	 * (or as the capture weapon to win the Jack off whoever else holds it) --
	 * discarding one undermines the whole reason it was being kept. Extracted
	 * 2026-08-06 as the single definition the scored-candidate discard scorer
	 * reads (see AICardScorer.java). */
	static boolean isProtectedDiamondAsset(Card c, List<Card> cards) {
		if (c.suit != Suit.DIAMOND) {
			return false;
		}
		boolean holdsJack = find(cards, x -> x.suit == Suit.DIAMOND && x.cardValue == CardValue.JACK) != null;
		boolean protectJack = HandEvaluator.hasJackOfDiamondsProtection(cards);
		boolean protectCaptureWeapons = protectJack || !holdsJack;
		return (protectJack && c.cardValue == CardValue.JACK)
				|| (protectCaptureWeapons && (c.cardValue == CardValue.ACE || c.cardValue == CardValue.KING));
	}

	// ------------------------------------------------------------------
	// Small helpers -- all pure, all just walking the legal-options list
	// or the cards already placed on the table this trick.
	// ------------------------------------------------------------------

	private interface CardPredicate {
		boolean test(Card c);
	}

	private static Card find(List<Card> cards, CardPredicate p) {
		for (Card c : cards) {
			if (p.test(c)) {
				return c;
			}
		}
		return null;
	}

	static boolean allSameSuit(List<Card> cards, Suit suit) {
		for (Card c : cards) {
			if (c.suit != suit) {
				return false;
			}
		}
		return true;
	}

	static Card highestOf(List<Card> cards) {
		Card best = cards.get(0);
		for (Card c : cards) {
			if (c.cardValue.compareTo(best.cardValue) > 0) {
				best = c;
			}
		}
		return best;
	}

	/** Suit with the most cards among `cards` (ties broken by enum order). */
	static Suit longestSuitAmong(List<Card> cards) {
		Suit best = cards.get(0).suit;
		int bestCount = 0;
		for (Suit s : Suit.values()) {
			int count = 0;
			for (Card c : cards) {
				if (c.suit == s) {
					count++;
				}
			}
			if (count > bestCount) {
				bestCount = count;
				best = s;
			}
		}
		return best;
	}

	/** True if leading Diamonds risks exposing a Jack-of-Diamonds asset
	 * worth protecting -- either a held, protected Jack itself, or an
	 * Ace/King "capture weapon" kept in reserve while hunting the Jack from
	 * whoever else holds it (see HandEvaluator.hasJackOfDiamondsProtection()
	 * and the project's TODO.md Jack-of-Diamonds strategy notes). Added
	 * 2026-07-31. Updated 2026-08-02: if the Jack has already been captured
	 * by any seat, return false immediately -- hunting/protecting a Jack that's
	 * already gone is pointless. */
	static boolean shouldProtectDiamondSuit(List<Card> cards, Table_Cards tc) {
		// Added 2026-08-02: Jack hunt/protect abandonment check. If the Jack
		// is already captured, there's no reason to shield Diamonds anymore.
		if (Scoring.hasJackBeenCaptured(wonCardsByPlayer(tc))) {
			return false;
		}
		if (HandEvaluator.hasJackOfDiamondsProtection(cards)) {
			return true;
		}
		boolean holdsJack = false;
		boolean holdsCaptureWeapon = false;
		for (Card c : cards) {
			if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
				holdsJack = true;
			}
			if (c.suit == Suit.DIAMOND && (c.cardValue == CardValue.ACE || c.cardValue == CardValue.KING)) {
				holdsCaptureWeapon = true;
			}
		}
		return !holdsJack && holdsCaptureWeapon;
	}

	static int countOfSuit(List<Card> cards, Suit suit) {
		int count = 0;
		for (Card c : cards) {
			if (c.suit == suit) {
				count++;
			}
		}
		return count;
	}

	static boolean anyOtherSeatVoidIn(Table_Cards tc, PLAYER self, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			if (p != self && VoidTracker.isVoid(tc, p, suit)) {
				return true;
			}
		}
		return false;
	}

	/** True if any seat that hasn't played yet THIS trick (positions
	 * playPosition+1..4) is already known void in leadSuit. tc.current_player
	 * is the seat about to act right now (the one calling chooseFollow), so
	 * walking Next_Dealer() forward from there enumerates exactly the
	 * remaining seats in turn order. */
	static boolean anyLaterSeatVoidIn(Table_Cards tc, int playPosition, Suit leadSuit) {
		PLAYER seat = tc.current_player;
		for (int i = playPosition; i < 4; i++) {
			seat = seat.Next_Dealer();
			if (VoidTracker.isVoid(tc, seat, leadSuit)) {
				return true;
			}
		}
		return false;
	}

	/** True if winning this trick would mean collecting the Queen of Spades
	 * -- either because `candidate` (the card under consideration) IS the
	 * Queen itself, or because some earlier seat this trick already
	 * discarded it (a void player can dump anything, suit-led trick or
	 * not). Aggressive's moon-stopping instinct never overrides this --
	 * see the 2026-07-31 chooseFollow update. */
	static boolean wouldCollectQueenOfSpades(Table_Cards tc, int playPosition, Card candidate) {
		return Rules.isQueenOfSpades(candidate) || anyAlreadyPlayedMatches(tc, playPosition, Rules::isQueenOfSpades);
	}

	/** True if winning this trick nets at least one Heart -- either because
	 * the led suit itself is Hearts (so `candidate`, a legal follow, is a
	 * Heart), or because some earlier seat this trick already discarded one
	 * while void. */
	static boolean wouldCollectAHeart(Table_Cards tc, int playPosition, Suit leadSuit, Card candidate) {
		return leadSuit == Suit.HEART || anyAlreadyPlayedMatches(tc, playPosition, c -> c.suit == Suit.HEART);
	}

	/** Hearts this trick would hand its winner: those already played plus my
	 * own card (2026-11-09, part of what taking the Queen really costs). */
	static int heartsOnTrick(Table_Cards tc, int playPosition, Card candidate) {
		Card[] played = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		int n = candidate != null && candidate.suit == Suit.HEART ? 1 : 0;
		for (int i = 0; i < playPosition - 1 && i < played.length; i++) {
			if (played[i] != null && played[i].suit == Suit.HEART) {
				n++;
			}
		}
		return n;
	}

	private static boolean anyAlreadyPlayedMatches(Table_Cards tc, int playPosition, CardPredicate p) {
		Card[] played = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		for (int i = 0; i < playPosition - 1 && i < played.length; i++) {
			if (played[i] != null && p.test(played[i])) {
				return true;
			}
		}
		return false;
	}

	/** Would the acting player (`me`) still end up ahead of (or tied with)
	 * `shooter` even after a successful moon shot? Shooting the moon adds
	 * -26 to the shooter's running total (see Scoring.java), so this
	 * compares me's current total against the shooter's PROJECTED total if
	 * they succeed -- not their current one. If I'd still be strictly
	 * better off (lower total) regardless, there's no need to spend a Heart
	 * stopping them. */
	static boolean safelyAheadOfShooter(Table_Cards tc, Player me, PLAYER shooter) {
		Player shooterPlayer = tc.players.get(shooter);
		int shooterTotalIfSuccessful = shooterPlayer.p_total_points - 26;
		return me.p_total_points < shooterTotalIfSuccessful;
	}

	/** Has ANY point card (a Heart or the Queen of Spades) been captured by
	 * anyone yet this hand? While this is false, every moon shot is still
	 * fully alive and no shooter is VISIBLE -- the window in which the
	 * 2026-08-07 reviewed hand was actually lost: West gifted its King of
	 * Hearts as a free discard one trick before any threat could be
	 * detected. Blocker retention (see AICardScorer) keys on this. */
	static boolean anyPointCardCaptured(Table_Cards tc) {
		for (PLAYER p : PLAYER.values()) {
			Hand won = tc.players.get(p).won_cards;
			if (won == null) {
				continue;
			}
			for (Card c : won.hand) {
				if (c.suit == Suit.HEART || Rules.isQueenOfSpades(c)) {
					return true;
				}
			}
		}
		return false;
	}

	static Map<PLAYER, Hand> wonCardsByPlayer(Table_Cards tc) {
		Map<PLAYER, Hand> map = new EnumMap<PLAYER, Hand>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			map.put(p, tc.players.get(p).won_cards);
		}
		return map;
	}

	/** Highest card of `suit` already placed on the table this trick, among
	 * positions 1..playPosition-1 (the current play hasn't happened yet). */
	static Card highestOfSuitOnTable(Table_Cards tc, Suit suit, int playPosition) {
		Card best = null;
		Card[] played = { tc.p1_card, tc.p2_card, tc.p3_card, tc.p4_card };
		for (int i = 0; i < playPosition - 1 && i < played.length; i++) {
			Card c = played[i];
			if (c != null && c.suit == suit && (best == null || c.cardValue.compareTo(best.cardValue) > 0)) {
				best = c;
			}
		}
		return best;
	}
}
