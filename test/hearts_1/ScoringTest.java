package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for Scoring.java's Omnibus rules, added
 * 2026-08-15. Replaces the ad-hoc "8 constructed scenarios" the 2026-07-30
 * Scoring.java header comment describes as having verified the class
 * originally -- those were never committed. See CHANGELOG.md's 2026-08-09
 * rules-audit entry for the real-session-log version of this same check
 * (also now committed, as LogReplayer).
 */
public final class ScoringTest {

	private ScoringTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		trickWinnerFollowsLeadSuit();
		trickWinnerIgnoresOffSuitDiscards();
		jackOfDiamondsIsIndependentNegativeBonus();
		nonMoonHandConservesSixteenPoints();
		moonShotReplacesHeartsAndQueenScoring();
		moonShotStacksWithJackForShooter();
		tragicMoonShotConsolationForNonShooter();
		inProgressMoonShooterTracksLoneThreat();
		hasJackBeenCapturedDetection();
		return TestKit.summarize();
	}

	private static void trickWinnerFollowsLeadSuit() {
		TestKit.suite("trickWinnerFollowsLeadSuit");
		Table_Cards tc = new Table_Cards();
		tc.p1_card = card(CardValue.FIVE, Suit.CLUB);
		tc.n_card = card(CardValue.FIVE, Suit.CLUB);
		tc.e_card = card(CardValue.KING, Suit.CLUB);
		tc.s_card = card(CardValue.TWO, Suit.HEART); // off-suit discard, cannot win
		tc.w_card = card(CardValue.NINE, Suit.CLUB);
		TestKit.checkEquals("highest Club wins, off-suit Heart discard ignored",
				PLAYER.EAST, Scoring.determineTrickWinner(tc));
	}

	private static void trickWinnerIgnoresOffSuitDiscards() {
		TestKit.suite("trickWinnerIgnoresOffSuitDiscards");
		Table_Cards tc = new Table_Cards();
		tc.p1_card = card(CardValue.THREE, Suit.SPADE);
		tc.n_card = card(CardValue.THREE, Suit.SPADE);
		tc.e_card = card(CardValue.ACE, Suit.HEART); // void in Spades, discards a bomb -- still can't win
		tc.s_card = card(CardValue.QUEEN, Suit.SPADE);
		tc.w_card = card(CardValue.TWO, Suit.SPADE);
		TestKit.checkEquals("Queen of Spades wins a Spade-led trick despite an off-suit Ace discard",
				PLAYER.SOUTH, Scoring.determineTrickWinner(tc));
	}

	private static void jackOfDiamondsIsIndependentNegativeBonus() {
		TestKit.suite("jackOfDiamondsIsIndependentNegativeBonus");
		Map<PLAYER, Hand> won = emptyHands();
		addAll(won.get(PLAYER.WEST), card(CardValue.JACK, Suit.DIAMOND), card(CardValue.TWO, Suit.CLUB));
		addAll(won.get(PLAYER.NORTH), card(CardValue.ACE, Suit.HEART)); // +1, unrelated to the Jack
		Map<PLAYER, Integer> scores = Scoring.computeHandScores(won);
		TestKit.checkEquals("Jack of Diamonds alone is worth -10", Integer.valueOf(-10), scores.get(PLAYER.WEST));
		TestKit.checkEquals("a lone captured Heart is worth +1", Integer.valueOf(1), scores.get(PLAYER.NORTH));
	}

	private static void nonMoonHandConservesSixteenPoints() {
		TestKit.suite("nonMoonHandConservesSixteenPoints");
		Map<PLAYER, Hand> won = emptyHands();
		// 13 Hearts + Queen of Spades = 26 raw points, split across 3 seats,
		// Jack of Diamonds (-10) landing on a 4th -- net must be exactly 16
		// regardless of who took what (Scoring.java's Omnibus header comment,
		// verified empirically in CHANGELOG.md's 2026-08-09 audit).
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR, CardValue.FIVE,
				CardValue.SIX, CardValue.SEVEN }) {
			won.get(PLAYER.NORTH).addX(card(v, Suit.HEART));
		}
		for (CardValue v : new CardValue[] { CardValue.EIGHT, CardValue.NINE, CardValue.TEN, CardValue.JACK,
				CardValue.QUEEN, CardValue.KING, CardValue.ACE }) {
			won.get(PLAYER.EAST).addX(card(v, Suit.HEART));
		}
		won.get(PLAYER.SOUTH).addX(card(CardValue.QUEEN, Suit.SPADE));
		won.get(PLAYER.WEST).addX(card(CardValue.JACK, Suit.DIAMOND));

		Map<PLAYER, Integer> scores = Scoring.computeHandScores(won);
		int sum = 0;
		for (int s : scores.values()) {
			sum += s;
		}
		TestKit.checkEquals("non-moon hand's 4 scores sum to 16 (26 - 10)", Integer.valueOf(16), Integer.valueOf(sum));
	}

	private static void moonShotReplacesHeartsAndQueenScoring() {
		TestKit.suite("moonShotReplacesHeartsAndQueenScoring");
		Map<PLAYER, Hand> won = emptyHands();
		for (CardValue v : CardValue.values()) {
			won.get(PLAYER.NORTH).addX(card(v, Suit.HEART));
		}
		won.get(PLAYER.NORTH).addX(card(CardValue.QUEEN, Suit.SPADE));

		Map<PLAYER, Integer> scores = Scoring.computeHandScores(won);
		TestKit.checkEquals("moon shooter scores -26", Integer.valueOf(-26), scores.get(PLAYER.NORTH));
		TestKit.checkEquals("non-shooter scores 0 for Hearts/Queen", Integer.valueOf(0), scores.get(PLAYER.EAST));
		TestKit.checkEquals("non-shooter scores 0 for Hearts/Queen", Integer.valueOf(0), scores.get(PLAYER.SOUTH));
		TestKit.checkEquals("non-shooter scores 0 for Hearts/Queen", Integer.valueOf(0), scores.get(PLAYER.WEST));
	}

	private static void moonShotStacksWithJackForShooter() {
		TestKit.suite("moonShotStacksWithJackForShooter");
		Map<PLAYER, Hand> won = emptyHands();
		for (CardValue v : CardValue.values()) {
			won.get(PLAYER.WEST).addX(card(v, Suit.HEART));
		}
		won.get(PLAYER.WEST).addX(card(CardValue.QUEEN, Suit.SPADE));
		won.get(PLAYER.WEST).addX(card(CardValue.JACK, Suit.DIAMOND));

		Map<PLAYER, Integer> scores = Scoring.computeHandScores(won);
		TestKit.checkEquals("moon shooter who also takes the Jack scores -36 (-26 + -10)",
				Integer.valueOf(-36), scores.get(PLAYER.WEST));
	}

	private static void tragicMoonShotConsolationForNonShooter() {
		TestKit.suite("tragicMoonShotConsolationForNonShooter");
		Map<PLAYER, Hand> won = emptyHands();
		for (CardValue v : CardValue.values()) {
			won.get(PLAYER.SOUTH).addX(card(v, Suit.HEART));
		}
		won.get(PLAYER.SOUTH).addX(card(CardValue.QUEEN, Suit.SPADE));
		won.get(PLAYER.EAST).addX(card(CardValue.JACK, Suit.DIAMOND)); // grabbed away from the shooter

		Map<PLAYER, Integer> scores = Scoring.computeHandScores(won);
		TestKit.checkEquals("moon shooter still scores -26 even without the Jack",
				Integer.valueOf(-26), scores.get(PLAYER.SOUTH));
		TestKit.checkEquals("the seat that denied the Jack banks -10 alone, unaffected by the moon",
				Integer.valueOf(-10), scores.get(PLAYER.EAST));
	}

	private static void inProgressMoonShooterTracksLoneThreat() {
		TestKit.suite("inProgressMoonShooterTracksLoneThreat");
		Map<PLAYER, Hand> won = emptyHands();
		TestKit.checkEquals("no shooter visible when nobody has captured a point card yet",
				null, Scoring.inProgressMoonShooter(won));

		won.get(PLAYER.NORTH).addX(card(CardValue.TWO, Suit.HEART));
		TestKit.checkEquals("a lone seat holding a Heart is the visible in-progress shooter",
				PLAYER.NORTH, Scoring.inProgressMoonShooter(won));

		won.get(PLAYER.EAST).addX(card(CardValue.THREE, Suit.HEART));
		TestKit.checkEquals("a second seat capturing a point card breaks the lone-threat read",
				null, Scoring.inProgressMoonShooter(won));
	}

	private static void hasJackBeenCapturedDetection() {
		TestKit.suite("hasJackBeenCapturedDetection");
		Map<PLAYER, Hand> won = emptyHands();
		TestKit.check("Jack not yet captured by anyone", !Scoring.hasJackBeenCaptured(won));
		won.get(PLAYER.WEST).addX(card(CardValue.JACK, Suit.DIAMOND));
		TestKit.check("Jack captured once any seat holds it", Scoring.hasJackBeenCaptured(won));
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static void addAll(Hand hand, Card... cards) {
		for (Card c : cards) {
			hand.addX(c);
		}
	}

	private static Map<PLAYER, Hand> emptyHands() {
		Map<PLAYER, Hand> hands = new EnumMap<PLAYER, Hand>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			hands.put(p, new Hand());
		}
		return hands;
	}
}
