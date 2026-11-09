package hearts_1;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

import hearts_1.Hearts_1.PLAYER;
import hearts_1.events.GameEvent;
import hearts_1.events.GameEventBus;
import hearts_1.events.GameLedger;

/** 2026-10-17: one CSV row per finished game, with moon tries and Queen/Jack counts. */
public final class GameLedgerTest {

	private GameLedgerTest() { }

	public static void main(String[] args) {
		System.exit(run() ? 0 : 1);
	}

	public static boolean run() {
		TestKit.reset();
		TestKit.suite("oneRowPerFinishedGame");
		try {
			File f = File.createTempFile("ledger", ".csv");
			f.delete();
			GameLedger ledger = new GameLedger(f, seat -> seat.name().substring(0, 1));
			ledger.onEvent(GameEvent.MOON_COMMITTED, GameEventBus.facts().with("seat", PLAYER.WEST).build());
			ledger.onEvent(GameEvent.QUEEN_DROPPED_ON, GameEventBus.facts().with("winner", PLAYER.EAST).build());
			ledger.onEvent(GameEvent.JACK_CAPTURED, GameEventBus.facts().with("winner", PLAYER.SOUTH).build());
			ledger.onEvent(GameEvent.HAND_ENDED, GameEventBus.facts().build());
			ledger.onEvent(GameEvent.HAND_ENDED, GameEventBus.facts().build());
			Map<PLAYER, Integer> totals = new EnumMap<PLAYER, Integer>(PLAYER.class);
			totals.put(PLAYER.NORTH, 61);
			totals.put(PLAYER.EAST, 102);
			totals.put(PLAYER.SOUTH, 17);
			totals.put(PLAYER.WEST, 60);
			ledger.onEvent(GameEvent.GAME_ENDED,
					GameEventBus.facts().with("winners", "SOUTH").with("totals", totals).build());
			// A second game starts from zero.
			ledger.onEvent(GameEvent.HAND_ENDED, GameEventBus.facts().build());
			ledger.onEvent(GameEvent.GAME_ENDED,
					GameEventBus.facts().with("winners", "NORTH").with("totals", totals).build());
			String[] lines = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split("\\R");
			TestKit.checkEquals("header + two rows", 3, lines.length);
			TestKit.check("header names the columns", lines[0].startsWith("date,hands,winners,human_place,north_name"));
			String[] row = lines[1].split(",");
			TestKit.checkEquals("hands counted", "2", row[1]);
			TestKit.checkEquals("winner recorded", "SOUTH", row[2]);
			TestKit.checkEquals("human won = place 1", "1", row[3]);
			// north(name,total,tries,made,queens,jacks) = 4..9; east 10..15; south 16..21; west 22..27
			TestKit.checkEquals("west tried the moon once", "1", row[22 + 2]);
			TestKit.checkEquals("east ate one Queen", "1", row[10 + 4]);
			TestKit.checkEquals("south took one Jack", "1", row[16 + 5]);
			String[] second = lines[2].split(",");
			TestKit.checkEquals("second game counts start fresh", "1", second[1]);
			TestKit.checkEquals("no leftover moon tries", "0", second[22 + 2]);
			f.delete();
		} catch (java.io.IOException e) {
			TestKit.check("no IOException: " + e, false);
		}
		return TestKit.summarize();
	}
}
