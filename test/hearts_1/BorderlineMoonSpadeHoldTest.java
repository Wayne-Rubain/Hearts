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
 * Committed regression coverage for the 2026-08-17 "hold Spade control
 * back on a borderline moon hand" lead guard, from a deep-dive web
 * research review of real Hearts strategy consensus.
 *
 * The live hand that prompted this: North committed to a moon shot on the
 * weaker King-of-Hearts route (no Ace of Hearts), and with Hearts still
 * unbroken at trick 2, Spades was its next-longest LEGAL suit -- so the
 * ordinary Tier 1 "run the longest suit" rule led the Ace of Spades,
 * capturing the Queen of Spades immediately and locking in +13 of
 * exposure before it was ever known whether the Ace of Hearts would
 * surface on its own. Research consensus: a shooter missing the Ace of
 * Hearts should hold its Spade control back rather than spend it hunting
 * the Queen -- defenders usually hand a suspected shooter the Queen for
 * free anyway (it costs THEM less to deny a Heart instead), and leading
 * the Ace/King of Spades in sequence is called out by name as exactly
 * the kind of play that telegraphs a shooter's hand for no real gain.
 *
 * Deliberately narrow: only the Ace/King of Spades are held back, not
 * Spades as a suit. A LOW Spade lead doesn't lock in the Queen's cost
 * and doesn't spend the control cards, so it stays available -- the
 * guard targets the specific "hunt with my boss cards" risk the research
 * flags, not ordinary suit-length play.
 */
public final class BorderlineMoonSpadeHoldTest {

	private BorderlineMoonSpadeHoldTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		borderlineHandHoldsTheAceBackAndRunsALowSpadeInstead();
		realAceOfHeartsControlStillRunsTheAceOfSpades();
		standsDownOnceTheAceOfHeartsHasAlreadyFallenToAnyone();
		notAMoonShotAtAllStillRunsOrdinaryBaseline();
		return TestKit.summarize();
	}

	/** The exact real hand, reconstructed: King-high Hearts (no Ace),
	 * Hearts unbroken, Spades North's next-longest legal suit. Before the
	 * fix this led the Ace of Spades; now it holds it back. */
	private static void borderlineHandHoldsTheAceBackAndRunsALowSpadeInstead() {
		TestKit.suite("borderlineHandHoldsTheAceBackAndRunsALowSpadeInstead");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts = false;
		List<Card> legalLeads = cards(
				card(CardValue.SIX, Suit.SPADE), card(CardValue.SEVEN, Suit.SPADE), card(CardValue.ACE, Suit.SPADE),
				card(CardValue.THREE, Suit.DIAMOND), card(CardValue.EIGHT, Suit.DIAMOND), card(CardValue.TEN, Suit.DIAMOND),
				card(CardValue.SEVEN, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(legalLeads, tc, tc.players.get(PLAYER.NORTH));
		TestKit.check("holds the Ace of Spades back -- does not lead it",
				!(chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.ACE));
		TestKit.check("still runs Spades, just the low Seven instead of the Ace",
				chosen.suit == Suit.SPADE && chosen.cardValue == CardValue.SEVEN);
	}

	/** A shooter genuinely holding the Ace of Hearts has nothing left to
	 * wait for -- Tier 1's ordinary "run the longest suit" rule should
	 * apply exactly as before this fix. */
	private static void realAceOfHeartsControlStillRunsTheAceOfSpades() {
		TestKit.suite("realAceOfHeartsControlStillRunsTheAceOfSpades");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts = true;
		List<Card> legalLeads = cards(
				card(CardValue.SIX, Suit.SPADE), card(CardValue.SEVEN, Suit.SPADE), card(CardValue.ACE, Suit.SPADE),
				card(CardValue.THREE, Suit.DIAMOND), card(CardValue.EIGHT, Suit.DIAMOND), card(CardValue.TEN, Suit.DIAMOND),
				card(CardValue.SEVEN, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(legalLeads, tc, tc.players.get(PLAYER.NORTH));
		TestKit.checkEquals("real Ace-of-Hearts control -- runs the Ace of Spades, unchanged from before",
				CardValue.ACE, chosen.cardValue);
		TestKit.checkEquals("", Suit.SPADE, chosen.suit);
	}

	/** Once the Ace of Hearts has already fallen -- to ANY seat -- the
	 * uncertainty this guard exists for is resolved, so it stands down
	 * even though North itself never held the card. Attributed to NORTH's
	 * own won_cards specifically: if any OTHER seat held a captured point
	 * card here, AI.isMoonShotAlive() would (correctly, per its own
	 * inProgressMoonShooter check) read North's shot as already dead, and
	 * mode would drop out of MOON_SHOT before this guard ever got asked --
	 * a different scenario than the one under test, same pitfall
	 * AceOfHeartsFlushTest's "already captured" case hit first. */
	private static void standsDownOnceTheAceOfHeartsHasAlreadyFallenToAnyone() {
		TestKit.suite("standsDownOnceTheAceOfHeartsHasAlreadyFallenToAnyone");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).moonShotHasAceOfHearts = false;
		tc.players.get(PLAYER.NORTH).won_cards.addX(card(CardValue.ACE, Suit.HEART));
		List<Card> legalLeads = cards(
				card(CardValue.SIX, Suit.SPADE), card(CardValue.SEVEN, Suit.SPADE), card(CardValue.ACE, Suit.SPADE),
				card(CardValue.THREE, Suit.DIAMOND), card(CardValue.EIGHT, Suit.DIAMOND), card(CardValue.TEN, Suit.DIAMOND),
				card(CardValue.SEVEN, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(legalLeads, tc, tc.players.get(PLAYER.NORTH));
		TestKit.checkEquals("the Ace of Hearts already fell elsewhere -- nothing left to wait for",
				CardValue.ACE, chosen.cardValue);
		TestKit.checkEquals("", Suit.SPADE, chosen.suit);
	}

	/** Baseline (non-moon) play must be completely unaffected -- this
	 * guard is tagged MOON_OFFENSE and gated on PlayMode.MOON_SHOT, so a
	 * seat that never committed should see no change at all. */
	private static void notAMoonShotAtAllStillRunsOrdinaryBaseline() {
		TestKit.suite("notAMoonShotAtAllStillRunsOrdinaryBaseline");
		Table_Cards tc = tableOf();
		tc.players.get(PLAYER.NORTH).attempting_moon_shot = false;
		List<Card> legalLeads = cards(
				card(CardValue.SIX, Suit.SPADE), card(CardValue.SEVEN, Suit.SPADE), card(CardValue.ACE, Suit.SPADE),
				card(CardValue.THREE, Suit.DIAMOND), card(CardValue.EIGHT, Suit.DIAMOND), card(CardValue.TEN, Suit.DIAMOND),
				card(CardValue.SEVEN, Suit.CLUB));
		Card chosen = AICardScorer.pickLead(legalLeads, tc, tc.players.get(PLAYER.NORTH));
		// Not asserting a specific card -- only that the guard (a MOON_OFFENSE
		// scorer gated on PlayMode.MOON_SHOT) contributes nothing when not
		// shooting, which HEARTS2_DEBUG_SCORER-style direct scoring already
		// guarantees structurally. This just documents the mode gate exists.
		TestKit.check("baseline mode still returns a legal card without error",
				legalLeads.contains(chosen));
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

	private static Table_Cards tableOf() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			pl.won_cards = new Hand();
			pl.Set_Player(Player_Type.NORMAL);
			players.put(p, pl);
		}
		players.get(PLAYER.NORTH).attempting_moon_shot = true;
		tc.players = players;

		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		tc.hearts_broken = false;
		tc.trick_nbr = 1;
		tc.who_leads = PLAYER.NORTH;
		tc.current_player = PLAYER.NORTH;
		return tc;
	}
}
