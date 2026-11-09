package hearts_1.roster;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared cross-game counter store, added 2026-08-16 -- ONE store, two
 * planned consumers, per the design decision recorded in both TODO.md's
 * player-stats entry and DESIGN_roster_and_trashtalk.md section 3.3: the
 * human-facing stats screen reads it now (StatsReport), and the AI's
 * opponent-modelling tendencies will read the same counters later, so the
 * recording layer is built exactly once.
 *
 * Two keyspaces:
 *   - per-player counters, keyed by a roster id (or Roster.HUMAN_ID);
 *   - per-ORDERED-pair counters, keyed "A>B" (A did something to B --
 *     asymmetric by construction, matching the design doc's observer ->
 *     subject framing).
 *
 * Counters are just named integers -- the store neither knows nor cares
 * what a name means, which is what makes adding a new stat a one-line
 * change in StatsRecorder with no schema migration. Persisted to
 * `history.txt` (generated data, kept separate from the authored
 * roster.txt so a corrupt history never costs the user their roster) with
 * the same versioned/forgiving/atomic conventions as Roster.
 */
public final class StatsStore {

	private static final int SCHEMA_VERSION = 1;
	private static final String FILE_NAME = "history.txt";

	private final Map<String, Map<String, Integer>> playerCounters =
			new LinkedHashMap<String, Map<String, Integer>>();
	private final Map<String, Map<String, Integer>> pairCounters =
			new LinkedHashMap<String, Map<String, Integer>>();

	public static StatsStore loadOrCreate(File dir) {
		StatsStore store = new StatsStore();
		File file = new File(dir, FILE_NAME);
		if (file.isFile()) {
			store.parse(file);
		}
		return store;
	}

	// ---- counters -----------------------------------------------------

	public int get(String playerId, String counter) {
		Map<String, Integer> row = playerCounters.get(playerId);
		Integer v = (row == null) ? null : row.get(counter);
		return v == null ? 0 : v.intValue();
	}

	public int getPair(String actorId, String subjectId, String counter) {
		Map<String, Integer> row = pairCounters.get(actorId + ">" + subjectId);
		Integer v = (row == null) ? null : row.get(counter);
		return v == null ? 0 : v.intValue();
	}

	public void add(String playerId, String counter, int delta) {
		put(playerId, counter, get(playerId, counter) + delta);
	}

	public void addPair(String actorId, String subjectId, String counter, int delta) {
		String key = actorId + ">" + subjectId;
		Map<String, Integer> row = pairCounters.get(key);
		if (row == null) {
			row = new LinkedHashMap<String, Integer>();
			pairCounters.put(key, row);
		}
		Integer v = row.get(counter);
		row.put(counter, Integer.valueOf((v == null ? 0 : v.intValue()) + delta));
	}

	/** Absolute set -- for values that aren't accumulating counts (current
	 * streak, best/worst single-hand score). */
	public void put(String playerId, String counter, int value) {
		Map<String, Integer> row = playerCounters.get(playerId);
		if (row == null) {
			row = new LinkedHashMap<String, Integer>();
			playerCounters.put(playerId, row);
		}
		row.put(counter, Integer.valueOf(value));
	}

	/** Every player id that has at least one recorded counter. */
	public List<String> knownPlayerIds() {
		return new ArrayList<String>(playerCounters.keySet());
	}

	// ---- persistence --------------------------------------------------

	private void parse(File file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("****** STATS unreadable (" + e.getMessage() + ") -- starting empty ******");
			return;
		}
		String sectionKind = null; // "player" or "pair"
		String sectionKey = null;
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("[")) {
				String inner = line.substring(1, line.endsWith("]") ? line.length() - 1 : line.length()).trim();
				int space = inner.indexOf(' ');
				if (space > 0) {
					sectionKind = inner.substring(0, space);
					sectionKey = inner.substring(space + 1).trim();
				} else {
					System.out.println("****** STATS skipping unknown section: " + line + " ******");
					sectionKind = null;
					sectionKey = null;
				}
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				System.out.println("****** STATS skipping unparseable line: " + line + " ******");
				continue;
			}
			String key = line.substring(0, eq).trim();
			String value = line.substring(eq + 1).trim();
			if ("version".equals(key) && sectionKind == null) {
				continue;
			}
			int number;
			try {
				number = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				System.out.println("****** STATS skipping non-numeric value: " + line + " ******");
				continue;
			}
			if ("player".equals(sectionKind)) {
				put(sectionKey, key, number);
			} else if ("pair".equals(sectionKind)) {
				int gt = sectionKey.indexOf('>');
				if (gt > 0) {
					addPair(sectionKey.substring(0, gt), sectionKey.substring(gt + 1), key, number);
				}
			} else {
				System.out.println("****** STATS skipping value outside any section: " + line + " ******");
			}
		}
	}

	public void save(File dir) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Hearts_2 history -- generated stats. Rewritten by the game at the\n");
		sb.append("# end of every hand; hand-edits survive only until then.\n");
		sb.append("version = ").append(SCHEMA_VERSION).append('\n');
		for (Map.Entry<String, Map<String, Integer>> e : playerCounters.entrySet()) {
			sb.append('\n').append("[player ").append(e.getKey()).append("]\n");
			for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
				sb.append(c.getKey()).append(" = ").append(c.getValue()).append('\n');
			}
		}
		for (Map.Entry<String, Map<String, Integer>> e : pairCounters.entrySet()) {
			sb.append('\n').append("[pair ").append(e.getKey()).append("]\n");
			for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
				sb.append(c.getKey()).append(" = ").append(c.getValue()).append('\n');
			}
		}
		Roster.atomicWrite(new File(dir, FILE_NAME), sb.toString());
	}
}
