package hearts_1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * 2026-11-02: BlunderGuardTest's rule extended to leads (AI.isSuicidalLead).
 * A blunder may lead a worse card, never the Queen of Spades into an Ace or
 * King still out, never the Ace or King of Spades into a Queen still out
 * (unless a Spade was the best lead anyway), and never a sure winner 10+
 * points worse than the best lead (4+ for experts) -- at every level since
 * 2026-11-05. Public information only.
 */
public final class LeadBlunderGuardTest {

	private LeadBlunderGuardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		queenIsNotLedIntoAnAceOrKing();
		aceOrKingIsNotLedIntoTheQueen();
		noLevelLeadsASureCostlyWinner();
		nearMissPlaysTheBestWhenEveryRunnerUpIsVetoed();
		nearMissPicksAnAllowedRunnerUp();
		jackIsNotLedIntoAHigherDiamond();
		return TestKit.summarize();
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static Table_Cards table() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		tc.players = players;
		return tc;
	}

	/** Puts cards in a completed-trick pile, i.e. already played. */
	private static void played(Table_Cards tc, Card... cards) {
		for (Card c : cards) {
			tc.players.get(PLAYER.NORTH).won_cards.addX(c);
		}
	}

	private static Player player(int level) {
		Player p = new Player();
		p.tbl_player = PLAYER.SOUTH;
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withLevel(level));
		return p;
	}

	private static List<Card> mine(Card... cards) {
		return new ArrayList<Card>(Arrays.asList(cards));
	}

	/** Wayne's game 2026-11-09, hand 12, trick 5: East (K Q J 8 of Diamonds)
	 * blundered the Jack lead instead of the 8; North's Ace took her. */
	private static void jackIsNotLedIntoAHigherDiamond() {
		TestKit.suite("jackIsNotLedIntoAHigherDiamond");
		Card jd = card(CardValue.JACK, Suit.DIAMOND), eightD = card(CardValue.EIGHT, Suit.DIAMOND);
		Card kd = card(CardValue.KING, Suit.DIAMOND), qd = card(CardValue.QUEEN, Suit.DIAMOND);
		Player p = player(4);
		TestKit.check("JD with the Ace of Diamonds out is vetoed",
				AI.isSuicidalLead(jd, eightD, mine(jd, eightD, kd, qd), table(), p));
		Table_Cards aceGone = table();
		played(aceGone, card(CardValue.ACE, Suit.DIAMOND));
		TestKit.check("JD once the A K Q are accounted for (a sure -10 for me) is allowed",
				!AI.isSuicidalLead(jd, eightD, mine(jd, eightD, kd, qd), aceGone, p));
		TestKit.check("an ordinary Diamond runner-up is allowed",
				!AI.isSuicidalLead(eightD, jd, mine(jd, eightD), table(), p));
	}

	private static void queenIsNotLedIntoAnAceOrKing() {
		TestKit.suite("queenIsNotLedIntoAnAceOrKing");
		Card qs = card(CardValue.QUEEN, Suit.SPADE), twoC = card(CardValue.TWO, Suit.CLUB);
		Player p = player(3);
		TestKit.check("QS with AS and KS both out is vetoed",
				AI.isSuicidalLead(qs, twoC, mine(qs, twoC), table(), p));
		Table_Cards aceGone = table();
		played(aceGone, card(CardValue.ACE, Suit.SPADE), card(CardValue.TWO, Suit.SPADE));
		TestKit.check("QS with only KS out is still vetoed", AI.isSuicidalLead(qs, twoC, mine(qs, twoC), aceGone, p));
		Card ks = card(CardValue.KING, Suit.SPADE);
		// With the Ace played and the King in my own hand the first rule passes,
		// but she is now a sure winner (13 points), so the sure-swing rule vetoes
		// her: together the two rules mean a blunder never leads the Queen.
		TestKit.check("QS with the Ace played and the King in my hand is a sure 13: vetoed",
				AI.isSuicidalLead(qs, twoC, mine(qs, ks, twoC), aceGone, p));
		Table_Cards bothGone = table();
		played(bothGone, card(CardValue.ACE, Suit.SPADE), ks);
		TestKit.check("QS once AS and KS are played is a sure 13: vetoed at level 3 too",
				AI.isSuicidalLead(qs, twoC, mine(qs, twoC), bothGone, p));
	}

	private static void aceOrKingIsNotLedIntoTheQueen() {
		TestKit.suite("aceOrKingIsNotLedIntoTheQueen");
		Card as = card(CardValue.ACE, Suit.SPADE), ks = card(CardValue.KING, Suit.SPADE);
		Card fiveC = card(CardValue.FIVE, Suit.CLUB), threeS = card(CardValue.THREE, Suit.SPADE);
		Player p = player(3);
		TestKit.check("AS with QS out, best a Club, is vetoed", AI.isSuicidalLead(as, fiveC, mine(as, fiveC), table(), p));
		TestKit.check("KS with QS out, best a Club, is vetoed", AI.isSuicidalLead(ks, fiveC, mine(ks, fiveC), table(), p));
		TestKit.check("AS is allowed when the best lead was a Spade anyway",
				!AI.isSuicidalLead(as, threeS, mine(as, threeS), table(), p));
		TestKit.check("AS is allowed when I hold the Queen",
				!AI.isSuicidalLead(as, fiveC, mine(as, card(CardValue.QUEEN, Suit.SPADE), fiveC), table(), p));
		Table_Cards queenGone = table();
		played(queenGone, card(CardValue.QUEEN, Suit.SPADE));
		TestKit.check("AS is allowed once the Queen is played", !AI.isSuicidalLead(as, fiveC, mine(as, fiveC), queenGone, p));
		TestKit.check("an ordinary Club runner-up is allowed",
				!AI.isSuicidalLead(card(CardValue.NINE, Suit.CLUB), fiveC, mine(fiveC), table(), p));
	}

	private static void noLevelLeadsASureCostlyWinner() {
		TestKit.suite("noLevelLeadsASureCostlyWinner");
		Card qs = card(CardValue.QUEEN, Suit.SPADE), fourC = card(CardValue.FOUR, Suit.CLUB);
		Table_Cards bothGone = table();
		played(bothGone, card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE));
		TestKit.check("an expert never leads the Queen as a sure winner (13 points)",
				AI.isSuicidalLead(qs, fourC, mine(qs, fourC), bothGone, player(4)));
		TestKit.check("level 3 never leads the Queen as a sure winner either",
				AI.isSuicidalLead(qs, fourC, mine(qs, fourC), bothGone, player(3)));
		TestKit.check("nor does level 1", AI.isSuicidalLead(qs, fourC, mine(qs, fourC), bothGone, player(1)));
		TestKit.check("a sure Queen over a sure low Heart (12 worse) is vetoed at level 2",
				AI.isSuicidalLead(qs, card(CardValue.ACE, Suit.HEART), mine(qs, card(CardValue.ACE, Suit.HEART)), bothGone, player(2)));
		Card ah = card(CardValue.ACE, Suit.HEART);
		TestKit.check("a sure-winning Ace of Hearts is only 1 point, allowed",
				!AI.isSuicidalLead(ah, fourC, mine(ah, fourC), table(), player(5)));
		// The best lead cashes the Jack of Diamonds for sure (-10); a slip that
		// throws that away is 10 points worse.
		Card jd = card(CardValue.JACK, Suit.DIAMOND), aceC = card(CardValue.ACE, Suit.CLUB);
		Table_Cards highDiamondsGone = table();
		played(highDiamondsGone, card(CardValue.ACE, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.DIAMOND));
		TestKit.check("an expert does not swap a sure Jack for a sure empty trick",
				AI.isSuicidalLead(aceC, jd, mine(jd, aceC), highDiamondsGone, player(5)));
		TestKit.check("level 1 does not swap a sure Jack for a sure empty trick either",
				AI.isSuicidalLead(aceC, jd, mine(jd, aceC), highDiamondsGone, player(1)));
		TestKit.check("a runner-up that may lose is not covered",
				!AI.isSuicidalLead(fourC, jd, mine(jd, fourC), highDiamondsGone, player(5)));
		TestKit.check("nor at level 1", !AI.isSuicidalLead(fourC, jd, mine(jd, fourC), highDiamondsGone, player(1)));
	}

	private static void nearMissPlaysTheBestWhenEveryRunnerUpIsVetoed() {
		TestKit.suite("nearMissPlaysTheBestWhenEveryRunnerUpIsVetoed");
		// Best lead 2C; the runners-up are KS and AS with the Queen still out.
		Card twoC = card(CardValue.TWO, Suit.CLUB), ks = card(CardValue.KING, Suit.SPADE),
				as = card(CardValue.ACE, Suit.SPADE);
		List<Card> ranked = Arrays.asList(twoC, ks, as);
		List<Card> hand = mine(twoC, ks, as);
		Table_Cards tc = table();
		Player p = player(3);
		TestKit.check("setup: KS is vetoed", AI.isSuicidalLead(ks, twoC, hand, tc, p));
		TestKit.check("setup: AS is vetoed", AI.isSuicidalLead(as, twoC, hand, tc, p));
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		AI.RNG.setSeed(7);
		boolean allBest = true;
		for (int i = 0; i < 200; i++) {
			if (AI.nearMiss(ranked, p, c -> AI.isSuicidalLead(c, twoC, hand, tc, p)) != twoC) {
				allBest = false;
			}
		}
		System.setOut(out);
		TestKit.check("every runner-up vetoed: the best card is played, every time", allBest);
	}

	private static void nearMissPicksAnAllowedRunnerUp() {
		TestKit.suite("nearMissPicksAnAllowedRunnerUp");
		Card twoC = card(CardValue.TWO, Suit.CLUB), as = card(CardValue.ACE, Suit.SPADE),
				nineC = card(CardValue.NINE, Suit.CLUB);
		List<Card> ranked = Arrays.asList(twoC, as, nineC);
		List<Card> hand = mine(twoC, as, nineC);
		Table_Cards tc = table();
		Player p = player(3);
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		AI.RNG.setSeed(3);
		boolean allNine = true;
		for (int i = 0; i < 200; i++) {
			if (AI.nearMiss(ranked, p, c -> AI.isSuicidalLead(c, twoC, hand, tc, p)) != nineC) {
				allNine = false;
			}
		}
		System.setOut(out);
		TestKit.check("AS into an unseen Queen is skipped for the harmless 9C, every time", allNine);
	}
}
