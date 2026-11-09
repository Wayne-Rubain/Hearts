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
 * Regression coverage for the 2026-10-08 Aggressive take guard, from live
 * hand 6: South led the 10 of Spades, West (Aggressive, holding the 3 and King
 * of Spades) played second and won with the King, and East, last to play,
 * dumped the Queen on it. Aggressive must not win a Spade trick with the King
 * or Ace while the Queen is still out and a seat yet to act may hold her.
 */
public final class AggressiveQueenGuardTest {

	private AggressiveQueenGuardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		aggressiveDucksWhileTheQueenIsLive();
		aggressiveTakesOnceTheQueenIsGone();
		aggressiveTakesWhenEveryLaterSeatIsVoidInSpades();
		guardIgnoresSpadesBelowTheQueen();
		return TestKit.summarize();
	}

	private static void aggressiveDucksWhileTheQueenIsLive() {
		TestKit.suite("aggressiveDucksWhileTheQueenIsLive");
		Fixture f = new Fixture();
		Card chosen = f.westPlays();
		TestKit.checkEquals("the hand-6 trick: West ducks with the 3, not the King", f.three, chosen);
	}

	private static void aggressiveTakesOnceTheQueenIsGone() {
		TestKit.suite("aggressiveTakesOnceTheQueenIsGone");
		Fixture f = new Fixture();
		f.tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		Card chosen = f.westPlays();
		TestKit.checkEquals("with the Queen already captured, Aggressive still fights with the King",
				f.king, chosen);
	}

	private static void aggressiveTakesWhenEveryLaterSeatIsVoidInSpades() {
		TestKit.suite("aggressiveTakesWhenEveryLaterSeatIsVoidInSpades");
		Fixture f = new Fixture();
		f.tc.void_suits.get(PLAYER.NORTH).add(Suit.SPADE);
		f.tc.void_suits.get(PLAYER.EAST).add(Suit.SPADE);
		TestKit.check("nobody left to act can hold the Queen, so the guard stands down",
				!AICardScorer.spadeWinRisksLiveQueen(f.context(), f.king));
	}

	private static void guardIgnoresSpadesBelowTheQueen() {
		TestKit.suite("guardIgnoresSpadesBelowTheQueen");
		Fixture f = new Fixture();
		Card jack = card(CardValue.JACK, Suit.SPADE);
		TestKit.check("a Jack can't attract the Queen -- she'd beat it", !AICardScorer.spadeWinRisksLiveQueen(
				f.context(), jack));
	}

	// ------------------------------------------------------------------

	private static final class Fixture {
		final Table_Cards tc = new Table_Cards();
		final Player west = new Player();
		final Card three = card(CardValue.THREE, Suit.SPADE);
		final Card king = card(CardValue.KING, Suit.SPADE);
		final Hand westHand = new Hand();

		Fixture() {
			Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
			Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
			for (PLAYER p : PLAYER.values()) {
				Player pl = (p == PLAYER.WEST) ? west : new Player();
				pl.tbl_player = p;
				pl.won_cards = new Hand();
				players.put(p, pl);
				voidSuits.put(p, EnumSet.noneOf(Suit.class));
			}
			west.Set_Player(Player_Type.AGGRESSIVE);
			tc.players = players;
			tc.void_suits = voidSuits;
			// Trick 2: South led the 10 of Spades; West plays second.
			tc.trick_nbr = 1;
			tc.who_leads = PLAYER.SOUTH;
			tc.current_player = PLAYER.WEST;
			tc.p1_card = card(CardValue.TEN, Suit.SPADE);
			westHand.addX(three);
			westHand.addX(king);
			westHand.addX(card(CardValue.NINE, Suit.DIAMOND));
			westHand.addX(card(CardValue.TEN, Suit.HEART));
		}

		List<Card> legal() {
			List<Card> l = new ArrayList<Card>();
			l.add(three);
			l.add(king);
			return l;
		}

		Card westPlays() {
			return AICardScorer.pickFollow(legal(), westHand, tc, 2, west);
		}

		AICardScorer.FollowContext context() {
			return new AICardScorer.FollowContext(legal(), westHand, tc, 2, west);
		}
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}
}
