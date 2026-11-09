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
 * Committed regression coverage for the 2026-08-23 high-Spade lead rework --
 * TODO.md's "Control-card retention" item together with the user's live-play
 * refinement of it.
 *
 * THE BUG THIS REPLACED. The lead-side high-Spade scorer fired on
 * safeToUnloadHighSpade(), which is true in two quite different states, and
 * paid a flat Tier-1 +1200 in both. One of them deserves it (the Queen still
 * live but every opponent provably void in Spades -- a rare, provable window
 * to shed a genuine liability). The other is the state that actually occurs
 * almost every hand: the Queen already captured. There the "liability" the
 * scorer exists to dump is already gone, so a +1200 "lead it now" was
 * spending a boss Spade to win a trick nobody was threatening it with,
 * collecting whatever the void seats chose to throw, and surrendering a
 * guaranteed re-entry for nothing.
 *
 * WHAT REPLACED IT. That second state now routes to a graded Tier-3 tempo
 * scorer weighing two opposed considerations, both of which the user named:
 *   HOLD  -- a boss Spade is a re-entry card, worth keeping while I have
 *            something to spend the lead on;
 *   SHED  -- unless it is about to be STRANDED as my last Spade, at which
 *            point it stops being optional at all: the next Spade lead forces
 *            it out on somebody else's schedule, and late in a hand that
 *            means eating whatever Hearts the void seats have been saving.
 * They are equal in magnitude on purpose, so a card that is both stranded and
 * useful nets to zero and ordinary lead scoring decides.
 */
public final class HighSpadeTempoTest {

	private HighSpadeTempoTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		urgentWhileTheQueenIsLiveAndUnreachable();
		notUrgentOnceTheQueenIsMerelyCaptured();
		notUrgentWhileIHoldTheQueenMyself();
		coveredHighSpadeIsNotDumpedOnReflex();
		strandedBossSpadeIsCashedBeforeItGetsForced();
		strandedKingIsNotCashedWhileTheAceIsStillOut();
		noStrandingRiskWhenIHoldEveryRemainingSpade();
		nonCountingSeatIsUnaffected();
		return TestKit.summarize();
	}

	// ---- the urgency predicate ------------------------------------------

	private static void urgentWhileTheQueenIsLiveAndUnreachable() {
		TestKit.suite("urgentWhileTheQueenIsLiveAndUnreachable");
		Table_Cards tc = emptyTable();
		// Every opponent provably void in Spades: the Queen cannot be dropped
		// on me, and this is a real chance to be rid of a card that some later
		// Spade trick would otherwise force me to win with her aboard.
		for (PLAYER p : PLAYER.values()) {
			if (p != PLAYER.SOUTH) {
				tc.void_suits.get(p).add(Suit.SPADE);
			}
		}
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("Queen live but provably unreachable -- shedding is genuinely urgent",
				AICardScorer.highSpadeUnloadIsUrgent(tc, mine, PLAYER.SOUTH));
	}

	private static void notUrgentOnceTheQueenIsMerelyCaptured() {
		TestKit.suite("notUrgentOnceTheQueenIsMerelyCaptured");
		Table_Cards tc = emptyTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("with the Queen already captured there is no liability left to rush out",
				!AICardScorer.highSpadeUnloadIsUrgent(tc, mine, PLAYER.SOUTH));
	}

	private static void notUrgentWhileIHoldTheQueenMyself() {
		TestKit.suite("notUrgentWhileIHoldTheQueenMyself");
		Table_Cards tc = emptyTable();
		for (PLAYER p : PLAYER.values()) {
			if (p != PLAYER.SOUTH) {
				tc.void_suits.get(p).add(Suit.SPADE);
			}
		}
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE),
				card(CardValue.QUEEN, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		TestKit.check("my own Queen makes leading her guards away worse, never urgent",
				!AICardScorer.highSpadeUnloadIsUrgent(tc, mine, PLAYER.SOUTH));
	}

	// ---- the tempo scorer -----------------------------------------------

	private static void coveredHighSpadeIsNotDumpedOnReflex() {
		TestKit.suite("coveredHighSpadeIsNotDumpedOnReflex");
		// The Queen is gone, so the King is safe -- but it is covered by three
		// low Spades, so nothing can force it out, and there is no reason to
		// spend a guaranteed trick winner. The old flat +1200 led it anyway.
		Table_Cards tc = queenCapturedTable();
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE),
				card(CardValue.FIVE, Suit.SPADE), card(CardValue.FOUR, Suit.SPADE),
				card(CardValue.THREE, Suit.SPADE), card(CardValue.TWO, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(mine, tc, tc.players.get(PLAYER.SOUTH));
		TestKit.check("a well-covered King is kept, not burned the moment it turns safe",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.KING));
	}

	private static void strandedBossSpadeIsCashedBeforeItGetsForced() {
		TestKit.suite("strandedBossSpadeIsCashedBeforeItGetsForced");
		// Queen gone and the Ace already played, so my King is provably boss
		// AND my only Spade -- the next Spade lead forces it out at a moment
		// somebody else picks. Cash it now, while opponents still hold cards
		// in every suit and have little to throw at me.
		Table_Cards tc = queenCapturedTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.ACE, Suit.SPADE));
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE),
				card(CardValue.SEVEN, Suit.CLUB), card(CardValue.SIX, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(mine, tc, tc.players.get(PLAYER.SOUTH));
		TestKit.check("a stranded boss Spade with no use for the lead is cashed early",
				chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.KING);
	}

	private static void strandedKingIsNotCashedWhileTheAceIsStillOut() {
		TestKit.suite("strandedKingIsNotCashedWhileTheAceIsStillOut");
		// The user's "Ace-fallen check": a singleton King with the Ace still
		// unaccounted for is NOT a forced take. Being made to play it into a
		// trick the Ace wins is a perfectly good outcome -- no points, no
		// lead, nothing lost -- so there is nothing to pre-empt.
		Table_Cards tc = queenCapturedTable();
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE),
				card(CardValue.SEVEN, Suit.CLUB), card(CardValue.SIX, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(mine, tc, tc.players.get(PLAYER.SOUTH));
		TestKit.check("a King that can still lose to the Ace isn't a forced take -- no rush",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.KING));
	}

	private static void noStrandingRiskWhenIHoldEveryRemainingSpade() {
		TestKit.suite("noStrandingRiskWhenIHoldEveryRemainingSpade");
		// Every Spade is either played or in my hand, so nobody can lead the
		// suit and nothing can ever force my Ace out. No risk, no reason.
		Table_Cards tc = queenCapturedTable();
		Hand played = tc.players.get(PLAYER.WEST).won_cards;
		for (CardValue v : CardValue.values()) {
			if (v != CardValue.ACE && v != CardValue.QUEEN) {
				played.addX(card(v, Suit.SPADE));
			}
		}
		List<Card> mine = cards(card(CardValue.ACE, Suit.SPADE),
				card(CardValue.SEVEN, Suit.CLUB), card(CardValue.SIX, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(mine, tc, tc.players.get(PLAYER.SOUTH));
		TestKit.check("nobody left holding a Spade means nothing can force mine -- keep it",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.ACE));
	}

	private static void nonCountingSeatIsUnaffected() {
		TestKit.suite("nonCountingSeatIsUnaffected");
		// The whole tactic is a counting deduction and is gated on
		// COUNTED_CERTAINTIES, exactly as the scorer it replaced was -- so a
		// seat not counting this hand sees no change in either direction.
		Table_Cards tc = queenCapturedTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.ACE, Suit.SPADE));
		tc.players.get(PLAYER.SOUTH).countsCardsThisHand = false;
		List<Card> mine = cards(card(CardValue.KING, Suit.SPADE),
				card(CardValue.SEVEN, Suit.CLUB), card(CardValue.SIX, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(mine, tc, tc.players.get(PLAYER.SOUTH));
		TestKit.check("a non-counting seat gets no stranding push -- ordinary scoring decides",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.KING));
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

	/** Base table with the Queen of Spades already in WEST's won pile, which
	 * is the state the tempo scorer exists for.
	 *
	 * NORTH is given a stray Heart deliberately, and it is load-bearing: with
	 * the Queen as the only captured point card, WEST would be the sole holder
	 * of points at the table and Scoring.inProgressMoonShooter() would read it
	 * as a shooter -- which leadHasALiveUse() then counts as a reason to hold
	 * the Spade for stopping duty, quietly cancelling the stranding shed these
	 * tests are trying to measure. Splitting the points across two seats is
	 * both the realistic state (once two seats have points no moon is possible
	 * at all) and the one that isolates the tactic under test.
	 */
	private static Table_Cards queenCapturedTable() {
		Table_Cards tc = emptyTable();
		tc.players.get(PLAYER.WEST).won_cards.addX(card(CardValue.QUEEN, Suit.SPADE));
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.TWO, Suit.HEART));
		return tc;
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
		players.get(PLAYER.SOUTH).Set_Player(Player_Type.NORMAL);
		// The tempo tactic lives under COUNTED_CERTAINTIES, so the acting seat
		// has to actually be counting this hand for any of it to fire.
		players.get(PLAYER.SOUTH).countsCardsThisHand = true;
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.SOUTH;
		return tc;
	}
}
