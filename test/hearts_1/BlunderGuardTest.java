package hearts_1;

import java.util.Arrays;
import java.util.List;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * 2026-10-17, user rule after Shark's near-miss blunder ate the Queen of
 * Spades as the last player of a Spade trick (3S was the best card, QS the
 * "runner-up"): a blunder is a human-sized mistake, never a suicidal play.
 * Guards: no Queen of Spades onto a Spade trick where she is the winning card,
 * and, as the last player, no trick that costs 10+ points more than the best
 * card would.
 */
public final class BlunderGuardTest {

	private BlunderGuardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		queenIsNeverBlunderedOntoAWinningSpot();
		lastSeatNeverBlundersIntoABigTrick();
		nearMissSkipsASuicidalRunnerUp();
		jackIsNeverBlunderedToAnotherSeat();
		return TestKit.summarize();
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static Table_Cards trick(Card p1, Card p2, Card p3) {
		Table_Cards tc = new Table_Cards();
		tc.p1_card = p1;
		tc.p2_card = p2;
		tc.p3_card = p3;
		return tc;
	}

	private static void jackIsNeverBlunderedToAnotherSeat() {
		TestKit.suite("jackIsNeverBlunderedToAnotherSeat");
		Card jd = card(CardValue.JACK, Suit.DIAMOND), twoC = card(CardValue.TWO, Suit.CLUB);
		Card fourD = card(CardValue.FOUR, Suit.DIAMOND);
		Table_Cards clubs = trick(card(CardValue.THREE, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB), null);
		TestKit.check("discarding JD on a Club trick is suicidal", AI.isSuicidalFollow(jd, twoC, clubs, 3));
		Table_Cards underAce = trick(card(CardValue.ACE, Suit.DIAMOND), null, null);
		TestKit.check("JD under the Ace of Diamonds is suicidal", AI.isSuicidalFollow(jd, fourD, underAce, 2));
		Table_Cards winning = trick(card(CardValue.THREE, Suit.DIAMOND), card(CardValue.FIVE, Suit.DIAMOND),
				card(CardValue.SEVEN, Suit.DIAMOND));
		TestKit.check("JD winning the trick last is not flagged", !AI.isSuicidalFollow(jd, fourD, winning, 4));
	}

	private static void queenIsNeverBlunderedOntoAWinningSpot() {
		TestKit.suite("queenIsNeverBlunderedOntoAWinningSpot");
		// The exact 2026-10-17 hand: 2S, 5S, 7S on the table, West last holding 3S and QS.
		Table_Cards tc = trick(card(CardValue.TWO, Suit.SPADE), card(CardValue.FIVE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.SPADE));
		Card qs = card(CardValue.QUEEN, Suit.SPADE), threeS = card(CardValue.THREE, Suit.SPADE);
		TestKit.check("QS over 2S/5S/7S as the last seat is suicidal", AI.isSuicidalFollow(qs, threeS, tc, 4));
		TestKit.check("3S is not", !AI.isSuicidalFollow(threeS, qs, tc, 4));
		// Second seat, only the lead on the table: QS would be the winning card for now.
		Table_Cards early = trick(card(CardValue.TWO, Suit.SPADE), null, null);
		TestKit.check("QS onto a lone low spade at seat 2 is suicidal too", AI.isSuicidalFollow(qs, threeS, early, 2));
		// A higher spade already out (KS): the Queen is not winning, it is just a dump.
		Table_Cards covered = trick(card(CardValue.TWO, Suit.SPADE), card(CardValue.KING, Suit.SPADE), null);
		TestKit.check("QS under a King is not flagged", !AI.isSuicidalFollow(qs, threeS, covered, 3));
		// Queen played onto a Club trick (discard) is never the winning card.
		Table_Cards clubs = trick(card(CardValue.TWO, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB),
				card(CardValue.SEVEN, Suit.CLUB));
		TestKit.check("discarding QS on a Club trick is not flagged",
				!AI.isSuicidalFollow(qs, card(CardValue.THREE, Suit.DIAMOND), clubs, 4));
	}

	private static void lastSeatNeverBlundersIntoABigTrick() {
		TestKit.suite("lastSeatNeverBlundersIntoABigTrick");
		// Hearts led, three Hearts on the table, last seat has KH (wins, 4 points) or 2H (ducks).
		Table_Cards tc = trick(card(CardValue.THREE, Suit.HEART), card(CardValue.FOUR, Suit.HEART),
				card(CardValue.FIVE, Suit.HEART));
		Card kh = card(CardValue.KING, Suit.HEART), twoH = card(CardValue.TWO, Suit.HEART);
		TestKit.check("a 4-point trick is a mistake, not suicide", !AI.isSuicidalFollow(kh, twoH, tc, 4));
		// Queen already in that trick: taking it costs 13+ more than ducking.
		Table_Cards withQueen = trick(card(CardValue.THREE, Suit.HEART), card(CardValue.QUEEN, Suit.SPADE),
				card(CardValue.FIVE, Suit.HEART));
		TestKit.check("taking a Queen-bearing trick over a safe duck is suicidal",
				AI.isSuicidalFollow(kh, twoH, withQueen, 4));
		// The Jack of Diamonds in the trick is a bonus, so winning it is fine.
		Table_Cards withJack = trick(card(CardValue.THREE, Suit.DIAMOND), card(CardValue.JACK, Suit.DIAMOND),
				card(CardValue.FIVE, Suit.DIAMOND));
		TestKit.check("winning the Jack of Diamonds is not suicidal",
				!AI.isSuicidalFollow(card(CardValue.KING, Suit.DIAMOND), card(CardValue.TWO, Suit.DIAMOND), withJack, 4));
	}

	private static void nearMissSkipsASuicidalRunnerUp() {
		TestKit.suite("nearMissSkipsASuicidalRunnerUp");
		Player p = new Player();
		p.tbl_player = PLAYER.WEST;
		Card best = card(CardValue.THREE, Suit.SPADE), queen = card(CardValue.QUEEN, Suit.SPADE);
		Table_Cards tc = trick(card(CardValue.TWO, Suit.SPADE), card(CardValue.FIVE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.SPADE));
		List<Card> ranked = Arrays.asList(best, queen);
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		int queens = 0;
		AI.RNG.setSeed(11);
		for (int i = 0; i < 500; i++) {
			if (AI.nearMiss(ranked, p, c -> AI.isSuicidalFollow(c, best, tc, 4)) == queen) {
				queens++;
			}
		}
		// Three choices: the suicidal 2nd is skipped for the harmless 3rd.
		Card harmless = card(CardValue.FOUR, Suit.SPADE);
		List<Card> three = Arrays.asList(best, queen, harmless);
		boolean allHarmless = true;
		for (int i = 0; i < 200; i++) {
			if (AI.nearMiss(three, p, c -> AI.isSuicidalFollow(c, best, tc, 4)) != harmless) {
				allHarmless = false;
			}
		}
		System.setOut(out);
		TestKit.checkEquals("the Queen is never chosen as the blunder", 0, queens);
		TestKit.check("with a harmless runner-up available, that one is played instead", allHarmless);
	}
}
