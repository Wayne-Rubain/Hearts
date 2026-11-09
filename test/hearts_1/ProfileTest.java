package hearts_1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Traits;
import hearts_1.Player.Player_Type;
import hearts_1.TacticWeights.Tactic;
import hearts_1.roster.Roster;
import hearts_1.roster.RosterPlayer;

/**
 * Committed coverage for hearts_1.Profile and the roster's template +
 * override syntax, added 2026-10-10 (AI players, step 1). The heavier proof
 * that every plain template plays card-for-card like the old preset was a
 * one-off fingerprint comparison against the pre-profile build (51,000
 * seeded hands, recorded in CHANGELOG.md); this suite pins the pieces that
 * can regress later.
 */
public final class ProfileTest {

	private ProfileTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		try {
			templatesMatchThePresets();
			overridesChangeOnlyWhatTheyName();
			badOverridesAreRejected();
			anOverrideActuallyChangesPlay();
			rosterLoadsTemplatesAndOverrides();
			rosterKeepsThePlayerWhenAnOverrideIsBad();
			rosterRoundTripKeepsOverrides();
			stepTwoSettingsParseAndRoundTrip();
			reliabilityDiceSkipFamilies();
			moodsComeAndGo();
			defaultsDrawNoRandomNumbers();
			nearMissPicksARunnerUp();
		} catch (IOException e) {
			TestKit.check("unexpected IOException: " + e.getMessage(), false);
		}
		return TestKit.summarize();
	}

	private static void templatesMatchThePresets() {
		TestKit.suite("templatesMatchThePresets");
		boolean weightsOk = true, traitsOk = true;
		for (Player_Type type : Player_Type.values()) {
			Player p = new Player();
			p.Set_Player(type);
			Map<Tactic, Double> row = TacticWeights.templateRow(type);
			for (Tactic t : Tactic.values()) {
				double expected = t == Tactic.CORE ? 1.0 : row.get(t).doubleValue();
				weightsOk &= TacticWeights.weight(p, t) == expected;
			}
			traitsOk &= p.p_traits.equals(type.traits()) && p.p_type == type;
			traitsOk &= Profile.of(type).overrides().isEmpty();
		}
		TestKit.check("every template reads exactly its preset's weights", weightsOk);
		TestKit.check("every template loads exactly its preset's traits, with no overrides", traitsOk);
		TestKit.check("a new player defaults to the plain Normal template",
				new Player().profile.template == Player_Type.NORMAL && new Player().profile.overrides().isEmpty());
	}

	private static void overridesChangeOnlyWhatTheyName() {
		TestKit.suite("overridesChangeOnlyWhatTheyName");
		Profile base = Profile.of(Player_Type.AGGRESSIVE);
		Profile p = base.withWeight(Tactic.JACK_HUNT, 0.5).withStyle(Player_Traits.Avoids_Queen, true);
		boolean othersSame = true;
		for (Tactic t : Tactic.values()) {
			if (t != Tactic.JACK_HUNT) {
				othersSame &= p.weight(t) == base.weight(t);
			}
		}
		TestKit.checkEquals("the named family changed", Double.valueOf(0.5), Double.valueOf(p.weight(Tactic.JACK_HUNT)));
		TestKit.check("every other family is untouched", othersSame);
		TestKit.check("the style switched on", p.traits().contains(Player_Traits.Avoids_Queen));
		TestKit.check("the template's own style is kept", p.traits().contains(Player_Traits.Aggressive));
		TestKit.checkEquals("overrides are listed for display", "[JACK_HUNT 0.5, +Avoids_Queen]", p.overrides().toString());
		TestKit.checkEquals("the original is unchanged (immutable)", Double.valueOf(1.5),
				Double.valueOf(base.weight(Tactic.JACK_HUNT)));
		Profile off = base.withStyle(Player_Traits.Aggressive, false);
		TestKit.check("a template style can be switched off", !off.traits().contains(Player_Traits.Aggressive));
	}

	private static void badOverridesAreRejected() {
		TestKit.suite("badOverridesAreRejected");
		Profile base = Profile.of(Player_Type.NORMAL);
		TestKit.check("CORE cannot be overridden", throwsIAE(() -> base.withWeight(Tactic.CORE, 0)));
		TestKit.check("a weight above 1.5 is rejected", throwsIAE(() -> base.withWeight(Tactic.SOCIAL, 2)));
		TestKit.check("a negative weight is rejected", throwsIAE(() -> base.withWeight(Tactic.SOCIAL, -0.5)));
		TestKit.check("competence flags are not styles", throwsIAE(() -> base.withStyle(Player_Traits.Bad, true)));
	}

	/** A Normal player keeps a well-protected Queen when passing (see
	 * AvoidsQueenPassTest). Switch its passing skill off and it passes its
	 * three highest cards instead -- Queen included. */
	private static void anOverrideActuallyChangesPlay() {
		TestKit.suite("anOverrideActuallyChangesPlay");
		Profile normal = Profile.of(Player_Type.NORMAL);
		TestKit.check("Normal keeps a protected Queen",
				!passesQueen(normal));
		TestKit.check("Normal with PASSING_CRAFT 0 passes it",
				passesQueen(normal.withWeight(Tactic.PASSING_CRAFT, 0)));
	}

	private static void rosterLoadsTemplatesAndOverrides() throws IOException {
		TestKit.suite("rosterLoadsTemplatesAndOverrides");
		File dir = writeRoster(
				"version = 2\n"
				+ "[player]\nid = AI_01\nname = Old Style\ntype = CAUTIOUS\n"
				+ "[player]\nid = AI_02\nname = Jack Hunter\ntemplate = AGGRESSIVE\n"
				+ "weight.JACK_HUNT = 1.5\nweight.BASIC_SAFETY = 1\nstyle.Avoids_Queen = on\n");
		Roster r = Roster.loadOrCreate(dir);
		RosterPlayer old = r.byId("AI_01"), hunter = r.byId("AI_02");
		TestKit.check("a version-1 'type =' line still loads", old != null && old.type == Player_Type.CAUTIOUS
				&& old.profile.overrides().isEmpty());
		TestKit.check("'template =' loads", hunter != null && hunter.type == Player_Type.AGGRESSIVE);
		TestKit.checkEquals("overrides applied", "[BASIC_SAFETY 1, +Avoids_Queen]", hunter.profile.overrides().toString());
		Player p = new Player();
		p.Set_Player(hunter.profile);
		TestKit.checkEquals("the seat reads the overridden weight",
				Double.valueOf(1.0), Double.valueOf(TacticWeights.weight(p, Tactic.BASIC_SAFETY)));
	}

	private static void rosterKeepsThePlayerWhenAnOverrideIsBad() throws IOException {
		TestKit.suite("rosterKeepsThePlayerWhenAnOverrideIsBad");
		File dir = writeRoster(
				"[player]\nid = AI_03\ntemplate = NORMAL\n"
				+ "weight.NOT_A_FAMILY = 1\nweight.SOCIAL = 9\nweight.VOID_AWARENESS = abc\n"
				+ "style.Bad = on\nstyle.Aggressive = maybe\nweight.QUEEN_FLUSH = 0.5\n");
		RosterPlayer p = Roster.loadOrCreate(dir).byId("AI_03");
		TestKit.check("the player still loads", p != null);
		TestKit.checkEquals("only the valid override survives", "[QUEEN_FLUSH 0.5]",
				p == null ? "" : p.profile.overrides().toString());
	}

	private static void rosterRoundTripKeepsOverrides() throws IOException {
		TestKit.suite("rosterRoundTripKeepsOverrides");
		File dir = writeRoster(
				"[player]\nid = AI_04\ntemplate = MOON_SHOOTER\n"
				+ "weight.SOCIAL = 1.5\nstyle.Shooting_the_Moon = off\n");
		Roster first = Roster.loadOrCreate(dir);
		File dir2 = Files.createTempDirectory("hearts2_profile_rt").toFile();
		dir2.deleteOnExit();
		first.save(dir2);
		RosterPlayer again = Roster.loadOrCreate(dir2).byId("AI_04");
		TestKit.checkEquals("overrides survive save + reload", first.byId("AI_04").profile.overrides().toString(),
				again == null ? "" : again.profile.overrides().toString());
		String text = new String(Files.readAllBytes(new File(dir2, "roster.txt").toPath()), StandardCharsets.UTF_8);
		TestKit.check("saved with the new 'template =' spelling", text.contains("template = MOON_SHOOTER"));
	}

	// ---- step 2: reliability, moods, blunder (2026-10-10) ---------------

	private static void stepTwoSettingsParseAndRoundTrip() throws IOException {
		TestKit.suite("stepTwoSettingsParseAndRoundTrip");
		File dir = writeRoster(
				"[player]\nid = AI_05\ntemplate = NORMAL\n"
				+ "reliable.JACK = 60\nreliable.QUEEN_FLUSH = 30\nreliable.MOON_DEFENSE = 80\n"
				+ "style.Aggressive = 40\nblunder = 10\n"
				+ "reliable.COUNTED_CERTAINTIES = 50\nreliable.CORE = 50\nreliable.SAFETY = 150\nblunder = x\n");
		RosterPlayer p = Roster.loadOrCreate(dir).byId("AI_05");
		Profile pr = p == null ? Profile.of(Player_Type.NORMAL) : p.profile;
		TestKit.checkEquals("a skill group sets every family in it", "60/60",
				pr.reliability(Tactic.JACK_GUARD) + "/" + pr.reliability(Tactic.JACK_HUNT));
		TestKit.checkEquals("a single family can be set", Integer.valueOf(30), Integer.valueOf(pr.reliability(Tactic.QUEEN_FLUSH)));
		TestKit.checkEquals("the rest of a group stays at 100", Integer.valueOf(100),
				Integer.valueOf(pr.reliability(Tactic.MOON_OFFENSE)));
		TestKit.checkEquals("a style percentage is a mood", Integer.valueOf(40),
				Integer.valueOf(pr.stylePercent(Player_Traits.Aggressive)));
		TestKit.check("a mood is not an always-on trait", !pr.traits().contains(Player_Traits.Aggressive));
		TestKit.checkEquals("blunder rate", Integer.valueOf(10), Integer.valueOf(pr.blunderPercent));
		TestKit.checkEquals("bad lines skipped, display lists the rest",
				"[Aggressive 40%, reliable.JACK 60%, reliable.QUEEN 30%, reliable.MOON_DEFENSE 80%, blunder 10%]",
				pr.overrides().toString());
		File dir2 = Files.createTempDirectory("hearts2_profile_rt2").toFile();
		dir2.deleteOnExit();
		Roster.loadOrCreate(dir).save(dir2);
		RosterPlayer again = Roster.loadOrCreate(dir2).byId("AI_05");
		TestKit.checkEquals("step-2 settings survive save + reload", pr.overrides().toString(),
				again == null ? "" : again.profile.overrides().toString());
		TestKit.check("style on/off still round-trips as on/off",
				pr.withStylePercent(Player_Traits.Aggressive, 100).traits().contains(Player_Traits.Aggressive)
				&& pr.withStyle(Player_Traits.Aggressive, false).stylePercent(Player_Traits.Aggressive) == 0);
	}

	private static void reliabilityDiceSkipFamilies() {
		TestKit.suite("reliabilityDiceSkipFamilies");
		Player p = new Player();
		p.Set_Player(Profile.of(Player_Type.NORMAL).withReliability(Tactic.JACK_HUNT, 0)
				.withReliability(Profile.Skill.QUEEN, 50));
		java.util.Random rng = new java.util.Random(7);
		int queenOff = 0, hands = 4000;
		boolean huntAlwaysOff = true, othersOn = true;
		for (int i = 0; i < hands; i++) {
			p.rollHand(rng);
			huntAlwaysOff &= TacticWeights.weight(p, Tactic.JACK_HUNT) == 0;
			othersOn &= TacticWeights.weight(p, Tactic.JACK_GUARD) == 1 && TacticWeights.weight(p, Tactic.CORE) == 1;
			if (TacticWeights.weight(p, Tactic.QUEEN_FLUSH) == 0) {
				queenOff++;
			}
		}
		TestKit.check("a 0% skill never plays", huntAlwaysOff);
		TestKit.check("100% skills are always in play", othersOn);
		TestKit.check("a 50% skill sits out about half the hands (" + queenOff + "/" + hands + ")",
				queenOff > hands * 0.45 && queenOff < hands * 0.55);
		p.Set_Player(Profile.of(Player_Type.NORMAL));
		TestKit.check("a new profile clears last hand's skipped skills", p.skippedThisHand.isEmpty());
	}

	private static void moodsComeAndGo() {
		TestKit.suite("moodsComeAndGo");
		Player p = new Player();
		p.Set_Player(Profile.of(Player_Type.AVOIDS_QUEEN).withStylePercent(Player_Traits.Aggressive, 30));
		java.util.Random rng = new java.util.Random(11);
		int aggressive = 0, hands = 4000;
		boolean templateKept = true;
		for (int i = 0; i < hands; i++) {
			p.rollHand(rng);
			if (p.p_traits.contains(Player_Traits.Aggressive)) {
				aggressive++;
			}
			templateKept &= p.p_traits.contains(Player_Traits.Avoids_Queen);
		}
		TestKit.check("a 30% mood is on about 30% of hands (" + aggressive + "/" + hands + ")",
				aggressive > hands * 0.26 && aggressive < hands * 0.34);
		TestKit.check("the always-on template style stays on every hand", templateKept);
	}

	private static void defaultsDrawNoRandomNumbers() {
		TestKit.suite("defaultsDrawNoRandomNumbers");
		Player p = new Player();
		p.Set_Player(Profile.of(Player_Type.AGGRESSIVE).withStyle(Player_Traits.Avoids_Queen, true)
				.withReliability(Tactic.SOCIAL, 100));
		java.util.Random a = new java.util.Random(3), b = new java.util.Random(3);
		p.rollHand(a);
		TestKit.check("on/off/100% settings roll no dice (seeded games replay unchanged)", a.nextLong() == b.nextLong());
	}

	private static void nearMissPicksARunnerUp() {
		TestKit.suite("nearMissPicksARunnerUp");
		Player p = new Player();
		p.tbl_player = PLAYER.WEST;
		Card best = new Card(CardValue.TWO, Suit.CLUB, null), second = new Card(CardValue.THREE, Suit.CLUB, null),
				third = new Card(CardValue.FOUR, Suit.CLUB, null), fourth = new Card(CardValue.FIVE, Suit.CLUB, null);
		List<Card> ranked = java.util.Arrays.asList(best, second, third, fourth);
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		int seconds = 0, thirds = 0, other = 0;
		AI.RNG.setSeed(5);
		for (int i = 0; i < 3000; i++) {
			Card c = AI.nearMiss(ranked, p);
			if (c == second) {
				seconds++;
			} else if (c == third) {
				thirds++;
			} else {
				other++;
			}
		}
		Card onlyTwo = AI.nearMiss(java.util.Arrays.asList(best, second), p);
		System.setOut(out);
		TestKit.check("a blunder is never the best card or a wild one", other == 0);
		TestKit.check("mostly the 2nd best, sometimes the 3rd (" + seconds + "/" + thirds + ")",
				thirds > 800 && thirds < 1200 && seconds > 1800);
		TestKit.check("with two choices it is the other one", onlyTwo == second);
	}

	// ------------------------------------------------------------------

	private static boolean passesQueen(Profile profile) {
		Hand hand = new Hand();
		Object[][] cards = {
				{ CardValue.QUEEN, Suit.SPADE }, { CardValue.TWO, Suit.SPADE }, { CardValue.THREE, Suit.SPADE },
				{ CardValue.FOUR, Suit.SPADE }, { CardValue.FIVE, Suit.SPADE },
				{ CardValue.SEVEN, Suit.HEART }, { CardValue.SIX, Suit.HEART },
				{ CardValue.TWO, Suit.CLUB }, { CardValue.THREE, Suit.CLUB }, { CardValue.FOUR, Suit.CLUB },
				{ CardValue.TWO, Suit.DIAMOND }, { CardValue.THREE, Suit.DIAMOND }, { CardValue.FOUR, Suit.DIAMOND } };
		for (Object[] c : cards) {
			hand.addX(new Card((CardValue) c[0], (Suit) c[1], null));
		}
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER s : PLAYER.values()) {
			Player pl = new Player();
			pl.tbl_player = s;
			pl.won_cards = new Hand();
			players.put(s, pl);
		}
		players.get(PLAYER.SOUTH).Set_Player(profile);
		tc.players = players;
		List<Card> passed = Deal_Hand.chooseCardsToPass(hand, tc, players.get(PLAYER.SOUTH), PLAYER.WEST);
		for (Card c : passed) {
			if (c.cardValue == CardValue.QUEEN && c.suit == Suit.SPADE) {
				return true;
			}
		}
		return false;
	}

	private static File writeRoster(String body) throws IOException {
		File dir = Files.createTempDirectory("hearts2_profile_test").toFile();
		dir.deleteOnExit();
		Files.write(new File(dir, "roster.txt").toPath(), body.getBytes(StandardCharsets.UTF_8));
		return dir;
	}

	private static boolean throwsIAE(Runnable r) {
		try {
			r.run();
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
