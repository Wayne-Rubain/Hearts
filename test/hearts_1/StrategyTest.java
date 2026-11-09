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
 * Strategy components (2026-10-11, see Strategy.java): the profile setting
 * itself, and one check per non-default option that it really changes the
 * decision it names. The defaults are covered by PassingRulesTest and the
 * moon-defense suites, and proven unchanged by seeded replay.
 */
public final class StrategyTest {

	private StrategyTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		try {
			profileSetting();
			rosterRoundTrip();
			kitPass();
			lowHearts();
			sideSuits();
			voidHunt();
			spadeControl();
			queenKeep();
			heartInsurance();
			moonAlarmReads();
			moonAlarmGatesTheDenial();
			wildnessSettings();
			wildnessThisHand();
			strayingEachHand();
		} finally {
			System.setOut(out);
		}
		return TestKit.summarize();
	}

	private static void profileSetting() {
		TestKit.suite("profileSetting");
		Profile n = Profile.of(Player_Type.NORMAL);
		for (Strategy s : Strategy.values()) {
			TestKit.check(s + " starts at its default", n.strategy(s) == s.defaultOption);
		}
		TestKit.check("no strategy settings on a plain template", n.settings().isEmpty());
		Profile p = n.withSetting("strategy.MOON_ALARM", "sure_winner_same");
		TestKit.check("parsed, case-insensitive", p.uses(Strategy.MoonAlarm.SURE_WINNER_SAME));
		TestKit.check("other components untouched", p.uses(Strategy.KitPass.HOLD_BACK));
		TestKit.checkEquals("written back for roster.txt", "strategy.MOON_ALARM=SURE_WINNER_SAME",
				p.settings().get(0)[0] + "=" + p.settings().get(0)[1]);
		TestKit.check("shown in the overrides", p.overrides().contains("MOON_ALARM=SURE_WINNER_SAME"));
		TestKit.check("back to the default clears it",
				p.withStrategy(Strategy.MoonAlarm.SHOOTER_READ).settings().isEmpty());
		TestKit.check("survives other overrides", p.withBlunder(10).uses(Strategy.MoonAlarm.SURE_WINNER_SAME));
		TestKit.check("an unknown option is refused", throwsOn(n, "strategy.KIT_PASS", "SOMETIMES"));
		TestKit.check("an unknown component is refused", throwsOn(n, "strategy.NOPE", "FREE"));
		TestKit.check("Strategy.of finds the component", Strategy.of(Strategy.QueenKeep.NEVER) == Strategy.QUEEN_KEEP);
	}

	/** roster.txt reads the key, skips a bad one, and writes it back. */
	private static void rosterRoundTrip() {
		TestKit.suite("rosterRoundTrip");
		try {
			java.io.File dir = java.nio.file.Files.createTempDirectory("hearts2_strategy_test").toFile();
			dir.deleteOnExit();
			String content = "version = 2\n[player]\nid = AI_01\ntemplate = NORMAL\n"
					+ "strategy.MOON_ALARM = SURE_WINNER_SAME\n"
					+ "strategy.KIT_PASS = SOMETIMES\n"
					+ "wildness = 25\n"
					+ "wildness.opening = 40\n"
					+ "stray.KIT_PASS = FREE 2, HOLD_BACK 1\n";
			java.nio.file.Files.write(new java.io.File(dir, "roster.txt").toPath(),
					content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			hearts_1.roster.Roster r = hearts_1.roster.Roster.loadOrCreate(dir);
			Profile p = r.byId("AI_01").profile;
			TestKit.check("the strategy line loads", p.uses(Strategy.MoonAlarm.SURE_WINNER_SAME));
			TestKit.check("a bad option is skipped, default kept", p.uses(Strategy.KitPass.HOLD_BACK));
			r.save(dir);
			Profile again = hearts_1.roster.Roster.loadOrCreate(dir).byId("AI_01").profile;
			TestKit.check("and survives a save and reload", again.uses(Strategy.MoonAlarm.SURE_WINNER_SAME));
			TestKit.check("wildness lines load and survive too",
					again.wildness() == 25 && again.openingWildness() == 40
							&& again.overrides().equals(p.overrides())
							&& again.overrides().contains("stray.KIT_PASS (FREE 2, HOLD_BACK 1)"));
			String saved = new String(java.nio.file.Files.readAllBytes(new java.io.File(dir, "roster.txt").toPath()),
					java.nio.charset.StandardCharsets.UTF_8);
			TestKit.check("the file header lists the options", saved.contains("MOON_ALARM: FIRST_HEART ") && saved.contains("SHOOTER_READ(default)"));
		} catch (java.io.IOException e) {
			TestKit.check("temp roster: " + e, false);
		}
	}

	/** A K 2 of Hearts with an unprotected Queen (three low Spades): the
	 * three biggest cards are a full kit. */
	private static void kitPass() {
		TestKit.suite("kitPass");
		Card[] hand = {
				c(CardValue.ACE, Suit.HEART), c(CardValue.KING, Suit.HEART), c(CardValue.TWO, Suit.HEART),
				c(CardValue.QUEEN, Suit.SPADE), c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.TWO, Suit.CLUB), c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND) };
		List<Card> blocker = pass(with(Strategy.KitPass.BLOCKER), null, hand);
		TestKit.check("BLOCKER sends the 2 of Hearts with the kit " + names(blocker),
				has(blocker, CardValue.TWO, Suit.HEART));
		List<Card> free = pass(with(Strategy.KitPass.FREE), null, hand);
		TestKit.check("FREE sends Ace + King of Hearts + Queen of Spades " + names(free),
				has(free, CardValue.ACE, Suit.HEART) && has(free, CardValue.KING, Suit.HEART)
						&& has(free, CardValue.QUEEN, Suit.SPADE));
		List<Card> hold = pass(Profile.of(Player_Type.NORMAL), null, hand);
		TestKit.check("default HOLD_BACK keeps the Ace of Hearts and sends no low-Heart blocker " + names(hold),
				!has(hold, CardValue.ACE, Suit.HEART) && !has(hold, CardValue.TWO, Suit.HEART));
	}

	/** The user's 9-3-2 of Hearts example. */
	private static void lowHearts() {
		TestKit.suite("lowHearts");
		Card[] hand = {
				c(CardValue.ACE, Suit.CLUB), c(CardValue.KING, Suit.CLUB), c(CardValue.SEVEN, Suit.CLUB),
				c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.NINE, Suit.HEART), c(CardValue.THREE, Suit.HEART), c(CardValue.TWO, Suit.HEART),
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE), c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND) };
		List<Card> free = pass(with(Strategy.LowHearts.PASS_FREELY), null, hand);
		TestKit.check("PASS_FREELY sends all three Hearts " + names(free), countSuit(free, Suit.HEART) == 3);
		List<Card> height = pass(with(Strategy.LowHearts.BY_HEIGHT), null, hand);
		TestKit.check("BY_HEIGHT sends the 9 and the Ace and King of Clubs " + names(height),
				has(height, CardValue.NINE, Suit.HEART) && has(height, CardValue.ACE, Suit.CLUB)
						&& has(height, CardValue.KING, Suit.CLUB));
		List<Card> low = pass(with(Strategy.LowHearts.KEEP_LOW), null, hand);
		TestKit.check("KEEP_LOW keeps the 3 and 2 " + names(low),
				!has(low, CardValue.THREE, Suit.HEART) && !has(low, CardValue.TWO, Suit.HEART));
	}

	/** Long Clubs with the low ones listed first: by height the Ace and King
	 * go; flat, the score ties and the first cards found go instead. */
	private static void sideSuits() {
		TestKit.suite("sideSuits");
		Card[] hand = {
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE), c(CardValue.FOUR, Suit.SPADE),
				c(CardValue.FIVE, Suit.SPADE), c(CardValue.SIX, Suit.SPADE), c(CardValue.SEVEN, Suit.SPADE),
				c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.ACE, Suit.CLUB), c(CardValue.KING, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND) };
		List<Card> height = pass(Profile.of(Player_Type.NORMAL), null, hand);
		TestKit.check("default BY_HEIGHT sends the Ace and King of Clubs " + names(height),
				has(height, CardValue.ACE, Suit.CLUB) && has(height, CardValue.KING, Suit.CLUB));
		List<Card> flat = pass(with(Strategy.SideSuits.FLAT), null, hand);
		TestKit.check("FLAT doesn't single them out " + names(flat),
				!(has(flat, CardValue.ACE, Suit.CLUB) && has(flat, CardValue.KING, Suit.CLUB)));
	}

	/** Two low Spades and a two-card Club suit. */
	private static void voidHunt() {
		TestKit.suite("voidHunt");
		Card[] hand = {
				c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.SIX, Suit.CLUB), c(CardValue.SEVEN, Suit.CLUB),
				c(CardValue.KING, Suit.DIAMOND), c(CardValue.TEN, Suit.DIAMOND), c(CardValue.NINE, Suit.DIAMOND),
				c(CardValue.EIGHT, Suit.DIAMOND), c(CardValue.SEVEN, Suit.DIAMOND),
				c(CardValue.TWO, Suit.HEART), c(CardValue.THREE, Suit.HEART), c(CardValue.FOUR, Suit.HEART),
				c(CardValue.FIVE, Suit.HEART) };
		List<Card> hunt = pass(Profile.of(Player_Type.NORMAL), null, hand);
		TestKit.check("default empties the Clubs " + names(hunt),
				has(hunt, CardValue.SIX, Suit.CLUB) && has(hunt, CardValue.SEVEN, Suit.CLUB));
		List<Card> never = pass(with(Strategy.VoidHunt.NEVER), null, hand);
		TestKit.check("NEVER doesn't " + names(never),
				!(has(never, CardValue.SIX, Suit.CLUB) && has(never, CardValue.SEVEN, Suit.CLUB)));
	}

	/** Unprotected King of Spades, recipient (WEST) leading the game. */
	private static void spadeControl() {
		TestKit.suite("spadeControl");
		Card[] hand = {
				c(CardValue.KING, Suit.SPADE), c(CardValue.JACK, Suit.SPADE), c(CardValue.TEN, Suit.SPADE),
				c(CardValue.THREE, Suit.SPADE), c(CardValue.TWO, Suit.SPADE),
				c(CardValue.SEVEN, Suit.CLUB), c(CardValue.EIGHT, Suit.CLUB),
				c(CardValue.ACE, Suit.DIAMOND), c(CardValue.KING, Suit.DIAMOND), c(CardValue.TWO, Suit.DIAMOND),
				c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND), c(CardValue.FIVE, Suit.DIAMOND) };
		int[] westLeads = { 40, 50, 60, 10 };
		List<Card> shield = pass(with(Strategy.SpadeControl.SHIELD_LEADER), westLeads, hand);
		TestKit.check("SHIELD_LEADER keeps it from the leader " + names(shield),
				!has(shield, CardValue.KING, Suit.SPADE));
		List<Card> anyone = pass(with(Strategy.SpadeControl.ANYONE), westLeads, hand);
		TestKit.check("ANYONE sends it " + names(anyone), has(anyone, CardValue.KING, Suit.SPADE));
	}

	/** The Queen with exactly four low Spades, passing to the seat that
	 * plays after me (protected Queen scores 0 there -- kept by default). */
	private static void queenKeep() {
		TestKit.suite("queenKeep");
		Card[] hand = {
				c(CardValue.QUEEN, Suit.SPADE), c(CardValue.TWO, Suit.SPADE), c(CardValue.THREE, Suit.SPADE),
				c(CardValue.FOUR, Suit.SPADE), c(CardValue.FIVE, Suit.SPADE),
				c(CardValue.ACE, Suit.CLUB), c(CardValue.KING, Suit.CLUB), c(CardValue.QUEEN, Suit.CLUB),
				c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB),
				c(CardValue.TWO, Suit.DIAMOND), c(CardValue.THREE, Suit.DIAMOND), c(CardValue.FOUR, Suit.DIAMOND) };
		List<Card> four = pass(Profile.of(Player_Type.NORMAL), null, hand);
		TestKit.check("default keeps her with four low Spades " + names(four), !has(four, CardValue.QUEEN, Suit.SPADE));
		List<Card> five = pass(with(Strategy.QueenKeep.FIVE_LOW_SPADES), null, hand);
		TestKit.check("FIVE_LOW_SPADES passes her " + names(five), has(five, CardValue.QUEEN, Suit.SPADE));
		List<Card> never = pass(with(Strategy.QueenKeep.NEVER), null, hand);
		TestKit.check("NEVER passes her " + names(never), has(never, CardValue.QUEEN, Suit.SPADE));
	}

	/** Void in Clubs, nobody has taken a point, only Hearts to throw. */
	private static void heartInsurance() {
		TestKit.suite("heartInsurance");
		Table_Cards tc = table();
		tc.who_leads = PLAYER.SOUTH;
		tc.p1_card = c(CardValue.FIVE, Suit.CLUB);
		tc.s_card = tc.p1_card;
		List<Card> options = list(c(CardValue.ACE, Suit.HEART), c(CardValue.TWO, Suit.HEART));
		Player west = tc.players.get(PLAYER.WEST);
		Card normal = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("default sheds the Ace of Hearts", CardValue.ACE, normal.cardValue);
		west.Set_Player(with(Strategy.HeartInsurance.ON));
		Card insured = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("ON holds it back and sheds the 2", CardValue.TWO, insured.cardValue);
	}

	private static void moonAlarmReads() {
		TestKit.suite("moonAlarmReads");
		Table_Cards tc = table();
		Player north = tc.players.get(PLAYER.NORTH);
		Player south = tc.players.get(PLAYER.SOUTH);
		// Trick 1: North leads the 5 of Clubs, East is void and dumps the
		// 2 of Hearts, North wins -- North's first Heart, dumped in Clubs.
		trick(tc, PLAYER.NORTH, PLAYER.NORTH, c(CardValue.FIVE, Suit.CLUB), c(CardValue.TWO, Suit.HEART),
				c(CardValue.THREE, Suit.CLUB), c(CardValue.FOUR, Suit.CLUB));
		TestKit.check("the Club dump is noted", north.heartDiscardSuit == Suit.CLUB);
		TestKit.check("FIRST_HEART: alarmed already", alarmed(tc, south, Strategy.MoonAlarm.FIRST_HEART));
		TestKit.check("FOUR_HEARTS: not yet", !alarmed(tc, south, Strategy.MoonAlarm.FOUR_HEARTS));
		TestKit.check("SURE_WINNER_SAME: not yet", !alarmed(tc, south, Strategy.MoonAlarm.SURE_WINNER_SAME));
		TestKit.check("KEPT_LEAD: not yet", !alarmed(tc, south, Strategy.MoonAlarm.KEPT_LEAD));
		// Trick 2: North leads the King of Clubs -- the Ace is still out, so
		// not a sure winner, even though it wins.
		trick(tc, PLAYER.NORTH, PLAYER.NORTH, c(CardValue.KING, Suit.CLUB), c(CardValue.SIX, Suit.DIAMOND),
				c(CardValue.SIX, Suit.CLUB), c(CardValue.SEVEN, Suit.CLUB));
		TestKit.check("a King with the Ace out is not a sure winner", !north.ledSureWinner);
		TestKit.check("but KEPT_LEAD fires (won again, Queen out)", alarmed(tc, south, Strategy.MoonAlarm.KEPT_LEAD));
		// Trick 3: the Ace of Clubs -- now the top Club left.
		trick(tc, PLAYER.NORTH, PLAYER.NORTH, c(CardValue.ACE, Suit.CLUB), c(CardValue.SEVEN, Suit.DIAMOND),
				c(CardValue.EIGHT, Suit.CLUB), c(CardValue.NINE, Suit.CLUB));
		TestKit.check("the Ace of Clubs, led and won, is a sure winner in the dumped suit",
				north.ledSureWinner && north.ledSureWinnerSameSuit);
		TestKit.check("SURE_WINNER_SAME: alarmed", alarmed(tc, south, Strategy.MoonAlarm.SURE_WINNER_SAME));
		TestKit.check("SHOOTER_READ: alarmed", alarmed(tc, south, Strategy.MoonAlarm.SHOOTER_READ));
		TestKit.check("QUEEN_AND_HEART: not yet", !alarmed(tc, south, Strategy.MoonAlarm.QUEEN_AND_HEART));
		north.won_cards.addX(c(CardValue.QUEEN, Suit.SPADE));
		TestKit.check("QUEEN_AND_HEART: once the Queen is in", alarmed(tc, south, Strategy.MoonAlarm.QUEEN_AND_HEART));
		north.won_cards.addX(c(CardValue.THREE, Suit.HEART));
		north.won_cards.addX(c(CardValue.FOUR, Suit.HEART));
		north.won_cards.addX(c(CardValue.FIVE, Suit.HEART));
		TestKit.check("FOUR_HEARTS: at four", alarmed(tc, south, Strategy.MoonAlarm.FOUR_HEARTS));
		MoonRead.startHand(north);
		TestKit.check("a new hand forgets the reads", north.heartDiscardSuit == null && !north.ledSureWinner);
	}

	/** Rule 2 (don't feed the shooter) only runs once the alarm is up. South
	 * holds one Heart and leads; West is void with a Heart and a safe card. */
	private static void moonAlarmGatesTheDenial() {
		TestKit.suite("moonAlarmGatesTheDenial");
		Table_Cards tc = table();
		// West within 10 of the shooter, so the 2026-11-08 "leader lets an early
		// moon pass" rule (MoonThreat.letsItPassForNow) stays out of this test.
		tc.players.get(PLAYER.WEST).p_total_points = 40;
		tc.players.get(PLAYER.SOUTH).won_cards.addX(c(CardValue.SIX, Suit.HEART));
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.SOUTH;
		tc.p1_card = c(CardValue.QUEEN, Suit.CLUB);
		tc.s_card = tc.p1_card;
		List<Card> options = list(c(CardValue.ACE, Suit.HEART), c(CardValue.FIVE, Suit.DIAMOND));
		Player west = tc.players.get(PLAYER.WEST);
		Card read = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("default SHOOTER_READ: one Heart isn't a moon yet, the Ace goes", Suit.HEART, read.suit);
		west.Set_Player(with(Strategy.MoonAlarm.FIRST_HEART));
		Card early = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("FIRST_HEART: won't feed the shooter, throws the Diamond", Suit.DIAMOND, early.suit);
		west.Set_Player(with(Strategy.MoonAlarm.FOUR_HEARTS));
		Card late = AICardScorer.pickFollow(options, handOf(options), tc, 2, west);
		TestKit.checkEquals("FOUR_HEARTS: one Heart isn't a moon yet, the Ace goes", Suit.HEART, late.suit);
	}

	private static void wildnessSettings() {
		TestKit.suite("wildnessSettings");
		Profile n = Profile.of(Player_Type.NORMAL);
		TestKit.check("off by default", n.wildness() == 0 && n.openingWildness() == 0);
		Profile w = n.withSetting("wildness", "30").withSetting("wildness.opening", "20")
				.withSetting("stray.MOON_ALARM", "first_heart 3, KEPT_LEAD 1");
		TestKit.check("parsed", w.wildness() == 30 && w.openingWildness() == 20);
		TestKit.check("shown in the overrides", w.overrides().contains("wildness 30%")
				&& w.overrides().contains("stray.MOON_ALARM (FIRST_HEART 3, KEPT_LEAD 1)"));
		TestKit.check("wildness over 100 refused", throwsOn(n, "wildness", "120"));
		TestKit.check("standings and opening defaults: 50%, 150%, 3 hands",
				n.leadingWildnessPct() == 50 && n.losingWildnessPct() == 150 && n.openingHands() == 3);
		Profile t = n.withSetting("wildness.openingHands", "5").withSetting("wildness.leading", "100")
				.withSetting("wildness.losing", "250");
		TestKit.check("all three settable per AI",
				t.openingHands() == 5 && t.leadingWildnessPct() == 100 && t.losingWildnessPct() == 250);
		TestKit.check("shown sensibly", t.overrides().contains("wildness.openingHands 5")
				&& t.overrides().contains("wildness.losing 250%"));
		TestKit.check("written back only when not the default",
				t.settings().size() == 3 && t.withSetting("wildness.leading", "50").settings().size() == 2);
		TestKit.check("opening hands over 20 refused", throwsOn(n, "wildness.openingHands", "30"));
		TestKit.check("a standings percent over 300 refused", throwsOn(n, "wildness.losing", "400"));
		TestKit.check("a stray option from another component refused", throwsOn(n, "stray.MOON_ALARM", "FREE 1"));
		TestKit.check("a zero stray weight refused", throwsOn(n, "stray.MOON_ALARM", "FOUR_HEARTS 0"));
		TestKit.check("a stray without a weight refused", throwsOn(n, "stray.MOON_ALARM", "FOUR_HEARTS"));
	}

	/** Base, plus the opening hands, times the standings. */
	private static void wildnessThisHand() {
		TestKit.suite("wildnessThisHand");
		Table_Cards tc = table(); // totals N 63, E 78, S 45, W 22 -- West leads, East last
		Player west = tc.players.get(PLAYER.WEST);
		Player north = tc.players.get(PLAYER.NORTH);
		Player east = tc.players.get(PLAYER.EAST);
		Profile p = Profile.of(Player_Type.NORMAL).withWildness(20).withOpeningWildness(30);
		for (Player x : new Player[] { west, north, east }) {
			x.Set_Player(p);
			x.handsThisGame = 5;
		}
		TestKit.checkEquals("mid-game, middle of the table: the base", 20, north.wildnessThisHand(tc));
		TestKit.checkEquals("leading: half", 10, west.wildnessThisHand(tc));
		TestKit.checkEquals("last: one and a half", 30, east.wildnessThisHand(tc));
		north.handsThisGame = 2;
		TestKit.checkEquals("an opening hand adds the opening wildness", 50, north.wildnessThisHand(tc));
		for (Player x : tc.players.values()) {
			x.p_total_points = 0;
		}
		north.handsThisGame = 1;
		TestKit.checkEquals("level scores: no standings change", 50, north.wildnessThisHand(tc));
		Profile custom = p.withOpeningHands(1).withStandingsWildness(100, 200);
		for (Player x : tc.players.values()) {
			x.p_total_points = x == east ? 40 : 10; // east alone in last
		}
		east.Set_Player(custom);
		east.handsThisGame = 2;
		TestKit.checkEquals("per-AI: one opening hand only, last place doubles", 40, east.wildnessThisHand(tc));
		west.Set_Player(custom);
		west.handsThisGame = 2;
		TestKit.checkEquals("per-AI: leading at 100% leaves it unchanged", 20, west.wildnessThisHand(tc));
		for (Player x : tc.players.values()) {
			x.p_total_points = 0;
		}
		north.Set_Player(Profile.of(Player_Type.NORMAL).withWildness(90).withOpeningWildness(90));
		TestKit.checkEquals("never above 100", 100, north.wildnessThisHand(tc));
		north.Set_Player(Profile.of(Player_Type.NORMAL));
		TestKit.checkEquals("no wildness: 0", 0, north.wildnessThisHand(tc));
	}

	private static void strayingEachHand() {
		TestKit.suite("strayingEachHand");
		Player p = new Player();
		p.tbl_player = PLAYER.NORTH;
		p.handsThisGame = 5;
		p.Set_Player(Profile.of(Player_Type.NORMAL).withWildness(100));
		p.rollHand(new java.util.Random(7), null);
		boolean allStrayed = true, signatureKept = true;
		for (Strategy c : Strategy.values()) {
			if (c.signature) {
				signatureKept &= p.strategy(c) == c.defaultOption;
			} else {
				allStrayed &= p.strategy(c) != c.defaultOption;
			}
		}
		TestKit.check("wildness 100: every ordinary component played some other way " + p.strategyThisHand, allStrayed);
		TestKit.check("signature components never stray (2026-11-05)", signatureKept);
		p.Set_Player(Profile.of(Player_Type.NORMAL).withWildness(100)
				.withSetting("stray.KIT_PASS", "FREE 1"));
		p.rollHand(new java.util.Random(7), null);
		TestKit.check("a stray mix decides where it goes", p.uses(Strategy.KitPass.FREE));
		TestKit.check("the profile itself is unchanged", p.profile.uses(Strategy.KitPass.HOLD_BACK));
		p.Set_Player(Profile.of(Player_Type.NORMAL));
		TestKit.check("a new profile clears last hand's strays", p.strategyThisHand.isEmpty());
		java.util.Random used = new java.util.Random(11);
		p.rollHand(used, null);
		TestKit.check("no wildness: no strays and no random numbers drawn",
				p.strategyThisHand.isEmpty() && used.nextInt() == new java.util.Random(11).nextInt());
	}

	// ------------------------------------------------------------------

	private static boolean alarmed(Table_Cards tc, Player me, Strategy.MoonAlarm alarm) {
		me.Set_Player(with(alarm));
		return MoonRead.alarmed(tc, me, PLAYER.NORTH);
	}

	/** Plays one completed trick: cards in seat order from the leader,
	 * recorded, then added to the winner's pile. */
	private static void trick(Table_Cards tc, PLAYER leader, PLAYER winner, Card... inOrder) {
		tc.who_leads = leader;
		tc.who_won = winner;
		tc.p1_card = inOrder[0];
		PLAYER seat = leader;
		for (Card x : inOrder) {
			switch (seat) {
				case NORTH: tc.n_card = x; break;
				case EAST: tc.e_card = x; break;
				case SOUTH: tc.s_card = x; break;
				default: tc.w_card = x; break;
			}
			seat = seat.Next_Dealer();
		}
		MoonRead.recordTrick(tc);
		for (Card x : inOrder) {
			tc.players.get(winner).won_cards.addX(x);
		}
	}

	private static Table_Cards table() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		int[] totals = { 63, 78, 45, 22 };
		for (PLAYER s : PLAYER.values()) {
			Player p = new Player();
			p.tbl_player = s;
			p.won_cards = new Hand();
			p.p_total_points = totals[s.ordinal()];
			players.put(s, p);
		}
		tc.players = players;
		Map<PLAYER, EnumSet<Suit>> voidSuits = new EnumMap<PLAYER, EnumSet<Suit>>(PLAYER.class);
		for (PLAYER p : PLAYER.values()) {
			voidSuits.put(p, EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voidSuits;
		return tc;
	}

	private static Profile with(Enum<?> option) {
		return Profile.of(Player_Type.NORMAL).withStrategy(option);
	}

	private static boolean throwsOn(Profile p, String key, String value) {
		try {
			p.withSetting(key, value);
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	/** totals, when given, are indexed by PLAYER.values() order (N, E, S, W);
	 * SOUTH passes to WEST. */
	private static List<Card> pass(Profile profile, int[] totals, Card... cards) {
		Hand hand = new Hand();
		for (Card x : cards) {
			hand.addX(x);
		}
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER s : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = s;
			pl.won_cards = new Hand();
			pl.Set_Player(s == PLAYER.SOUTH ? profile : Profile.of(Player_Type.NORMAL));
			if (totals != null) {
				pl.p_total_points = totals[s.ordinal()];
			}
			players.put(s, pl);
		}
		tc.players = players;
		return Deal_Hand.chooseCardsToPass(hand, tc, players.get(PLAYER.SOUTH), PLAYER.WEST);
	}

	private static Card c(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static List<Card> list(Card... cs) {
		List<Card> l = new ArrayList<Card>();
		for (Card x : cs) {
			l.add(x);
		}
		return l;
	}

	private static Hand handOf(List<Card> cs) {
		Hand h = new Hand();
		for (Card x : cs) {
			h.addX(x);
		}
		return h;
	}

	private static boolean has(List<Card> cards, CardValue v, Suit s) {
		for (Card x : cards) {
			if (x.cardValue == v && x.suit == s) {
				return true;
			}
		}
		return false;
	}

	private static int countSuit(List<Card> cards, Suit s) {
		int n = 0;
		for (Card x : cards) {
			n += x.suit == s ? 1 : 0;
		}
		return n;
	}

	private static String names(List<Card> cards) {
		StringBuilder sb = new StringBuilder("[");
		for (Card x : cards) {
			sb.append(sb.length() == 1 ? "" : " ").append(x.cardValue).append(' ').append(x.suit);
		}
		return sb.append(']').toString();
	}
}
