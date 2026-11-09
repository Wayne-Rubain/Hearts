package hearts_1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PASS_DIRECTION;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * Committed regression coverage for the post-exchange moon-shot
 * re-evaluation, added 2026-08-16 (TODO.md's passing-craft item).
 *
 * Before this, Player.attempting_moon_shot was decided once in
 * Deal_Hand.beginHand() from the PRE-exchange hand and never revisited, so
 * every seat committed or declined on the strength of three cards it was
 * about to give away and in ignorance of the three coming back. The second
 * pass runs at the end of passCards() and is deliberately UPGRADE-ONLY --
 * see Deal_Hand.reevaluateMoonShotAfterExchange() for why turning a
 * commitment OFF there would strand a seat holding the danger kit
 * offensivePass() deliberately kept.
 *
 * Seat topology these fixtures depend on (verified, not assumed): under
 * PASS_DIRECTION.RIGHT, EAST passes to NORTH.
 */
public final class PostExchangeMoonTest {

	private PostExchangeMoonTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		incomingCardsCanCompleteAShot();
		aHandThatStillCannotShootStaysHome();
		anExistingCommitmentSurvivesTheExchange();
		theHumanSeatIsNeverCommittedForThem();
		keepHandsAreLeftAlone();
		return TestKit.summarize();
	}

	/**
	 * The case the whole feature exists for. NORTH is dealt six LOW Hearts
	 * and no Ace or King, so hasMoonHeartControl() fails outright and
	 * beginHand() declines. EAST (which passes RIGHT, i.e. to NORTH) then
	 * sends the Ace of Hearts and two more, and the post-exchange look sees
	 * a hand that genuinely clears every gate. Under the old one-shot
	 * decision this seat could never have noticed.
	 */
	private static void incomingCardsCanCompleteAShot() {
		TestKit.suite("incomingCardsCanCompleteAShot");
		Fixture f = upgradeFixture();
		TestKit.check("beginHand() declines -- six low Hearts can't clear Heart control",
				!f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
		Deal_Hand.passCards(f.hands, f.tc, PASS_DIRECTION.RIGHT);
		TestKit.check("the post-exchange look commits once the Ace of Hearts lands",
				f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
		TestKit.check("and it committed on a hand that really does hold Heart control",
				HandEvaluator.hasMoonHeartControl(f.hands.n_hand));
		// 2026-08-17: moonShotHasAceOfHearts must be finalized from the same
		// real post-exchange hand, not left at beginHand()'s stale false --
		// see AICardScorer's borderline-moon Spade-hold-back guard, which
		// reads exactly this flag.
		TestKit.check("moonShotHasAceOfHearts is finalized true from the real post-exchange hand",
				f.tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts);
	}

	/**
	 * The guard against manufacturing a shot from nothing: the same
	 * re-evaluation runs over every seat, but hands that still can't reach
	 * the hard hasMoonHeartControl() bar stay exactly where they were.
	 */
	private static void aHandThatStillCannotShootStaysHome() {
		TestKit.suite("aHandThatStillCannotShootStaysHome");
		Fixture f = junkFixture();
		Deal_Hand.passCards(f.hands, f.tc, PASS_DIRECTION.RIGHT);
		for (PLAYER seat : PLAYER.values()) {
			TestKit.check(seat + " has no Heart control and is not committed",
					!f.tc.players.get(seat).attempting_moon_shot);
		}
	}

	/** Upgrade-only, from the other direction: a seat that legitimately
	 * committed BEFORE the exchange is still committed after it. */
	private static void anExistingCommitmentSurvivesTheExchange() {
		TestKit.suite("anExistingCommitmentSurvivesTheExchange");
		Fixture f = alreadyCommittedFixture();
		TestKit.check("NORTH commits on the dealt hand",
				f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
		TestKit.check("and moonShotHasAceOfHearts is already true from the dealt hand itself",
				f.tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts);
		Deal_Hand.passCards(f.hands, f.tc, PASS_DIRECTION.RIGHT);
		TestKit.check("and keeps that commitment through the exchange -- never turned off",
				f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
		TestKit.check("moonShotHasAceOfHearts survives the exchange too (still holds the real Ace)",
				f.tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts);
	}

	/** A human's intent is unknowable, so the re-evaluation must never
	 * commit on their behalf -- the same rule beginHand() already follows.
	 * Uses the upgrade fixture, so the hand genuinely WOULD qualify: the
	 * check is that being the human seat is what stops it. */
	private static void theHumanSeatIsNeverCommittedForThem() {
		TestKit.suite("theHumanSeatIsNeverCommittedForThem");
		Fixture f = upgradeFixture(PLAYER.NORTH);
		List<Card> humanPass = new ArrayList<Card>(f.hands.n_hand.hand.subList(0, 3));
		Deal_Hand.passCards(f.hands, f.tc, PASS_DIRECTION.RIGHT, PLAYER.NORTH, humanPass);
		TestKit.check("the human seat is never auto-committed, even holding a qualifying hand",
				!f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
	}

	/** KEEP exchanges nothing, so there is no new information and
	 * passCards() correctly returns before the re-evaluation. */
	private static void keepHandsAreLeftAlone() {
		TestKit.suite("keepHandsAreLeftAlone");
		Fixture f = upgradeFixture();
		Deal_Hand.passCards(f.hands, f.tc, PASS_DIRECTION.KEEP);
		TestKit.check("a KEEP hand exchanges nothing, so no seat is newly committed",
				!f.tc.players.get(PLAYER.NORTH).attempting_moon_shot);
	}

	// ------------------------------------------------------------------

	private static final class Fixture {
		All_Hands hands;
		Table_Cards tc;
	}

	private static Fixture upgradeFixture() {
		return upgradeFixture(null);
	}

	/**
	 * NORTH: six LOW Hearts (no Ace, no King -- fails Heart control both
	 * ways) plus high side-suit cards for quality. EAST: the Ace of Hearts
	 * and two mid Hearts among otherwise worthless filler, so its defensive
	 * pass ships exactly those three to NORTH.
	 */
	private static Fixture upgradeFixture(PLAYER humanSeat) {
		Fixture f = new Fixture();
		f.hands = new All_Hands();
		f.hands.n_hand = handOf(
				card(CardValue.TWO, Suit.HEART), card(CardValue.THREE, Suit.HEART),
				card(CardValue.FOUR, Suit.HEART), card(CardValue.FIVE, Suit.HEART),
				card(CardValue.SIX, Suit.HEART), card(CardValue.SEVEN, Suit.HEART),
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE),
				card(CardValue.ACE, Suit.CLUB), card(CardValue.KING, Suit.CLUB),
				card(CardValue.ACE, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.DIAMOND));
		f.hands.e_hand = handOf(
				card(CardValue.ACE, Suit.HEART), card(CardValue.NINE, Suit.HEART),
				card(CardValue.EIGHT, Suit.HEART),
				card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.CLUB),
				card(CardValue.FOUR, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB),
				card(CardValue.SIX, Suit.CLUB), card(CardValue.SEVEN, Suit.CLUB),
				card(CardValue.TWO, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.FOUR, Suit.DIAMOND), card(CardValue.FIVE, Suit.DIAMOND));
		f.hands.s_hand = filler(Suit.SPADE, Suit.CLUB);
		f.hands.w_hand = filler(Suit.SPADE, Suit.DIAMOND);
		f.tc = tableOf();
		Deal_Hand.beginHand(f.hands, f.tc, humanSeat);
		return f;
	}

	/** NORTH holds the Ace of Hearts and five more -- clears Heart control
	 * on the dealt hand, so beginHand() commits it before any exchange. */
	private static Fixture alreadyCommittedFixture() {
		Fixture f = new Fixture();
		f.hands = new All_Hands();
		f.hands.n_hand = handOf(
				card(CardValue.ACE, Suit.HEART), card(CardValue.KING, Suit.HEART),
				card(CardValue.QUEEN, Suit.HEART), card(CardValue.JACK, Suit.HEART),
				card(CardValue.TEN, Suit.HEART), card(CardValue.NINE, Suit.HEART),
				card(CardValue.ACE, Suit.SPADE), card(CardValue.KING, Suit.SPADE),
				card(CardValue.ACE, Suit.CLUB), card(CardValue.KING, Suit.CLUB),
				card(CardValue.ACE, Suit.DIAMOND), card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.DIAMOND));
		f.hands.e_hand = filler(Suit.CLUB, Suit.DIAMOND);
		f.hands.s_hand = filler(Suit.SPADE, Suit.CLUB);
		f.hands.w_hand = filler(Suit.SPADE, Suit.DIAMOND);
		f.tc = tableOf();
		Deal_Hand.beginHand(f.hands, f.tc, null);
		return f;
	}

	private static Fixture junkFixture() {
		Fixture f = new Fixture();
		f.hands = new All_Hands();
		f.hands.n_hand = filler(Suit.CLUB, Suit.DIAMOND);
		f.hands.e_hand = filler(Suit.DIAMOND, Suit.CLUB);
		f.hands.s_hand = filler(Suit.SPADE, Suit.CLUB);
		f.hands.w_hand = filler(Suit.SPADE, Suit.DIAMOND);
		f.tc = tableOf();
		Deal_Hand.beginHand(f.hands, f.tc, null);
		return f;
	}

	/** 13 low cards across two non-Heart suits -- unable to clear
	 * hasMoonHeartControl() under any circumstances. */
	private static Hand filler(Suit a, Suit b) {
		CardValue[] lows = { CardValue.TWO, CardValue.THREE, CardValue.FOUR, CardValue.FIVE,
				CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT };
		Hand h = new Hand();
		for (int i = 0; i < 7; i++) {
			h.addX(card(lows[i], a));
		}
		for (int i = 0; i < 6; i++) {
			h.addX(card(lows[i], b));
		}
		return h;
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

	private static Table_Cards tableOf() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			// MOON_SHOOTER keeps MOON_OFFENSE above zero so the gate under
			// test is the HAND, not the personality row.
			pl.Set_Player(Player_Type.MOON_SHOOTER);
			players.put(p, pl);
		}
		tc.players = players;
		return tc;
	}
}
