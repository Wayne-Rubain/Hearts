package hearts_1;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;

/**
 * Coverage for the side-suit moon look (2026-11-08, user design from Wayne's
 * live moon tries on 2026-11-07): roster keys moonLook and moonOptimism, the
 * hand score, the pass and the commitment. The hands are Wayne's own.
 */
public final class MoonLookTest {

	private MoonLookTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		barsFollowOptimism();
		waynesTriesScoreAndHisOtherHandsDoNot();
		passesTheLowHeartsLikeWayne();
		neverPassesSpadeControl();
		neverPassesALoneTopHeartOrWinners();
		runsMissingTheAceStillCount();
		heartLimits();
		testsTheOneHeartThenLeadsSpades();
		queenLeadIsAWarning();
		aceKingHoldTheExtraHeartForLast();
		leaderLetsAnEarlyMoonPass();
		shedsALoserEarlyOnlyWhenSafe();
		neverSheddingWithNoWayBackIn();
		commitsOnlyWhenTheLookIsOn();
		lookPassUsedForALookShot();
		rosterKeysRoundTrip();
		return TestKit.summarize();
	}

	private static void barsFollowOptimism() {
		TestKit.suite("barsFollowOptimism");
		TestKit.checkEquals("optimism 0 needs 16", Integer.valueOf(16), Integer.valueOf(HandEvaluator.sideSuitMoonBar(0)));
		TestKit.checkEquals("optimism 50 needs 12", Integer.valueOf(12), Integer.valueOf(HandEvaluator.sideSuitMoonBar(50)));
		TestKit.checkEquals("optimism 100 needs 8", Integer.valueOf(8), Integer.valueOf(HandEvaluator.sideSuitMoonBar(100)));
		TestKit.checkEquals("build bar is three lower", Integer.valueOf(9), Integer.valueOf(HandEvaluator.sideSuitBuildBar(50)));
	}

	private static void waynesTriesScoreAndHisOtherHandsDoNot() {
		TestKit.suite("waynesTriesScoreAndHisOtherHandsDoNot");
		// 11/07 game 2 hand 9, made with the Jack (-36).
		TestKit.checkEquals("G2 h9 (made)", Integer.valueOf(20), Integer.valueOf(score("SAQ HQ9 DAKQT9542 C9")));
		// 11/07 game 1 hand 7, made (-26).
		TestKit.checkEquals("G1 h7 (made)", Integer.valueOf(13), Integer.valueOf(score("SKQ976 HAQ DK62 CKT8")));
		// 11/07 game 2 hand 11, broken on the last trick.
		TestKit.checkEquals("G2 h11", Integer.valueOf(19), Integer.valueOf(score("SQJ86 HAKJ DAJ93 CAK")));
		// Hands he did not try.
		TestKit.checkEquals("G1 h21 (no try)", Integer.valueOf(2), Integer.valueOf(score("SK7 HKQJT64 DJ7653")));
		TestKit.checkEquals("G2 h8 (no try)", Integer.valueOf(0), Integer.valueOf(score("S84 HKQ9832 DAT9 C75")));
		TestKit.check("the made moons clear the default bar", score("SKQ976 HAQ DK62 CKT8") >= HandEvaluator.sideSuitMoonBar(50));
		TestKit.check("the long-Heart hand he passed on does not clear even the most optimistic bar",
				score("SK7 HKQJT64 DJ7653") < HandEvaluator.sideSuitMoonBar(100));
	}

	private static void passesTheLowHeartsLikeWayne() {
		TestKit.suite("passesTheLowHeartsLikeWayne");
		// 11/07 game 2 hand 18: Wayne passed 8, 5 and 4 of Hearts.
		List<Card> pass = HandEvaluator.moonLookPass(cards("SQJ3 HK854 DAT75 C65"));
		TestKit.checkEquals("passes three cards", Integer.valueOf(3), Integer.valueOf(pass.size()));
		TestKit.check("all three are Hearts", pass.stream().allMatch(c -> c.suit == Suit.HEART));
		TestKit.check("and keeps the King", pass.stream().noneMatch(c -> c.cardValue == CardValue.KING));
		// Only one low Heart: the rest come from the shortest suit.
		List<Card> pass2 = HandEvaluator.moonLookPass(cards("SAQ63 H4 DAKQT952 C96"));
		TestKit.check("the lone low Heart goes", pass2.stream().anyMatch(c -> c.suit == Suit.HEART));
		TestKit.check("then the short Clubs", pass2.stream().filter(c -> c.suit == Suit.CLUB).count() == 2);
	}

	private static void neverPassesSpadeControl() {
		TestKit.suite("neverPassesSpadeControl");
		List<Card> pass = HandEvaluator.moonLookPass(cards("SAKQ DAKQJT98765"));
		TestKit.check("no Ace, King or Queen of Spades", pass.stream()
				.noneMatch(c -> c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.QUEEN) >= 0));
	}

	private static void neverPassesALoneTopHeartOrWinners() {
		TestKit.suite("neverPassesALoneTopHeartOrWinners");
		List<Card> pass = HandEvaluator.moonLookPass(cards("HA SAK3 DAK2 CKQJT98"));
		TestKit.check("keeps the lone Ace of Hearts", pass.stream().noneMatch(c -> c.suit == Suit.HEART));
		TestKit.check("keeps every Ace and King", pass.stream().noneMatch(c -> c.cardValue.compareTo(CardValue.KING) >= 0));
	}

	private static void runsMissingTheAceStillCount() {
		TestKit.suite("runsMissingTheAceStillCount");
		// Wayne 2026-11-08: "pull all clubs minus the ace or king".
		TestKit.check("a King-high club run clears the default bar",
				score("H5 SK64 DAQ7 CKQJT98") >= HandEvaluator.sideSuitMoonBar(50));
		TestKit.check("a broken King-high suit does not get the run credit",
				score("H5 SK64 DAQ7 CK9752") < HandEvaluator.sideSuitMoonBar(50));
		TestKit.check("one or no Hearts and all winners scores high", score("SAK5 DAKQ4 CAKQJ32") >= 20);
	}

	private static void heartLimits() {
		TestKit.suite("heartLimits");
		// Wayne 2026-11-08: "the Q73, you can't shoot the moon with that ... the 7 & 3 are stoppers".
		TestKit.check("Q73: never", !HandEvaluator.lookHeartsOk(cards("HQ73")));
		TestKit.check("no Hearts: fine", HandEvaluator.lookHeartsOk(cards("SA")));
		TestKit.check("one Heart (tested once): fine", HandEvaluator.lookHeartsOk(cards("HK")));
		TestKit.check("Ace and Queen: fine", HandEvaluator.lookHeartsOk(cards("HAQ")));
		TestKit.check("Ace and 8 (looks like dumping): fine", HandEvaluator.lookHeartsOk(cards("HA8")));
		TestKit.check("Ace and 4: no", !HandEvaluator.lookHeartsOk(cards("HA4")));
		TestKit.check("King and Jack, no Ace: no", !HandEvaluator.lookHeartsOk(cards("HKJ")));
		TestKit.check("A K Q and a 9: fine", HandEvaluator.lookHeartsOk(cards("HAKQ9")));
		TestKit.check("A K Q with two others: no", !HandEvaluator.lookHeartsOk(cards("HAKQ98")));
		Table_Cards tc = table();
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100).withMoonOptimism(100));
		p.moonLookThisHand = true;
		TestKit.check("a strong side-suit hand with Q73 is never a look shot",
				!HandEvaluator.shouldAttemptMoonShot(hand("SAK DAKQ CAKQJT HQ73"), tc, p));
	}

	private static void testsTheOneHeartThenLeadsSpades() {
		TestKit.suite("testsTheOneHeartThenLeadsSpades");
		Table_Cards tc = table();
		java.util.Map<PLAYER, java.util.EnumSet<Suit>> voids = new EnumMap<PLAYER, java.util.EnumSet<Suit>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			voids.put(seat, java.util.EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.SOUTH;
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100));
		p.moonLookThisHand = true;
		p.attempting_moon_shot = true;
		p.moonByLook = true;
		Hand h = hand("HK SAK4 DAK CAKQJT9");
		Card first = AI.chooseLead(h.hand, h, tc, p);
		TestKit.check("leads the King of Hearts to test it first", first.suit == Suit.HEART);
		Hand h2 = hand("SAK4 DAK CAKQJT9");
		List<Card> ranked = AICardScorer.rankLead(h2.hand, h2, tc, p);
		int aceSpades = -1, aceClubs = -1;
		for (int i = 0; i < ranked.size(); i++) {
			Card c = ranked.get(i);
			if (c.cardValue == CardValue.ACE && c.suit == Suit.SPADE) aceSpades = i;
			if (c.cardValue == CardValue.ACE && c.suit == Suit.CLUB) aceClubs = i;
		}
		TestKit.check("Heart gone (it held): the Ace of Spades now ranks above running the Clubs",
				aceSpades >= 0 && aceSpades < aceClubs);
		TestKit.check("and the lead is a top card", ranked.get(0).cardValue.compareTo(CardValue.KING) >= 0);
		tc.hearts_broken = false;
		Hand h3 = hand("HK SAK4 DAK CAKQJT9");
		List<Card> legal = new java.util.ArrayList<Card>();
		for (Card c : h3.hand) {
			if (c.suit != Suit.HEART) {
				legal.add(c);
			}
		}
		Card waiting = AI.chooseLead(legal, h3, tc, p);
		TestKit.check("Hearts not broken yet: holds the Spades back until the test",
				waiting.suit != Suit.SPADE);
	}

	private static void aceKingHoldTheExtraHeartForLast() {
		TestKit.suite("aceKingHoldTheExtraHeartForLast");
		Table_Cards tc = table();
		java.util.Map<PLAYER, java.util.EnumSet<Suit>> voids = new EnumMap<PLAYER, java.util.EnumSet<Suit>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			voids.put(seat, java.util.EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.SOUTH;
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100));
		p.moonLookThisHand = true;
		p.attempting_moon_shot = true;
		p.moonByLook = true;
		// Wayne's game 3 hand 10 shape: A K 10 of Hearts with a lot of winners.
		Hand h = hand("HAKT SAQ DKQJ CAKQJ");
		Card lead = AI.chooseLead(h.hand, h, tc, p);
		TestKit.check("does not test the 10 early", !(lead.suit == Suit.HEART && lead.cardValue == CardValue.TEN));
		TestKit.check("runs a side-suit winner before the A K of Hearts", lead.suit != Suit.HEART);
		Hand late = hand("HAKT");
		Card l2 = AI.chooseLead(late.hand, late, tc, p);
		TestKit.check("side winners gone: the A or K of Hearts next, 10 still held",
				l2.suit == Suit.HEART && l2.cardValue.compareTo(CardValue.KING) >= 0);
		Hand last = hand("HT");
		TestKit.check("and the 10 last", AI.chooseLead(last.hand, last, tc, p).cardValue == CardValue.TEN);
		p.won_cards.addX(cards("HA").get(0));
		p.won_cards.addX(cards("HK").get(0));
		Hand tail = hand("HT DKQ");
		Card l4 = AI.chooseLead(tail.hand, tail, tc, p);
		TestKit.check("A K already cashed: the 10 still waits behind the side cards", l4.suit != Suit.HEART);
		p.won_cards = new Hand();
		// A + one other: still tested early (Wayne's two-Heart rule).
		Hand two = hand("HA9 SAKQ DAKQ CAKQJ");
		Card l3 = AI.chooseLead(two.hand, two, tc, p);
		TestKit.check("A 9 only: the 9 is tested early", l3.suit == Suit.HEART && l3.cardValue == CardValue.NINE);
		// moonHeartLast off this hand: the A K 10 player tests the 10 early instead.
		p.moonHeartLastThisHand = false;
		Card l5 = AI.chooseLead(h.hand, h, tc, p);
		TestKit.check("heart-last off: the 10 is tested early", l5.suit == Suit.HEART && l5.cardValue == CardValue.TEN);
		p.moonHeartLastThisHand = true;
	}

	private static void queenLeadIsAWarning() {
		TestKit.suite("queenLeadIsAWarning");
		Table_Cards tc = table();
		Player west = tc.players.get(PLAYER.WEST);
		west.Set_Player(Profile.of(Player.Player_Type.NORMAL)); // MOON_ALARM default SHOOTER_READ
		tc.players.get(PLAYER.SOUTH).won_cards.addX(cards("HJ").get(0)); // one Heart, dumped on them
		TestKit.check("one dumped Heart alone: no alarm", !MoonRead.alarmed(tc, west, PLAYER.SOUTH));
		tc.who_leads = PLAYER.SOUTH;
		tc.p1_card = cards("SQ").get(0);
		TestKit.check("leading the Queen of Spades: alarm, on the trick itself", MoonRead.alarmed(tc, west, PLAYER.SOUTH));
	}

	private static void leaderLetsAnEarlyMoonPass() {
		TestKit.suite("leaderLetsAnEarlyMoonPass");
		// Game 3 hand 10 (2026-11-08): West 10, South 20, North 37, East 77; trick 7.
		Table_Cards tc = table();
		int[] totals = { 37, 77, 20, 10 };
		for (PLAYER s : PLAYER.values()) {
			tc.players.get(s).p_total_points = totals[s.ordinal()];
		}
		Player west = tc.players.get(PLAYER.WEST);
		Player south = tc.players.get(PLAYER.SOUTH);
		for (Card c : cards("CK CA CQ CJ DA D2 D3 D4 S7 SK S5 S3 S4 ST S8 S6 C5 C7 D5 CQ D6 HJ DJ D2")) {
			south.won_cards.addX(c); // six tricks' worth, one Heart
		}
		TestKit.check("leading by 10, shooter has one Heart, trick 7: lets it pass",
				MoonThreat.letsItPassForNow(tc, west, PLAYER.SOUTH));
		west.p_total_points = 15;
		TestKit.check("only 5 ahead: defends", !MoonThreat.letsItPassForNow(tc, west, PLAYER.SOUTH));
		west.p_total_points = 10;
		tc.players.get(PLAYER.NORTH).p_total_points = 5;
		TestKit.check("not leading the game: defends", !MoonThreat.letsItPassForNow(tc, west, PLAYER.SOUTH));
		tc.players.get(PLAYER.NORTH).p_total_points = 37;
		for (Card c : cards("H2 H3 H4")) {
			south.won_cards.addX(c);
		}
		TestKit.check("shooter at 4 Hearts: defends again", !MoonThreat.letsItPassForNow(tc, west, PLAYER.SOUTH));
	}

	private static void shedsALoserEarlyOnlyWhenSafe() {
		TestKit.suite("shedsALoserEarlyOnlyWhenSafe");
		Table_Cards tc = table();
		java.util.Map<PLAYER, java.util.EnumSet<Suit>> voids = new EnumMap<PLAYER, java.util.EnumSet<Suit>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			voids.put(seat, java.util.EnumSet.noneOf(Suit.class));
		}
		tc.void_suits = voids;
		tc.hearts_broken = false;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.SOUTH;
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100));
		p.moonLookThisHand = true;
		p.attempting_moon_shot = true;
		p.moonByLook = true;
		// Hearts not broken, only sure-winner Hearts, Queen of Spades held: the low
		// Club is the loser to shed. (With the boss Jack of Diamonds in hand the
		// AI cashes the Jack first instead, which is also right.)
		Hand h = hand("SAKQJT HAK C65432");
		List<Card> legalNoHearts = new java.util.ArrayList<Card>();
		for (Card c : h.hand) {
			if (c.suit != Suit.HEART) {
				legalNoHearts.add(c);
			}
		}
		Card lead = AI.chooseLead(legalNoHearts, h, tc, p);
		TestKit.check("sheds the lowest Club while everyone still follows", lead.suit == Suit.CLUB
				&& lead.cardValue == CardValue.TWO);
		voids.get(PLAYER.WEST).add(Suit.CLUB);
		Card lead2 = AI.chooseLead(legalNoHearts, h, tc, p);
		TestKit.check("someone void in Clubs (could throw a Heart): no shedding there", lead2.suit != Suit.CLUB);
	}

	/** Wayne's 11-Club moon (2026-11-09) replayed with AIs in his seat: after
	 * trick 4 South held 10s 5d and seven Clubs nobody else had. It led the 10
	 * of Spades "to shed a loser", lost the lead, and never got it back. */
	private static void neverSheddingWithNoWayBackIn() {
		TestKit.suite("neverSheddingWithNoWayBackIn");
		Table_Cards tc = table();
		java.util.Map<PLAYER, java.util.EnumSet<Suit>> voids = new EnumMap<PLAYER, java.util.EnumSet<Suit>>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			voids.put(seat, java.util.EnumSet.noneOf(Suit.class));
		}
		voids.get(PLAYER.NORTH).add(Suit.CLUB);
		voids.get(PLAYER.EAST).add(Suit.CLUB);
		tc.void_suits = voids;
		tc.hearts_broken = true;
		tc.who_leads = PLAYER.SOUTH;
		tc.current_player = PLAYER.SOUTH;
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100));
		p.moonLookThisHand = true;
		p.attempting_moon_shot = true;
		p.moonByLook = true;
		// Tricks so far: every other Club is played (won), the Queen is South's.
		for (Card c : cards("C23AK45 SQ H7QK6AJ")) {
			p.won_cards.addX(c);
		}
		Hand h = hand("ST D5 C6789TJQ");
		Card lead = AI.chooseLead(h.hand, h, tc, p);
		TestKit.check("runs the Clubs instead of leading the 10 of Spades or 5 of Diamonds", lead.suit == Suit.CLUB);
		// Same hand with one Club still out in another hand: the Club is an
		// entry back only if it is a sure winner in a suit others hold -- here
		// the Queen of Clubs is, so shedding is allowed again.
		p.won_cards = new Hand();
		for (Card c : cards("C23AK4 SQ H7QK6AJ")) {
			p.won_cards.addX(c);
		}
		voids.get(PLAYER.NORTH).remove(Suit.CLUB);
		voids.get(PLAYER.EAST).remove(Suit.CLUB);
		AICardScorer.LeadContext ctx = new AICardScorer.LeadContext(h.hand, tc, p);
		ctx.fullHand = h.hand;
		TestKit.check("with a Club still out, the Queen of Clubs is a way back in",
				AICardScorer.hasEntryBack(ctx, h.hand, Suit.SPADE));
	}

	private static void commitsOnlyWhenTheLookIsOn() {
		TestKit.suite("commitsOnlyWhenTheLookIsOn");
		Table_Cards tc = table();
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.CAUTIOUS).withMoonLook(40));
		Hand made = hand("SKQ976 HAQ DK62 CKT8");
		p.moonLookThisHand = false;
		TestKit.check("look off this hand: no commitment", !HandEvaluator.shouldAttemptMoonShot(made, tc, p));
		p.moonLookThisHand = true;
		TestKit.check("look on: commits (even a Cautious template)", HandEvaluator.shouldAttemptMoonShot(made, tc, p));
		p.attempting_moon_shot = true;
		TestKit.check("and plays the shot", TacticWeights.weight(p, TacticWeights.Tactic.MOON_OFFENSE) > 0);
		p.Set_Player(Profile.of(Player.Player_Type.CAUTIOUS).withMoonLook(40).withMoonOptimism(0));
		TestKit.check("a pessimist (bar 16) passes on the same hand", !HandEvaluator.shouldAttemptMoonShot(made, tc, p));
	}

	private static void lookPassUsedForALookShot() {
		TestKit.suite("lookPassUsedForALookShot");
		Table_Cards tc = table();
		Player p = tc.players.get(PLAYER.SOUTH);
		p.Set_Player(Profile.of(Player.Player_Type.NORMAL).withMoonLook(100).withMoonOptimism(100));
		p.moonLookThisHand = true;
		// Builds (after its own pass the hand clears the build bar) and passes its low Hearts.
		Hand h = hand("SQJ3 HK854 DAT75 C65");
		List<Card> passed = Deal_Hand.chooseCardsToPass(h, tc, p);
		TestKit.check("passes the low Hearts", passed.stream().allMatch(c -> c.suit == Suit.HEART));
		p.moonLookThisHand = false;
		List<Card> normal = Deal_Hand.chooseCardsToPass(hand("SQJ3 HK854 DAT75 C65"), tc, p);
		TestKit.check("without the look the normal pass is used",
				!normal.stream().allMatch(c -> c.suit == Suit.HEART && c.cardValue.compareTo(CardValue.TEN) <= 0));
	}

	private static void rosterKeysRoundTrip() {
		TestKit.suite("rosterKeysRoundTrip");
		Profile p = Profile.of(Player.Player_Type.NORMAL).withSetting("moonLook", "35").withSetting("moonOptimism", "70");
		TestKit.checkEquals("moonLook read", Integer.valueOf(35), Integer.valueOf(p.moonLookPercent));
		TestKit.checkEquals("moonOptimism read", Integer.valueOf(70), Integer.valueOf(p.moonOptimism));
		boolean look = false, opt = false;
		for (String[] kv : p.settings()) {
			look |= kv[0].equals("moonLook") && kv[1].equals("35");
			opt |= kv[0].equals("moonOptimism") && kv[1].equals("70");
		}
		TestKit.check("both written back", look && opt);
		Profile plain = Profile.of(Player.Player_Type.NORMAL);
		TestKit.checkEquals("default look is off", Integer.valueOf(0), Integer.valueOf(plain.moonLookPercent));
		TestKit.check("defaults write nothing", plain.settings().stream().noneMatch(kv -> kv[0].startsWith("moon")));
		TestKit.check("other settings keep the look", p.withBlunder(5).moonLookPercent == 35 && p.withLevel(4).moonOptimism == 70);
		TestKit.checkEquals("default heart-last is on", Integer.valueOf(100), Integer.valueOf(plain.moonHeartLast));
		Profile q = p.withSetting("moonHeartLast", "40");
		TestKit.checkEquals("moonHeartLast read", Integer.valueOf(40), Integer.valueOf(q.moonHeartLast));
		TestKit.check("moonHeartLast written back", q.settings().stream().anyMatch(kv -> kv[0].equals("moonHeartLast") && kv[1].equals("40")));
		TestKit.check("other settings keep heart-last", q.withLevel(3).moonHeartLast == 40 && q.withMoonLook(10).moonHeartLast == 40);
		// The roster file itself (the loader keeps a list of profile keys).
		try {
			java.io.File dir = java.nio.file.Files.createTempDirectory("heartlastroster").toFile();
			java.nio.file.Files.write(new java.io.File(dir, "roster.txt").toPath(),
					("version = 2\n\n[player]\nid = AI_X\nname = Tester\ntemplate = NORMAL\n"
							+ "moonLook = 30\nmoonOptimism = 60\nmoonHeartLast = 20\n")
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			java.io.PrintStream real = System.out;
			System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
			Profile loaded = hearts_1.roster.Roster.loadOrCreate(dir).byId("AI_X").profile;
			System.setOut(real);
			TestKit.check("roster.txt moon keys are loaded", loaded.moonLookPercent == 30
					&& loaded.moonOptimism == 60 && loaded.moonHeartLast == 20);
		} catch (java.io.IOException e) {
			TestKit.check("no IOException: " + e, false);
		}
	}

	// "SQJ3 HK854 DAT75 C65" -> cards
	static List<Card> cards(String spec) {
		List<Card> out = new java.util.ArrayList<Card>();
		for (String part : spec.trim().split("\\s+")) {
			Suit s = part.charAt(0) == 'S' ? Suit.SPADE : part.charAt(0) == 'H' ? Suit.HEART
					: part.charAt(0) == 'D' ? Suit.DIAMOND : Suit.CLUB;
			for (char ch : part.substring(1).toCharArray()) {
				out.add(new Card(value(ch), s, null));
			}
		}
		return out;
	}

	private static CardValue value(char ch) {
		switch (ch) {
			case 'A': return CardValue.ACE;
			case 'K': return CardValue.KING;
			case 'Q': return CardValue.QUEEN;
			case 'J': return CardValue.JACK;
			case 'T': return CardValue.TEN;
			default: return CardValue.values()[ch - '2'];
		}
	}

	private static Hand hand(String spec) {
		Hand h = new Hand();
		for (Card c : cards(spec)) {
			h.addX(c);
		}
		return h;
	}

	private static int score(String spec) {
		return HandEvaluator.sideSuitMoonScore(cards(spec));
	}

	private static Table_Cards table() {
		Table_Cards tc = new Table_Cards();
		Map<PLAYER, Player> players = new EnumMap<PLAYER, Player>(PLAYER.class);
		for (PLAYER seat : PLAYER.values()) {
			Player p = new Player();
			p.tbl_player = seat;
			players.put(seat, p);
		}
		tc.players = players;
		return tc;
	}
}
