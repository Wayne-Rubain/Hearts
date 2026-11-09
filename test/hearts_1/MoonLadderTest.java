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
 * 2026-10-17, user design: player LEVEL (1-5) and the level-graded moon-defense
 * ladder (MoonThreat.breakTheShotWith). Nervous low-level players panic and grab
 * a point trick as soon as they fear a moon; advanced players only pay when it is
 * needed. Scenario from live hand 4: South holds one Heart (the King, dumped on
 * them); West holds 4 5 6 J Q A of Hearts.
 */
public final class MoonLadderTest {

	private MoonLadderTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		levelIsARosterSetting();
		lowLevelPanicsWithTheTopCard();
		advancedKeepsItsStopperWhenTheShooterPlaysLater();
		advancedActsWhenItIsUrgent();
		advancedReadsADumpedHeartAsNoMoon();
		return TestKit.summarize();
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static void levelIsARosterSetting() {
		TestKit.suite("levelIsARosterSetting");
		Profile p = Profile.of(Player_Type.NORMAL);
		TestKit.checkEquals("default level", Profile.DEFAULT_LEVEL, p.level);
		Profile five = p.withSetting("level", "5");
		TestKit.checkEquals("level 5 read", 5, five.level);
		TestKit.check("written back for roster.txt", five.overrides().contains("level 5"));
		TestKit.check("default not written", !p.overrides().toString().contains("level"));
		TestKit.checkEquals("survives other overrides", 5, five.withBlunder(4).level);
		boolean refused = false;
		try {
			p.withSetting("level", "9");
		} catch (IllegalArgumentException e) {
			refused = true;
		}
		TestKit.check("out of range refused", refused);
		try {
			java.io.File dir = java.nio.file.Files.createTempDirectory("levelroster").toFile();
			java.nio.file.Files.write(new java.io.File(dir, "roster.txt").toPath(),
					("version = 2\n\n[player]\nid = AI_X\nname = Tester\ntemplate = NORMAL\nlevel = 5\n")
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			java.io.PrintStream real = System.out;
			System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
			int loaded = hearts_1.roster.Roster.loadOrCreate(dir).byId("AI_X").profile.level;
			System.setOut(real);
			TestKit.checkEquals("roster.txt 'level = 5' is loaded", 5, loaded);
		} catch (java.io.IOException e) {
			TestKit.check("no IOException: " + e, false);
		}
	}

	/** West's table: South (shooter) holds the King of Hearts, West has six Hearts
	 * and (optionally) two Clubs; `leader` led the 3 of Hearts. */
	private static Table_Cards table(int level, PLAYER leader, int shooterHearts, boolean dumped) {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = p;
			players.put(p, pl);
		}
		tc.players = players;
		Player west = players.get(PLAYER.WEST);
		west.Set_Player(Profile.of(Player_Type.NORMAL).withLevel(level));
		west.strategyThisHand.put(Strategy.MOON_ALARM, Strategy.MoonAlarm.FIRST_HEART);
		Player south = players.get(PLAYER.SOUTH);
		CardValue[] got = { CardValue.KING, CardValue.TEN, CardValue.NINE, CardValue.EIGHT, CardValue.SEVEN };
		for (int i = 0; i < shooterHearts; i++) {
			south.won_cards.addX(card(got[i], Suit.HEART));
		}
		if (dumped) {
			south.heartDiscardSuit = Suit.DIAMOND;
		}
		Map<PLAYER, EnumSet<Suit>> voids = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voids.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		tc.hearts_broken = true;
		tc.who_leads = leader;
		tc.current_player = PLAYER.WEST;
		tc.p1_card = card(CardValue.THREE, Suit.HEART);
		return tc;
	}

	private static Card play(Table_Cards tc, boolean withClubs) {
		List<Card> legal = new ArrayList<Card>();
		Hand h = new Hand();
		for (CardValue v : new CardValue[] { CardValue.FOUR, CardValue.FIVE, CardValue.SIX, CardValue.JACK,
				CardValue.QUEEN, CardValue.ACE }) {
			Card c = card(v, Suit.HEART);
			legal.add(c);
			h.addX(c);
		}
		if (withClubs) {
			h.addX(card(CardValue.FOUR, Suit.CLUB));
			h.addX(card(CardValue.TEN, Suit.CLUB));
			h.addX(card(CardValue.NINE, Suit.DIAMOND));
		}
		return AI.chooseFollow(legal, h, tc, 2, tc.players.get(PLAYER.WEST));
	}

	private static void lowLevelPanicsWithTheTopCard() {
		TestKit.suite("lowLevelPanicsWithTheTopCard");
		Card c = play(table(1, PLAYER.SOUTH, 1, false), true);
		TestKit.checkEquals("level 1 grabs the trick with the Ace even though a 4 would do", CardValue.ACE,
				c.cardValue);
		Card c3 = play(table(3, PLAYER.SOUTH, 1, false), true);
		TestKit.check("level 3 sees every Heart it holds beats the 3: no Ace", c3.cardValue != CardValue.ACE);
	}

	private static void advancedKeepsItsStopperWhenTheShooterPlaysLater() {
		TestKit.suite("advancedKeepsItsStopperWhenTheShooterPlaysLater");
		// North led the 3; South (shooter) plays after West. Level 3 still grabs
		// with the top card; level 5 holds the Ace of Hearts (the stopper) back.
		Card c3 = play(table(3, PLAYER.NORTH, 1, false), true);
		TestKit.checkEquals("level 3: shooter still to play, takes with the Ace", CardValue.ACE, c3.cardValue);
		Card c5 = play(table(5, PLAYER.NORTH, 1, false), true);
		TestKit.check("level 5: keeps the Ace of Hearts for later (" + c5.cardValue + ")",
				c5.cardValue != CardValue.ACE);
		Card noClubs = play(table(5, PLAYER.NORTH, 1, false), false);
		TestKit.checkEquals("level 5 with nothing but Hearts left (can be squeezed): takes now, with the cheapest card nobody can beat (Q and A are its own, K is gone)",
				CardValue.JACK, noClubs.cardValue);
	}

	private static void advancedActsWhenItIsUrgent() {
		TestKit.suite("advancedActsWhenItIsUrgent");
		Card c = play(table(5, PLAYER.NORTH, 5, false), true);
		TestKit.checkEquals("shooter already has 5 Hearts: level 5 takes now -- with the 4, which nobody can beat (every higher Heart is accounted for)",
				CardValue.FOUR, c.cardValue);
	}

	private static void advancedReadsADumpedHeartAsNoMoon() {
		TestKit.suite("advancedReadsADumpedHeartAsNoMoon");
		Card c4 = play(table(4, PLAYER.NORTH, 1, true), false);
		TestKit.check("level 4: the shooter's only Heart was dumped on them -- not a shooter, no Ace",
				c4.cardValue != CardValue.ACE);
		Card c3 = play(table(3, PLAYER.NORTH, 1, true), false);
		TestKit.checkEquals("level 3 doesn't read that and still grabs", CardValue.ACE, c3.cardValue);
	}
}
