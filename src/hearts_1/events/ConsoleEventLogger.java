package hearts_1.events;

import java.util.Map;

/**
 * The one real GameEventBus listener that exists today, added 2026-08-15
 * alongside the bus itself. Its only job is to prove the bus actually
 * fires end-to-end (install it once, watch "****** EVENT ..." lines
 * appear in the same console log DEAL/PASS/MOON/TRICK already use) --
 * trashTalk, the roster's tendency counters, and the stats screen are
 * the real future listeners, none of which exist yet. Not installed
 * automatically; call install() once at startup (Hearts_JFrame's
 * constructor does this) so tests that don't want the noise can simply
 * not call it.
 */
public final class ConsoleEventLogger {

	private ConsoleEventLogger() { }

	private static final GameEventBus.Listener LISTENER = new GameEventBus.Listener() {
		public void onEvent(GameEvent event, Map<String, Object> facts) {
			System.out.println("****** EVENT " + event + " " + facts + " ******");
		}
	};

	private static boolean installed = false;

	public static void install() {
		if (!installed) {
			GameEventBus.subscribe(LISTENER);
			installed = true;
		}
	}
}
