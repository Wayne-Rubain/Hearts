package hearts_1;

import java.io.OutputStream;
import java.io.PrintStream;
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
 * Regression coverage for Wayne's live game of 2026-11-08, hand 9, trick 7:
 * South led the 7 of Spades, West threw the 2 of Hearts, and North (level 5,
 * counting cards) held the King and Queen of Spades. Both won; North won with
 * the Queen for 13 because the "take it as cheaply as you can" rule compared
 * rank only. North had no Clubs left and dumped nothing on the next Club lead.
 * The whole hand is replayed from the log up to that decision.
 */
public final class OwnQueenWinTest {

	private OwnQueenWinTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		hand9WinsWithTheKing();
		aggressiveDoesNotLeadIntoASuitEveryoneIsOutOf();
		return TestKit.summarize();
	}

	private static void hand9WinsWithTheKing() {
		TestKit.suite("hand9WinsWithTheKing");
		for (Player_Type type : Player_Type.values()) {
			if (type.traits().contains(Player.Player_Traits.Brain_Dead) || type.traits().contains(Player.Player_Traits.Bad)) {
				continue;
			}
			for (int level = 1; level <= 5; level++) {
				Replay r = new Replay(Profile.of(type).withLevel(level), "H39 S468QK D7TQK CAT");
				Card c = r.northPlays();
				TestKit.check(type + " level " + level + ": wins with the King, not the Queen",
						c.suit == Suit.SPADE && c.cardValue == CardValue.KING);
			}
		}
	}

	/** Wayne's game 2026-11-09, hand 1, trick 7: West (Shark, Aggressive mood,
	 * counting) had won every trick, held 4 8 of Hearts, 2 7 of Spades and the
	 * 6 9 10 of Clubs -- nobody else had a Club left -- and led the 10 of Clubs;
	 * North, East and South threw three Hearts on it. */
	private static void aggressiveDoesNotLeadIntoASuitEveryoneIsOutOf() {
		TestKit.suite("aggressiveDoesNotLeadIntoASuitEveryoneIsOutOf");
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			Player p = new Player();
			p.tbl_player = seat;
			p.Set_Player(Profile.of(Player_Type.NORMAL));
			p.won_cards = new Hand();
			players.put(seat, p);
		}
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voids = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			voids.put(seat, EnumSet.noneOf(Suit.class));
		}
		voids.get(PLAYER.SOUTH).add(Suit.CLUB);
		tc.void_suits = voids;
		Player west = players.get(PLAYER.WEST);
		west.p_traits.add(Player.Player_Traits.Aggressive);
		west.countsCardsThisHand = true;
		// West's six tricks: every Club but its own 6 9 10, the Queen of Spades,
		// no Hearts yet.
		for (Card c : hand("C234578JQKA SQKJ98 DAK59T8 D27").hand) {
			west.won_cards.addX(c);
		}
		tc.who_leads = PLAYER.WEST;
		tc.current_player = PLAYER.WEST;
		tc.play_position = 1;
		Hand h = hand("H48 S27 C69T");
		List<Card> legal = new ArrayList<Card>();
		for (Card c : h.hand) {
			if (c.suit != Suit.HEART) {
				legal.add(c); // Hearts not broken yet
			}
		}
		PrintStream real = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		Card lead;
		try {
			lead = AICardScorer.pickLead(legal, h, tc, west);
		} finally {
			System.setOut(real);
		}
		TestKit.check("counting Aggressive West does not lead a Club nobody else holds", lead.suit != Suit.CLUB);
	}

	// ------------------------------------------------------------------

	static CardValue v(char ch) {
		return CardValue.values()["23456789TJQKA".indexOf(ch)];
	}

	static Suit s(char ch) {
		return ch == 'S' ? Suit.SPADE : ch == 'H' ? Suit.HEART : ch == 'D' ? Suit.DIAMOND : Suit.CLUB;
	}

	static Hand hand(String spec) {
		Hand h = new Hand();
		for (String part : spec.trim().split("\s+")) {
			for (char ch : part.substring(1).toCharArray()) {
				h.addX(new Card(v(ch), s(part.charAt(0)), null));
			}
		}
		return h;
	}

	static Card find(Hand h, String c) {
		for (Card x : h.hand) {
			if (x.cardValue == v(c.charAt(0)) && x.suit == s(Character.toUpperCase(c.charAt(1)))) {
				return x;
			}
		}
		throw new IllegalStateException("not in hand: " + c);
	}

	private static final class Replay {
		final Table_Cards tc = new Table_Cards();
		final Map<PLAYER, Hand> hands = new EnumMap<PLAYER, Hand>(PLAYER.class);
		final Player north;

		Replay(Profile northProfile, String northHand) {
			Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
			int[] totals = { 0, 70, 53, 5 };
			for (PLAYER seat : PLAYER.values()) {
				Player p = new Player();
				p.tbl_player = seat;
				p.Set_Player(seat == PLAYER.NORTH ? northProfile : Profile.of(Player_Type.NORMAL));
				p.p_total_points = totals[seat.ordinal()];
				p.won_cards = new Hand();
				p.handsThisGame = 8;
				players.put(seat, p);
			}
			north = players.get(PLAYER.NORTH);
			north.countsCardsThisHand = true;
			tc.players = players;
			Map<PLAYER, EnumSet<Suit>> voids = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
			for (PLAYER seat : PLAYER.values()) {
				voids.put(seat, EnumSet.noneOf(Suit.class));
			}
			tc.void_suits = voids;
			hands.put(PLAYER.NORTH, hand(northHand));
			hands.put(PLAYER.EAST, hand("H56AJ S5T D6 C2468JQ"));
			hands.put(PLAYER.SOUTH, hand("H4TKQ S379J DA C359K"));
			hands.put(PLAYER.WEST, hand("H278 S2A D234589J C7"));
			String[][] tricks = { { "EAST", "2c", "9c", "7c", "Tc" }, { "NORTH", "4s", "5s", "3s", "As" },
					{ "WEST", "9d", "7d", "6d", "Ad" }, { "SOUTH", "3c", "8h", "Ac", "Qc" },
					{ "NORTH", "6s", "Ts", "Js", "2s" }, { "SOUTH", "9s", "7h", "8s", "Ah" }, { "SOUTH", "7s", "2h" } };
			PrintStream real = System.out;
			System.setOut(new PrintStream(OutputStream.nullOutputStream()));
			try {
				for (String[] t : tricks) {
					PLAYER seat = PLAYER.valueOf(t[0]);
					tc.who_leads = seat;
					tc.current_player = seat;
					for (int pos = 1; pos < t.length; pos++) {
						tc.play_position = pos;
						Card c = find(hands.get(seat), t[pos]);
						hands.get(seat).pull_card(c);
						GameEngine.placeCard(tc, pos, seat, c);
						seat = seat.Next_Dealer();
						tc.current_player = seat;
					}
					if (t.length == 5) {
						GameEngine.resolveTrick(tc, false);
					}
				}
			} finally {
				System.setOut(real);
			}
		}

		Card northPlays() {
			tc.play_position = 3;
			List<Card> legal = new ArrayList<Card>();
			for (Card c : hands.get(PLAYER.NORTH).hand) {
				if (c.suit == Suit.SPADE) {
					legal.add(c);
				}
			}
			PrintStream real = System.out;
			System.setOut(new PrintStream(OutputStream.nullOutputStream()));
			try {
				return AICardScorer.pickFollow(legal, hands.get(PLAYER.NORTH), tc, 3, north);
			} finally {
				System.setOut(real);
			}
		}
	}
}
