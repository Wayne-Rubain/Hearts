package hearts_1;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Queen payback, added 2026-10-10 (user design; revives the long-inert
 * Payback_p1/2/3 traits' idea). A player who gets stuck with the Queen of
 * Spades holds a grudge against whoever did it, and on the next three hands
 * tries to get the Queen to THEM -- strongest on the next hand, weaker on the
 * second, weaker again on the third, then gone (the profile's taper,
 * default 100/60/25).
 *
 * Who is the culprit (user rules):
 *   - whoever played the Queen onto a trick I won; or
 *   - if I played her onto my own trick, whoever PASSED her to me this hand
 *     -- a passed Queen only counts if I end up taking it.
 * Getting stuck by the same player again resets that grudge to full. Two
 * different culprits are both remembered, each fading on its own clock.
 *
 * How much a grudge matters also depends on where the target stands (user
 * rules -- only winning matters): a target in 1st or 2nd is worth full
 * effort; 3rd place only a little; last place not at all -- unless the
 * target is within 15 points of the leader, which puts them back in it.
 *
 * What a grudge does: see AICardScorer's PAYBACK scorer (dump the Queen or
 * Hearts on a trick the target is winning) and Deal_Hand.paybackPassBias()
 * (pass so the target is more likely to end up with the Queen). Giving the
 * Queen to the standings leader always comes first -- the payback bonus
 * never applies on a trick the leader is winning, the existing SOCIAL
 * attack-the-leader scorer owns that one.
 */
public final class Payback {

	private Payback() { }

	/** Default taper: next hand, the one after, the one after that. */
	public static final int[] DEFAULT_TAPER = { 100, 60, 25 };
	/** A target this close to the leader is still in contention, wherever
	 * they sit in the order (user: "within 15 pts from the leader"). */
	static final int CONTENTION_GAP = 15;
	/** 3rd place, not within CONTENTION_GAP of the leader: "don't worry as
	 * much". */
	static final double THIRD_PLACE_RELEVANCE = 0.25;

	/** The taper a template gets when payback is simply switched on (user:
	 * adjust the numbers by player type). Aggressive players hold a grudge
	 * longer; cautious ones let go faster; a moon shooter is busy with its
	 * own plans. */
	static int[] defaultTaper(Profile p) {
		if (p.stylePercent(Player.Player_Traits.Aggressive) >= 50) {
			return new int[] { 100, 80, 50 };
		}
		switch (p.template) {
			case CAUTIOUS: return new int[] { 70, 35, 10 };
			case MOON_SHOOTER: return new int[] { 80, 40, 15 };
			default: return DEFAULT_TAPER.clone();
		}
	}

	/** Called as each trick resolves (Play_Hand and the headless simulator),
	 * BEFORE the next trick overwrites tc's four cards. */
	public static void recordTrick(Table_Cards tc) {
		PLAYER[] seats = { PLAYER.NORTH, PLAYER.EAST, PLAYER.SOUTH, PLAYER.WEST };
		Card[] played = { tc.n_card, tc.e_card, tc.s_card, tc.w_card };
		for (int i = 0; i < seats.length; i++) {
			if (played[i] == null || !Rules.isQueenOfSpades(played[i])) {
				continue;
			}
			PLAYER taker = tc.who_won;
			Player victim = tc.players.get(taker);
			if (victim == null) {
				return;
			}
			PLAYER culprit = seats[i] != taker ? seats[i] : victim.queenPassedBy;
			if (culprit != null && culprit != taker) {
				victim.grudges.put(culprit, Integer.valueOf(0));
			}
			if (culprit != null && culprit != taker && victim.profile.paybackTaper != null) {
				System.out.println("****** PAYBACK " + taker + " now holds a grudge against " + culprit + " ******");
			}
			return;
		}
	}

	/** Start of a hand: every grudge is one hand older; past the taper it's
	 * forgotten. Also clears last hand's passed-Queen note. */
	public static void startHand(Player p) {
		p.queenPassedBy = null;
		for (Iterator<Map.Entry<PLAYER, Integer>> it = p.grudges.entrySet().iterator(); it.hasNext();) {
			Map.Entry<PLAYER, Integer> e = it.next();
			int age = e.getValue().intValue() + 1;
			if (age > 3) {
				it.remove();
			} else {
				e.setValue(Integer.valueOf(age));
			}
		}
	}

	/** How hard (0..1) `me` wants to pay `target` back this hand: the taper
	 * for the grudge's age times the target's standing relevance. 0 for a
	 * player without the payback trait, with no grudge against `target`, or
	 * whose standings rules say the target isn't worth it. */
	public static double strength(Table_Cards tc, Player me, PLAYER target) {
		int[] taper = me.profile.paybackTaper;
		if (taper == null || target == null || target == me.tbl_player) {
			return 0;
		}
		Integer age = me.grudges.get(target);
		if (age == null || age.intValue() < 1 || age.intValue() > taper.length) {
			return 0;
		}
		return taper[age.intValue() - 1] / 100.0 * relevance(tc, target);
	}

	/** Every target `me` holds a live grudge against, with its strength. */
	public static Map<PLAYER, Double> targets(Table_Cards tc, Player me) {
		Map<PLAYER, Double> out = new EnumMap<PLAYER, Double>(PLAYER.class);
		if (me.profile.paybackTaper == null) {
			return out;
		}
		for (PLAYER t : me.grudges.keySet()) {
			double s = strength(tc, me, t);
			if (s > 0) {
				out.put(t, Double.valueOf(s));
			}
		}
		return out;
	}

	/** The user's standings rules. Place 1 = lowest total; ties share the
	 * better place. */
	static double relevance(Table_Cards tc, PLAYER target) {
		Player t = tc.players.get(target);
		if (t == null) {
			return 0;
		}
		int lowest = Integer.MAX_VALUE, better = 0;
		for (Player p : tc.players.values()) {
			lowest = Math.min(lowest, p.p_total_points);
			if (p.p_total_points < t.p_total_points) {
				better++;
			}
		}
		int place = better + 1;
		if (place <= 2 || t.p_total_points - lowest <= CONTENTION_GAP) {
			return 1.0;
		}
		return place == 3 ? THIRD_PLACE_RELEVANCE : 0;
	}

	/** Is `me` currently 1st or 2nd? Passing the Queen to a target is risky
	 * from up there -- they can drop her straight back on an off-suit trick
	 * (user caution). */
	static boolean inTopTwo(Table_Cards tc, Player me) {
		int better = 0;
		for (Player p : tc.players.values()) {
			if (p.p_total_points < me.p_total_points) {
				better++;
			}
		}
		return better < 2;
	}
}
