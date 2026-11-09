package hearts_1;

import hearts_1.Player.Player_Type;

/**
 * HeadlessSimulator's per-hand observer (2026-11-02): captures() reports, once
 * per hand and after hand(), which seat took the Queen of Spades and which took
 * the Jack of Diamonds. Every card is captured each hand, so exactly one seat
 * holds each. Lineup's Queen and Jack counts depend on this.
 */
public final class SimulatorObserverTest {

	private SimulatorObserverTest() { }

	public static void main(String[] args) {
		boolean ok = run();
		System.exit(ok ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		exactlyOneQueenAndOneJackEachHand();
		return TestKit.summarize();
	}

	private static void exactlyOneQueenAndOneJackEachHand() {
		TestKit.suite("exactlyOneQueenAndOneJackEachHand");
		final int[] handCalls = { 0 }, captureCalls = { 0 }, badQueen = { 0 }, badJack = { 0 }, outOfOrder = { 0 };
		HeadlessSimulator.observer = new HeadlessSimulator.HandObserver() {
			@Override public void hand(boolean[] att, boolean[] gam, int[] tot, int[] hs) {
				handCalls[0]++;
			}
			@Override public void captures(boolean[] queen, boolean[] jack) {
				captureCalls[0]++;
				if (captureCalls[0] != handCalls[0]) outOfOrder[0]++;
				if (count(queen) != 1) badQueen[0]++;
				if (count(jack) != 1) badJack[0]++;
			}
		};
		java.io.PrintStream out = System.out;
		try {
			System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
			Player_Type n = Player_Type.NORMAL;
			HeadlessSimulator.playSeededGame(4242L, new Player_Type[] { n, n, n, n }, new int[] { 50, 50, 50, 50 });
		} finally {
			System.setOut(out);
			HeadlessSimulator.observer = null;
		}
		TestKit.check("the game played some hands", handCalls[0] > 0);
		TestKit.checkEquals("captures() once per hand", Integer.valueOf(handCalls[0]), Integer.valueOf(captureCalls[0]));
		TestKit.checkEquals("captures() always follows hand()", Integer.valueOf(0), Integer.valueOf(outOfOrder[0]));
		TestKit.checkEquals("hands without exactly one Queen taker", Integer.valueOf(0), Integer.valueOf(badQueen[0]));
		TestKit.checkEquals("hands without exactly one Jack taker", Integer.valueOf(0), Integer.valueOf(badJack[0]));
	}

	private static int count(boolean[] b) {
		int n = 0;
		for (boolean x : b) if (x) n++;
		return n;
	}
}
