package hearts_1;

import java.awt.EventQueue;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;

/** Smoke test of the real Swing game (2026-11-05): plays it for a number of
 * hands with nobody at the keyboard, South clicking its first legal card,
 * and writes the console log to a file. Needs a display; in a container use
 * xvfb-run. Seeds AI.RNG, so with a ~/.hearts2/forced_deal.txt in the given
 * home folder the first hand plays the same every time -- run two builds and
 * diff the "******" lines of their logs to prove a change kept the real
 * game's play identical (Card@... object ids differ between runs; mask them).
 *
 * Args: homeFolder logFile hands. homeFolder stands in for user.home, so it
 * should hold .hearts2/roster.txt (and optionally .hearts2/forced_deal.txt).
 * Example: xvfb-run -a java -cp "out:forms-1.3.0.jar" hearts_1.UiSmoke /tmp/h /tmp/log.txt 5 */
public final class UiSmoke {
  static Hearts_JFrame f;
  static Object get(String name) throws Exception { Field x = Hearts_JFrame.class.getDeclaredField(name); x.setAccessible(true); return x.get(f); }
  public static void main(String[] a) throws Exception {
    System.setProperty("user.home", a[0]);
    int hands = Integer.parseInt(a[2]);
    PrintStream log = new PrintStream(new FileOutputStream(a[1]), true);
    System.setOut(log);
    AI.RNG.setSeed(424242L);
    AI.seedRolls(424242L);
    EventQueue.invokeAndWait(() -> { f = new Hearts_JFrame(); f.setVisible(true); });
    int handsDone = 0; boolean wasStartEnabled = false;
    for (int step = 0; step < 20000 && handsDone < hands; step++) {
      final int[] done = { handsDone };
      final boolean[] prev = { wasStartEnabled };
      EventQueue.invokeAndWait(() -> {
        try {
          JButton pass = (JButton) get("jbPassCards"), play = (JButton) get("jbPlayCard"), clear = (JButton) get("jbClearTable"), start = (JButton) get("jbStartHand");
          @SuppressWarnings("unchecked") List<JButton> cards = (List<JButton>) get("jbA");
          @SuppressWarnings("unchecked") List<Card> sel = (List<Card>) get("south_pass_selection");
          boolean passing = (Boolean) get("passing_selection_mode");
          if (start.isEnabled() && !prev[0]) { done[0]++; prev[0] = true; return; }
          if (start.isEnabled() && done[0] < hands) { prev[0] = false; start.doClick(); return; }
          if (pass.isEnabled()) { pass.doClick(); return; }
          if (passing && sel.size() < 3) {
            cards.get(sel.size()).doClick(); return;
          }
          if (clear.isEnabled()) { clear.doClick(); return; }
          if (play.isEnabled()) { play.doClick(); return; }
          Boolean my = f.My_Turn;
          if (my != null && my) {
            for (JButton b : cards) { if (b.isEnabled() && b.isVisible() && b.getIcon() != null) { b.doClick(); return; } }
          }
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      handsDone = done[0]; wasStartEnabled = prev[0];
      Thread.sleep(2);
    }
    System.err.println("hands completed: " + handsDone);
    System.exit(0);
  }
}
