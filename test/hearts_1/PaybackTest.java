package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Queen payback (2026-10-10, hearts_1.Payback): who gets the grudge, how it
 * fades, how the target's standing scales it, and that it really changes
 * what gets passed and discarded.
 */
public final class PaybackTest {

	private PaybackTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		try {
			theDumperIsTheCulprit();
			aPassedQueenCountsOnlyIfTaken();
			theGrudgeTapersThenEnds();
			sameCulpritAgainResets();
			standingsDecideWhoIsWorthIt();
			noTraitNoPayback();
			profileSettings();
			passesTheQueenToTheTarget();
			dumpsOnTheTargetInPlay();
		} finally {
			System.setOut(out);
		}
		return TestKit.summarize();
	}

	private static final int[] TAPER = { 100, 60, 25 };

	private static void theDumperIsTheCulprit() {
		TestKit.suite("theDumperIsTheCulprit");
		Table_Cards tc = table(0, 0, 0, 0);
		trick(tc, PLAYER.EAST, PLAYER.NORTH);
		TestKit.check("EAST took the Queen NORTH dropped: grudge against NORTH",
				tc.players.get(PLAYER.EAST).grudges.containsKey(PLAYER.NORTH));
		TestKit.check("nobody else holds a grudge", tc.players.get(PLAYER.NORTH).grudges.isEmpty()
				&& tc.players.get(PLAYER.SOUTH).grudges.isEmpty() && tc.players.get(PLAYER.WEST).grudges.isEmpty());
	}

	private static void aPassedQueenCountsOnlyIfTaken() {
		TestKit.suite("aPassedQueenCountsOnlyIfTaken");
		Table_Cards tc = table(0, 0, 0, 0);
		Player east = tc.players.get(PLAYER.EAST);
		east.queenPassedBy = PLAYER.SOUTH;
		trick(tc, PLAYER.EAST, PLAYER.EAST);
		TestKit.check("EAST played the passed Queen onto its own trick: grudge against the passer",
				east.grudges.containsKey(PLAYER.SOUTH));
		Table_Cards tc2 = table(0, 0, 0, 0);
		Player e2 = tc2.players.get(PLAYER.EAST);
		e2.queenPassedBy = PLAYER.SOUTH;
		trick(tc2, PLAYER.WEST, PLAYER.EAST);
		TestKit.check("EAST got rid of the passed Queen: no grudge against the passer", e2.grudges.isEmpty());
		TestKit.check("...but WEST, who took it, blames EAST", tc2.players.get(PLAYER.WEST).grudges.containsKey(PLAYER.EAST));
		Table_Cards tc3 = table(0, 0, 0, 0);
		trick(tc3, PLAYER.EAST, PLAYER.EAST);
		TestKit.check("own Queen onto own trick, nobody passed it: no grudge", tc3.players.get(PLAYER.EAST).grudges.isEmpty());
	}

	private static void theGrudgeTapersThenEnds() {
		TestKit.suite("theGrudgeTapersThenEnds");
		Table_Cards tc = table(0, 0, 0, 0);
		Player east = tc.players.get(PLAYER.EAST);
		trick(tc, PLAYER.EAST, PLAYER.NORTH);
		TestKit.checkEquals("no payback during the hand it happened", "0.0",
				String.valueOf(Payback.strength(tc, east, PLAYER.NORTH)));
		StringBuilder seen = new StringBuilder();
		for (int hand = 1; hand <= 4; hand++) {
			Payback.startHand(east);
			seen.append(Payback.strength(tc, east, PLAYER.NORTH)).append(' ');
		}
		TestKit.checkEquals("100%, 60%, 25%, then gone", "1.0 0.6 0.25 0.0 ", seen.toString());
		TestKit.check("the forgotten grudge is removed", east.grudges.isEmpty());
	}

	private static void sameCulpritAgainResets() {
		TestKit.suite("sameCulpritAgainResets");
		Table_Cards tc = table(0, 0, 0, 0);
		Player east = tc.players.get(PLAYER.EAST);
		trick(tc, PLAYER.EAST, PLAYER.NORTH);
		Payback.startHand(east);
		Payback.startHand(east);
		trick(tc, PLAYER.EAST, PLAYER.SOUTH);
		trick(tc, PLAYER.EAST, PLAYER.NORTH);
		Payback.startHand(east);
		TestKit.checkEquals("NORTH again: back to full", "1.0", String.valueOf(Payback.strength(tc, east, PLAYER.NORTH)));
		TestKit.checkEquals("SOUTH too: both targets live", "1.0", String.valueOf(Payback.strength(tc, east, PLAYER.SOUTH)));
	}

	private static void standingsDecideWhoIsWorthIt() {
		TestKit.suite("standingsDecideWhoIsWorthIt");
		// N=10 (1st) E=30 (2nd) S=50 (3rd) W=70 (4th)
		Table_Cards tc = table(10, 30, 50, 70);
		TestKit.checkEquals("1st place: full", "1.0", String.valueOf(Payback.relevance(tc, PLAYER.NORTH)));
		TestKit.checkEquals("2nd place: full", "1.0", String.valueOf(Payback.relevance(tc, PLAYER.EAST)));
		TestKit.checkEquals("3rd place, far back: a little", "0.25", String.valueOf(Payback.relevance(tc, PLAYER.SOUTH)));
		TestKit.checkEquals("last place, far back: not worth it", "0.0", String.valueOf(Payback.relevance(tc, PLAYER.WEST)));
		Table_Cards close = table(40, 45, 50, 55);
		TestKit.checkEquals("3rd within 15 of the leader: full", "1.0", String.valueOf(Payback.relevance(close, PLAYER.SOUTH)));
		TestKit.checkEquals("last within 15 of the leader: full", "1.0", String.valueOf(Payback.relevance(close, PLAYER.WEST)));
		Table_Cards edge = table(40, 45, 50, 56);
		TestKit.checkEquals("last, 16 behind the leader: not worth it", "0.0", String.valueOf(Payback.relevance(edge, PLAYER.WEST)));
		TestKit.check("top two: 1st and 2nd yes, 3rd no", Payback.inTopTwo(tc, tc.players.get(PLAYER.NORTH))
				&& Payback.inTopTwo(tc, tc.players.get(PLAYER.EAST)) && !Payback.inTopTwo(tc, tc.players.get(PLAYER.SOUTH)));
	}

	private static void noTraitNoPayback() {
		TestKit.suite("noTraitNoPayback");
		Table_Cards tc = table(0, 0, 0, 0);
		Player east = tc.players.get(PLAYER.EAST);
		east.Set_Player(Profile.of(Player_Type.NORMAL));
		trick(tc, PLAYER.EAST, PLAYER.NORTH);
		Payback.startHand(east);
		TestKit.checkEquals("a player without payback never acts on it", "0.0",
				String.valueOf(Payback.strength(tc, east, PLAYER.NORTH)));
	}

	private static void profileSettings() {
		TestKit.suite("profileSettings");
		Profile n = Profile.of(Player_Type.NORMAL);
		TestKit.check("off by default", n.paybackTaper() == null);
		TestKit.checkEquals("'on' gives Normal the default taper", "[100, 60, 25]",
				java.util.Arrays.toString(n.withSetting("payback", "on").paybackTaper()));
		TestKit.checkEquals("'on' gives an Aggressive player a longer grudge", "[100, 80, 50]",
				java.util.Arrays.toString(Profile.of(Player_Type.AGGRESSIVE).withSetting("payback", "on").paybackTaper()));
		TestKit.checkEquals("'on' gives a Cautious player a shorter one", "[70, 35, 10]",
				java.util.Arrays.toString(Profile.of(Player_Type.CAUTIOUS).withSetting("payback", "on").paybackTaper()));
		TestKit.checkEquals("explicit numbers", "[90, 50, 0]",
				java.util.Arrays.toString(n.withSetting("payback", "90, 50, 0").paybackTaper()));
		TestKit.check("a taper that rises is rejected", throwsIAE(() -> n.withSetting("payback", "50,60,25")));
		TestKit.check("two numbers are rejected", throwsIAE(() -> n.withSetting("payback", "100,60")));
		TestKit.checkEquals("shown in the profile", "[payback 100,60,25]", n.withSetting("payback", "on").overrides().toString());
	}

	/** Protected Queen (four low Spades), passing LEFT to a grudge target.
	 * Normally kept (a protected Queen only goes to the seat on my right).
	 * From 3rd or 4th place, payback sends her; from 1st or 2nd it doesn't. */
	private static void passesTheQueenToTheTarget() {
		TestKit.suite("passesTheQueenToTheTarget");
		// SOUTH passes LEFT, to WEST. SOUTH is last (60); WEST is 1st (10).
		TestKit.check("without a grudge the protected Queen stays", !passesQueen(10, 30, 60, 20, false));
		TestKit.check("4th place with a grudge against the receiver: the Queen goes to them",
				passesQueen(10, 30, 60, 20, true));
		// SOUTH now 2nd (15), WEST 1st (10): too risky from up here.
		TestKit.check("2nd place: too risky, the Queen stays", !passesQueen(40, 30, 15, 10, true));
	}

	private static boolean passesQueen(int n, int e, int s, int w, boolean grudge) {
		Table_Cards tc = table(n, e, s, w);
		Player south = tc.players.get(PLAYER.SOUTH);
		if (grudge) {
			south.grudges.put(PLAYER.WEST, Integer.valueOf(1));
		}
		Hand hand = new Hand();
		Object[][] cs = {
				{ CardValue.QUEEN, Suit.SPADE }, { CardValue.TWO, Suit.SPADE }, { CardValue.THREE, Suit.SPADE },
				{ CardValue.FOUR, Suit.SPADE }, { CardValue.FIVE, Suit.SPADE },
				{ CardValue.TWO, Suit.CLUB }, { CardValue.THREE, Suit.CLUB }, { CardValue.FOUR, Suit.CLUB },
				{ CardValue.FIVE, Suit.CLUB },
				{ CardValue.TWO, Suit.DIAMOND }, { CardValue.THREE, Suit.DIAMOND }, { CardValue.FOUR, Suit.DIAMOND },
				{ CardValue.FIVE, Suit.DIAMOND } };
		for (Object[] c : cs) {
			hand.addX(new Card((CardValue) c[0], (Suit) c[1], null));
		}
		for (Card c : Deal_Hand.chooseCardsToPass(hand, tc, south, PLAYER.WEST)) {
			if (Rules.isQueenOfSpades(c)) {
				return true;
			}
		}
		return false;
	}

	/** NORTH leads a Club, EAST (2nd in the standings) is winning it, and
	 * WEST is void in Clubs holding the Queen behind plenty of low Spades.
	 * With a grudge against EAST, the Queen goes on EAST's trick. */
	private static void dumpsOnTheTargetInPlay() {
		TestKit.suite("dumpsOnTheTargetInPlay");
		Card withoutGrudge = westDiscard(false);
		Card withGrudge = westDiscard(true);
		TestKit.check("with a grudge against the trick's winner, the Queen goes on it (got "
				+ withGrudge.cardValue + "-" + withGrudge.suit + ")", Rules.isQueenOfSpades(withGrudge));
		// Not a payback effect: a void seat already sheds the Queen on the
		// first chance it gets. Recorded so the payback scorer is known to
		// add to that, never fight it.
		TestKit.check("without one it goes too -- shedding her when void is the baseline",
				Rules.isQueenOfSpades(withoutGrudge));
		Card leaderWinning = westDiscard(true, true);
		TestKit.check("the payback bonus never steers away from the leader's trick (got "
				+ leaderWinning.cardValue + "-" + leaderWinning.suit + ")", Rules.isQueenOfSpades(leaderWinning));
	}

	private static Card westDiscard(boolean grudge) {
		return westDiscard(grudge, false);
	}

	/** `eastLeads` = EAST is also the standings leader. */
	private static Card westDiscard(boolean grudge, boolean eastLeads) {
		Table_Cards tc = eastLeads ? table(30, 10, 20, 40) : table(10, 30, 50, 40);
		Player west = tc.players.get(PLAYER.WEST);
		if (grudge) {
			west.grudges.put(PLAYER.EAST, Integer.valueOf(1));
		}
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = new Card(CardValue.TWO, Suit.CLUB, null);
		tc.p2_card = new Card(CardValue.KING, Suit.CLUB, null);
		tc.p3_card = new Card(CardValue.FIVE, Suit.CLUB, null);
		tc.n_card = tc.p1_card;
		tc.e_card = tc.p2_card;
		tc.s_card = tc.p3_card;
		tc.trick_nbr = 3;
		tc.hearts_broken = true;
		tc.void_suits.get(PLAYER.WEST).add(Suit.CLUB);
		List<Card> hand = new ArrayList<Card>();
		Object[][] cs = {
				{ CardValue.QUEEN, Suit.SPADE }, { CardValue.TWO, Suit.SPADE }, { CardValue.THREE, Suit.SPADE },
				{ CardValue.FOUR, Suit.SPADE }, { CardValue.SIX, Suit.SPADE },
				{ CardValue.KING, Suit.DIAMOND }, { CardValue.ACE, Suit.DIAMOND },
				{ CardValue.FOUR, Suit.HEART }, { CardValue.THREE, Suit.HEART }, { CardValue.TEN, Suit.DIAMOND } };
		for (Object[] c : cs) {
			hand.add(new Card((CardValue) c[0], (Suit) c[1], null));
		}
		Hand h = new Hand();
		for (Card c : hand) {
			h.addX(c);
		}
		return AICardScorer.pickFollow(hand, h, tc, 4, west);
	}

	// ------------------------------------------------------------------

	/** Four Normal players with payback on (100/60/25) at these totals. */
	private static Table_Cards table(int n, int e, int s, int w) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		int[] totals = { n, e, s, w };
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Profile.of(Player_Type.NORMAL).withPayback(TAPER));
			pl.p_total_points = totals[p.ordinal()];
			players.put(p, pl);
		}
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voids = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voids.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		return tc;
	}

	/** A finished trick where `queenBy` played the Queen and `winner` took it. */
	private static void trick(Table_Cards tc, PLAYER winner, PLAYER queenBy) {
		Card low = new Card(CardValue.TWO, Suit.CLUB, null);
		tc.n_card = low;
		tc.e_card = low;
		tc.s_card = low;
		tc.w_card = low;
		Card q = new Card(CardValue.QUEEN, Suit.SPADE, null);
		switch (queenBy) {
			case NORTH: tc.n_card = q; break;
			case EAST: tc.e_card = q; break;
			case SOUTH: tc.s_card = q; break;
			default: tc.w_card = q; break;
		}
		tc.who_won = winner;
		Payback.recordTrick(tc);
	}

	private static boolean throwsIAE(Runnable r) {
		try {
			r.run();
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
