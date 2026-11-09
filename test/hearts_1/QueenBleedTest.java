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
 * Coverage for the Queen-of-Spades bleed-out (2026-10-09): a Queen holder
 * with real Spade length leads Ace, then King, then low Spades, keeping the
 * Queen for last -- while the bare Ace-and-Queen hand of the 2026-08-07 bug
 * keeps its high-Spade penalty.
 */
public final class QueenBleedTest {

	private QueenBleedTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		leadsTheAceFirst();
		leadsTheKingOnceTheAceIsGone();
		leadsLowSpadesOnceTheTopIsGone();
		neverLeadsTheQueen();
		bareAceAndQueenKeepsThePenalty();
		standsDownWhenAnOpponentIsVoidInSpades();
		standsDownWhenTheQueenIsNotMine();
		standsDownForCautious();
		standsDownForAggressive();
		return TestKit.summarize();
	}

	private static void leadsTheAceFirst() {
		TestKit.suite("leadsTheAceFirst");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.ACE), spade(CardValue.KING), spade(CardValue.QUEEN),
				spade(CardValue.FOUR), spade(CardValue.THREE), club(CardValue.TWO), diamond(CardValue.FIVE));
		TestKit.check("the bleed is on", AICardScorer.queenBleedApplies(f.ctx()));
		TestKit.checkEquals("leads the Ace of Spades", CardValue.ACE + "-" + Suit.SPADE, f.lead());
	}

	private static void leadsTheKingOnceTheAceIsGone() {
		TestKit.suite("leadsTheKingOnceTheAceIsGone");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.KING), spade(CardValue.QUEEN),
				spade(CardValue.FOUR), spade(CardValue.THREE), club(CardValue.TWO), diamond(CardValue.FIVE));
		TestKit.checkEquals("leads the King of Spades", CardValue.KING + "-" + Suit.SPADE, f.lead());
	}

	private static void leadsLowSpadesOnceTheTopIsGone() {
		TestKit.suite("leadsLowSpadesOnceTheTopIsGone");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.QUEEN), spade(CardValue.SIX), spade(CardValue.THREE),
				club(CardValue.TWO), diamond(CardValue.FIVE));
		TestKit.checkEquals("leads its lowest Spade", CardValue.THREE + "-" + Suit.SPADE, f.lead());
	}

	private static void neverLeadsTheQueen() {
		TestKit.suite("neverLeadsTheQueen");
		Fixture f = new Fixture(Player_Type.AVOIDS_QUEEN,
				spade(CardValue.QUEEN), spade(CardValue.SIX), spade(CardValue.THREE), club(CardValue.TWO));
		TestKit.check("the Queen is not the lead", !f.lead().startsWith("QUEEN-SPADE"));
	}

	/** The 2026-08-07 hand: only the Ace and Queen -- leading the Ace would
	 * leave a bare Queen. No bleed, penalty stays, the Ace is not led. */
	private static void bareAceAndQueenKeepsThePenalty() {
		TestKit.suite("bareAceAndQueenKeepsThePenalty");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.ACE), spade(CardValue.QUEEN), club(CardValue.TWO), diamond(CardValue.FIVE));
		TestKit.check("no bleed without low Spades to duck with", !AICardScorer.queenBleedApplies(f.ctx()));
		TestKit.check("the Ace is not led", !f.lead().equals(CardValue.ACE + "-" + Suit.SPADE));
	}

	private static void standsDownWhenAnOpponentIsVoidInSpades() {
		TestKit.suite("standsDownWhenAnOpponentIsVoidInSpades");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.ACE), spade(CardValue.QUEEN), spade(CardValue.FOUR), spade(CardValue.THREE),
				club(CardValue.TWO));
		f.tc.void_suits.get(PLAYER.EAST).add(Suit.SPADE);
		TestKit.check("a void opponent would dump Hearts on my Ace -- no bleed",
				!AICardScorer.queenBleedApplies(f.ctx()));
	}

	private static void standsDownWhenTheQueenIsNotMine() {
		TestKit.suite("standsDownWhenTheQueenIsNotMine");
		Fixture f = new Fixture(Player_Type.NORMAL,
				spade(CardValue.ACE), spade(CardValue.FOUR), spade(CardValue.THREE), club(CardValue.TWO));
		TestKit.check("without the Queen in hand, leading the Ace stays dangerous",
				!AICardScorer.queenBleedApplies(f.ctx()));
	}

	private static void standsDownForCautious() {
		TestKit.suite("standsDownForCautious");
		Fixture f = new Fixture(Player_Type.CAUTIOUS,
				spade(CardValue.ACE), spade(CardValue.QUEEN), spade(CardValue.FOUR), spade(CardValue.THREE),
				club(CardValue.TWO));
		TestKit.check("Cautious (QUEEN_FLUSH weight 0) never bleeds", !AICardScorer.queenBleedApplies(f.ctx()));
	}

	private static void standsDownForAggressive() {
		TestKit.suite("standsDownForAggressive");
		for (Player_Type t : new Player_Type[] { Player_Type.AGGRESSIVE, Player_Type.AGGRESSIVE_AVOIDS_QUEEN }) {
			Fixture f = new Fixture(t,
					spade(CardValue.ACE), spade(CardValue.QUEEN), spade(CardValue.FOUR), spade(CardValue.THREE),
					club(CardValue.TWO));
			TestKit.check(t + " never bleeds (no measured benefit)", !AICardScorer.queenBleedApplies(f.ctx()));
		}
	}

	// ------------------------------------------------------------------

	private static final class Fixture {
		final Table_Cards tc = new Table_Cards();
		final Player me = new Player();
		final List<Card> hand = new ArrayList<Card>();

		Fixture(Player_Type type, Card... cards) {
			Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
			Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
			for (PLAYER p : PLAYER.values()) {
				Player pl = (p == PLAYER.SOUTH) ? me : new Player();
				pl.tbl_player = p;
				pl.won_cards = new Hand();
				players.put(p, pl);
				voidSuits.put(p, EnumSet.noneOf(Suit.class));
			}
			me.Set_Player(type);
			tc.players = players;
			tc.void_suits = voidSuits;
			tc.trick_nbr = 2;
			tc.current_player = PLAYER.SOUTH;
			tc.who_leads = PLAYER.SOUTH;
			for (Card c : cards) {
				hand.add(c);
			}
		}

		AICardScorer.LeadContext ctx() {
			return new AICardScorer.LeadContext(hand, tc, me);
		}

		String lead() {
			Card c = AICardScorer.pickLead(hand, tc, me);
			return c.cardValue + "-" + c.suit;
		}
	}

	private static Card spade(CardValue v) {
		return new Card(v, Suit.SPADE, null);
	}

	private static Card club(CardValue v) {
		return new Card(v, Suit.CLUB, null);
	}

	private static Card diamond(CardValue v) {
		return new Card(v, Suit.DIAMOND, null);
	}
}
