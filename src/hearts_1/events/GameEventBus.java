package hearts_1.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal synchronous publish/subscribe bus, added 2026-08-15 alongside
 * GameEvent. Facts are a plain String-keyed bag rather than a typed
 * payload per event -- deliberately matching the Valve-style "query is a
 * bag of key/value facts" design already chosen for the planned trashTalk
 * engine (DESIGN_roster_and_trashtalk.md section 4.1), so a future rule
 * engine can consume these events with no translation layer. Consumers
 * (trashTalk, the roster's tendency counters, the stats screen) don't
 * exist yet -- see ConsoleEventLogger for the one listener that does,
 * which exists purely to prove the bus actually fires.
 *
 * Static/global rather than an instance passed around: every emission
 * point (Play_Hand, Deal_Hand, Hearts_JFrame) already reaches deep into
 * static game state (Scoring, Rules) the same way, and a game-wide bus
 * has exactly one instance for the process's whole lifetime regardless,
 * so a singleton avoids threading a bus reference through call chains
 * that don't otherwise need one.
 */
public final class GameEventBus {

	private GameEventBus() { }

	public interface Listener {
		void onEvent(GameEvent event, Map<String, Object> facts);
	}

	private static final List<Listener> LISTENERS = new ArrayList<Listener>();

	public static void subscribe(Listener listener) {
		LISTENERS.add(listener);
	}

	/** Convenience for tests -- undoes subscribe() so a test's listener
	 * doesn't leak into the next test's run. */
	public static void unsubscribe(Listener listener) {
		LISTENERS.remove(listener);
	}

	public static void publish(GameEvent event, Map<String, Object> facts) {
		// Copy so a listener that subscribes/unsubscribes mid-publish (a
		// realistic thing for a stats screen toggling itself) can't cause
		// a ConcurrentModificationException.
		List<Listener> snapshot = new ArrayList<Listener>(LISTENERS);
		for (Listener l : snapshot) {
			l.onEvent(event, facts);
		}
	}

	/** Builder-style fact bag, since every publish() call site otherwise
	 * needs a 4-line HashMap construction for what's usually 2-3 facts. */
	public static Facts facts() {
		return new Facts();
	}

	public static final class Facts {
		private final Map<String, Object> map = new HashMap<String, Object>();

		public Facts with(String key, Object value) {
			map.put(key, value);
			return this;
		}

		public Map<String, Object> build() {
			return Collections.unmodifiableMap(map);
		}
	}
}
