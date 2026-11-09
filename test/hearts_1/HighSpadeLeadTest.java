package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Committed regression coverage for AICardScorer.highSpadeLeadRisksTheQueen.
 * Original version added 2026-08-16 -- a user correction to the existing
 * "never lead the King/Ace of Spades while the Queen is live" danger
 * (LEAD_HIGH_SPADE_LIVE_QUEEN, 2026-08-07): a moon shooter elsewhere at the
 * table never drops the Queen onto that lead, since they need her for their
 * own attempt, so the risk this scorer prices should stand down while one is
 * in progress -- UNLESS this seat holds the Queen itself, in which case the
 * danger is self-protection, unrelated to anyone else's shooting status.
 *
 * Tightened 2026-08-16 (same day, later user correction from a live hand):
 * the original exception waived the risk for ANY seat that happened to be
 * the sole holder of a point card so far, which fires off a single
 * incidental Heart with no relation to a real moon attempt -- exactly the
 * false positive that let a live hand's East lead the Ace of Spades into a
 * completely uninvolved North's live Queen. The exception now defers to
 * MoonThreat.queenDenialWorthIt()'s stricter standard (a real Hearts-banked
 * threshold, the Jack-of-Diamonds offset, and a standings safety check) --
 * see that method's own tests in QueenDenialTest for the full matrix; this
 * file only re-covers the wiring at the highSpadeLeadRisksTheQueen level.
 */
public final class HighSpadeLeadTest {

	private HighSpadeLeadTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		riskyByDefaultWhileTheQueenIsLive();
		safeOnceTheQueenIsAlreadyWon();
		riskyWhileAnotherSeatsShootingStatusIsJustOneIncidentalHeart();
		safeWhileAnotherSeatIsGenuinelyShooting();
		stillRiskyWhenTheShooterIsMe();
		stillRiskyWhenIHoldTheQueenMyselfDespiteAShooterElsewhere();
		return TestKit.summarize();
	}

	private static void riskyByDefaultWhileTheQueenIsLive() {
		TestKit.suite("riskyByDefaultWhileTheQueenIsLive");
		Table_Cards tc = emptyTable();
		List<Card> myOptions = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Player me = playerOf(PLAYER.SOUTH);
		TestKit.check("with no shooter and the Queen unaccounted for, leading a high Spade is risky",
				AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, false));
	}

	private static void safeOnceTheQueenIsAlreadyWon() {
		TestKit.suite("safeOnceTheQueenIsAlreadyWon");
		Table_Cards tc = emptyTable();
		List<Card> myOptions = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Player me = playerOf(PLAYER.SOUTH);
		TestKit.check("queenOfSpadesGone=true makes the lead safe regardless of anything else",
				!AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, true));
	}

	private static void riskyWhileAnotherSeatsShootingStatusIsJustOneIncidentalHeart() {
		TestKit.suite("riskyWhileAnotherSeatsShootingStatusIsJustOneIncidentalHeart");
		Table_Cards tc = emptyTable();
		// WEST has captured exactly one Heart -- the sole seat with any point
		// card so far, so Scoring.inProgressMoonShooter flags it, but one
		// incidental Heart is nowhere near proof of a real attempt (the
		// exact false positive this test guards against).
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.TWO, Suit.HEART));
		List<Card> myOptions = cards(card(CardValue.ACE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Player me = playerOf(PLAYER.SOUTH);
		TestKit.check("one incidental Heart doesn't meet queenDenialWorthIt's bar -- still risky",
				AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, false));
	}

	private static void safeWhileAnotherSeatIsGenuinelyShooting() {
		TestKit.suite("safeWhileAnotherSeatIsGenuinelyShooting");
		// SOUTH (me) leading at 20, West (shooter) at 30 -- standings where the
		// Queen check says yes (keep-my-lead rule, 2026-11-09), isolating the
		// timing gate itself, as in QueenDenialTest.allowsOnceTheShooterHasBankedAMajorityOfHearts.
		Table_Cards tc = tableWithStandings(60, 60, 20, 30);
		Hand shooterHand = new Hand();
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR,
				CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT }) { // 7 Hearts -- majority
			shooterHand.addX(card(v, Suit.HEART));
		}
		tc.players.get(PLAYER.WEST).won_cards = shooterHand;
		List<Card> myOptions = cards(card(CardValue.ACE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Player me = tc.players.get(PLAYER.SOUTH);
		TestKit.check("a shooter with a genuine majority of Hearts banked hoards the Queen -- the lead is safe",
				!AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, false));
	}

	private static void stillRiskyWhenTheShooterIsMe() {
		TestKit.suite("stillRiskyWhenTheShooterIsMe");
		Table_Cards tc = emptyTable();
		Player me = playerOf(PLAYER.SOUTH);
		// I am myself the sole seat with a captured point card -- the
		// exception is specifically about OTHER seats hoarding the Queen,
		// not me.
		tc.players.get(PLAYER.SOUTH).won_cards.addX(card(CardValue.TWO, Suit.HEART));
		List<Card> myOptions = cards(card(CardValue.ACE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("a shooter that IS me doesn't waive the risk -- irrelevant here, mode should already gate this",
				AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, false));
	}

	private static void stillRiskyWhenIHoldTheQueenMyselfDespiteAShooterElsewhere() {
		TestKit.suite("stillRiskyWhenIHoldTheQueenMyselfDespiteAShooterElsewhere");
		Table_Cards tc = emptyTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.TWO, Suit.HEART)); // WEST is shooting
		List<Card> myOptions = cards(card(CardValue.ACE, Suit.SPADE),
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Player me = playerOf(PLAYER.SOUTH);
		TestKit.check("holding the Queen myself keeps the self-protection risk regardless of a shooter elsewhere",
				AICardScorer.highSpadeLeadRisksTheQueen(tc, myOptions, me, false));
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static List<Card> cards(Card... cs) {
		List<Card> list = new ArrayList<Card>();
		for (Card c : cs) {
			list.add(c);
		}
		return list;
	}

	private static Player playerOf(PLAYER seat) {
		return playerOf(seat, 0);
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

	/** NORTH/EAST/SOUTH/WEST totals, in that fixed seat order -- same shape
	 * as QueenDenialTest's helper of the same name. */
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
