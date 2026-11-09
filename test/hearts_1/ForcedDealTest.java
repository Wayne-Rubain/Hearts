package hearts_1;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import hearts_1.Hearts_1.PASS_DIRECTION;

/** 2026-10-17: a deal copied from a game log can be replayed exactly (once). */
public final class ForcedDealTest {

	private ForcedDealTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	private static String names(Hand h) {
		List<String> out = new ArrayList<String>();
		for (Card c : h.hand) {
			out.add(c.cardValue + " " + c.suit);
		}
		java.util.Collections.sort(out);
		return String.join(", ", out);
	}

	public static boolean run() {
		TestKit.reset();
		TestKit.suite("forcedDealReplaysALoggedHandOnce");
		java.io.PrintStream real = System.out;
		try {
			File dir = Files.createTempDirectory("forceddeal").toFile();
			File f = new File(dir, "forced_deal.txt");
			String n = "EIGHT HEART, NINE HEART, KING HEART, THREE SPADE, EIGHT SPADE, NINE SPADE, TEN SPADE, KING SPADE, SEVEN DIAMOND, TWO CLUB, EIGHT CLUB, NINE CLUB, KING CLUB";
			String e = "SEVEN HEART, FOUR SPADE, SIX SPADE, SEVEN SPADE, JACK SPADE, TWO DIAMOND, FOUR DIAMOND, FIVE DIAMOND, SIX DIAMOND, JACK DIAMOND, QUEEN DIAMOND, SIX CLUB, JACK CLUB";
			String s = "TWO HEART, THREE HEART, TEN HEART, TWO SPADE, FIVE SPADE, QUEEN SPADE, ACE SPADE, EIGHT DIAMOND, KING DIAMOND, THREE CLUB, FIVE CLUB, SEVEN CLUB, QUEEN CLUB";
			String w = "FOUR HEART, FIVE HEART, SIX HEART, JACK HEART, QUEEN HEART, ACE HEART, THREE DIAMOND, NINE DIAMOND, TEN DIAMOND, ACE DIAMOND, FOUR CLUB, TEN CLUB, ACE CLUB";
			String text = "DIRECTION KEEP\n****** DEAL NORTH hand = " + n + " ******\n****** DEAL EAST hand = " + e
					+ " ******\n****** DEAL SOUTH hand = " + s + " ******\n****** DEAL WEST hand = " + w + " ******\n";
			Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
			System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
			Deck deck = new Deck();
			deck.shuffle_now();
			All_Hands dealt = deck.deal(Hearts_1.PLAYER.NORTH, deck);
			All_Hands got = ForcedDeal.applyIfPending(dealt, deck, f);
			System.setOut(real);
			TestKit.checkEquals("North's hand matches the log", n.length() > 0 ? sortedCsv(n) : "", names(got.n_hand));
			TestKit.checkEquals("West's hand matches the log", sortedCsv(w), names(got.w_hand));
			TestKit.checkEquals("South has 13 cards", 13, got.s_hand.hand.size());
			TestKit.checkEquals("direction comes from the file", PASS_DIRECTION.KEEP,
					ForcedDeal.directionOr(PASS_DIRECTION.LEFT));
			TestKit.check("file consumed (applies once)", !f.exists() && new File(dir, "forced_deal.used.txt").exists());
		} catch (java.io.IOException ex) {
			System.setOut(real);
			TestKit.check("no IOException: " + ex, false);
		}
		return TestKit.summarize();
	}

	private static String sortedCsv(String csv) {
		List<String> l = new ArrayList<String>(java.util.Arrays.asList(csv.split(", ")));
		java.util.Collections.sort(l);
		return String.join(", ", l);
	}
}
