package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Committed regression coverage for HandEvaluator's moon-shot-attempt
 * decision, added 2026-08-15 -- pins down the two live-play bugs fixed
 * 2026-08-09/08-10 (see CHANGELOG.md) so neither can silently return: the
 * unbounded standings term, and Heart control being a bonus instead of a
 * hard precondition. Replaces the old uncommitted MoonThresholdCheck (8
 * scenarios) and half of MoonGateCheck/MoonGatePositive.
 */
public final class MoonThresholdTest {

	private MoonThresholdTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		heartControlGateRequiresAceOrKingWithLength();
		heartlessHandNeverAttemptsRegardlessOfStandings();
		desperateStandingsClampedToDocumentedFloor();
		comfortableLeaderClampedToDocumentedCeiling();
		zeroWeightTypeNeverAttempts();
		endGameDangerRaisesThresholdNearTarget();
		return TestKit.summarize();
	}

	private static void heartControlGateRequiresAceOrKingWithLength() {
		TestKit.suite("heartControlGateRequiresAceOrKingWithLength");
		TestKit.check("Ace + exactly 4 Hearts passes the gate (boundary)",
				hasMoonHeartControl(heartsHand(CardValue.ACE, CardValue.TWO, CardValue.THREE, CardValue.FOUR)));
		TestKit.check("Ace + only 3 Hearts fails the gate",
				!hasMoonHeartControl(heartsHand(CardValue.ACE, CardValue.TWO, CardValue.THREE)));
		TestKit.check("King + exactly 5 Hearts (no Ace) passes the gate (boundary)",
				hasMoonHeartControl(heartsHand(CardValue.KING, CardValue.TWO, CardValue.THREE, CardValue.FOUR, CardValue.FIVE)));
		TestKit.check("King + only 4 Hearts (no Ace) fails the gate",
				!hasMoonHeartControl(heartsHand(CardValue.KING, CardValue.TWO, CardValue.THREE, CardValue.FOUR)));
		TestKit.check("no Ace and no King never passes the gate regardless of length",
				!hasMoonHeartControl(heartsHand(CardValue.TWO, CardValue.THREE, CardValue.FOUR, CardValue.FIVE, CardValue.SIX, CardValue.SEVEN)));
	}

	/** The real 2026-08-10 bug: NORTH committed holding 3H 7H JH (no Ace,
	 * King, or Queen of Hearts) because Heart strength was scored as a
	 * bonus, not gated -- Spade control alone cleared the threshold. */
	private static void heartlessHandNeverAttemptsRegardlessOfStandings() {
		TestKit.suite("heartlessHandNeverAttemptsRegardlessOfStandings");
		Hand strongSpadesNoHeartControl = new Hand();
		addAll(strongSpadesNoHeartControl,
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE),
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.JACK, Suit.SPADE),
				card(CardValue.TEN, Suit.SPADE), card(CardValue.NINE, Suit.SPADE),
				card(CardValue.THREE, Suit.HEART), card(CardValue.SEVEN, Suit.HEART), card(CardValue.JACK, Suit.HEART));

		Table_Cards tc = tableWithStandings(PLAYER.SOUTH, 100, 0, 0, 0); // as desperate as it gets
		Player me = playerOf(PLAYER.SOUTH, Player_Type.NORMAL, 100);
		TestKit.check("a heart-control-gated hand never attempts a moon shot, no matter how desperate the standings",
				!HandEvaluator.shouldAttemptMoonShot(strongSpadesNoHeartControl, tc, me));
	}

	private static void desperateStandingsClampedToDocumentedFloor() {
		TestKit.suite("desperateStandingsClampedToDocumentedFloor");
		// MOON_SHOOTER's documented range is 14 down to 12 (CHANGELOG.md,
		// 2026-08-09 FIXED entry) -- the floor is what's under test here, so
		// pick a hand whose quality sits between the unclamped-desperate
		// value and the floor, and confirm the floor still lets it through
		// (quality >= 12) while the moon-shooter's usual undiscounted 14
		// would NOT (proving the standings term is actually doing something,
		// not just coincidentally irrelevant).
		// Ace + exactly 4 Hearts clears the gate at its floor (quality 8 on
		// its own -- see moonShotHandQuality's arithmetic); adding the King
		// of Spades (+4, no isolated-suit penalty since it's >= TEN) lands
		// exactly on 12, MOON_SHOOTER's documented floor.
		Hand hand = heartsHand(CardValue.ACE, CardValue.TWO, CardValue.THREE, CardValue.FOUR);
		hand.addX(card(CardValue.KING, Suit.SPADE));
		int quality = HandEvaluator.moonShotHandQuality(hand);
		TestKit.check("test hand's quality sits in the 12-13 pin (adjust fixture if HandEvaluator scoring changes)",
				quality >= 12 && quality < 14);

		// meTotal is 30, not 100 -- MAX_STANDING_DISCOUNT clamps at a delta of
		// 20 either way (0.5 * 20 = 10), so this is exactly as "desperate" as
		// 100 for the term under test, but stays outside EndGame's 25-point
		// danger zone (see EndGame.DANGER_ZONE) so the 2026-08-15 end-of-game
		// caution term (a SEPARATE, deliberately additive effect -- see
		// endGameDangerRaisesThresholdNearTarget() below) can't also fire and
		// confound this floor-clamp assertion.
		Table_Cards desperate = tableWithStandings(PLAYER.SOUTH, 30, 0, 0, 0);
		Player desperateShooter = playerOf(PLAYER.SOUTH, Player_Type.MOON_SHOOTER, 30);
		TestKit.check("desperate standings drop MOON_SHOOTER's threshold to the floor (12), letting a quality-"
				+ quality + " hand through",
				HandEvaluator.shouldAttemptMoonShot(hand, desperate, desperateShooter));

		Table_Cards level = tableWithStandings(PLAYER.SOUTH, 0, 0, 0, 0);
		Player levelShooter = playerOf(PLAYER.SOUTH, Player_Type.MOON_SHOOTER, 0);
		TestKit.check("at level standings the same quality-" + quality + " hand stays below MOON_SHOOTER's 14",
				!HandEvaluator.shouldAttemptMoonShot(hand, level, levelShooter));
	}

	private static void comfortableLeaderClampedToDocumentedCeiling() {
		TestKit.suite("comfortableLeaderClampedToDocumentedCeiling");
		// NORMAL/AGGRESSIVE's documented range is 26 down to 16, capping at
		// 36 for a comfortable leader (CHANGELOG.md). Build a hand strong
		// enough to clear 26 (would attempt at level standings) but confirm
		// a deeply-ahead leader raises the bar past it.
		Hand strongHand = new Hand();
		addAll(strongHand, card(CardValue.ACE, Suit.HEART), card(CardValue.KING, Suit.HEART),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE), card(CardValue.QUEEN, Suit.SPADE));
		int quality = HandEvaluator.moonShotHandQuality(strongHand);
		TestKit.check("fixture hand clears the undiscounted NORMAL threshold of 26", quality >= 26);

		Table_Cards level = tableWithStandings(PLAYER.SOUTH, 0, 0, 0, 0);
		Player levelPlayer = playerOf(PLAYER.SOUTH, Player_Type.NORMAL, 0);
		TestKit.check("at level standings the strong hand clears the threshold",
				HandEvaluator.shouldAttemptMoonShot(strongHand, level, levelPlayer));

		if (quality < 36) {
			Table_Cards comfortable = tableWithStandings(PLAYER.SOUTH, 0, 100, 100, 100);
			Player comfortableLeader = playerOf(PLAYER.SOUTH, Player_Type.NORMAL, 0);
			TestKit.check("a comfortable leader's raised threshold (up to 36) declines the same hand",
					!HandEvaluator.shouldAttemptMoonShot(strongHand, comfortable, comfortableLeader));
		}
	}

	/** EndGame.java, added 2026-08-15: proximity to TARGET_SCORE raises the
	 * threshold on top of (not instead of) the standings-delta term above --
	 * a seat can be maximally desperate in the RELATIVE sense (huge deficit
	 * against the field, same clamped -10 standings adjustment either way)
	 * and still get a stricter bar once its own absolute total is close
	 * enough to end the game. Reuses the same quality-12 hand and
	 * MOON_SHOOTER shooter as the floor test above -- only meTotal changes. */
	private static void endGameDangerRaisesThresholdNearTarget() {
		TestKit.suite("endGameDangerRaisesThresholdNearTarget");
		Hand hand = heartsHand(CardValue.ACE, CardValue.TWO, CardValue.THREE, CardValue.FOUR);
		hand.addX(card(CardValue.KING, Suit.SPADE));
		int quality = HandEvaluator.moonShotHandQuality(hand);
		TestKit.check("fixture hand quality unchanged from the floor test (12)", quality == 12);

		// meTotal=90: margin 10 of EndGame's 25-point zone -> danger 0.6 ->
		// +8.4 to the threshold (14 base - 10 clamped standings + 8.4 danger
		// = 12.4). Same -10 clamped standings adjustment as the meTotal=30
		// floor case (others still 0,0,0, delta still well past the clamp),
		// so the ONLY thing that changed is proximity to the target -- and
		// that alone is now enough to push the quality-12 hand below bar.
		Table_Cards nearTarget = tableWithStandings(PLAYER.SOUTH, 90, 0, 0, 0);
		Player nearTargetShooter = playerOf(PLAYER.SOUTH, Player_Type.MOON_SHOOTER, 90);
		TestKit.check("the same quality-" + quality + " hand that attempts at meTotal=30 "
				+ "declines at meTotal=90 -- danger outweighs the standings floor",
				!HandEvaluator.shouldAttemptMoonShot(hand, nearTarget, nearTargetShooter));

		// meTotal=100 -- the exact fixture the pre-2026-08-15 version of this
		// suite used for "as desperate as it gets" (see the floor test's
		// comment). Danger is now 1.0 (margin 0), pushing the threshold to
		// 14 - 10 + 14 = 18 -- this is the documented semantic change: being
		// AT the target is no longer just "desperate", it's also maximally
		// dangerous, and the second effect now dominates the first.
		Table_Cards atTarget = tableWithStandings(PLAYER.SOUTH, 100, 0, 0, 0);
		Player atTargetShooter = playerOf(PLAYER.SOUTH, Player_Type.MOON_SHOOTER, 100);
		TestKit.check("at meTotal=100 (the target itself) the quality-" + quality + " hand declines",
				!HandEvaluator.shouldAttemptMoonShot(hand, atTarget, atTargetShooter));
	}

	private static void zeroWeightTypeNeverAttempts() {
		TestKit.suite("zeroWeightTypeNeverAttempts");
		Hand monsterHand = new Hand();
		addAll(monsterHand, card(CardValue.ACE, Suit.HEART), card(CardValue.KING, Suit.HEART),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE));
		Table_Cards desperate = tableWithStandings(PLAYER.SOUTH, 100, 0, 0, 0);
		Player cautious = playerOf(PLAYER.SOUTH, Player_Type.CAUTIOUS, 100); // MOON_OFFENSE weight 0
		TestKit.check("a type with MOON_OFFENSE weight 0 never attempts, regardless of hand or standings",
				!HandEvaluator.shouldAttemptMoonShot(monsterHand, desperate, cautious));
	}

	// ------------------------------------------------------------------

	private static boolean hasMoonHeartControl(Hand hand) {
		// HandEvaluator.hasMoonHeartControl(Hand) is package-private (see its
		// javadoc: "so the moon-decision tests can exercise it directly") --
		// this file is in package hearts_1 specifically so that access works.
		return HandEvaluator.hasMoonHeartControl(hand);
	}

	private static Hand heartsHand(CardValue... hearts) {
		Hand h = new Hand();
		for (CardValue v : hearts) {
			h.addX(card(v, Suit.HEART));
		}
		return h;
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static void addAll(Hand hand, Card... cards) {
		for (Card c : cards) {
			hand.addX(c);
		}
	}

	private static Player playerOf(PLAYER seat, Player_Type type, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.Set_Player(type);
		p.p_total_points = totalPoints;
		return p;
	}

	private static Table_Cards tableWithStandings(PLAYER me, int meTotal, int t1, int t2, int t3) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		int[] totals = { meTotal, t1, t2, t3 };
		int i = 0;
		players.put(me, playerOf(me, Player_Type.NORMAL, totals[i++]));
		for (PLAYER p : PLAYER.values()) {
			if (p != me) {
				players.put(p, playerOf(p, Player_Type.NORMAL, totals[i++]));
			}
		}
		tc.players = players;
		return tc;
	}
}
