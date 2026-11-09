package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for MoonThreat.queenDenialWorthIt() and its
 * jackOfDiamondsOffsetCredit() helper, added 2026-08-16 after a user
 * correction grounded in real Hearts strategy consensus ("opponents will
 * tend to allow you to take the Queen of Spades even if they suspect you're
 * shooting the moon" -- swallowing her defensively is a rare last resort,
 * not a routine reaction) plus a ruleset-specific insight (the Jack of
 * Diamonds' -10 can offset most of the Queen's +13 cost for a seat with a
 * realistic shot at it too).
 */
public final class QueenDenialTest {

	private QueenDenialTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		fullCreditWhileHoldingAProtectedJack();
		halfCreditForTheCaptureWeaponWithoutTheJack();
		noCreditOnceTheJackIsAlreadyCaptured();
		noCreditWithNeitherAsset();
		refusesTooEarlyWithNoJackOffsetAndFewHeartsBanked();
		allowsOnceTheShooterHasBankedAMajorityOfHearts();
		allowsEarlyWhenTheJackOffsetMakesItCheapEnough();
		letsALevelShooterGo();
		leaderStopsTheSecondPlaceShooter();
		leaderAboveFiftyLetsItGo();
		hand10LetsTheTrailingShooterGo();
		neverEatsTheQueenOverTheWall();
		heartsOnTheTrickAddToTheCost();
		return TestKit.summarize();
	}

	private static void fullCreditWhileHoldingAProtectedJack() {
		TestKit.suite("fullCreditWhileHoldingAProtectedJack");
		Table_Cards tc = emptyTable();
		Hand hand = handOf(card(CardValue.JACK, Suit.DIAMOND), card(CardValue.ACE, Suit.DIAMOND));
		TestKit.checkEquals("holding the Jack with the Ace as protection earns full credit (10)",
				Double.valueOf(10), Double.valueOf(MoonThreat.jackOfDiamondsOffsetCredit(tc, hand)));
	}

	private static void halfCreditForTheCaptureWeaponWithoutTheJack() {
		TestKit.suite("halfCreditForTheCaptureWeaponWithoutTheJack");
		Table_Cards tc = emptyTable();
		Hand hand = handOf(card(CardValue.KING, Suit.DIAMOND), card(CardValue.TWO, Suit.CLUB));
		TestKit.checkEquals("holding the King of Diamonds without the Jack earns half credit (5)",
				Double.valueOf(5), Double.valueOf(MoonThreat.jackOfDiamondsOffsetCredit(tc, hand)));
	}

	private static void noCreditOnceTheJackIsAlreadyCaptured() {
		TestKit.suite("noCreditOnceTheJackIsAlreadyCaptured");
		Table_Cards tc = emptyTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.JACK, Suit.DIAMOND));
		Hand hand = handOf(card(CardValue.ACE, Suit.DIAMOND), card(CardValue.TWO, Suit.CLUB));
		TestKit.checkEquals("a captured Jack is moot -- zero credit even while holding the capture weapon",
				Double.valueOf(0), Double.valueOf(MoonThreat.jackOfDiamondsOffsetCredit(tc, hand)));
	}

	private static void noCreditWithNeitherAsset() {
		TestKit.suite("noCreditWithNeitherAsset");
		Table_Cards tc = emptyTable();
		Hand hand = handOf(card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.SPADE));
		TestKit.checkEquals("no Jack, no Ace/King of Diamonds -- zero credit",
				Double.valueOf(0), Double.valueOf(MoonThreat.jackOfDiamondsOffsetCredit(tc, hand)));
	}

	private static void refusesTooEarlyWithNoJackOffsetAndFewHeartsBanked() {
		TestKit.suite("refusesTooEarlyWithNoJackOffsetAndFewHeartsBanked");
		Table_Cards tc = tableWithStandings(50, 50, 50, 50); // level -- standings would allow it
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.TWO, Suit.HEART)); // shooter, only 1 Heart so far
		Hand myHand = handOf(card(CardValue.TWO, Suit.CLUB)); // no Jack-of-Diamonds prospects
		Player me = playerOf(PLAYER.SOUTH, 50);
		TestKit.check("full-cost swallow refused while the shot still looks like a bare suspicion",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, myHand));
	}

	private static void allowsOnceTheShooterHasBankedAMajorityOfHearts() {
		TestKit.suite("allowsOnceTheShooterHasBankedAMajorityOfHearts");
		// SOUTH leading at 20, West shooting from 40: standings where the
		// keep-my-lead rule says yes -- isolates the timing gate itself.
		Table_Cards tc = tableWithStandings(60, 60, 20, 40);
		Hand shooterHand = new Hand();
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR,
				CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT }) { // 7 Hearts
			shooterHand.addX(card(v, Suit.HEART));
		}
		tc.players.get(PLAYER.WEST).won_cards = shooterHand;
		Hand myHand = handOf(card(CardValue.TWO, Suit.CLUB));
		Player me = playerOf(PLAYER.SOUTH, 20);
		TestKit.check("full-cost swallow allowed once the shooter has clearly banked a majority of the Hearts",
				MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, myHand));
	}

	private static void allowsEarlyWhenTheJackOffsetMakesItCheapEnough() {
		TestKit.suite("allowsEarlyWhenTheJackOffsetMakesItCheapEnough");
		Table_Cards tc = tableWithStandings(60, 60, 20, 30); // me leading at 20, West shooting from 30
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.TWO, Suit.HEART)); // only 1 Heart -- too early on its own
		Hand myHand = handOf(card(CardValue.JACK, Suit.DIAMOND), card(CardValue.ACE, Suit.DIAMOND)); // full 10 credit -> net cost 3
		Player me = playerOf(PLAYER.SOUTH, 20);
		TestKit.check("a cheap-enough net cost (Jack offset) bypasses the timing gate entirely",
				MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, myHand));
	}

	/** Wayne, 2026-11-09: shooter and stopper at 50, the other two at 40.
	 * Taking 13+ leaves 40, 40, 57, 63; letting it go leaves 24, 40, 40, 50.
	 * Staying 10 behind is better than 13 points closer to the wall. */
	private static void letsALevelShooterGo() {
		TestKit.suite("letsALevelShooterGo");
		Table_Cards tc = tableWithStandings(40, 40, 30, 50); // NORTH 40, EAST 40, WEST 50 (shooter); me SOUTH 50
		tc.players.get(PLAYER.WEST).won_cards = sevenHearts();
		Player me = playerOf(PLAYER.SOUTH, 50);
		tc.players.put(PLAYER.SOUTH, me);
		TestKit.check("not in 1st: let it go and stay off the wall",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, handOf(card(CardValue.TWO, Suit.CLUB))));
	}

	/** Wayne, 2026-11-09: 1st place stops 2nd place. Me 20, shooter 40 with 7
	 * Hearts, others 60: the moon puts the shooter at 14, ahead of me; taking
	 * the Queen keeps me 1st at 33 against their 47. */
	private static void leaderStopsTheSecondPlaceShooter() {
		TestKit.suite("leaderStopsTheSecondPlaceShooter");
		Table_Cards tc = tableWithStandings(60, 60, 20, 40);
		tc.players.get(PLAYER.WEST).won_cards = sevenHearts();
		Player me = playerOf(PLAYER.SOUTH, 20);
		tc.players.put(PLAYER.SOUTH, me);
		TestKit.check("1st place keeps its lead by taking the Queen",
				MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, handOf(card(CardValue.TWO, Suit.CLUB))));
	}

	/** Above 50 a leader is fighting the wall and doesn't eat the Queen. */
	private static void leaderAboveFiftyLetsItGo() {
		TestKit.suite("leaderAboveFiftyLetsItGo");
		Table_Cards tc = tableWithStandings(90, 90, 55, 70);
		tc.players.get(PLAYER.WEST).won_cards = sevenHearts();
		Player me = playerOf(PLAYER.SOUTH, 55);
		tc.players.put(PLAYER.SOUTH, me);
		TestKit.check("leading at 55: let the moon go",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, handOf(card(CardValue.TWO, Suit.CLUB))));
	}

	/** Wayne's game, 2026-11-08 hand 10: North 16, West -5 (has the Jack),
	 * South 50 shooting with 12 Hearts, East 73. Stopping costs North 13+ against
	 * West, the seat it has to beat, and South stays behind North either way. */
	private static void hand10LetsTheTrailingShooterGo() {
		TestKit.suite("hand10LetsTheTrailingShooterGo");
		Table_Cards tc = tableWithStandings(16, 73, 50, 5);
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.JACK, Suit.DIAMOND));
		Hand south = new Hand();
		for (CardValue v : CardValue.values()) {
			if (v != CardValue.ACE) {
				south.addX(card(v, Suit.HEART));
			}
		}
		tc.players.get(PLAYER.SOUTH).won_cards = south;
		Player me = playerOf(PLAYER.NORTH, 16);
		TestKit.check("North lets Wayne's moon go rather than eat 13",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.SOUTH, handOf(card(CardValue.NINE, Suit.DIAMOND))));
	}

	/** Near the 100 wall: eating 13 at 90 ends the game with me losing. */
	private static void neverEatsTheQueenOverTheWall() {
		TestKit.suite("neverEatsTheQueenOverTheWall");
		Table_Cards tc = tableWithStandings(40, 40, 90, 30);
		tc.players.get(PLAYER.WEST).won_cards = sevenHearts();
		Player me = playerOf(PLAYER.SOUTH, 90);
		TestKit.check("at 90, taking 13 would end the game with me last",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, handOf(card(CardValue.TWO, Suit.CLUB))));
	}

	/** Hearts on the Queen's trick add to the cost (13 is the minimum). */
	private static void heartsOnTheTrickAddToTheCost() {
		TestKit.suite("heartsOnTheTrickAddToTheCost");
		// Me leading at 20, North/East 60, West 30 shooting with 7 Hearts. The
		// moon puts West at 4. The bare Queen: me 33 vs West 37, still 1st --
		// take it. With 6 Hearts on her trick: 39 vs 37 -- let it go.
		Table_Cards tc = tableWithStandings(60, 60, 20, 30);
		tc.players.get(PLAYER.WEST).won_cards = sevenHearts();
		Player me = playerOf(PLAYER.SOUTH, 20);
		tc.players.put(PLAYER.SOUTH, me);
		Hand mine = handOf(card(CardValue.TWO, Suit.CLUB));
		TestKit.check("the bare Queen: worth taking", MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, mine, 0));
		TestKit.check("the Queen plus 6 Hearts: let the moon go",
				!MoonThreat.queenDenialWorthIt(tc, me, PLAYER.WEST, mine, 6));
	}

	private static Hand sevenHearts() {
		Hand h = new Hand();
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR,
				CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT }) {
			h.addX(card(v, Suit.HEART));
		}
		return h;
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static Hand handOf(Card... cards) {
		Hand h = new Hand();
		for (Card c : cards) {
			h.addX(c);
		}
		return h;
	}

	private static Player playerOf(PLAYER seat, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.p_total_points = totalPoints;
		return p;
	}

	private static Table_Cards emptyTable() {
		return tableWithStandings(0, 0, 0, 0);
	}

	/** NORTH/EAST/SOUTH/WEST totals, in that fixed seat order. */
	private static Table_Cards tableWithStandings(int north, int east, int south, int west) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, playerOf(PLAYER.NORTH, north));
		players.put(PLAYER.EAST, playerOf(PLAYER.EAST, east));
		players.put(PLAYER.SOUTH, playerOf(PLAYER.SOUTH, south));
		players.put(PLAYER.WEST, playerOf(PLAYER.WEST, west));
		tc.players = players;
		return tc;
	}
}
