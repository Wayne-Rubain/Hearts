package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.roster.RosterPlayer;

/**
 * Coverage for the moon-interest roll (2026-10-17, user design): AIs must try
 * the moon now and then like real people, low levels most, experts least, and
 * more when trailing the leader.
 */
public final class MoonInterestTest {

	private MoonInterestTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		lowerLevelsAreMoreInterested();
		trailingTheLeaderDoublesTheChance();
		zeroStaysZeroAndNeverExceeds100();
		interestLetsACautiousPlayerCommitOnAGoodHand();
		interestStillNeedsHeartControl();
		interestedPlayerPassesToBuildAndKeepsHearts();
		noInterestNoChange();
		return TestKit.summarize();
	}

	private static void lowerLevelsAreMoreInterested() {
		TestKit.suite("lowerLevelsAreMoreInterested");
		for (int level = 1; level < 5; level++) {
			TestKit.check("level " + level + " is at least as interested as level " + (level + 1),
					RosterPlayer.defaultMoonInterest(level) >= RosterPlayer.defaultMoonInterest(level + 1));
		}
		TestKit.check("even an expert has some interest", RosterPlayer.defaultMoonInterest(5) > 0);
	}

	private static void trailingTheLeaderDoublesTheChance() {
		TestKit.suite("trailingTheLeaderDoublesTheChance");
		Table_Cards tc = table(0, 0, 0, 0);
		TestKit.checkEquals("level standings leave the base chance",
				Integer.valueOf(10), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 10))));
		tc = table(40, 40, 0, 40);
		TestKit.checkEquals("leading never lowers it",
				Integer.valueOf(10), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 10))));
		tc = table(0, 50, 30, 50);
		TestKit.checkEquals("30 behind the leader doubles it",
				Integer.valueOf(20), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 10))));
		tc = table(0, 0, 200, 0);
		TestKit.checkEquals("a huge gap never goes past double",
				Integer.valueOf(20), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 10))));
		tc = table(0, 0, 15, 0);
		TestKit.checkEquals("15 behind is halfway to double",
				Integer.valueOf(15), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 10))));
	}

	private static void zeroStaysZeroAndNeverExceeds100() {
		TestKit.suite("zeroStaysZeroAndNeverExceeds100");
		Table_Cards tc = table(0, 0, 60, 0);
		TestKit.checkEquals("zero stays zero however far behind",
				Integer.valueOf(0), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 0))));
		TestKit.check("never above 100",
				Deal_Hand.effectiveMoonInterestPercent(tc, seat(tc, PLAYER.SOUTH, 90)) <= 100);
	}

	private static void interestLetsACautiousPlayerCommitOnAGoodHand() {
		TestKit.suite("interestLetsACautiousPlayerCommitOnAGoodHand");
		Table_Cards tc = table(0, 0, 0, 0);
		Player p = cautious(tc);
		Hand hand = strongHearts();
		TestKit.check("the hand clears the interest bar", HandEvaluator.moonShotHandQuality(hand) >= 12);
		p.moonInterestThisHand = false;
		TestKit.check("Cautious (weight 0) does not commit without interest",
				!HandEvaluator.shouldAttemptMoonShot(hand, tc, p));
		p.moonInterestThisHand = true;
		TestKit.check("Cautious commits when interested", HandEvaluator.shouldAttemptMoonShot(hand, tc, p));
		p.attempting_moon_shot = true;
		TestKit.check("and then plays the shot (moon weight kept alive)",
				TacticWeights.weight(p, TacticWeights.Tactic.MOON_OFFENSE) > 0);
	}

	private static void interestStillNeedsHeartControl() {
		TestKit.suite("interestStillNeedsHeartControl");
		Table_Cards tc = table(0, 0, 0, 0);
		Player p = cautious(tc);
		p.moonInterestThisHand = true;
		Hand weak = new Hand();
		weak.addX(card(CardValue.TWO, Suit.HEART));
		weak.addX(card(CardValue.THREE, Suit.HEART));
		weak.addX(card(CardValue.ACE, Suit.SPADE));
		weak.addX(card(CardValue.KING, Suit.SPADE));
		TestKit.check("no Heart control, no commitment, however interested",
				!HandEvaluator.shouldAttemptMoonShot(weak, tc, p));
	}

	private static void interestedPlayerPassesToBuildAndKeepsHearts() {
		TestKit.suite("interestedPlayerPassesToBuildAndKeepsHearts");
		Table_Cards tc = table(0, 0, 0, 0);
		Player p = cautious(tc);
		// Three Hearts with the Ace, but only 2 of Spades control: one good card short of Heart control.
		Hand hand = new Hand();
		hand.addX(card(CardValue.ACE, Suit.HEART));
		hand.addX(card(CardValue.NINE, Suit.HEART));
		hand.addX(card(CardValue.FIVE, Suit.HEART));
		hand.addX(card(CardValue.ACE, Suit.SPADE));
		hand.addX(card(CardValue.KING, Suit.SPADE));
		hand.addX(card(CardValue.QUEEN, Suit.SPADE));
		hand.addX(card(CardValue.JACK, Suit.SPADE));
		hand.addX(card(CardValue.TWO, Suit.CLUB));
		hand.addX(card(CardValue.FOUR, Suit.CLUB));
		hand.addX(card(CardValue.SIX, Suit.DIAMOND));
		hand.addX(card(CardValue.EIGHT, Suit.DIAMOND));
		hand.addX(card(CardValue.THREE, Suit.DIAMOND));
		hand.addX(card(CardValue.SEVEN, Suit.CLUB));
		p.moonInterestThisHand = false;
		TestKit.check("not interested, no building", !Deal_Hand.buildsTowardMoonShot(hand, p));
		p.moonInterestThisHand = true;
		TestKit.check("interested with a Heart honour and three Hearts builds", Deal_Hand.buildsTowardMoonShot(hand, p));
		java.util.List<Card> passed = Deal_Hand.chooseCardsToPass(hand, tc, p);
		int heartsPassed = 0;
		for (Card c : passed) {
			if (c.suit == Suit.HEART) {
				heartsPassed++;
			}
		}
		TestKit.checkEquals("a building pass gives away three cards", Integer.valueOf(3), Integer.valueOf(passed.size()));
		TestKit.checkEquals("and keeps every Heart", Integer.valueOf(0), Integer.valueOf(heartsPassed));
		Hand bare = new Hand();
		bare.addX(card(CardValue.TWO, Suit.HEART));
		bare.addX(card(CardValue.ACE, Suit.SPADE));
		TestKit.check("two Hearts and no honour: no building", !Deal_Hand.buildsTowardMoonShot(bare, p));
	}

	private static void noInterestNoChange() {
		TestKit.suite("noInterestNoChange");
		Table_Cards tc = table(0, 0, 0, 0);
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL));
		TestKit.checkEquals("a seat with no interest rate rolls nothing",
				Integer.valueOf(0), Integer.valueOf(Deal_Hand.effectiveMoonInterestPercent(tc, p)));
	}

	private static Player cautious(Table_Cards tc) {
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.CAUTIOUS));
		return p;
	}

	private static Hand strongHearts() {
		Hand h = new Hand();
		CardValue[] hearts = { CardValue.ACE, CardValue.KING, CardValue.QUEEN, CardValue.JACK, CardValue.TEN,
				CardValue.NINE, CardValue.EIGHT };
		for (CardValue v : hearts) {
			h.addX(card(v, Suit.HEART));
		}
		h.addX(card(CardValue.ACE, Suit.SPADE));
		h.addX(card(CardValue.KING, Suit.SPADE));
		h.addX(card(CardValue.ACE, Suit.DIAMOND));
		h.addX(card(CardValue.ACE, Suit.CLUB));
		h.addX(card(CardValue.KING, Suit.CLUB));
		return h;
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static Player seat(Table_Cards tc, PLAYER seat, int interest) {
		Player p = tc.players.get(seat);
		p.moonInterestPercent = interest;
		return p;
	}

	private static Table_Cards table(int north, int east, int south, int west) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		int[] totals = { north, east, south, west };
		PLAYER[] seats = { PLAYER.NORTH, PLAYER.EAST, PLAYER.SOUTH, PLAYER.WEST };
		for (int i = 0; i < 4; i++) {
			Player p = new Player();
			p.tbl_player = seats[i];
			p.p_total_points = totals[i];
			players.put(seats[i], p);
		}
		tc.players = players;
		return tc;
	}
}
