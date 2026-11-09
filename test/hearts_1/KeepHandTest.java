package hearts_1;

import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Committed regression coverage for the 2026-08-09 KEEP-hand defect (see
 * CHANGELOG.md's rules-audit entry, defects 1 and 2): attempting_moon_shot
 * used to only be refreshed by Deal_Hand.chooseCardsToPass(), which sits
 * behind passCards()'s KEEP-direction early return -- so on every 4th hand
 * (KEEP) no seat's flag was written at all, and a seat that had committed
 * the previous hand carried a stale `true` into a hand it never evaluated.
 * Fixed by moving the refresh into Deal_Hand.beginHand(), which runs for
 * every hand regardless of pass direction. These tests call beginHand()
 * twice with no passCards() in between -- exactly what a KEEP hand looks
 * like -- and check the flag reflects the SECOND hand, not the first.
 * Replaces the old uncommitted KeepHandCheck (5 scenarios).
 */
public final class KeepHandTest {

	private KeepHandTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		flagRefreshesOnASecondBeginHandWithNoPassCardsBetween();
		staleTrueFlagDoesNotSurviveANonQualifyingSecondHand();
		humanSeatFlagIsAlwaysClearedRegardlessOfHandStrength();
		return TestKit.summarize();
	}

	private static void flagRefreshesOnASecondBeginHandWithNoPassCardsBetween() {
		TestKit.suite("flagRefreshesOnASecondBeginHandWithNoPassCardsBetween");
		Table_Cards tc = freshTableCards();

		// Hand 1: NORTH is dealt a hand that will NOT qualify (weak, no
		// heart control) -- attempting_moon_shot should end up false.
		Deal_Hand.beginHand(allHandsWith(PLAYER.NORTH, weakHand()), tc, null);
		TestKit.check("hand 1: weak hand does not commit", !tc.players.get(PLAYER.NORTH).attempting_moon_shot);

		// Hand 2 (simulating a KEEP hand -- passCards() never called):
		// NORTH is dealt a genuinely strong moon hand this time.
		Deal_Hand.beginHand(allHandsWith(PLAYER.NORTH, strongMoonHand()), tc, null);
		TestKit.check("hand 2: a second beginHand() with no passCards() between them still evaluates fresh -- strong hand commits",
				tc.players.get(PLAYER.NORTH).attempting_moon_shot);
	}

	private static void staleTrueFlagDoesNotSurviveANonQualifyingSecondHand() {
		TestKit.suite("staleTrueFlagDoesNotSurviveANonQualifyingSecondHand");
		Table_Cards tc = freshTableCards();

		// Hand 1: a strong moon hand -- commits.
		Deal_Hand.beginHand(allHandsWith(PLAYER.WEST, strongMoonHand()), tc, null);
		TestKit.check("hand 1: strong hand commits", tc.players.get(PLAYER.WEST).attempting_moon_shot);

		// Hand 2 (KEEP-hand shape again): a weak hand. The exact bug this
		// pins down: AI.isMoonShotAlive() reads a `true` flag with no point
		// card captured yet as "still alive", so a stale true here would put
		// the seat in MOON_SHOT play mode for a hand it never actually
		// qualified for.
		Deal_Hand.beginHand(allHandsWith(PLAYER.WEST, weakHand()), tc, null);
		TestKit.check("hand 2: weak hand does NOT inherit hand 1's stale true",
				!tc.players.get(PLAYER.WEST).attempting_moon_shot);
	}

	private static void humanSeatFlagIsAlwaysClearedRegardlessOfHandStrength() {
		TestKit.suite("humanSeatFlagIsAlwaysClearedRegardlessOfHandStrength");
		Table_Cards tc = freshTableCards();
		Deal_Hand.beginHand(allHandsWith(PLAYER.SOUTH, strongMoonHand()), tc, PLAYER.SOUTH);
		TestKit.check("the designated human seat's flag is cleared even on a hand that would otherwise qualify",
				!tc.players.get(PLAYER.SOUTH).attempting_moon_shot);
	}

	// ------------------------------------------------------------------

	private static Table_Cards freshTableCards() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.Set_Player(Player_Type.MOON_SHOOTER); // lowest threshold -- isolates the refresh bug, not the eagerness tuning
			players.put(p, pl);
		}
		tc.players = players;
		return tc;
	}

	/** All_Hands with `subject`'s hand set to `subjectHand`; the other 3
	 * seats get a harmless filler hand -- beginHand() evaluates every seat,
	 * but only `subject`'s result is under test in each scenario above. */
	private static All_Hands allHandsWith(PLAYER subject, Hand subjectHand) {
		All_Hands all = new All_Hands();
		all.n_hand = (subject == PLAYER.NORTH) ? subjectHand : weakHand();
		all.e_hand = (subject == PLAYER.EAST) ? subjectHand : weakHand();
		all.s_hand = (subject == PLAYER.SOUTH) ? subjectHand : weakHand();
		all.w_hand = (subject == PLAYER.WEST) ? subjectHand : weakHand();
		return all;
	}

	/** Passes HandEvaluator's heart-control gate (Ace + 4+ Hearts) and
	 * scores comfortably above even MOON_SHOOTER's floor. */
	private static Hand strongMoonHand() {
		Hand h = new Hand();
		addAll(h, card(CardValue.ACE, Suit.HEART), card(CardValue.KING, Suit.HEART),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART), card(CardValue.TEN, Suit.HEART),
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE));
		return h;
	}

	/** Fails the heart-control gate outright (no Ace/King of Hearts) -- can
	 * never commit regardless of standings, per HandEvaluator's gate. */
	private static Hand weakHand() {
		Hand h = new Hand();
		addAll(h, card(CardValue.TWO, Suit.CLUB), card(CardValue.FOUR, Suit.DIAMOND),
				card(CardValue.SIX, Suit.SPADE), card(CardValue.THREE, Suit.HEART));
		return h;
	}

	private static Card card(CardValue value, Suit suit) {
		return new Card(value, suit, null);
	}

	private static void addAll(Hand hand, Card... cards) {
		for (Card c : cards) {
			hand.addX(c);
		}
	}
}
