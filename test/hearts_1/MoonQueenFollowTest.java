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

/**
 * Committed regression coverage for AICardScorer's "MOON-STOPPING, tactic
 * 2d" scorer, added 2026-08-26 from a live hand the user flagged: WEST
 * committed to a moon shot but never held the Queen of Spades (passed away
 * during the exchange), ran the table to trick 10, and led the King of
 * Spades. EAST held only the Two and Queen of Spades -- forced to follow
 * suit, unable to win either way, but with a real choice of which one to
 * duck under with -- and played the Queen, handing WEST the exact card the
 * shot needed.
 *
 * The gap this closes: tactic 2 (the flagship "never feed the Queen"
 * penalty, weight MOON_DENY_FEED_QUEEN) only ever fires on a VOID discard --
 * it returns 0 whenever ctx.followingSuit is true. Tactic 2c reserves the
 * Ace/King of Spades on a forced Spade follow, but explicitly excludes the
 * Queen (candidate.cardValue != KING && != ACE -> return 0). With neither
 * engaged, the baseline duck-under rule decided alone, and that rule sheds
 * the HIGHEST safe loser first -- exactly backwards for the one card a live
 * shooter is hunting.
 */
public final class MoonQueenFollowTest {

	private MoonQueenFollowTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		theActualHand_QueenIsDuckedInsteadOfFed();
		noPenaltyWithoutAThreateningShooter();
		noPenaltyWhenTheShooterIsAlreadyBeaten();
		noPenaltyWhenAHeartStopperMakesTheQueenExpendable();
		noPenaltyOnASingletonQueen();
		followSideStillCapturesWithTheQueenWhenThatWinsInstead();
		return TestKit.summarize();
	}

	/**
	 * The bug, reproduced directly: WEST (the shooter) leads the King of
	 * Spades, already unbeatable by EAST's Two or Queen. Before the fix,
	 * ordinary duck-highest-first order chose the Queen; the fix must choose
	 * the Two instead.
	 */
	private static void theActualHand_QueenIsDuckedInsteadOfFed() {
		TestKit.suite("theActualHand_QueenIsDuckedInsteadOfFed");
		Table_Cards tc = kingLedTable();
		List<Card> options = twoAndQueenOfSpades();
		Player east = tc.players.get(PLAYER.EAST);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, east);
		TestKit.checkEquals("the Two is played instead of feeding the shooter the Queen",
				CardValue.TWO, chosen.cardValue);
	}

	private static void noPenaltyWithoutAThreateningShooter() {
		TestKit.suite("noPenaltyWithoutAThreateningShooter");
		Table_Cards tc = kingLedTable();
		tc.players.get(PLAYER.WEST).won_cards = new Hand(); // no captured points -- no shooter visible
		List<Card> options = twoAndQueenOfSpades();
		Player east = tc.players.get(PLAYER.EAST);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, east);
		TestKit.checkEquals("with no visible shooter, ordinary duck preference sheds the Queen as usual",
				CardValue.QUEEN, chosen.cardValue);
	}

	private static void noPenaltyWhenTheShooterIsAlreadyBeaten() {
		TestKit.suite("noPenaltyWhenTheShooterIsAlreadyBeaten");
		// WEST leads the King, but NORTH (playing before EAST) has already
		// covered it with the Ace -- WEST cannot win this trick regardless
		// of what EAST throws, so there is nothing left to protect.
		Table_Cards tc = kingLedTable();
		tc.p2_card = card(CardValue.ACE, Suit.SPADE);
		List<Card> options = twoAndQueenOfSpades();
		Player east = tc.players.get(PLAYER.EAST);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 3, east);
		TestKit.checkEquals("shooter already beaten in this trick -- ordinary duck preference applies",
				CardValue.QUEEN, chosen.cardValue);
	}

	/**
	 * The expendable exception, and the reason it needed myHand rather than
	 * legalOptions (see the scorer's own doc): EAST holds a boss Heart
	 * nothing else can beat, which guarantees the shot can be broken later
	 * regardless of this trick -- so protecting the Queen here buys nothing,
	 * and ordinary duck preference should decide instead.
	 */
	private static void noPenaltyWhenAHeartStopperMakesTheQueenExpendable() {
		TestKit.suite("noPenaltyWhenAHeartStopperMakesTheQueenExpendable");
		Table_Cards tc = kingLedTable();
		List<Card> options = twoAndQueenOfSpades();
		Hand fullHand = handOf(options);
		fullHand.addX(card(CardValue.ACE, Suit.HEART)); // the stopper -- NOT among legalOptions
		Player east = tc.players.get(PLAYER.EAST);
		Card chosen = AICardScorer.pickFollow(options, fullHand, tc, 2, east);
		TestKit.checkEquals("a held Heart stopper makes the Queen expendable -- ordinary duck preference applies",
				CardValue.QUEEN, chosen.cardValue);
	}

	private static void noPenaltyOnASingletonQueen() {
		TestKit.suite("noPenaltyOnASingletonQueen");
		Table_Cards tc = kingLedTable();
		List<Card> options = cards(card(CardValue.QUEEN, Suit.SPADE));
		Player east = tc.players.get(PLAYER.EAST);
		Card chosen = AICardScorer.pickFollow(options, handOf(options), tc, 2, east);
		TestKit.checkEquals("no alternative Spade -- the Queen is forced regardless",
				CardValue.QUEEN, chosen.cardValue);
	}

	/**
	 * Sanity check that this scorer's !ctx.canWin guard leaves the
	 * take-the-Queen-to-deny-the-shot decision (tactic 3) untouched: when the
	 * Queen actually WINS the trick, playing it is a denial, not a feed.
	 */
	private static void followSideStillCapturesWithTheQueenWhenThatWinsInstead() {
		TestKit.suite("followSideStillCapturesWithTheQueenWhenThatWinsInstead");
		Table_Cards tc = kingLedTable();
		tc.p1_card = card(CardValue.JACK, Suit.SPADE); // WEST leads low -- EAST's Queen would win
		List<Card> options = twoAndQueenOfSpades();
		Player east = tc.players.get(PLAYER.EAST);
		AICardScorer.FollowContext ctx = new AICardScorer.FollowContext(options, handOf(options), tc, 2, east);
		TestKit.check("the Queen can actually win this trick -- tactic 2d must not apply",
				ctx.canWin);
	}

	// ------------------------------------------------------------------

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static List<Card> twoAndQueenOfSpades() {
		return cards(card(CardValue.TWO, Suit.SPADE), card(CardValue.QUEEN, Suit.SPADE));
	}

	private static List<Card> cards(Card... cs) {
		List<Card> list = new ArrayList<Card>();
		for (Card c : cs) {
			list.add(c);
		}
		return list;
	}

	private static Hand handOf(List<Card> cs) {
		Hand h = new Hand();
		for (Card c : cs) {
			h.addX(c);
		}
		return h;
	}

	/** WEST leads the King of Spades and has already banked a majority of
	 * the Hearts (7) -- a clearly real, already-in-progress shot, so
	 * MoonThreat.threateningShooter fires without needing any standings
	 * finesse. EAST is about to follow into position 2. */
	private static Table_Cards kingLedTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		players.put(PLAYER.NORTH, playerOf(PLAYER.NORTH, 20));
		Player east = playerOf(PLAYER.EAST, 20);
		players.put(PLAYER.EAST, east);
		players.put(PLAYER.SOUTH, playerOf(PLAYER.SOUTH, 20));
		Player west = playerOf(PLAYER.WEST, 0);
		for (CardValue v : new CardValue[] { CardValue.TWO, CardValue.THREE, CardValue.FOUR,
				CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT }) { // 7 Hearts
			west.won_cards.addX(card(v, Suit.HEART));
		}
		players.put(PLAYER.WEST, west);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.who_leads = PLAYER.WEST;
		tc.current_player = PLAYER.EAST;
		tc.p1_card = card(CardValue.KING, Suit.SPADE);
		tc.hearts_broken = true;
		return tc;
	}

	private static Player playerOf(PLAYER seat, int totalPoints) {
		Player p = new Player();
		p.tbl_player = seat;
		p.p_total_points = totalPoints;
		p.won_cards = new Hand();
		return p;
	}
}
