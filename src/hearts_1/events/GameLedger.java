package hearts_1.events;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import hearts_1.Hearts_1.PLAYER;

/**
 * One CSV row per FINISHED game, kept forever, so trends show up across many
 * games (user request 2026-10-17: if Wayne wins too much, or the AIs never try
 * to shoot the moon, the AI traits need rework). Lives next to the game logs as
 * {@code games_ledger.csv}; never pruned. The per-player lifetime counters in
 * history.txt stay as they are -- this adds the per-game view they lack.
 *
 * Columns: date, hands, winners, human_place (1 = won), then for each seat in
 * the order NORTH, EAST, SOUTH, WEST: name, total, moon_tries (committed to a
 * shot), moon_made, queens_eaten, jacks_taken. A game stopped before the end
 * is not recorded here (its log is in gamelogs/).
 */
public final class GameLedger implements GameEventBus.Listener {

	private static final PLAYER[] ORDER = { PLAYER.NORTH, PLAYER.EAST, PLAYER.SOUTH, PLAYER.WEST };

	private final File file;
	private final Function<PLAYER, String> nameOf;
	private int hands;
	private final Map<PLAYER, int[]> counts = new EnumMap<PLAYER, int[]>(PLAYER.class);

	private static final int TRIES = 0, MADE = 1, QUEENS = 2, JACKS = 3;

	public GameLedger(File file, Function<PLAYER, String> nameOf) {
		this.file = file;
		this.nameOf = nameOf;
		reset();
	}

	public static File defaultFile() {
		return new File(GameLog.defaultDirectory(), "games_ledger.csv");
	}

	private void reset() {
		hands = 0;
		counts.clear();
		for (PLAYER p : PLAYER.values()) {
			counts.put(p, new int[4]);
		}
	}

	private void bump(Object seat, int what) {
		if (seat instanceof PLAYER) {
			counts.get((PLAYER) seat)[what]++;
		}
	}

	@Override
	public synchronized void onEvent(GameEvent event, Map<String, Object> facts) {
		switch (event) {
			case MOON_COMMITTED:
				bump(facts.get("seat"), TRIES);
				break;
			case MOON_SUCCEEDED:
				bump(facts.get("shooter"), MADE);
				break;
			case QUEEN_DROPPED_ON:
				bump(facts.get("winner"), QUEENS);
				break;
			case JACK_CAPTURED:
				bump(facts.get("winner"), JACKS);
				break;
			case HAND_ENDED:
				hands++;
				break;
			case GAME_ENDED:
				write(facts);
				reset();
				break;
			default:
				break;
		}
	}

	@SuppressWarnings("unchecked")
	private void write(Map<String, Object> facts) {
		Map<PLAYER, Integer> totals = (Map<PLAYER, Integer>) facts.get("totals");
		if (totals == null) {
			return;
		}
		int human = totals.get(PLAYER.SOUTH).intValue();
		int place = 1;
		for (PLAYER p : PLAYER.values()) {
			if (p != PLAYER.SOUTH && totals.get(p).intValue() < human) {
				place++;
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append(',')
				.append(hands).append(',').append(csv(String.valueOf(facts.get("winners")))).append(',')
				.append(place);
		for (PLAYER p : ORDER) {
			int[] c = counts.get(p);
			sb.append(',').append(csv(nameOf.apply(p))).append(',').append(totals.get(p))
					.append(',').append(c[TRIES]).append(',').append(c[MADE])
					.append(',').append(c[QUEENS]).append(',').append(c[JACKS]);
		}
		try {
			file.getParentFile().mkdirs();
			boolean fresh = !file.exists() || file.length() == 0;
			try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
				if (fresh) {
					out.println(header());
				}
				out.println(sb);
			}
		} catch (IOException e) {
			// A ledger failure must never interrupt play.
		}
	}

	static String header() {
		StringBuilder sb = new StringBuilder("date,hands,winners,human_place");
		for (PLAYER p : ORDER) {
			String s = p.name().toLowerCase();
			sb.append(',').append(s).append("_name,").append(s).append("_total,").append(s).append("_moon_tries,")
					.append(s).append("_moon_made,").append(s).append("_queens_eaten,").append(s).append("_jacks_taken");
		}
		return sb.toString();
	}

	private static String csv(String s) {
		return s == null ? "" : (s.indexOf(',') >= 0 || s.indexOf('"') >= 0)
				? '"' + s.replace("\"", "\"\"") + '"' : s;
	}
}
