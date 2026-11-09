package hearts_1.roster;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import hearts_1.Player.Player_Type;
import hearts_1.Profile;

/**
 * The persistent AI roster, added 2026-08-16 -- loads and saves
 * `roster.txt` per DESIGN_roster_and_trashtalk.md section 3.2: a small,
 * line-oriented, human-editable text format (deliberately NOT Java
 * serialization -- enum renames must never brick a save), a schema-version
 * line, a forgiving parser (unknown keys and unknown type names are
 * skipped with a logged warning, never fatal), and atomic
 * write-temp-then-rename saves so an interrupted write can't truncate a
 * good file.
 *
 * roster.txt lives in `%USERPROFILE%\.hearts2\` in real use (see
 * defaultDirectory()); every method here takes an explicit directory so
 * tests can point at a scratch folder instead. It holds only AUTHORED
 * data -- identities and the human profile -- kept in a separate file
 * from the generated stats (history.txt, see StatsStore) so a corrupt
 * history never costs the user their hand-edited roster.
 *
 * First run writes a starter roster of 10 players (AI_01..AI_10) with a
 * spread of the existing Player_Type presets -- the file is the user's to
 * edit from there (types, display names, or the human profile's name).
 */
public final class Roster {

	/** The human's stable stats identity -- never a roster member. */
	public static final String HUMAN_ID = "HUMAN";

	/** 2: `template` (was `type`) plus weight./style. overrides; step 2 of
	 * the AI players work added reliable./blunder and percentage styles
	 * without a version bump (older files simply don't use them). Version-1
	 * files load unchanged. */
	private static final int SCHEMA_VERSION = 2;
	private static final String FILE_NAME = "roster.txt";

	private final List<RosterPlayer> players = new ArrayList<RosterPlayer>();
	private String humanName = "Wayne";

	public static File defaultDirectory() {
		return new File(System.getProperty("user.home"), ".hearts2");
	}

	/** Loads the roster from `dir`, creating (and saving) the starter
	 * roster if no file exists yet. Never throws on a malformed file --
	 * every recoverable line is kept, everything else is warned and
	 * skipped, and a file that yields zero players falls back to the
	 * starter set. */
	public static Roster loadOrCreate(File dir) {
		Roster roster = new Roster();
		File file = new File(dir, FILE_NAME);
		if (file.isFile()) {
			roster.parse(file);
		}
		if (roster.players.isEmpty()) {
			roster.installStarterRoster();
			roster.save(dir);
		}
		return roster;
	}

	public List<RosterPlayer> players() {
		return new ArrayList<RosterPlayer>(players);
	}

	public String humanName() {
		return humanName;
	}

	public RosterPlayer byId(String id) {
		for (RosterPlayer p : players) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	// ---- starter content ----------------------------------------------

	/** 10 players spread across the existing presets. Deliberately skips
	 * BRAIN_DEAD (a debugging competence tier, not an opponent anyone
	 * would seat on purpose) but keeps one BAD for variety; the file is
	 * editable if a different mix is wanted.
	 *
	 * countsCardsPercent is ALSO spread deliberately (2026-08-16, user
	 * request) -- 4 players never count, the other 6 count at varying
	 * rates (25/50/75/100), independent of Player_Type: this ability is a
	 * per-INDIVIDUAL trait, not implied by personality. Purely a starting
	 * point; roster.txt is the user's to rebalance. */
	private void installStarterRoster() {
		players.clear();
		Player_Type[] spread = {
				Player_Type.NORMAL, Player_Type.CAUTIOUS, Player_Type.AGGRESSIVE,
				Player_Type.AVOIDS_QUEEN, Player_Type.AGGRESSIVE_AVOIDS_QUEEN,
				Player_Type.MOON_SHOOTER, Player_Type.NORMAL, Player_Type.AGGRESSIVE,
				Player_Type.CAUTIOUS, Player_Type.BAD };
		int[] countsCards = { 50, 75, 25, 0, 100, 50, 0, 75, 100, 0 };
		// Spread independently of both Player_Type and countsCards -- also a
		// per-individual trait, not implied by either. Zero for half the
		// roster (this is a rare, risky play; most individuals never try
		// it), the rest spread 20-80 so live play can see the standings
		// boost (Deal_Hand.effectiveMoonGamblePercent()) matter differently
		// for a low-base vs. high-base gambler.
		int[] moonGamble = { 0, 20, 0, 40, 0, 60, 0, 80, 0, 30 };
		for (int i = 0; i < spread.length; i++) {
			String id = String.format("AI_%02d", i + 1);
			players.add(new RosterPlayer(id, id, spread[i], countsCards[i], moonGamble[i]));
		}
	}

	// ---- persistence --------------------------------------------------

	private void parse(File file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("****** ROSTER unreadable (" + e.getMessage() + ") -- using starter roster ******");
			return;
		}
		String section = "";
		String id = null, name = null;
		Player_Type type = null;
		int countCardsPercent = 0;
		int moonGamblePercent = 0;
		int moonInterestPercent = -1;
		List<String[]> overrides = new ArrayList<String[]>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("[")) {
				flushPlayer(section, id, name, type, overrides, countCardsPercent, moonGamblePercent, moonInterestPercent);
				section = line;
				id = null;
				name = null;
				type = null;
				countCardsPercent = 0;
				moonGamblePercent = 0;
				moonInterestPercent = -1;
				overrides = new ArrayList<String[]>();
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				System.out.println("****** ROSTER skipping unparseable line: " + line + " ******");
				continue;
			}
			String key = line.substring(0, eq).trim();
			String value = line.substring(eq + 1).trim();
			if ("[human]".equals(section)) {
				if ("name".equals(key)) {
					humanName = value;
				} else if (!"id".equals(key)) {
					System.out.println("****** ROSTER skipping unknown human key: " + key + " ******");
				}
			} else if ("[player]".equals(section)) {
				if ("id".equals(key)) {
					id = value;
				} else if ("name".equals(key)) {
					name = value;
				} else if ("template".equals(key) || "type".equals(key)) {
					// "type" is the version-1 spelling; files written before
					// 2026-10-10 still say it.
					type = parseType(value);
				} else if (isProfileSetting(key)) {
					overrides.add(new String[] { key, value });
				} else if ("countCardsPercent".equals(key)) {
					countCardsPercent = parsePercent(value);
				} else if ("moonGamblePercent".equals(key)) {
					moonGamblePercent = parsePercent(value);
				} else if ("moonInterestPercent".equals(key)) {
					moonInterestPercent = parsePercent(value);
				} else {
					System.out.println("****** ROSTER skipping unknown player key: " + key + " ******");
				}
			} else if ("version".equals(key)) {
				// Schema version -- nothing to migrate yet; recorded for later.
			} else {
				System.out.println("****** ROSTER skipping key outside any section: " + key + " ******");
			}
		}
		flushPlayer(section, id, name, type, overrides, countCardsPercent, moonGamblePercent, moonInterestPercent);
	}

	private void flushPlayer(String section, String id, String name, Player_Type type,
			List<String[]> overrides, int countCardsPercent, int moonGamblePercent, int moonInterestPercent) {
		if (!"[player]".equals(section) || id == null) {
			return;
		}
		Profile profile = Profile.of(type == null ? Player_Type.NORMAL : type);
		for (String[] kv : overrides) {
			profile = applyOverride(profile, kv[0], kv[1], id);
		}
		// -1 = the file does not say: the level's default applies (2026-10-17).
		int interest = moonInterestPercent >= 0 ? moonInterestPercent
				: RosterPlayer.defaultMoonInterest(profile.level);
		players.add(new RosterPlayer(id, name == null ? id : name, profile,
				countCardsPercent, moonGamblePercent, interest));
	}

	private static boolean isProfileSetting(String key) {
		return key.startsWith(Profile.WEIGHT_PREFIX) || key.startsWith(Profile.STYLE_PREFIX)
				|| key.startsWith(Profile.RELIABLE_PREFIX) || Profile.BLUNDER_KEY.equals(key)
				|| Profile.PAYBACK_KEY.equals(key) || key.startsWith(Profile.STRATEGY_PREFIX)
				|| Profile.WILDNESS_KEY.equals(key) || Profile.OPENING_WILDNESS_KEY.equals(key)
				|| key.startsWith(Profile.STRAY_PREFIX) || Profile.OPENING_HANDS_KEY.equals(key)
				|| Profile.LEADING_KEY.equals(key) || Profile.LOSING_KEY.equals(key)
				|| Profile.LEVEL_KEY.equals(key) || Profile.MOON_LOOK_KEY.equals(key)
				|| Profile.MOON_OPTIMISM_KEY.equals(key) || Profile.MOON_HEART_LAST_KEY.equals(key);
	}

	/** One profile line (see Profile.withSetting for the keys). Anything
	 * invalid is warned and skipped -- the player still loads with the rest
	 * of its profile. */
	static Profile applyOverride(Profile profile, String key, String value, String id) {
		try {
			return profile.withSetting(key, value);
		} catch (IllegalArgumentException e) {
			System.out.println("****** ROSTER " + id + ": skipping override '" + key + " = " + value
					+ "' (" + e.getMessage() + ") ******");
			return profile;
		}
	}

	/** Clamped to 0-100 and defaults to 0 (never) on anything unparseable --
	 * a hand-edited roster.txt with a typo should degrade to "doesn't
	 * count," not crash the load. */
	private static int parsePercent(String value) {
		try {
			int p = Integer.parseInt(value.trim());
			return Math.max(0, Math.min(100, p));
		} catch (NumberFormatException e) {
			System.out.println("****** ROSTER invalid countCardsPercent '" + value + "' -- using 0 ******");
			return 0;
		}
	}

	/** Unknown type names (a renamed or future enum constant) fall back to
	 * NORMAL with a warning rather than dropping the player -- design doc
	 * section 3.2's "a save file from three sessions ago must still load
	 * after the enum has moved on." */
	private static Player_Type parseType(String value) {
		try {
			return Player_Type.valueOf(value);
		} catch (IllegalArgumentException e) {
			System.out.println("****** ROSTER unknown type '" + value + "' -- falling back to NORMAL ******");
			return null;
		}
	}

	public void save(File dir) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Hearts_2 roster -- authored identities. Edit freely; the game\n");
		sb.append("# never rewrites this file except to create it on first run.\n");
		sb.append("#\n");
		sb.append("# Each player starts from a template (NORMAL, CAUTIOUS, AGGRESSIVE,\n");
		sb.append("# AVOIDS_QUEEN, AGGRESSIVE_AVOIDS_QUEEN, MOON_SHOOTER, BAD, BRAIN_DEAD)\n");
		sb.append("# and may override any of it:\n");
		sb.append("#   weight.<FAMILY> = 0..1.5   (0 doesn't know it, 1 full use, 1.5 overvalues)\n");
		sb.append("#     families: BASIC_SAFETY VOID_AWARENESS JACK_GUARD JACK_HUNT\n");
		sb.append("#       COUNTED_CERTAINTIES QUEEN_FLUSH MOON_OFFENSE MOON_DEFENSE\n");
		sb.append("#       PASSING_CRAFT END_GAME SOCIAL\n");
		sb.append("#   style.<Style> = on|off|0..100   (Aggressive, Avoids_Queen, Shooting_the_Moon;\n");
		sb.append("#     a percentage = that style on about that share of hands)\n");
		sb.append("#   reliable.<SKILL or FAMILY> = 0..100   (how often the skill is used, rolled each\n");
		sb.append("#     hand; skills: SAFETY JACK QUEEN MOON PASSING STANDINGS)\n");
		sb.append("#   level = 1..5   (how much reasoning: 1 beginner, panics .. 5 expert; default 3;\n");
		sb.append("#     4-5 = expert: blunder capped at 2%/1%, slips stay small, no strays into leaks)\n");
		sb.append("#   blunder = 0..100   (chance per card of playing the 2nd or 3rd best card)\n");
		sb.append("#   payback = on|off|100,60,25   (Queen payback: grudge strength for the next\n");
		sb.append("#     three hands against whoever stuck this player with the Queen)\n");
		sb.append("#   strategy.<COMPONENT> = <OPTION>   (which way this player makes a decision):\n");
		for (hearts_1.Strategy c : hearts_1.Strategy.values()) {
			sb.append("#     ").append(c.name()).append(':');
			for (Enum<?> o : c.options()) {
				sb.append(' ').append(o.name()).append(o == c.defaultOption ? "(default)" : "");
			}
			sb.append('\n');
		}
		sb.append("#   wildness = 0..100   (chance each hand of playing a component some other way)\n");
		sb.append("#   wildness.opening = 0..100   (added during the first hands of each game)\n");
		sb.append("#   wildness.openingHands = 0..20   (how many first hands; default 3)\n");
		sb.append("#   wildness.leading = 0..300   (% of wildness while leading the game; default 50)\n");
		sb.append("#   wildness.losing = 0..300   (% of wildness while in last place; default 150)\n");
		sb.append("#   stray.<COMPONENT> = OPTION weight, OPTION weight   (where it strays, e.g.\n");
		sb.append("#     stray.KIT_PASS = FREE 2, HOLD_BACK 1; without it, evenly among the others)\n");
		sb.append("version = ").append(SCHEMA_VERSION).append('\n');
		sb.append('\n');
		sb.append("[human]\n");
		sb.append("id = ").append(HUMAN_ID).append('\n');
		sb.append("name = ").append(humanName).append('\n');
		for (RosterPlayer p : players) {
			sb.append('\n');
			sb.append("[player]\n");
			sb.append("id = ").append(p.id).append('\n');
			sb.append("name = ").append(p.displayName).append('\n');
			sb.append("template = ").append(p.type.name()).append('\n');
			for (String[] kv : p.profile.settings()) {
				sb.append(kv[0]).append(" = ").append(kv[1]).append('\n');
			}
			sb.append("countCardsPercent = ").append(p.countCardsPercent).append('\n');
			sb.append("moonGamblePercent = ").append(p.moonGamblePercent).append('\n');
			sb.append("moonInterestPercent = ").append(p.moonInterestPercent).append('\n');
		}
		atomicWrite(new File(dir, FILE_NAME), sb.toString());
	}

	/** Write to a temp file in the same directory, then rename over the
	 * target -- an interrupted write can never truncate the existing good
	 * file. Shared with StatsStore. */
	static void atomicWrite(File target, String content) {
		try {
			File dir = target.getParentFile();
			if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
				System.out.println("****** ROSTER cannot create " + dir + " -- save skipped ******");
				return;
			}
			File tmp = File.createTempFile(target.getName(), ".tmp", dir);
			Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			System.out.println("****** ROSTER save failed (" + e.getMessage() + ") ******");
		}
	}
}
