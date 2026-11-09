package hearts_1;

import java.io.OutputStream;
import java.io.PrintStream;
import java.security.MessageDigest;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.Player.Player_Type;

/** Scratch: fingerprint of every card played in fixed seeded hands. Run on
 * two builds; identical output = identical play. */
public final class Equiv {
	public static void main(String[] args) throws Exception {
		int hands = Integer.parseInt(args[0]);
		PrintStream out = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		StringBuilder report = new StringBuilder();
		for (Player_Type t : Player_Type.values()) {
			for (int pct : new int[] { 0, 100 }) {
				Player_Type[] types = { t, t, t, t };
				report.append(fingerprint(t + "/count" + pct, types, new int[] { pct, pct, pct, pct }, hands, 0));
			}
		}
		Player_Type[] all = Player_Type.values();
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		long n = 0;
		for (int h = 0; h < hands; h++) {
			Player_Type[] types = { all[h % all.length], all[(h / 2) % all.length], all[(h / 3) % all.length], all[(h / 5) % all.length] };
			HeadlessSimulator.SeededHand r = HeadlessSimulator.playSeededHand(500_000 + h, types, new int[] { 50, 50, 50, 50 });
			md.update(r.plays.getBytes());
			md.update(r.scores.toString().getBytes());
			n++;
		}
		report.append(String.format("%-36s %d hands %s%n", "MIXED/count50", n, hex(md.digest())));
		System.setOut(out);
		System.out.print(report);
	}

	static String fingerprint(String label, Player_Type[] types, int[] pcts, int hands, long base) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		for (int h = 0; h < hands; h++) {
			HeadlessSimulator.SeededHand r = HeadlessSimulator.playSeededHand(base + h, types, pcts);
			md.update(r.plays.getBytes());
			md.update(r.scores.toString().getBytes());
		}
		return String.format("%-36s %d hands %s%n", label, hands, hex(md.digest()));
	}

	static String hex(byte[] b) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) sb.append(String.format("%02x", b[i]));
		return sb.toString();
	}
}
