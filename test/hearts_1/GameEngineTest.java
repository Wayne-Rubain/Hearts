package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * GameEngine (2026-11-05): the play steps shared by the real game and the
 * simulator. Placing a card fills the position and seat fields and tracks
 * Hearts broken and proven voids; resolving a trick counts it, finds the
 * winner, gives the winner the four cards and makes them the next leader.
 */
public final class GameEngineTest {

	private GameEngineTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		placingACard();
		resolvingATrick();
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

	private static void placingACard() {
		TestKit.suite("placingACard");
		Table_Cards tc = table();
		Card lead = card(CardValue.FIVE, Suit.CLUB), discard = card(CardValue.TWO, Suit.HEART);
		GameEngine.placeCard(tc, 1, PLAYER.EAST, lead);
		TestKit.check("the lead is position 1", tc.p1_card == lead);
		TestKit.check("and East's card", tc.e_card == lead);
		TestKit.check("Hearts not broken yet", !tc.hearts_broken);
		GameEngine.placeCard(tc, 2, PLAYER.SOUTH, discard);
		TestKit.check("the second card is position 2", tc.p2_card == discard && tc.s_card == discard);
		TestKit.check("a Heart breaks Hearts", tc.hearts_broken);
		TestKit.check("not following Clubs proves South is void in Clubs",
				VoidTracker.isVoid(tc, PLAYER.SOUTH, Suit.CLUB));
	}

	private static void resolvingATrick() {
		TestKit.suite("resolvingATrick");
		Table_Cards tc = table();
		tc.who_leads = PLAYER.EAST;
		GameEngine.placeCard(tc, 1, PLAYER.EAST, card(CardValue.FIVE, Suit.CLUB));
		GameEngine.placeCard(tc, 2, PLAYER.SOUTH, card(CardValue.KING, Suit.CLUB));
		GameEngine.placeCard(tc, 3, PLAYER.WEST, card(CardValue.QUEEN, Suit.SPADE));
		GameEngine.placeCard(tc, 4, PLAYER.NORTH, card(CardValue.ACE, Suit.HEART));
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		PLAYER winner;
		try {
			winner = GameEngine.resolveTrick(tc, true);
		} finally {
			System.setOut(out);
		}
		TestKit.check("the King of the led suit wins", winner == PLAYER.SOUTH && tc.who_won == PLAYER.SOUTH);
		TestKit.checkEquals("the trick is counted", 1, tc.trick_nbr);
		TestKit.checkEquals("the winner holds all four cards", 4, tc.players.get(PLAYER.SOUTH).won_cards.hand.size());
		TestKit.checkEquals("nobody else holds any", 0, tc.players.get(PLAYER.NORTH).won_cards.hand.size());
		TestKit.check("the winner leads next", tc.who_leads == PLAYER.SOUTH && tc.current_player == PLAYER.SOUTH);
	}
}
