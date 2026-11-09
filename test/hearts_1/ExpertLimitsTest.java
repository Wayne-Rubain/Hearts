package hearts_1;

import java.util.Arrays;
import java.util.List;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Type;

/**
 * 2026-10-17, user rule: expert players (level 4-5) make fewer and smaller
 * mistakes. Their blunder rate is capped (2% at level 4, 1% at level 5), a
 * blunder is only ever their 2nd-best card and never costs 4+ points as the
 * last player, and their wildness never strays into a measured leak
 * (Strategy.isWeak, e.g. MOON_ALARM=FIRST_HEART).
 */
public final class ExpertLimitsTest {

	private ExpertLimitsTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		blunderRateIsCappedForExperts();
		expertBlundersAreOnlyTheSecondBestCard();
		expertBlundersStaySmall();
		expertsNeverStrayIntoALeak();
		return TestKit.summarize();
	}

	private static Profile profile(int level) {
		return Profile.of(Player_Type.NORMAL).withLevel(level);
	}

	private static Card card(CardValue v, Suit s) {
		return new Card(v, s, null);
	}

	private static void blunderRateIsCappedForExperts() {
		TestKit.suite("blunderRateIsCappedForExperts");
		TestKit.checkEquals("level 5: 2% -> 1%", 1, profile(5).withBlunder(2).effectiveBlunderPercent());
		TestKit.checkEquals("level 4: 3% -> 2%", 2, profile(4).withBlunder(3).effectiveBlunderPercent());
		TestKit.checkEquals("level 4 below the cap keeps its own rate", 1,
				profile(4).withBlunder(1).effectiveBlunderPercent());
		TestKit.checkEquals("level 3 uncapped", 15, profile(3).withBlunder(15).effectiveBlunderPercent());
	}

	private static void expertBlundersAreOnlyTheSecondBestCard() {
		TestKit.suite("expertBlundersAreOnlyTheSecondBestCard");
		Player p = new Player();
		p.tbl_player = PLAYER.WEST;
		p.Set_Player(profile(5));
		List<Card> ranked = Arrays.asList(card(CardValue.TWO, Suit.CLUB), card(CardValue.THREE, Suit.CLUB),
				card(CardValue.FOUR, Suit.CLUB), card(CardValue.FIVE, Suit.CLUB));
		java.io.PrintStream out = System.out;
		System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		boolean onlySecond = true;
		AI.RNG.setSeed(9);
		for (int i = 0; i < 600; i++) {
			onlySecond &= AI.nearMiss(ranked, p) == ranked.get(1);
		}
		System.setOut(out);
		TestKit.check("600 expert blunders, every one the 2nd-best card", onlySecond);
	}

	private static void expertBlundersStaySmall() {
		TestKit.suite("expertBlundersStaySmall");
		// Hearts trick, last seat: KH would take a 4-point trick where 2H ducks.
		Table_Cards tc = new Table_Cards();
		tc.p1_card = card(CardValue.THREE, Suit.HEART);
		tc.p2_card = card(CardValue.FOUR, Suit.HEART);
		tc.p3_card = card(CardValue.FIVE, Suit.HEART);
		Card kh = card(CardValue.KING, Suit.HEART), twoH = card(CardValue.TWO, Suit.HEART);
		TestKit.check("anyone: a 4-point slip is allowed", !AI.isSuicidalFollow(kh, twoH, tc, 4, AI.SUICIDAL_BLUNDER_COST));
		TestKit.check("expert: a 4-point slip is vetoed", AI.isSuicidalFollow(kh, twoH, tc, 4, AI.EXPERT_BLUNDER_COST));
	}

	private static void expertsNeverStrayIntoALeak() {
		TestKit.suite("expertsNeverStrayIntoALeak");
		TestKit.check("FIRST_HEART is a measured leak", Strategy.isWeak(Strategy.MoonAlarm.FIRST_HEART));
		TestKit.check("SHOOTER_READ is not", !Strategy.isWeak(Strategy.MoonAlarm.SHOOTER_READ));
		int expertStrays = 0, normalStrays = 0;
		for (int level : new int[] { 5, 3 }) {
			Player p = new Player();
			p.tbl_player = PLAYER.WEST;
			p.Set_Player(profile(level).withSetting("wildness", "100").withSetting("stray.MOON_ALARM", "FIRST_HEART 1"));
			java.util.Random rng = new java.util.Random(4);
			for (int h = 0; h < 50; h++) {
				p.rollHand(rng, null);
				if (p.strategyThisHand.get(Strategy.MOON_ALARM) == Strategy.MoonAlarm.FIRST_HEART) {
					if (level == 5) {
						expertStrays++;
					} else {
						normalStrays++;
					}
				}
			}
		}
		TestKit.checkEquals("level 5 with 100% wildness never strays to FIRST_HEART", 0, expertStrays);
		TestKit.check("level 3 does (" + normalStrays + " of 50 hands)", normalStrays > 40);
	}
}
