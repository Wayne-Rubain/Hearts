package hearts_1;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Traits;

/**
 * Hand-strength assessment for speculative, "do I actually have a shot at
 * this" decisions -- added 2026-07-31 for the passing phase (Deal_Hand.java).
 * Two structurally identical problems live here, per the project's "deep
 * dive" design discussion: whether a player should commit to ATTEMPTING to
 * shoot the moon this hand, and whether a player holding (or hunting) the
 * Jack of Diamonds has a realistic chance of actually capturing it. Both are
 * "assess viability once, then commit" decisions, not something re-rolled
 * every trick -- see Player.attempting_moon_shot, set once during passing.
 *
 * Deliberately pure logic, no Swing dependency, same pattern as
 * Rules.java/Scoring.java/AI.java/VoidTracker.java.
 */
public final class HandEvaluator {

	private HandEvaluator() { }

	// Tuned to be reachable by a genuinely strong hand but not a routine one
	// -- moon shots should be rare for a player without Shooting_the_Moon,
	// not a coin flip. These are deliberately simple numbers for a first
	// version; TODO.md already flags turning this into a real tunable
	// percentage per player as a later refinement.
	private static final double MOON_SHOT_BASE_THRESHOLD = 22;
	private static final double MOON_SHOT_TRAIT_BONUS = 8;
	private static final double STANDING_SENSITIVITY = 0.5;

	/**
	 * Bounds on how far standings alone may move the threshold, added
	 * 2026-08-09 after a live 15-hand game exposed the unbounded version as
	 * genuinely broken.
	 *
	 * STANDING_SENSITIVITY was being applied to a raw standingDelta, which in a
	 * game to TARGET_SCORE routinely reaches 50-76. That made the standings term
	 * (25-38) swamp the base threshold (26) entirely, and the observed result on
	 * a plain NORMAL seat was:
	 *
	 *   hand  1: threshold  26.0, hand quality 17 -> declined a decent hand
	 *   hand  8: threshold  -6.0, hand quality 21 -> committed
	 *   hand 13: threshold   0.7, hand quality  5 -> committed on junk
	 *   hand 15: threshold -12.0, hand quality  9 -> committed
	 *
	 * Once the threshold goes negative it sits below the minimum achievable
	 * hand quality, so moonShotHandQuality() stops mattering at all -- the
	 * evaluator becomes dead code and the seat commits blind. Worse, the
	 * behaviour inverts: it refuses to shoot when its CARDS justify it and
	 * shoots with garbage once it is losing, which is backwards. It is also
	 * self-reinforcing (fall behind -> commit on hopeless hands -> take 15-20
	 * points -> fall further behind), and that spiral is most of how the seat in
	 * that game reached 101 points.
	 *
	 * Being behind should be a TIEBREAKER, not the dominant term. The cap bounds
	 * the standings influence in both directions (a leading player likewise
	 * shouldn't become infinitely reluctant), and the floor guarantees a player
	 * never stops reading its own cards no matter how far behind it falls.
	 */
	private static final double MAX_STANDING_DISCOUNT = 10;
	private static final double MIN_THRESHOLD = 12;

	/**
	 * End-of-game caution, added 2026-08-15 (EndGame.java) once
	 * TARGET_SCORE/checkForGameOver() gave a game end for this to react to.
	 * Distinct from the standingDelta term above, which compares seats
	 * against EACH OTHER -- a seat can be comfortably ahead of the field and
	 * still sit at 90 points, one bad moon attempt from ending the game.
	 * Moon-mode play captures Hearts aggressively; a FAILED attempt still
	 * banks whatever points landed before it broke, so committing near the
	 * target risks the exact bad outcome the standings term alone can't see.
	 * Always raises the threshold (never lowers it, unlike standingDelta,
	 * which cuts both ways) -- proximity to the target is a risk regardless
	 * of how the standings term reads. */
	private static final double MAX_DANGER_DISCOUNT = 14;

	private static final int JACK_PROTECTION_LOW_DIAMOND_COUNT = 3;

	/** Diamonds needed in hand to sustain a Jack hunt (see
	 * hasJackCaptureChance). Three means the boss plus two more pulls --
	 * enough to strip a typical three-or-four-card holding down to the Jack.
	 * A singleton or doubleton boss buys one pull and then nothing. */
	private static final int JACK_HUNT_MIN_DIAMONDS = 3;

	/** ...unless so few Diamonds remain unaccounted for that the holder is
	 * already near-bare, in which case even one pull is likely to force the
	 * Jack out. Counted excluding the Jack itself. */
	private static final int JACK_HUNT_FLUSH_CEILING = 2;

	/**
	 * Heart-control preconditions, added 2026-08-09 after a live hand where
	 * NORTH committed to a moon holding 3H 7H JH -- no Ace, King or Queen of
	 * Hearts -- led AH/KH/QH to pull Spades, deliberately took the Queen, then
	 * lost the very first Heart trick to EAST's KH and finished +14.
	 *
	 * That commitment was not a tuning miss, it was structural. moonShotHandQuality()
	 * treats Heart strength as a BONUS (+2 per Heart honour) rather than a
	 * precondition, so the score is dominated by Spade control: the offending
	 * hand scored 18, of which 16 came from Spades (5-card suit + 2 honours = 9,
	 * has-Ace = 6, has-King = 4, minus 3 for weak Diamonds) and only 2 from the
	 * lone JH. A hand with AS/KS and six Spades scores ~24 with literally zero
	 * Hearts, clearing even the undiscounted base threshold of 22.
	 *
	 * The rule the score was missing: you cannot win a trick in a suit you are
	 * void in, so taking all 13 Hearts requires enough Hearts to actually win
	 * each Heart-led round, topped high enough to beat what is still out. No
	 * amount of Spade control substitutes for that. Hence a gate consulted
	 * BEFORE the quality score, which no threshold discount can talk its way
	 * past.
	 *
	 * Holding the Ace is the normal route. The Ace-less route is left open but
	 * made deliberately expensive: it only works if the Ace holder is forced to
	 * discard it onto a trick you win, which needs the King as the next stopper
	 * plus enough length to keep leading Hearts until that happens.
	 */
	private static final int MOON_MIN_HEART_COUNT = 4;
	private static final int MOON_MIN_HEART_COUNT_WITHOUT_ACE = 5;

	/** Points per Heart held beyond MOON_MIN_HEART_COUNT. Deliberately modest:
	 * because hasMoonHeartControl() runs first, every hand that can collect this
	 * credit already holds the Ace (or King plus five), so the credit only ever
	 * sharpens the ranking among hands that are genuinely moon-capable -- it can
	 * never drag a heartless hand over the threshold. */
	private static final int HEART_LENGTH_CREDIT = 2;

	/** Numeric hand-quality score for shooting-the-moon viability. Higher is
	 * stronger. Signals, in the order of importance from the design
	 * discussion: Spade control (Ace/King -- lets you hold/release the Queen
	 * on your own terms), a long suit topped by high cards (lets you run
	 * tricks and force Hearts onto you), Heart strength (you need to
	 * actually WIN Heart tricks eventually), and a penalty for isolated weak
	 * suits (force early, awkward discards before control is established). */
	public static int moonShotHandQuality(Hand hand) {
		int score = 0;

		Suit longest = longestSuit(hand);
		int longestLen = countOfSuit(hand, longest);
		int longestHighCards = 0;
		for (Card c : hand.hand) {
			if (c.suit == longest && c.cardValue.compareTo(CardValue.JACK) >= 0) {
				longestHighCards++;
			}
		}
		score += longestLen + (2 * longestHighCards);

		if (hasCard(hand, CardValue.ACE, Suit.SPADE)) {
			score += 6;
		}
		if (hasCard(hand, CardValue.KING, Suit.SPADE)) {
			score += 4;
		}

		for (Card c : hand.hand) {
			if (c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.JACK) >= 0) {
				score += 2;
			}
		}

		// Heart LENGTH, credited separately from the honours above (2026-08-10).
		// The honour loop cannot tell a three-card holding from a seven-card one
		// carrying the same top cards, yet length is precisely what wins the
		// later Heart rounds once the honours are spent -- an Ace and King take
		// two tricks, and something has to take the other eleven. The long-suit
		// term at the top only helps when Hearts happen to BE the longest suit,
		// which is exactly the case that needs the credit least.
		//
		// Measured from MOON_MIN_HEART_COUNT rather than zero so this rewards
		// length beyond what the gate already demands, instead of handing points
		// to every hand for holding the bare minimum.
		int hearts = countOfSuit(hand, Suit.HEART);
		if (hearts > MOON_MIN_HEART_COUNT) {
			score += HEART_LENGTH_CREDIT * (hearts - MOON_MIN_HEART_COUNT);
		}

		for (Suit s : Suit.values()) {
			int count = countOfSuit(hand, s);
			if (count >= 1 && count <= 2 && !hasCardAtLeast(hand, s, CardValue.TEN)) {
				score -= 3; // isolated, unsupported low cards -- awkward, not helpful
			}
		}

		return score;
	}

	/** Can this hand realistically WIN Heart tricks, as opposed to merely
	 * containing some Hearts? A hard precondition for a moon shot -- see the
	 * MOON_MIN_HEART_COUNT comment for why this is a gate and not a score
	 * term. Package-private so the moon-decision tests can exercise it
	 * directly. */
	static boolean hasMoonHeartControl(Hand hand) {
		int hearts = countOfSuit(hand, Suit.HEART);
		if (hasCard(hand, CardValue.ACE, Suit.HEART)) {
			return hearts >= MOON_MIN_HEART_COUNT;
		}
		// No Ace: the only way the Ace ever reaches you is as someone else's
		// discard onto a trick you win, so demand the King behind it and the
		// extra length needed to keep forcing the issue.
		return hasCard(hand, CardValue.KING, Suit.HEART)
				&& hearts >= MOON_MIN_HEART_COUNT_WITHOUT_ACE;
	}

	/** Same as above, but for callers (AICardScorer's Aggressive lead
	 * scorer, 2026-08-16) that only have a List<Card> -- a lead's
	 * legalOptions, which reflects the full hand for the suits that
	 * matter here (see CardCounter's class doc for why that's sound).
	 * Package-private for the same reason as the Hand overload. */
	static boolean hasMoonHeartControl(java.util.List<Card> cards) {
		Hand h = new Hand();
		for (Card c : cards) {
			h.addX(c);
		}
		return hasMoonHeartControl(h);
	}

	/** One-time commitment decision: is this hand, this player's personality,
	 * and this player's current standing enough to go for it? Standings-aware
	 * via Scoring.standingDelta() -- a player behind the field gets an
	 * easier effective threshold (more reason to gamble on a big swing); a
	 * player ahead gets a harder one (less reason to risk a lead they don't
	 * need to risk). */
	public static boolean shouldAttemptMoonShot(Hand hand, Table_Cards tc, Player me) {
		if (attemptsByHeartRules(hand, tc, me)) {
			return true;
		}
		return attemptsByMoonLook(hand, me);
	}

	/** The heart-length paths only (normal bar and moon interest), without the
	 * side-suit look. */
	static boolean attemptsByHeartRules(Hand hand, Table_Cards tc, Player me) {
		if (shouldAttemptNormally(hand, tc, me)) {
			return true;
		}
		// Moon interest (2026-10-17, user design): a player who rolled interest
		// this hand will gamble on a hand with real Heart control and decent
		// quality even when its template, reliability or the usual bar says no.
		return me.moonInterestThisHand && hasMoonHeartControl(hand)
				&& moonShotHandQuality(hand) >= MIN_THRESHOLD;
	}

	/** The moon-look path (2026-11-08, see Profile.moonLookPercent): a player
	 * who gave this hand the side-suit look commits when the hand scores at
	 * least its optimism's bar. Ignores the template's MOON_OFFENSE weight, the
	 * heart-length gate and the standings, like the moon-interest path: the
	 * roster dialled this player in on purpose. */
	static boolean attemptsByMoonLook(Hand hand, Player me) {
		return me.moonLookThisHand && lookHeartsOk(hand.hand)
				&& sideSuitMoonScore(hand.hand) >= sideSuitMoonBar(me.profile.moonOptimism);
	}

	/** Hearts a side-suit moon can live with (Wayne, 2026-11-08). The run
	 * needs every Heart and the Queen, and a low Heart is a stopper: someone
	 * plays over it. "Q73, you can never shoot the moon with that hand." So:
	 * no Hearts; or one Heart (if it isn't the Ace it gets tested once, early);
	 * or the Ace (with the King, Queen... in sequence) plus at most ONE other
	 * Heart, and that one a 7 or higher ("Ace and Q or J or 10 ... sometimes it
	 * even works better with a 7, 8 or 9, because people think you're trying to
	 * get rid of your hearts"). */
	public static boolean lookHeartsOk(java.util.List<Card> cards) {
		java.util.List<CardValue> hs = valuesHighFirst(cards, Suit.HEART);
		if (hs.size() <= 1) {
			return true;
		}
		if (hs.get(0) != CardValue.ACE) {
			return false;
		}
		int seq = 1;
		while (seq < hs.size() && hs.get(seq).ordinal() == CardValue.ACE.ordinal() - seq) {
			seq++;
		}
		int losers = hs.size() - seq;
		return losers == 0 || (losers == 1 && hs.get(hs.size() - 1).compareTo(CardValue.SEVEN) >= 0);
	}

	// ------------------------------------------------------------------
	// Side-suit moon look (2026-11-08, user design from Wayne's live tries).
	// Fitted on his 39 logged hands of 2026-11-07: after the pass, a score of
	// 10+ flagged 17 of his 21 tries and none of his 18 other hands; 8+
	// flagged 18 and 1. The heart-length rule above flagged 0 of the 21.
	// ------------------------------------------------------------------

	/** Bar after the pass: optimism 0 needs 16, the default 50 needs 12,
	 * 100 needs 8. */
	public static int sideSuitMoonBar(int optimism) {
		return 16 - Math.round(optimism * 8 / 100f);
	}

	/** Bar for passing toward the look before the pass (the hand as it will
	 * be once its own planned pass is gone, before anything comes back): three
	 * lower than the commit bar, because the three cards coming back often
	 * add the top cards (Wayne's tries firmed up from what he received). */
	public static int sideSuitBuildBar(int optimism) {
		return sideSuitMoonBar(optimism) - 3;
	}

	/** How a real, optimistic player reads a 13-card hand for a moon:
	 *  + 2 per Ace, 1 per King (sure and likely winners);
	 *  + the best side suit (Spades, Diamonds or Clubs): its unbroken sequence
	 *    from the top card (2 per card from the Ace; 1 per card from a King or
	 *    Queen with 3+ in sequence), plus its length past four (doubled when
	 *    headed by the Ace or King) -- the suit that is run to make the others
	 *    throw their Hearts;
	 *  + top Hearts (Ace 3, King 2, Queen 1);
	 *  - 2 per Heart of Ten or lower (the cards that lose Heart tricks; this
	 *    look passes them first);
	 *  + 2 for the Queen, Ace or King of Spades (control of the Queen). */
	public static int sideSuitMoonScore(java.util.List<Card> cards) {
		int score = 0;
		for (Card c : cards) {
			if (c.cardValue == CardValue.ACE) {
				score += 2;
			} else if (c.cardValue == CardValue.KING) {
				score += 1;
			}
		}
		int best = 0;
		for (Suit s : new Suit[] { Suit.SPADE, Suit.DIAMOND, Suit.CLUB }) {
			java.util.List<CardValue> vs = valuesHighFirst(cards, s);
			if (vs.isEmpty()) {
				continue;
			}
			// The unbroken sequence from the suit's top card. Headed by the Ace,
			// every card in it is a winner (2 each). Headed by the King or Queen
			// with 3+ in sequence it loses one trick to the Ace (and King), then
			// runs (1 each) -- Wayne, 2026-11-08: "pull all clubs minus the ace
			// or king".
			int seq = 1;
			for (int i = 1; i < vs.size(); i++) {
				if (vs.get(i).ordinal() == vs.get(0).ordinal() - i) {
					seq++;
				} else {
					break;
				}
			}
			int per = vs.get(0) == CardValue.ACE ? 2
					: vs.get(0).compareTo(CardValue.QUEEN) >= 0 && seq >= 3 ? 1 : 0;
			int extra = Math.max(0, vs.size() - 4);
			int run = per * seq + (vs.get(0).compareTo(CardValue.KING) >= 0 ? 2 * extra : extra);
			best = Math.max(best, run);
		}
		score += best;
		boolean spadeControl = false;
		for (Card c : cards) {
			if (c.suit == Suit.HEART) {
				if (c.cardValue == CardValue.ACE) {
					score += 3;
				} else if (c.cardValue == CardValue.KING) {
					score += 2;
				} else if (c.cardValue == CardValue.QUEEN) {
					score += 1;
				} else if (c.cardValue.compareTo(CardValue.TEN) <= 0) {
					score -= 2;
				}
			} else if (c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.QUEEN) >= 0) {
				spadeControl = true;
			}
		}
		if (spadeControl) {
			score += 2;
		}
		return score;
	}

	/** The pass this look makes: Hearts of Ten or lower first (lowest first),
	 * then the lowest cards of the shortest other suits; never an Ace or King,
	 * the Queen of Hearts or the Queen of Spades (the winners and the control
	 * the shot needs) unless nothing else is left. Used both to pass and to
	 * judge the hand before the pass. */
	public static java.util.List<Card> moonLookPass(java.util.List<Card> cards) {
		java.util.List<Card> out = new java.util.ArrayList<Card>();
		java.util.List<Card> lowHearts = new java.util.ArrayList<Card>();
		for (Card c : cards) {
			if (c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.TEN) <= 0) {
				lowHearts.add(c);
			}
		}
		lowHearts.sort((a, b) -> a.cardValue.compareTo(b.cardValue));
		for (int i = 0; i < lowHearts.size() && out.size() < 3; i++) {
			out.add(lowHearts.get(i));
		}
		while (out.size() < 3) {
			Card pick = null;
			int pickLen = 0;
			for (Card c : cards) {
				if (out.contains(c) || keptForTheShot(c)) {
					continue;
				}
				int len = 0;
				for (Card d : cards) {
					if (d.suit == c.suit && !out.contains(d)) {
						len++;
					}
				}
				if (pick == null || len < pickLen || (len == pickLen && c.cardValue.compareTo(pick.cardValue) < 0)) {
					pick = c;
					pickLen = len;
				}
			}
			if (pick == null) {
				// Only kept cards left (a hand of Aces, Kings and Queens): the lowest of them.
				for (Card c : cards) {
					if (!out.contains(c) && (pick == null || c.cardValue.compareTo(pick.cardValue) < 0)) {
						pick = c;
					}
				}
			}
			if (pick == null) {
				break;
			}
			out.add(pick);
		}
		return out;
	}

	private static boolean keptForTheShot(Card c) {
		return c.cardValue.compareTo(CardValue.KING) >= 0
				|| (c.cardValue == CardValue.QUEEN && (c.suit == Suit.SPADE || c.suit == Suit.HEART));
	}

	/** The look's score of the hand that is left after its own planned pass. */
	public static int sideSuitBuildScore(java.util.List<Card> cards) {
		java.util.List<Card> rest = new java.util.ArrayList<Card>(cards);
		rest.removeAll(moonLookPass(cards));
		return sideSuitMoonScore(rest);
	}

	private static java.util.List<CardValue> valuesHighFirst(java.util.List<Card> cards, Suit s) {
		java.util.List<CardValue> vs = new java.util.ArrayList<CardValue>();
		for (Card c : cards) {
			if (c.suit == s) {
				vs.add(c.cardValue);
			}
		}
		vs.sort(java.util.Collections.reverseOrder());
		return vs;
	}

	private static boolean shouldAttemptNormally(Hand hand, Table_Cards tc, Player me) {
		// Tactic-weighting gate (2026-08-07, see TacticWeights.java): a type
		// with MOON_OFFENSE weight 0 (Cautious, Avoids_Queen, Bad) never
		// commits, and an under-1 weight (Normal/Aggressive at 0.5) demands a
		// visibly stronger hand before gambling. Weights of 1+ change nothing
		// here -- Moon_Shooter's eagerness is already expressed by its
		// Shooting_the_Moon trait lowering the threshold below.
		double eagerness = TacticWeights.weight(me, TacticWeights.Tactic.MOON_OFFENSE);
		if (eagerness == 0) {
			return false;
		}
		// Checked before anything score- or standings-based: a hand that cannot
		// win Heart tricks cannot shoot the moon, however desperate the seat or
		// however commanding its Spades. Deliberately ahead of the threshold
		// arithmetic so no discount can reach past it.
		if (!hasMoonHeartControl(hand)) {
			return false;
		}
		double threshold = MOON_SHOT_BASE_THRESHOLD;
		if (me.p_traits.contains(Player_Traits.Shooting_the_Moon)) {
			threshold -= MOON_SHOT_TRAIT_BONUS;
		}
		threshold += (1.0 - Math.min(eagerness, 1.0)) * MOON_SHOT_TRAIT_BONUS;
		// Clamped both ways (see MAX_STANDING_DISCOUNT): positive delta means
		// trailing, which lowers the bar; negative means leading, which raises
		// it. Neither may run away with the decision.
		double standingAdjustment = STANDING_SENSITIVITY * Scoring.standingDelta(tc, me.tbl_player);
		if (standingAdjustment > MAX_STANDING_DISCOUNT) {
			standingAdjustment = MAX_STANDING_DISCOUNT;
		} else if (standingAdjustment < -MAX_STANDING_DISCOUNT) {
			standingAdjustment = -MAX_STANDING_DISCOUNT;
		}
		threshold -= standingAdjustment;
		// End-of-game caution (2026-08-15): raises the bar further as MY OWN
		// total nears TARGET_SCORE, on top of whatever the standings term
		// above decided -- a trailing seat close to the target still needs a
		// stronger hand than one equally far behind but nowhere near danger.
		threshold += MAX_DANGER_DISCOUNT * EndGame.dangerLevel(tc, me.tbl_player);
		// Hard floor: however desperate the standings, the hand still has to be
		// worth something. Without this the evaluator above is ignorable.
		if (threshold < MIN_THRESHOLD) {
			threshold = MIN_THRESHOLD;
		}
		return moonShotHandQuality(hand) >= threshold;
	}

	/** Can this hand protect the Jack of Diamonds long enough to win it
	 * themselves? False if they don't even hold it (protection is moot).
	 * True if they hold the Ace and/or King of Diamonds (can control when
	 * the Jack becomes safe), or hold enough low Diamonds to duck behind
	 * until the Ace/King are forced out of the game by others. */
	public static boolean hasJackOfDiamondsProtection(Hand hand) {
		return hasJackOfDiamondsProtection(hand.hand);
	}

	/** Same as above, but for callers (AI.java) that only have a List<Card>
	 * on hand -- e.g. chooseLead()'s legalOptions, which for a lead choice
	 * always reflects the full hand where Diamonds are concerned (Rules
	 * never filters Diamonds out of legal leads the way it can Hearts).
	 * Added 2026-07-31 alongside AI.java's Jack-of-Diamonds hunt/protect
	 * wiring. */
	public static boolean hasJackOfDiamondsProtection(java.util.List<Card> cards) {
		if (!containsCard(cards, CardValue.JACK, Suit.DIAMOND)) {
			return false;
		}
		if (containsCard(cards, CardValue.ACE, Suit.DIAMOND) || containsCard(cards, CardValue.KING, Suit.DIAMOND)) {
			return true;
		}
		int lowDiamonds = 0;
		for (Card c : cards) {
			if (c.suit == Suit.DIAMOND && c.cardValue.compareTo(CardValue.TEN) < 0) {
				lowDiamonds++;
			}
		}
		return lowDiamonds >= JACK_PROTECTION_LOW_DIAMOND_COUNT;
	}

	/**
	 * The HUNTING half of TODO.md's "Jack-of-Diamonds realistic chance
	 * viability dial" (2026-08-23) -- can this hand realistically capture the
	 * Jack FROM whoever holds it, as opposed to merely owning one high Diamond
	 * and hoping?
	 *
	 * The held half of that item is already covered by
	 * hasJackOfDiamondsProtection() above, which AI.shouldProtectDiamondSuit()
	 * consults; this is its missing mirror image. Same shape as
	 * hasMoonHeartControl(): a hard precondition consulted BEFORE the tactic
	 * runs, not a score term the tactic can talk its way past.
	 *
	 * WHY THE PULL LEAD NEEDED A GATE. AICardScorer's "PULL THE JACK OF
	 * DIAMONDS OUT" scorer already requires the candidate to be a guaranteed
	 * winner, which sounds like it settles the question but doesn't: winning
	 * the TRICK is certain, capturing the JACK is not. The holder simply
	 * follows with a low Diamond and keeps it. What IS certain is that every
	 * seat void in Diamonds gets a free discard onto a trick I have committed
	 * to winning -- Hearts, most likely. jackPullRisksTheQueen() (2026-08-16)
	 * already covers the catastrophic version of that exposure; this covers
	 * the ordinary one, which is only worth accepting when the hunt can
	 * actually be sustained.
	 *
	 * Sustaining it means being able to keep leading Diamonds until the Jack
	 * has nothing left to hide behind. A singleton boss Diamond buys exactly
	 * one pull: if the Jack doesn't drop, the weapon is spent, the void seats
	 * got their free discard, and nothing was gained. So either:
	 *   - I hold enough Diamonds to pull more than once
	 *     (JACK_HUNT_MIN_DIAMONDS), or
	 *   - so few Diamonds are still unaccounted for that the holder is
	 *     already close to bare and one pull is likely to force it
	 *     (JACK_HUNT_FLUSH_CEILING).
	 *
	 * `cards` is the caller's legal-options list, which for a lead reflects
	 * every Diamond held -- see CardCounter's class doc for why that's sound.
	 */
	public static boolean hasJackCaptureChance(java.util.List<Card> cards, Table_Cards tc) {
		// Holding it myself is the protection question, not the hunt.
		if (containsCard(cards, CardValue.JACK, Suit.DIAMOND)) {
			return false;
		}
		boolean[] accounted = CardCounter.accountedRanks(tc, cards, 1, Suit.DIAMOND);
		// Already played, or sitting in somebody's won pile -- nothing to hunt.
		if (accounted[CardValue.JACK.ordinal()]) {
			return false;
		}
		// A weapon that cannot win a Diamond trick cannot take the Jack off
		// anyone. "Provably boss" rather than "is the Ace" so a King behind a
		// spent Ace still counts -- and so that a hand holding BOTH the Ace
		// and King counts them as two separate pulls, which is exactly what
		// they are.
		int bossDiamonds = 0;
		int myDiamonds = 0;
		for (Card c : cards) {
			if (c.suit != Suit.DIAMOND) {
				continue;
			}
			myDiamonds++;
			if (!CardCounter.anyAboveUnaccounted(tc, cards, 1, Suit.DIAMOND, c.cardValue)) {
				bossDiamonds++;
			}
		}
		if (bossDiamonds == 0) {
			return false;
		}
		// Two guaranteed winners ARE the sustained hunt, however short the
		// suit -- Ace plus King pulls twice on its own and needs no filler
		// behind it.
		if (bossDiamonds >= 2) {
			return true;
		}
		if (myDiamonds >= JACK_HUNT_MIN_DIAMONDS) {
			return true;
		}
		// Everything still out there except the Jack itself -- how much cover
		// its holder could still be sitting behind.
		int outstandingBesidesJack = 0;
		for (CardValue v : CardValue.values()) {
			if (v != CardValue.JACK && !accounted[v.ordinal()]) {
				outstandingBesidesJack++;
			}
		}
		return outstandingBesidesJack <= JACK_HUNT_FLUSH_CEILING;
	}

	private static boolean containsCard(java.util.List<Card> cards, CardValue value, Suit suit) {
		for (Card c : cards) {
			if (c.cardValue == value && c.suit == suit) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasCard(Hand hand, CardValue value, Suit suit) {
		for (Card c : hand.hand) {
			if (c.cardValue == value && c.suit == suit) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasCardAtLeast(Hand hand, Suit suit, CardValue minValue) {
		for (Card c : hand.hand) {
			if (c.suit == suit && c.cardValue.compareTo(minValue) >= 0) {
				return true;
			}
		}
		return false;
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
