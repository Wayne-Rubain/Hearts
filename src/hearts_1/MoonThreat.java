package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Moon-shot threat assessment, added 2026-08-06. Answers the three questions
 * every anti-moon decision needs: who (if anyone) is shooting, does that
 * actually threaten ME, and can they still win the trick in front of us?
 *
 * WHY THIS EXISTS: a real hand was lost to a moon shot that was entirely
 * preventable (see TODO.md's write-up). North collected the Queen of Spades
 * on trick 2 and the Ace of Hearts on trick 4 -- both handed over by East as
 * free discards while East was void in Clubs and holding five cards it could
 * have thrown instead. The Ace of Hearts in particular was the one card that
 * guaranteed the shot could be broken: keep it, and the moment the shooter
 * leads a Heart you take a trick and the moon is dead. The old void-discard
 * logic threw the HIGHEST Heart first, which is correct ordinary Hearts play
 * and exactly wrong against a shooter -- it hands over the strongest denial
 * weapon in the hand.
 *
 * Deliberately NOT gated behind any personality trait. Whether a player is
 * threatened enough to act is a standings question, not a temperament one --
 * an "unaggressive" player about to lose the game to a moon shot still has
 * every reason to stop it. Traits may later tune HOW hard a seat works to
 * deny (see TODO.md's ranked-trait roster), but the decision to care at all
 * belongs here.
 *
 * Pure logic, no Swing dependency -- same pattern as Rules.java/Scoring.java/
 * VoidTracker.java/HandEvaluator.java.
 */
public final class MoonThreat {

	private MoonThreat() { }

	/** Capturing the Queen of Spades costs this much, and is why taking it
	 * deliberately is a last resort rather than a first instinct. */
	private static final int QUEEN_OF_SPADES_COST = 13;

	/** Full credit toward the Queen's cost when this seat is near-certain to
	 * also bank the Jack of Diamonds' -10 this hand (2026-08-16 user
	 * refinement, specific to this Omnibus ruleset -- standard Hearts
	 * strategy has no analog since it has no Jack rule). Applies while
	 * holding the Jack with real protection
	 * (HandEvaluator.hasJackOfDiamondsProtection) -- near-certain to bank it
	 * themselves. */
	private static final double JACK_HELD_PROTECTED_CREDIT = 10;
	/** Half credit while merely positioned to intercept the Jack from
	 * whoever holds it (the Ace and/or King of Diamonds, without the Jack
	 * itself) -- a real but less certain prospect than protecting an
	 * already-held Jack, per TODO.md's "realistic chance" framing for the
	 * not-holding-it case. */
	private static final double JACK_CAPTURE_WEAPON_CREDIT = 5;
	/** Once the Jack-discounted net cost drops to this or below, the
	 * sacrifice is cheap enough to take regardless of how far along the
	 * shooter looks -- there's little left to wait for. */
	private static final double CHEAP_ENOUGH_NET_COST = 5;
	/** Out of 13 -- how many Hearts the shooter must already hold before
	 * this seat treats the moon as real enough to justify a full-cost
	 * sacrifice (2026-08-16 user refinement, grounded in the actual Hearts
	 * consensus: "opponents will tend to allow you to take the Queen even
	 * if they suspect you're shooting the moon" -- swallowing her defensively
	 * is a rare last resort, not a routine early reaction to a suspicion). A
	 * majority: clearly a real sweep in progress, not an early guess. */
	private static final int HEARTS_URGENCY_THRESHOLD = 7;

	/** The seat currently on track to shoot the moon from `me`'s point of
	 * view, or null if there isn't one, or if it's me (nobody stops
	 * themselves). Thin wrapper over Scoring.inProgressMoonShooter() so the
	 * "and it isn't me" half can't be forgotten at a call site. */
	public static PLAYER threateningShooter(Table_Cards tc, Player me) {
		PLAYER shooter = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(tc));
		if (shooter == null || shooter == me.tbl_player) {
			return null;
		}
		if (AI.safelyAheadOfShooter(tc, me, shooter)) {
			// Even a successful shot leaves me better off than them -- no
			// reason to spend anything stopping it.
			return null;
		}
		return shooter;
	}

	/** Lead over the shooter at which a game leader lets an early moon pass. */
	static final int LEADER_LETS_IT_PASS_MARGIN = 10;
	/** "Early": the shooter holds fewer Hearts than this... */
	static final int LETS_IT_PASS_MAX_HEARTS = 4;
	/** ...and fewer tricks than this have been played. */
	static final int LETS_IT_PASS_MAX_TRICKS = 8;

	/**
	 * LEADER LETS IT PASS (2026-11-08, Wayne, from his -36 moon in live game 3):
	 * a player leading the game, with the shooter not close to its score, lets
	 * an early moon go by -- "thinking that the other players or they themselves
	 * would stop it later" -- rather than chance taking points while a moon is
	 * still a long shot. Once the shot is far along (4+ Hearts, or 8 tricks
	 * played) normal defense comes back. Gates only the two COSTLY defenses:
	 * refusing to throw Hearts on the shooter's trick (keeping them risks
	 * eating them later) and taking a point trick to break the shot. Free
	 * choices, like which Heart to play on a trick already lost, are unaffected.
	 */
	static boolean letsItPassForNow(Table_Cards tc, Player me, PLAYER shooter) {
		Player s = tc.players.get(shooter);
		if (s == null || Scoring.countHearts(s.won_cards) >= LETS_IT_PASS_MAX_HEARTS) {
			return false;
		}
		int tricks = 0;
		for (Player p : tc.players.values()) {
			tricks += p.won_cards == null ? 0 : p.won_cards.hand.size();
		}
		if (tricks / 4 >= LETS_IT_PASS_MAX_TRICKS) {
			return false;
		}
		for (Player p : tc.players.values()) {
			if (p != me && p.p_total_points < me.p_total_points) {
				return false; // I'm not leading the game
			}
		}
		return s.p_total_points - me.p_total_points >= LEADER_LETS_IT_PASS_MARGIN;
	}

	/** Which seat plays in `position` (1-4) of the trick in progress, derived
	 * from who led it. */
	private static PLAYER seatAtPosition(Table_Cards tc, int position) {
		PLAYER seat = tc.who_leads;
		for (int i = 1; i < position; i++) {
			seat = seat.Next_Dealer();
		}
		return seat;
	}

	/** Position (1-4) `seat` occupies in the trick in progress, or -1. */
	private static int positionOf(Table_Cards tc, PLAYER seat) {
		PLAYER p = tc.who_leads;
		for (int i = 1; i <= 4; i++) {
			if (p == seat) {
				return i;
			}
			p = p.Next_Dealer();
		}
		return -1;
	}

	/**
	 * Could `shooter` still end up winning the trick in progress? If they've
	 * already played and are being beaten, they cannot -- whatever point card
	 * lands in this trick goes to somebody else, so there's no reason to
	 * withhold a discard. That distinction matters: without it a player would
	 * hoard Hearts all hand long and end up force-feeding them later anyway.
	 *
	 * `playPosition` is the position the ACTING player is about to play into.
	 */
	public static boolean canStillWinTrick(Table_Cards tc, PLAYER shooter, int playPosition, Suit leadSuit) {
		int shooterPos = positionOf(tc, shooter);
		if (shooterPos < 0 || shooterPos >= playPosition) {
			return true; // hasn't played yet this trick
		}
		Card shooterCard = cardPlayedAt(tc, shooterPos);
		if (shooterCard == null || shooterCard.suit != leadSuit) {
			return false; // they discarded off-suit -- can't win it
		}
		// Already played and following suit: still winning only if nothing
		// played so far beats them.
		for (int i = 1; i < playPosition; i++) {
			if (i == shooterPos) {
				continue;
			}
			Card c = cardPlayedAt(tc, i);
			if (c != null && c.suit == leadSuit
					&& c.cardValue.compareTo(shooterCard.cardValue) > 0) {
				return false;
			}
		}
		return true;
	}

	/** Hearts in the shooter's pile that make an advanced player act now even
	 * while holding a stopper (see breakTheShotWith). */
	static final int URGENT_SHOOTER_HEARTS = 4;
	/** At this many cards left in hand (or fewer) a stopper may get squeezed
	 * out, so an advanced player stops waiting. */
	static final int URGENT_CARDS_LEFT = 4;

	/**
	 * MOON-DEFENSE LADDER (2026-10-17, user design from live hand 4): should `me`
	 * take this trick NOW to break `shooter`'s moon, and with which card? Returns
	 * the card to take with, or null for "don't take, play normally". Graded by
	 * the player's level -- nervous low-level players panic, advanced ones reason:
	 *
	 *   level 1-2: panic -- once alarmed, grab the point trick with the top card.
	 *   level 3:   don't bother when the shooter has already played and every card
	 *              I could follow with beats theirs (the moon dies whatever I play);
	 *              otherwise take with the cheapest card that beats their card.
	 *   level 4-5: also (a) read the shooter -- their first Heart was dumped on
	 *              them and they never led a sure winner since, or they are leading
	 *              a Heart that can be beaten: not a shooter; and (b) keep a reliable
	 *              STOPPER (the top Heart still out, plus 2+ other cards so it can't
	 *              be squeezed) for later instead of paying now, unless it is urgent
	 *              (shooter has 4+ Hearts or the Queen, or 4 or fewer cards left).
	 *              When they do take with the shooter still to play, they use the
	 *              cheapest card nobody can beat.
	 *
	 * The caller has already checked: I can win the trick, a lone point-taker
	 * threatens me, my MOON_ALARM read says they are a real threat, and they can
	 * still win this trick.
	 */
	public static Card breakTheShotWith(Table_Cards tc, Player me, PLAYER shooter, List<Card> legal, Hand myHand,
			int playPosition, Suit leadSuit, Card highestLegal) {
		int level = me.profile.level;
		if (level <= 2) {
			return highestLegal;
		}
		Card shooterCard = cardPlayedBy(tc, shooter, playPosition);
		boolean shooterPlayed = shooterCard != null && shooterCard.suit == leadSuit;
		Card cheapestOver = null;
		boolean allOver = true;
		if (shooterPlayed) {
			for (Card c : legal) {
				if (c.suit != leadSuit) {
					continue;
				}
				if (c.cardValue.compareTo(shooterCard.cardValue) > 0) {
					if (cheapestOver == null || c.cardValue.compareTo(cheapestOver.cardValue) < 0) {
						cheapestOver = c;
					}
				} else {
					allOver = false;
				}
			}
			if (allOver) {
				return null; // any card I play beats them: the moon dies for free
			}
		}
		if (level >= 4) {
			if (showsNoMoon(tc, shooter, myHand, playPosition)) {
				return null;
			}
			if (holdsReliableStopper(tc, myHand, playPosition) && !urgent(tc, shooter, myHand)) {
				return null;
			}
			if (!shooterPlayed) {
				Card sure = cheapestSureWinner(tc, legal, myHand, playPosition, leadSuit);
				return sure != null ? sure : highestLegal;
			}
		}
		return shooterPlayed && cheapestOver != null ? cheapestOver : highestLegal;
	}

	/** Public evidence that `shooter` is not really shooting: their first Heart
	 * was dumped on them and they have not led a sure winner since, or they led
	 * this trick with a Heart that can still be beaten (a shooter leads winners). */
	static boolean showsNoMoon(Table_Cards tc, PLAYER shooter, Hand myHand, int playPosition) {
		Player s = tc.players.get(shooter);
		if (s != null && s.heartDiscardSuit != null && !s.ledSureWinner
				&& !Scoring.hasQueenOfSpades(s.won_cards)) {
			return true;
		}
		if (tc.who_leads == shooter && tc.p1_card != null && tc.p1_card.suit == Suit.HEART) {
			boolean iBeatIt = false;
			if (myHand != null) {
				for (Card c : myHand.hand) {
					iBeatIt |= c.suit == Suit.HEART && c.cardValue.compareTo(tc.p1_card.cardValue) > 0;
				}
			}
			return iBeatIt || CardCounter.anyAboveUnaccounted(tc, myHand == null ? new java.util.ArrayList<Card>()
					: myHand.hand, playPosition, Suit.HEART, tc.p1_card.cardValue);
		}
		return false;
	}

	/** I hold the highest Heart still out and at least two non-Hearts, so I can
	 * break the moon later and can't be squeezed into throwing the stopper away. */
	static boolean holdsReliableStopper(Table_Cards tc, Hand myHand, int playPosition) {
		if (myHand == null) {
			return false;
		}
		Card top = null;
		int others = 0;
		for (Card c : myHand.hand) {
			if (c.suit == Suit.HEART) {
				if (top == null || c.cardValue.compareTo(top.cardValue) > 0) {
					top = c;
				}
			} else {
				others++;
			}
		}
		return top != null && others >= 2
				&& !CardCounter.anyAboveUnaccounted(tc, myHand.hand, playPosition, Suit.HEART, top.cardValue);
	}

	private static boolean urgent(Table_Cards tc, PLAYER shooter, Hand myHand) {
		Player s = tc.players.get(shooter);
		if (s != null && (Scoring.countHearts(s.won_cards) >= URGENT_SHOOTER_HEARTS
				|| Scoring.hasQueenOfSpades(s.won_cards))) {
			return true;
		}
		return myHand != null && myHand.hand.size() <= URGENT_CARDS_LEFT;
	}

	/** Lowest legal card of the led suit that nothing still out can beat. */
	private static Card cheapestSureWinner(Table_Cards tc, List<Card> legal, Hand myHand, int playPosition,
			Suit leadSuit) {
		Card best = null;
		List<Card> mine = myHand == null ? legal : myHand.hand;
		for (Card c : legal) {
			if (c.suit == leadSuit && !CardCounter.anyAboveUnaccounted(tc, mine, playPosition, leadSuit, c.cardValue)
					&& (best == null || c.cardValue.compareTo(best.cardValue) < 0)) {
				best = c;
			}
		}
		return best;
	}

	/** The card `shooter` has already played into this trick, or null if they
	 * have not played yet (they act after position `playPosition - 1`). */
	public static Card cardPlayedBy(Table_Cards tc, PLAYER shooter, int playPosition) {
		int pos = positionOf(tc, shooter);
		if (pos < 0 || pos >= playPosition) {
			return null;
		}
		return cardPlayedAt(tc, pos);
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

	/**
	 * Is deliberately capturing the Queen of Spades to deny a shooter worth
	 * it? Taking it is +13, so this is the last resort in the stopping order
	 * -- redirect first, take a Heart second, this only if nothing cheaper
	 * works. Confirmed against real Hearts strategy consensus (2026-08-16):
	 * experienced play strongly prefers denying via a cheap Heart, and
	 * "opponents will tend to allow you to take the Queen of Spades even if
	 * they suspect you're trying to shoot the moon" -- swallowing her
	 * defensively is a rare last resort, not a routine reaction.
	 *
	 * Two refinements on top of the original standings check, both from a
	 * 2026-08-16 user correction:
	 *
	 * 1. JACK-OF-DIAMONDS OFFSET (this ruleset's own Omnibus twist, no
	 *    standard-Hearts analog): the Jack's -10 can cancel most of the
	 *    Queen's +13 if this seat also has a realistic shot at it --
	 *    jackOfDiamondsOffsetCredit() discounts the effective cost before
	 *    anything else runs.
	 *
	 * 2. LAST-RESORT TIMING GATE: the standings check alone was too willing
	 *    to swallow the Queen on a bare suspicion. Now requires the shot to
	 *    look genuinely real -- the shooter already holding a majority of
	 *    the Hearts (HEARTS_URGENCY_THRESHOLD) -- UNLESS the Jack-discounted
	 *    cost is already cheap enough that timing barely matters.
	 *
	 * Only past both of those does the original question still apply: how
	 * the score lands across ALL FOUR players, not just me against the
	 * shooter. Absorbing the (possibly discounted) cost can hand a THIRD
	 * player the lead I was holding, which is a genuine loss even though the
	 * moon got stopped -- so this still refuses when swallowing the Queen
	 * would drop me behind an opponent I'm currently level with or ahead of.
	 */
	public static boolean queenDenialWorthIt(Table_Cards tc, Player me, PLAYER shooter, Hand myHand) {
		return queenDenialWorthIt(tc, me, shooter, myHand, 0);
	}

	/** `trickHearts`: Hearts on the trick that would take the Queen, which the
	 * stopper takes with her (13 is the minimum, Wayne 2026-11-09). */
	public static boolean queenDenialWorthIt(Table_Cards tc, Player me, PLAYER shooter, Hand myHand,
			int trickHearts) {
		double jackCredit = jackOfDiamondsOffsetCredit(tc, myHand);
		double netCost = QUEEN_OF_SPADES_COST - jackCredit;

		if (netCost > CHEAP_ENOUGH_NET_COST) {
			int shooterHearts = Scoring.countHearts(AI.wonCardsByPlayer(tc).get(shooter));
			if (shooterHearts < HEARTS_URGENCY_THRESHOLD) {
				return false; // too early to be sure the shot is real -- let it ride
			}
		}

		return stoppingBeatsLettingItGo(tc, me, shooter, netCost + trickHearts);
	}

	/** At or below this total a leader may still spend points stopping a moon;
	 * above it a player is "fighting the 100 wall" (Wayne, 2026-11-09). */
	static final int STOPPER_MAX_TOTAL = 50;

	/**
	 * KEEP MY LEAD (2026-11-09, Wayne's rule). Every point a stopper takes moves
	 * it toward the 100 wall, so the default is to let a moon go. A moon can't
	 * end the game (everyone else scores 0 that hand). The one case worth
	 * paying for: I'm in 1st, the moon would put the shooter ahead of me, and
	 * taking the Queen (plus any Hearts on her trick, `queenCost`, Jack-
	 * discounted) keeps me in 1st -- the more Hearts the shooter has already
	 * taken, the more that happens, since they now count against them. Never
	 * above STOPPER_MAX_TOTAL. Earlier the same day this compared the gap to
	 * the leader both ways, which spent points the wall can't afford.
	 *
	 *   Let it go: the shooter scores -26 (-36 with the Jack), everyone else 0
	 *   (a Jack already won still counts -10).
	 *   Stop it:   I take `queenCost`; everyone keeps what they have won. (While
	 *   a moon is on, the stopper has no Hearts of its own -- only a Jack,
	 *   which counts the same both ways.)
	 * Package-private for QueenDenialTest.
	 */
	static boolean stoppingBeatsLettingItGo(Table_Cards tc, Player me, PLAYER shooter, double queenCost) {
		if (me.p_total_points > STOPPER_MAX_TOTAL) {
			return false;
		}
		Map<PLAYER, Integer> soFar = Scoring.computeHandScores(AI.wonCardsByPlayer(tc));
		Map<PLAYER, Integer> before = new EnumMap<PLAYER, Integer>(PLAYER.class);
		Map<PLAYER, Integer> letGo = new EnumMap<PLAYER, Integer>(PLAYER.class);
		Map<PLAYER, Integer> stop = new EnumMap<PLAYER, Integer>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = p == me.tbl_player ? me : tc.players.get(p);
			if (pl == null) {
				continue;
			}
			Hand won = AI.wonCardsByPlayer(tc).get(p);
			boolean hasJack = won != null && Scoring.hasJackOfDiamonds(won);
			int moonScore = (p == shooter ? Scoring.SHOOT_THE_MOON_POINTS : 0)
					+ (hasJack ? Scoring.JACK_OF_DIAMONDS_POINTS : 0);
			int now = soFar.containsKey(p) ? soFar.get(p).intValue() : 0;
			int stopScore = p == me.tbl_player ? now + (int) Math.round(queenCost) : now;
			before.put(p, Integer.valueOf(pl.p_total_points));
			letGo.put(p, Integer.valueOf(pl.p_total_points + moonScore));
			stop.put(p, Integer.valueOf(pl.p_total_points + stopScore));
		}
		int mine = stop.get(me.tbl_player).intValue();
		if (mine >= Hearts_JFrame.TARGET_SCORE) {
			return false;
		}
		return inFirst(before, me.tbl_player) && letGo.get(shooter).intValue() < letGo.get(me.tbl_player).intValue()
				&& inFirst(stop, me.tbl_player);
	}

	/** Lowest total, ties included. */
	private static boolean inFirst(Map<PLAYER, Integer> totals, PLAYER me) {
		int mine = totals.get(me).intValue();
		for (Map.Entry<PLAYER, Integer> e : totals.entrySet()) {
			if (e.getKey() != me && e.getValue().intValue() < mine) {
				return false;
			}
		}
		return true;
	}

	/** List<Card> overload for callers on the lead side, where the hand is
	 * already a List<Card> (LeadContext.legalOptions IS the full hand while
	 * leading -- no suit restriction) rather than a Hand. Added 2026-08-16
	 * so highSpadeLeadRisksTheQueen() can route through this same standard
	 * instead of its own unguarded shooter check. */
	public static boolean queenDenialWorthIt(Table_Cards tc, Player me, PLAYER shooter,
			java.util.List<Card> myCards) {
		Hand h = new Hand();
		for (Card c : myCards) {
			h.addX(c);
		}
		return queenDenialWorthIt(tc, me, shooter, h);
	}

	/** See queenDenialWorthIt()'s point 1. Zero once the Jack is already
	 * captured (moot either way). Package-private for QueenDenialTest. */
	static double jackOfDiamondsOffsetCredit(Table_Cards tc, Hand myHand) {
		if (Scoring.hasJackBeenCaptured(AI.wonCardsByPlayer(tc))) {
			return 0;
		}
		if (HandEvaluator.hasJackOfDiamondsProtection(myHand)) {
			return JACK_HELD_PROTECTED_CREDIT;
		}
		if (CardCounter.holds(myHand.hand, CardValue.ACE, Suit.DIAMOND)
				|| CardCounter.holds(myHand.hand, CardValue.KING, Suit.DIAMOND)) {
			return JACK_CAPTURE_WEAPON_CREDIT;
		}
		return 0;
	}

	/** True if `suit` is one the shooter is already known void in -- leading
	 * it means they cannot win the trick, so the danger lands on a
	 * less-threatening opponent instead. This is the cheapest stopping
	 * tactic: it costs nothing and takes no points. */
	public static boolean shooterVoidIn(Table_Cards tc, PLAYER shooter, Suit suit) {
		return VoidTracker.isVoid(tc, shooter, suit);
	}

	/** Unused today -- kept because seatAtPosition() documents the
	 * who_leads-relative seat ordering the rest of this class depends on. */
	static PLAYER leaderOfTrick(Table_Cards tc) {
		return seatAtPosition(tc, 1);
	}
}
