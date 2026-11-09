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
 * Committed regression coverage for AICardScorer's moon-stopper logic, added
 * 2026-08-15 with the stopper exception itself (a user refinement to the
 * attack-the-leader tactic): denial of point cards to a visible shooter is
 * mandatory UNLESS this seat provably holds the stopper -- a Heart that
 * outranks every Heart still at large, guaranteeing the shot can be broken.
 * With the stopper held, feeding the shooter sub-stopper points is a trap,
 * not a catastrophe: they eat everything the moment the stopper breaks the
 * shot. The stopper itself must never be fed, and a King is never trusted
 * while the Ace is unaccounted for.
 */
public final class MoonStopperTest {

	private MoonStopperTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		aceIsAlwaysTheStopper();
		kingIsNotTrustedWhileAceIsAtLarge();
		kingBecomesTheStopperOnceAceIsAccountedFor();
		noHeartsMeansNoStopper();
		queenIsExpendableOnlyWithStopperHeld();
		subStopperHeartsAreExpendableTheStopperIsNot();
		return TestKit.summarize();
	}

	private static void aceIsAlwaysTheStopper() {
		TestKit.suite("aceIsAlwaysTheStopper");
		Table_Cards tc = emptyTable();
		List<Card> mine = cards(card(CardValue.ACE, Suit.HEART), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("holding the Ace of Hearts is a provable stopper regardless of what's out",
				AICardScorer.holdsMoonStopper(tc, mine, 1));
	}

	private static void kingIsNotTrustedWhileAceIsAtLarge() {
		TestKit.suite("kingIsNotTrustedWhileAceIsAtLarge");
		Table_Cards tc = emptyTable();
		List<Card> mine = cards(card(CardValue.KING, Suit.HEART));
		TestKit.check("a King is no stopper while the unseen Ace could be in the shooter's hand",
				!AICardScorer.holdsMoonStopper(tc, mine, 1));
	}

	private static void kingBecomesTheStopperOnceAceIsAccountedFor() {
		TestKit.suite("kingBecomesTheStopperOnceAceIsAccountedFor");
		Table_Cards tc = emptyTable();
		// The Ace was captured in an earlier trick -- now nothing at large
		// outranks the King.
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.ACE, Suit.HEART));
		List<Card> mine = cards(card(CardValue.KING, Suit.HEART));
		TestKit.check("the King is the stopper once the Ace sits in a won pile",
				AICardScorer.holdsMoonStopper(tc, mine, 1));

		// Also accounted when face up in the trick in progress: play
		// position 3 sees positions 1-2.
		Table_Cards live = emptyTable();
		live.p1_card = card(CardValue.THREE, Suit.CLUB);
		live.p2_card = card(CardValue.ACE, Suit.HEART); // discarded onto this trick
		TestKit.check("the King is the stopper once the Ace is face up on the table",
				AICardScorer.holdsMoonStopper(live, mine, 3));
	}

	private static void noHeartsMeansNoStopper() {
		TestKit.suite("noHeartsMeansNoStopper");
		Table_Cards tc = emptyTable();
		List<Card> mine = cards(card(CardValue.ACE, Suit.SPADE), card(CardValue.ACE, Suit.CLUB));
		TestKit.check("a heartless hand holds no stopper however strong its other suits",
				!AICardScorer.holdsMoonStopper(tc, mine, 1));
	}

	private static void queenIsExpendableOnlyWithStopperHeld() {
		TestKit.suite("queenIsExpendableOnlyWithStopperHeld");
		Table_Cards tc = emptyTable();
		Card queen = card(CardValue.QUEEN, Suit.SPADE);

		List<Card> withStopper = cards(card(CardValue.ACE, Suit.HEART), queen);
		TestKit.check("the Queen may be fed to a shooter while the stopper is held",
				AICardScorer.expendableAgainstShooter(tc, withStopper, 1, queen));

		List<Card> withoutStopper = cards(card(CardValue.KING, Suit.HEART), queen);
		TestKit.check("the Queen must be denied while the shot is genuinely live",
				!AICardScorer.expendableAgainstShooter(tc, withoutStopper, 1, queen));
	}

	private static void subStopperHeartsAreExpendableTheStopperIsNot() {
		TestKit.suite("subStopperHeartsAreExpendableTheStopperIsNot");
		Table_Cards tc = emptyTable();
		Card ace = card(CardValue.ACE, Suit.HEART);
		Card three = card(CardValue.THREE, Suit.HEART);
		List<Card> mine = cards(ace, three);

		TestKit.check("a low Heart may be fed while the Ace stays behind it",
				AICardScorer.expendableAgainstShooter(tc, mine, 1, three));
		TestKit.check("the stopper itself is never expendable",
				!AICardScorer.expendableAgainstShooter(tc, mine, 1, ace));

		// Two boss-grade Hearts: with the Queen of Hearts the top card still
		// at large, feeding the KING keeps the Ace as the stopper.
		Card king = card(CardValue.KING, Suit.HEART);
		List<Card> twoBosses = cards(ace, king);
		TestKit.check("a second-from-top Heart is expendable while the true boss stays home",
				AICardScorer.expendableAgainstShooter(tc, twoBosses, 1, king));
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

	/** Four seats with empty won piles -- every Heart not in `myCards` reads
	 * as still at large, which is the conservative baseline the stopper
	 * logic is defined against. */
	private static Table_Cards emptyTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player player = new Player();
			player.tbl_player = p;
			players.put(p, player);
		}
		tc.players = players;
		return tc;
	}
}
