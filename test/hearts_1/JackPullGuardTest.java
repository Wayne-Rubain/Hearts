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
 * Committed regression coverage for AICardScorer.jackPullRisksTheQueen and
 * its effect on the JACK_HUNT "pull the Jack out" lead, added 2026-08-16 for
 * TODO.md's "Jack-of-Diamonds realistic chance viability dial".
 *
 * The pull lead spends a boss Diamond to force the Jack of Diamonds out.
 * Winning the trick is certain; CAPTURING the Jack is not -- its holder can
 * simply follow with a low Diamond. What IS certain is that any seat void in
 * Diamonds gets a free discard onto a trick the puller has already committed
 * to winning, and the Queen of Spades is the best discard a void hand can
 * make. -10 does not cover +13, so the hunt stands down whenever that trap
 * is live.
 */
public final class JackPullGuardTest {

	private JackPullGuardTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		safeWhenNobodyIsVoidInDiamonds();
		riskyOnceAVoidSeatCouldDumpTheQueen();
		safeOnceTheQueenIsAlreadyWon();
		safeWhileIHoldTheQueenMyself();
		safeWhenTheOnlyVoidSeatIsAlsoVoidInSpades();
		pullLeadStandsDownIntoTheTrap();
		pullLeadStillFiresWhenTheTrapIsAbsent();
		pullLeadStandsDownOnAnUnsustainableHunt();
		twoBossDiamondsSustainTheHuntWithoutLength();
		holdingTheJackIsNotAHunt();
		noWeaponMeansNoChance();
		return TestKit.summarize();
	}

	// ---- the predicate itself -------------------------------------------

	private static void safeWhenNobodyIsVoidInDiamonds() {
		TestKit.suite("safeWhenNobodyIsVoidInDiamonds");
		Table_Cards tc = emptyTable();
		TestKit.check("everyone can still follow Diamonds -- no discards possible, no trap",
				!AICardScorer.jackPullRisksTheQueen(tc, pullingHand(), PLAYER.EAST, false));
	}

	private static void riskyOnceAVoidSeatCouldDumpTheQueen() {
		TestKit.suite("riskyOnceAVoidSeatCouldDumpTheQueen");
		Table_Cards tc = emptyTable();
		tc.void_suits.get(PLAYER.NORTH).add(Suit.DIAMOND);
		TestKit.check("a Diamond-void seat that could still hold the Queen makes the pull a trap",
				AICardScorer.jackPullRisksTheQueen(tc, pullingHand(), PLAYER.EAST, false));
	}

	private static void safeOnceTheQueenIsAlreadyWon() {
		TestKit.suite("safeOnceTheQueenIsAlreadyWon");
		Table_Cards tc = emptyTable();
		tc.void_suits.get(PLAYER.NORTH).add(Suit.DIAMOND);
		TestKit.check("queenOfSpadesGone=true removes the trap regardless of any void",
				!AICardScorer.jackPullRisksTheQueen(tc, pullingHand(), PLAYER.EAST, true));
	}

	private static void safeWhileIHoldTheQueenMyself() {
		TestKit.suite("safeWhileIHoldTheQueenMyself");
		Table_Cards tc = emptyTable();
		tc.void_suits.get(PLAYER.NORTH).add(Suit.DIAMOND);
		List<Card> withQueen = cards(card(CardValue.ACE, Suit.DIAMOND),
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("holding the Queen myself means nobody can drop her on me -- no trap",
				!AICardScorer.jackPullRisksTheQueen(tc, withQueen, PLAYER.EAST, false));
	}

	private static void safeWhenTheOnlyVoidSeatIsAlsoVoidInSpades() {
		TestKit.suite("safeWhenTheOnlyVoidSeatIsAlsoVoidInSpades");
		Table_Cards tc = emptyTable();
		// Void in Diamonds so it WILL discard -- but also proven void in
		// Spades, and the Queen is a Spade, so it provably cannot hold her.
		tc.void_suits.get(PLAYER.NORTH).add(Suit.DIAMOND);
		tc.void_suits.get(PLAYER.NORTH).add(Suit.SPADE);
		TestKit.check("a seat void in Spades provably isn't holding the Queen -- no trap",
				!AICardScorer.jackPullRisksTheQueen(tc, pullingHand(), PLAYER.EAST, false));
	}

	// ---- the scorer that consumes it ------------------------------------

	private static void pullLeadStandsDownIntoTheTrap() {
		TestKit.suite("pullLeadStandsDownIntoTheTrap");
		Table_Cards tc = emptyTable();
		tc.void_suits.get(PLAYER.NORTH).add(Suit.DIAMOND);
		Card chosen = AICardScorer.pickLead(pullingHand(), tc, tc.players.get(PLAYER.EAST));
		TestKit.check("with the Queen live and a void seat waiting, the Ace of Diamonds is not led",
				!(chosen.suit == Suit.DIAMOND && chosen.cardValue == CardValue.ACE));
	}

	private static void pullLeadStillFiresWhenTheTrapIsAbsent() {
		TestKit.suite("pullLeadStillFiresWhenTheTrapIsAbsent");
		// Identical position except nobody is void -- the hunt is unchanged
		// by this guard, which is the point: it narrows the tactic, it
		// doesn't remove it.
		Table_Cards tc = emptyTable();
		Card chosen = AICardScorer.pickLead(pullingHand(), tc, tc.players.get(PLAYER.EAST));
		TestKit.check("with no void seat to fear, the boss Diamond is still led to pull the Jack",
				chosen.suit == Suit.DIAMOND && chosen.cardValue == CardValue.ACE);
	}

	// ---- the viability gate (2026-08-23) --------------------------------

	private static void pullLeadStandsDownOnAnUnsustainableHunt() {
		TestKit.suite("pullLeadStandsDownOnAnUnsustainableHunt");
		// No void seat anywhere, so the Queen trap is absent and the ONLY
		// thing that can stop the pull is the viability gate. A singleton Ace
		// of Diamonds buys exactly one pull: the Jack's holder ducks under it
		// with any lower Diamond and the weapon is gone.
		Table_Cards tc = emptyTable();
		List<Card> singletonWeapon = cards(card(CardValue.ACE, Suit.DIAMOND),
				card(CardValue.THREE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(singletonWeapon, tc, tc.players.get(PLAYER.EAST));
		TestKit.check("a lone boss Diamond can't sustain a hunt -- the pull stands down",
				!(chosen.suit == Suit.DIAMOND && chosen.cardValue == CardValue.ACE));
	}

	private static void twoBossDiamondsSustainTheHuntWithoutLength() {
		TestKit.suite("twoBossDiamondsSustainTheHuntWithoutLength");
		// Ace AND King: a two-card Diamond holding, but two guaranteed pulls,
		// which is the thing that actually matters. Length is a proxy for
		// sustained pulls, never the point in itself.
		Table_Cards tc = emptyTable();
		List<Card> twoWeapons = cards(card(CardValue.ACE, Suit.DIAMOND),
				card(CardValue.KING, Suit.DIAMOND),
				card(CardValue.THREE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("Ace plus King is two pulls -- viable despite a short suit",
				HandEvaluator.hasJackCaptureChance(twoWeapons, tc));
	}

	private static void holdingTheJackIsNotAHunt() {
		TestKit.suite("holdingTheJackIsNotAHunt");
		Table_Cards tc = emptyTable();
		List<Card> holdsJack = cards(card(CardValue.ACE, Suit.DIAMOND),
				card(CardValue.JACK, Suit.DIAMOND), card(CardValue.FOUR, Suit.DIAMOND),
				card(CardValue.TWO, Suit.CLUB));
		TestKit.check("holding the Jack is the protection question, not the capture question",
				!HandEvaluator.hasJackCaptureChance(holdsJack, tc));
	}

	private static void noWeaponMeansNoChance() {
		TestKit.suite("noWeaponMeansNoChance");
		Table_Cards tc = emptyTable();
		// Plenty of Diamonds, none of them able to win a trick.
		List<Card> noWeapon = cards(card(CardValue.FIVE, Suit.DIAMOND),
				card(CardValue.FOUR, Suit.DIAMOND), card(CardValue.THREE, Suit.DIAMOND),
				card(CardValue.TWO, Suit.CLUB));
		TestKit.check("length without a winner captures nothing",
				!HandEvaluator.hasJackCaptureChance(noWeapon, tc));
	}

	// ------------------------------------------------------------------

	/** A hand that holds the Diamond capture weapon (Ace) but NOT the Jack
	 * itself -- the exact shape the pull lead is written for.
	 *
	 * Given real Diamond LENGTH on 2026-08-23, when the second half of the
	 * viability dial landed (HandEvaluator.hasJackCaptureChance). The original
	 * version of this helper was a SINGLETON Ace, which the new gate correctly
	 * refuses to hunt with: one pull, and if the Jack simply ducks under it the
	 * weapon is spent for nothing. That case is now covered explicitly by
	 * pullLeadStandsDownOnAnUnsustainableHunt() below; this helper's job is to
	 * isolate the QUEEN-TRAP guard, so it needs a hand that clears the
	 * viability gate outright and leaves the trap as the only variable. */
	private static List<Card> pullingHand() {
		return cards(card(CardValue.ACE, Suit.DIAMOND),
				card(CardValue.FIVE, Suit.DIAMOND), card(CardValue.FOUR, Suit.DIAMOND),
				card(CardValue.THREE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
	}

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

	private static Table_Cards emptyTable() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			players.put(p, pl);
		}
		players.get(PLAYER.EAST).Set_Player(Player_Type.NORMAL);
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.EAST;
		tc.current_player = PLAYER.EAST;
		return tc;
	}
}
