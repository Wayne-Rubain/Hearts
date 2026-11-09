package hearts_1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * 2026-11-05, decision 3 in docs/decisions_2026-11.md: signature options for
 * Queen-phobic (Strategy.QUEEN_PHOBIA) and Jack Hunter (Strategy.JACK_CHASE).
 * OFF, the default, must change nothing; each option must do its one thing.
 */
public final class SignatureOptionsTest {

	private SignatureOptionsTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		parsedFromTheRoster();
		passBias();
		fishForTheQueen();
		diamondPressure();
		return TestKit.summarize();
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static Player player(String phobia, String chase) {
		Player p = new Player();
		p.tbl_player = PLAYER.SOUTH;
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL)
				.withSetting("strategy.QUEEN_PHOBIA", phobia).withSetting("strategy.JACK_CHASE", chase));
		return p;
	}

	private static Table_Cards table(Player south) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER s : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = s;
			players.put(s, pl);
		}
		players.put(PLAYER.SOUTH, south);
		tc.players = players;
		tc.hearts_broken = true;
		return tc;
	}

	private static void parsedFromTheRoster() {
		TestKit.suite("parsedFromTheRoster");
		Profile n = Profile.of(Player.Player_Type.NORMAL);
		TestKit.check("QUEEN_PHOBIA defaults to OFF", n.strategy(Strategy.QUEEN_PHOBIA) == Strategy.QueenPhobia.OFF);
		TestKit.check("JACK_CHASE defaults to OFF", n.strategy(Strategy.JACK_CHASE) == Strategy.JackChase.OFF);
		TestKit.check("both are signature components",
				Strategy.QUEEN_PHOBIA.signature && Strategy.JACK_CHASE.signature && !Strategy.KIT_PASS.signature);
		Player both = player("BOTH", "BOTH");
		TestKit.check("BOTH turns on both Queen-phobic behaviors",
				Strategy.passesHighSpades(both) && Strategy.fishesForQueen(both));
		TestKit.check("BOTH turns on both Jack Hunter behaviors",
				Strategy.keepsJackWeapons(both) && Strategy.pressuresJack(both));
		Player off = player("OFF", "OFF");
		TestKit.check("OFF turns on nothing", !Strategy.passesHighSpades(off) && !Strategy.fishesForQueen(off)
				&& !Strategy.keepsJackWeapons(off) && !Strategy.pressuresJack(off));
	}

	private static void passBias() {
		TestKit.suite("passBias");
		Card as = card(CardValue.ACE, Suit.SPADE), ks = card(CardValue.KING, Suit.SPADE),
				ad = card(CardValue.ACE, Suit.DIAMOND), qd = card(CardValue.QUEEN, Suit.DIAMOND),
				jd = card(CardValue.JACK, Suit.DIAMOND), fiveS = card(CardValue.FIVE, Suit.SPADE);
		Player off = player("OFF", "OFF");
		for (Card c : Arrays.asList(as, ks, ad, qd, jd, fiveS)) {
			TestKit.checkEquals("OFF: no bias on " + c.cardValue + " " + c.suit, 0, Deal_Hand.signaturePassBias(c, off));
		}
		Player spades = player("PASS_HIGH_SPADES", "OFF");
		TestKit.check("PASS_HIGH_SPADES: the Ace of Spades is pushed out", Deal_Hand.signaturePassBias(as, spades) > 0);
		TestKit.check("PASS_HIGH_SPADES: the King too", Deal_Hand.signaturePassBias(ks, spades) > 0);
		TestKit.checkEquals("PASS_HIGH_SPADES: low Spades untouched", 0, Deal_Hand.signaturePassBias(fiveS, spades));
		Player diamonds = player("OFF", "KEEP_DIAMONDS");
		TestKit.check("KEEP_DIAMONDS: the Ace of Diamonds is kept", Deal_Hand.signaturePassBias(ad, diamonds) < 0);
		TestKit.check("KEEP_DIAMONDS: the Queen of Diamonds is kept", Deal_Hand.signaturePassBias(qd, diamonds) < 0);
		TestKit.checkEquals("KEEP_DIAMONDS: the Jack itself is left to the Jack rules", 0,
				Deal_Hand.signaturePassBias(jd, diamonds));
	}

	private static Card lead(Player p, Card... hand) {
		List<Card> legal = new ArrayList<Card>(Arrays.asList(hand));
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		try {
			return AICardScorer.pickLead(legal, table(p), p);
		} finally {
			System.setOut(out);
		}
	}

	private static void fishForTheQueen() {
		TestKit.suite("fishForTheQueen");
		// A hand with a safe low Club; the fisher prefers its low Spade.
		Card threeS = card(CardValue.THREE, Suit.SPADE), sevenS = card(CardValue.SEVEN, Suit.SPADE),
				fourC = card(CardValue.FOUR, Suit.CLUB), nineC = card(CardValue.NINE, Suit.CLUB),
				tenC = card(CardValue.TEN, Suit.CLUB);
		Card fisher = lead(player("FISH", "OFF"), threeS, sevenS, fourC, nineC, tenC);
		TestKit.check("FISH leads the lowest Spade, got " + fisher.cardValue + " " + fisher.suit, fisher == threeS);
		Card plain = lead(player("OFF", "OFF"), threeS, sevenS, fourC, nineC, tenC);
		TestKit.check("OFF leads the ordinary low Club instead, got " + plain.cardValue + " " + plain.suit, plain == fourC);
		Card withKing = lead(player("FISH", "OFF"), threeS, card(CardValue.KING, Suit.SPADE), fourC, nineC, tenC);
		Card withKingOff = lead(player("OFF", "OFF"), threeS, card(CardValue.KING, Suit.SPADE), fourC, nineC, tenC);
		TestKit.check("no fishing while holding the King of Spades", withKing == withKingOff);
		Card withQueen = lead(player("FISH", "OFF"), threeS, card(CardValue.QUEEN, Suit.SPADE), fourC, nineC, tenC);
		Card withQueenOff = lead(player("OFF", "OFF"), threeS, card(CardValue.QUEEN, Suit.SPADE), fourC, nineC, tenC);
		TestKit.check("no fishing while holding the Queen", withQueen == withQueenOff);
	}

	private static void diamondPressure() {
		TestKit.suite("diamondPressure");
		Card twoD = card(CardValue.TWO, Suit.DIAMOND), kd = card(CardValue.KING, Suit.DIAMOND),
				fourC = card(CardValue.FOUR, Suit.CLUB), nineC = card(CardValue.NINE, Suit.CLUB),
				tenC = card(CardValue.TEN, Suit.CLUB);
		Card presser = lead(player("OFF", "PRESSURE"), twoD, kd, fourC, nineC, tenC);
		TestKit.check("PRESSURE leads the low Diamond under a King, got " + presser.cardValue + " " + presser.suit,
				presser == twoD);
		Card notPressing = lead(player("OFF", "OFF"), twoD, kd, fourC, nineC, tenC);
		TestKit.check("OFF leads the ordinary low Club instead, got " + notPressing.cardValue + " " + notPressing.suit,
				notPressing == fourC);
		Card noWeapon = lead(player("OFF", "PRESSURE"), twoD, card(CardValue.FIVE, Suit.DIAMOND), fourC, nineC, tenC);
		Card noWeaponOff = lead(player("OFF", "OFF"), twoD, card(CardValue.FIVE, Suit.DIAMOND), fourC, nineC, tenC);
		TestKit.check("no pressure without a Diamond above the Jack", noWeapon == noWeaponOff);
		Card ownJack = lead(player("OFF", "PRESSURE"), twoD, kd, card(CardValue.JACK, Suit.DIAMOND), nineC, tenC);
		Card ownJackOff = lead(player("OFF", "OFF"), twoD, kd, card(CardValue.JACK, Suit.DIAMOND), nineC, tenC);
		TestKit.check("no pressure while holding the Jack", ownJack == ownJackOff);
	}
}
