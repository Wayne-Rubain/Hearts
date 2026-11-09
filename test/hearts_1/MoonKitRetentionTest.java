package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Committed regression coverage for the Moon_Shooter passing nuance, added
 * 2026-08-23 for the second half of TODO.md's "passing-craft per-type
 * nuances" ("Moon_Shooter keeps its own kit when not committed" -- the
 * Avoids_Queen half shipped 2026-08-16, see AvoidsQueenPassTest).
 *
 * THE GAP. A Moon_Shooter-type seat whose pre-pass hand didn't justify a shot
 * falls through to defensivePass(), where dangerScore() ranks exactly the moon
 * kit -- the Queen of Spades, the top Hearts, Spade control -- as the FIRST
 * cards to get rid of. Right for a seat that will never shoot; precisely
 * wrong for the one type whose whole identity is looking for the chance.
 *
 * WHY IT'S BUILDABLE NOW. The blocker used to be that
 * Player.attempting_moon_shot was computed from the PRE-pass hand and never
 * revisited, so a seat retaining its kit "in case the incoming three complete
 * it" could never notice that they had. Deal_Hand.reevaluateMoonShotAfterExchange()
 * shipped 2026-08-16 and gives that second look, so a retained kit now has a
 * path to being used.
 *
 * WHY IT'S MILD. TODO.md's own analysis is explicit that a non-committed seat
 * which hoards the full kit and STILL fails the second look is holding pure
 * liability. So this is a tie-breaking reluctance, not a veto -- these tests
 * pin BOTH halves of that: the kit stops being volunteered ahead of harmless
 * filler, and a Queen the pass is otherwise screaming to be rid of still goes.
 */
public final class MoonKitRetentionTest {

	private MoonKitRetentionTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		moonShooterKeepsATopHeartThatANormalSeatShipsOut();
		normalSeatIsCompletelyUnaffected();
		theReluctanceIsNotAVeto();
		everyOtherTypeKeepsTheOldBehaviour();
		return TestKit.summarize();
	}

	/**
	 * The nuance in one check. A hand with no Spade danger at all, so the
	 * pass is decided among Hearts and filler: a Normal seat ships its three
	 * highest Hearts, while a Moon_Shooter's reluctance is enough to keep the
	 * best one back and send a spare low card instead.
	 */
	private static void moonShooterKeepsATopHeartThatANormalSeatShipsOut() {
		TestKit.suite("moonShooterKeepsATopHeartThatANormalSeatShipsOut");
		List<Card> normal = passOf(heartRichHand(), Player_Type.NORMAL);
		List<Card> shooter = passOf(heartRichHand(), Player_Type.MOON_SHOOTER);
		TestKit.check("a Normal seat passes the Ace of Hearts away",
				contains(normal, CardValue.ACE, Suit.HEART));
		TestKit.check("a Moon_Shooter keeps it, in case the incoming three complete a shot",
				!contains(shooter, CardValue.ACE, Suit.HEART));
	}

	private static void normalSeatIsCompletelyUnaffected() {
		TestKit.suite("normalSeatIsCompletelyUnaffected");
		// The bias is trait-gated, so every seat without Shooting_the_Moon
		// must pass exactly what it passed before this landed.
		List<Card> passed = passOf(heartRichHand(), Player_Type.NORMAL);
		TestKit.check("the three highest Hearts still go, top-down",
				contains(passed, CardValue.ACE, Suit.HEART)
						&& contains(passed, CardValue.NINE, Suit.HEART)
						&& contains(passed, CardValue.EIGHT, Suit.HEART));
	}

	/**
	 * The other half of "mild". An unprotected Queen of Spades scores 15-25 in
	 * dangerScore()'s units and the retention bias is worth 10 against her --
	 * nowhere near enough to make a seat sit on her. If this ever inverts, the
	 * bias has been sized wrong and the type has become suicidal rather than
	 * ambitious.
	 */
	private static void theReluctanceIsNotAVeto() {
		TestKit.suite("theReluctanceIsNotAVeto");
		List<Card> passed = passOf(bareQueenHand(), Player_Type.MOON_SHOOTER);
		TestKit.check("a bare, unprotected Queen of Spades still gets passed away",
				contains(passed, CardValue.QUEEN, Suit.SPADE));
	}

	private static void everyOtherTypeKeepsTheOldBehaviour() {
		TestKit.suite("everyOtherTypeKeepsTheOldBehaviour");
		Player_Type[] unchanged = { Player_Type.NORMAL, Player_Type.CAUTIOUS,
				Player_Type.AGGRESSIVE, Player_Type.AVOIDS_QUEEN,
				Player_Type.AGGRESSIVE_AVOIDS_QUEEN };
		for (Player_Type type : unchanged) {
			List<Card> passed = passOf(heartRichHand(), type);
			TestKit.check(type + " still ships its best Heart",
					contains(passed, CardValue.ACE, Suit.HEART));
		}
	}

	// ------------------------------------------------------------------

	/**
	 * ONE top Heart over a run of middling ones, plus harmless filler, and
	 * deliberately no Queen of Spades and no Spade control -- so the pass is
	 * decided purely on the Hearts axis this nuance is about.
	 *
	 * The shape is chosen carefully, and the first attempt at it was wrong in
	 * an instructive way: a hand with the Ace, King and Queen of Hearts makes
	 * EVERY type keep the Ace, because three top Hearts is 12 moon fuel and
	 * withholdMoonFuel() (2026-08-07) already swaps the highest one back out
	 * to avoid handing a recipient a complete kit. That is a different, older
	 * rule about what the RECIPIENT gets, and it masked the rule under test
	 * entirely. One top Heart is 4 fuel, safely under MOON_FUEL_LIMIT, so the
	 * older rule stays out of the way and the retention bias is the only thing
	 * that can move the choice.
	 *
	 * The middling Hearts are spaced so that the Ace's 8-point bias is exactly
	 * enough to drop it below the 7 of Hearts (20-8=12 against 13) and no
	 * further -- a genuine tie-break, which is what "mild" is supposed to mean.
	 *
	 * Kept well under the moon-commitment bar on purpose: this whole nuance
	 * only applies to a seat that did NOT commit, since a committed one goes
	 * to offensivePass() and never reaches dangerScore() at all.
	 */
	private static Hand heartRichHand() {
		return handOf(
				card(CardValue.ACE, Suit.HEART), card(CardValue.NINE, Suit.HEART),
				card(CardValue.EIGHT, Suit.HEART), card(CardValue.SEVEN, Suit.HEART),
				card(CardValue.TWO, Suit.SPADE), card(CardValue.THREE, Suit.SPADE),
				card(CardValue.FOUR, Suit.SPADE), card(CardValue.FIVE, Suit.SPADE),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.SIX, Suit.HEART),
				card(CardValue.FIVE, Suit.HEART),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND));
	}
	// 2026-10-10: four low Spades (was three, with the 5 of Clubs). Three
	// now counts as short on Spades, and the void rule (Deal_Hand.
	// clubDiamondPassScore) would empty the Diamonds instead -- right play,
	// but not what this suite is about.
	// 2026-10-11: the 6 and 5 of Hearts replace the 3 and 4 of Clubs. The
	// new two-ducking-Hearts floor (Deal_Hand.isDuckingHeart) would otherwise
	// stop the third Heart going -- again right play, not this suite's point.

	/** The Queen with only two low Spades behind her -- well below the
	 * protection bar, so she is the standout danger in the hand. */
	private static Hand bareQueenHand() {
		return handOf(
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.SPADE),
				card(CardValue.THREE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.HEART), card(CardValue.SIX, Suit.HEART),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.CLUB),
				card(CardValue.FOUR, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FOUR, Suit.DIAMOND), card(CardValue.FIVE, Suit.DIAMOND));
	}

	private static List<Card> passOf(Hand hand, Player_Type type) {
		Table_Cards tc = tableFor(type);
		Player me = tc.players.get(PLAYER.SOUTH);
		List<Card> chosen = Deal_Hand.chooseCardsToPass(hand, tc, me, PLAYER.WEST);
		TestKit.check(type + " did not commit to a shot (this nuance only covers seats that didn't)",
				!me.attempting_moon_shot);
		return chosen;
	}

	private static boolean contains(List<Card> cards, CardValue value, Suit suit) {
		for (Card c : cards) {
			if (c.cardValue == value && c.suit == suit) {
				return true;
			}
		}
		return false;
	}

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

	private static Table_Cards tableFor(Player_Type type) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Player_Type.NORMAL);
			players.put(p, pl);
		}
		Player me = players.get(PLAYER.SOUTH);
		me.Set_Player(type);
		me.tbl_player = PLAYER.SOUTH;
		tc.players = players;
		return tc;
	}
}
