package hearts_1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import hearts_1.Player.Player_Traits;
import hearts_1.Player.Player_Type;
import hearts_1.TacticWeights.Tactic;

/**
 * One AI player's playing profile, added 2026-10-10 -- step 1 of the AI
 * players work (user: ten different AIs, each a blend of the tactics "at
 * our choice", some with random traits, some that get better over time).
 *
 * TEMPLATES + OVERRIDES (user-approved design): a profile starts from one
 * of the eight Player_Type presets -- its tactic-weight row and its style
 * flags -- and then overrides only what differs ("Aggressive, but values
 * the Jack hunt at 1.5"). Every AI therefore starts from measured, tested
 * play, and roster.txt only spells out the differences. The presets are
 * unchanged: Profile.of(type) reproduces each one exactly (proven by
 * replaying seeded hands card-for-card against the pre-profile build).
 *
 * Settings:
 *   - a WEIGHT per tactic family (TacticWeights.Tactic, except CORE which
 *     is always 1): 0 = doesn't know it, 0.5 undervalues, 1 full use, 1.5
 *     overvalues. Allowed range 0..MAX_WEIGHT.
 *   - STYLE flags that change HOW the AI plays rather than what it knows:
 *     Aggressive, Avoids_Queen, Shooting_the_Moon (OVERRIDABLE_STYLES).
 *     Competence tiers (Bad, Brain_Dead) come only from the template.
 *
 * Step 2 (2026-10-10, user-approved picks) adds three per-hand dials, all
 * defaulting to "exactly as before":
 *   - RELIABILITY per family, 0-100: how often the AI USES a skill it
 *     knows. Rolled once per hand (Player.rollHand) -- a 60 means the skill
 *     is in play on about 6 hands in 10 and absent on the rest, like a
 *     person who knows the trick but doesn't always think of it. Settable
 *     per Skill group or per family. COUNTED_CERTAINTIES is excluded: its
 *     per-hand roll already exists as countCardsPercent.
 *   - MOODS: a style may be a percentage instead of on/off -- Aggressive 40
 *     means aggressive on about 4 hands in 10.
 *   - BLUNDER rate, 0-100: the chance per card that the AI makes a human
 *     mistake -- a NEAR MISS, its 2nd or 3rd best card, never a wild one
 *     (see AICardScorer.nearMiss). Bad keeps its own random 1-in-3.
 *
 * QUEEN PAYBACK (2026-10-10, see Payback.java): off by default; when on,
 * the per-hand taper of the grudge against whoever stuck this player with
 * the Queen -- e.g. 100,60,25 for the next three hands.
 *
 * STRATEGY (2026-10-11, see Strategy.java): one option per strategy
 * component -- which way this player makes each decision. Defaults are the
 * measured-best rules; the others are older or alternative rules kept so
 * AIs can differ.
 *
 * WILDNESS (2026-10-11, see Wild): how often this player strays from its
 * usual strategy options, hand by hand.
 *
 * Immutable: the with...() methods return a new profile.
 */
public final class Profile {

	public static final double MAX_WEIGHT = 1.5;

	/** Style flags a roster entry may switch on or off. */
	public static final EnumSet<Player_Traits> OVERRIDABLE_STYLES =
			EnumSet.of(Player_Traits.Aggressive, Player_Traits.Avoids_Queen, Player_Traits.Shooting_the_Moon);

	/** The grouped reliability knobs (user pick: a few knobs, with any
	 * single family still settable on its own). */
	public enum Skill {
		SAFETY(Tactic.BASIC_SAFETY, Tactic.VOID_AWARENESS),
		JACK(Tactic.JACK_GUARD, Tactic.JACK_HUNT),
		QUEEN(Tactic.QUEEN_FLUSH),
		MOON(Tactic.MOON_OFFENSE, Tactic.MOON_DEFENSE),
		PASSING(Tactic.PASSING_CRAFT),
		STANDINGS(Tactic.END_GAME, Tactic.SOCIAL);

		public final EnumSet<Tactic> families;

		private Skill(Tactic first, Tactic... rest) {
			this.families = EnumSet.of(first, rest);
		}
	}

	/** Roster keys -- parsed by withSetting(), written by settings(). */
	public static final String WEIGHT_PREFIX = "weight.";
	public static final String STYLE_PREFIX = "style.";
	public static final String RELIABLE_PREFIX = "reliable.";
	public static final String BLUNDER_KEY = "blunder";
	public static final String PAYBACK_KEY = "payback";
	public static final String STRATEGY_PREFIX = "strategy.";
	public static final String WILDNESS_KEY = "wildness";
	public static final String OPENING_WILDNESS_KEY = "wildness.opening";
	public static final String STRAY_PREFIX = "stray.";
	public static final String OPENING_HANDS_KEY = "wildness.openingHands";
	public static final String LEADING_KEY = "wildness.leading";
	public static final String LOSING_KEY = "wildness.losing";

	public final Player_Type template;
	private final Map<Tactic, Double> weights;
	private final EnumSet<Player_Traits> traits;
	/** Families used on fewer than 100% of hands. Absent = 100. */
	private final Map<Tactic, Integer> reliability;
	/** Styles held on some hands only (1..99). A style in here is never
	 * also in `traits`. */
	private final Map<Player_Traits, Integer> moods;
	public final int blunderPercent;
	/**
	 * PLAYER LEVEL (2026-10-17, user design): how much REASONING this player does,
	 * 1 = beginner .. 5 = expert. Separate from what it knows (weights), how
	 * reliably it uses it, its blunder rate and its wildness: a nervous low-level
	 * player panics early (e.g. grabs a point trick the moment it fears a moon),
	 * an advanced one weighs whether a play is needed at all. Features that want
	 * to know "is this an advanced player" read this one setting. Roster key
	 * "level"; unset = DEFAULT_LEVEL.
	 */
	public final int level;
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 5;
	public static final int DEFAULT_LEVEL = 3;
	public static final String LEVEL_KEY = "level";
	/** Level 4 and up play as experts (user rule, 2026-10-17): a capped blunder
	 * rate, blunders only ever the 2nd-best card and never costly, and no strays
	 * into a measured leak (Strategy.isWeak). */
	public static final int EXPERT_LEVEL = 4;

	/**
	 * MOON LOOK (2026-11-08, user design from Wayne's live moon tries): the way
	 * a real, optimistic player looks at a hand with a remote chance of a moon,
	 * which the heart-length rule in HandEvaluator never sees. Pass the low
	 * Hearts and keep the top ones, run a long side suit headed by the Ace,
	 * hold the Spade control or the Queen, and decide once the three passed-in
	 * cards are seen (HandEvaluator.sideSuitMoonScore).
	 *
	 * moonLookPercent (roster key "moonLook", 0..100, default 0 = never): the
	 * chance each hand that this player gives its hand that look. 0 draws no
	 * random number, so a roster without it plays exactly as before.
	 * moonOptimism (roster key "moonOptimism", 0..100, default 50): how remote
	 * a chance is still worth a try; higher means more hands qualify
	 * (HandEvaluator.sideSuitMoonBar). Does nothing while moonLook is 0.
	 */
	public final int moonLookPercent;
	public final int moonOptimism;
	public static final String MOON_LOOK_KEY = "moonLook";
	public static final String MOON_OPTIMISM_KEY = "moonOptimism";
	public static final int DEFAULT_MOON_OPTIMISM = 50;

	/**
	 * moonHeartLast (roster key "moonHeartLast", 0..100, default 100; Wayne,
	 * 2026-11-08): the chance each hand that a look shooter holding the A K of
	 * Hearts (two or more sure-winner Hearts) plays its extra Heart LAST --
	 * side-suit winners first, then the A K late, then the extra Heart, hoping
	 * nobody has a Heart left to beat it. Otherwise it tests the extra Heart
	 * early, as with a lone Heart. Measured on Wayne's A K 10 deal: testing
	 * early made more moons against alarmed AI defenders (44 vs 28 of 120),
	 * holding last is how Wayne plays it. Default 100 keeps the built behavior
	 * and draws no random number (also at 0).
	 */
	public final int moonHeartLast;
	public static final String MOON_HEART_LAST_KEY = "moonHeartLast";
	public static final int DEFAULT_MOON_HEART_LAST = 100;

	public Profile withMoonHeartLast(int percent) {
		checkPercent(percent);
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, strategies, wild,
				level, moonLookPercent, moonOptimism, percent);
	}

	public Profile withMoonLook(int percent) {
		checkPercent(percent);
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, strategies, wild,
				level, percent, moonOptimism, moonHeartLast);
	}

	public Profile withMoonOptimism(int percent) {
		checkPercent(percent);
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, strategies, wild,
				level, moonLookPercent, percent, moonHeartLast);
	}

	public boolean isExpert() {
		return level >= EXPERT_LEVEL;
	}

	/** The blunder rate actually used: the roster's rate, capped for experts at
	 * 2% (level 4) and 1% (level 5). */
	public int effectiveBlunderPercent() {
		int cap = level >= 5 ? 1 : level == 4 ? 2 : 100;
		return Math.min(blunderPercent, cap);
	}
	/** Queen payback taper, or null when this player doesn't hold grudges. */
	final int[] paybackTaper;
	/** Strategy options that differ from each component's default. */
	private final Map<Strategy, Enum<?>> strategies;
	/** How often this player strays from its usual strategy, and where to. */
	final Wild wild;

	/**
	 * WILDNESS (2026-10-11, user design): each hand, each strategy component
	 * has a chance of being played some other way than usual -- a person who
	 * doesn't always play the same. `base` is that chance (0-100); `opening`
	 * is added in the first `openingHands` hands of each game (top players
	 * feel out the table, then settle); Player.rollHand scales the total by
	 * standings -- `leadingPct` percent of it while leading the game,
	 * `losingPct` while in last place (user: wilder when losing, steadier
	 * when winning). All per-AI (user, 2026-10-11). Where it strays:
	 * `stray` (relative weights per component) if given, otherwise evenly
	 * among the component's other options. Immutable.
	 */
	static final class Wild {
		static final int DEFAULT_OPENING_HANDS = 3;
		static final int DEFAULT_LEADING_PCT = 50;
		static final int DEFAULT_LOSING_PCT = 150;
		/** Upper bounds for the roster's sanity check. */
		static final int MAX_OPENING_HANDS = 20;
		static final int MAX_STANDINGS_PCT = 300;

		static final Wild NONE = new Wild(0, 0, DEFAULT_OPENING_HANDS, DEFAULT_LEADING_PCT, DEFAULT_LOSING_PCT,
				new EnumMap<Strategy, Map<Enum<?>, Integer>>(Strategy.class));

		final int base;
		final int opening;
		final int openingHands;
		final int leadingPct;
		final int losingPct;
		final Map<Strategy, Map<Enum<?>, Integer>> stray;

		Wild(int base, int opening, int openingHands, int leadingPct, int losingPct,
				Map<Strategy, Map<Enum<?>, Integer>> stray) {
			this.base = base;
			this.opening = opening;
			this.openingHands = openingHands;
			this.leadingPct = leadingPct;
			this.losingPct = losingPct;
			this.stray = stray;
		}

		boolean isOff() {
			return base == 0 && opening == 0;
		}
	}

	private Profile(Player_Type template, Map<Tactic, Double> weights, EnumSet<Player_Traits> traits,
			Map<Tactic, Integer> reliability, Map<Player_Traits, Integer> moods, int blunderPercent,
			int[] paybackTaper, Map<Strategy, Enum<?>> strategies, Wild wild, int level, int moonLookPercent,
			int moonOptimism, int moonHeartLast) {
		this.level = level;
		this.moonHeartLast = moonHeartLast;
		this.moonLookPercent = moonLookPercent;
		this.moonOptimism = moonOptimism;
		this.template = template;
		this.weights = weights;
		this.traits = traits;
		this.reliability = reliability;
		this.moods = moods;
		this.blunderPercent = blunderPercent;
		this.paybackTaper = paybackTaper;
		this.strategies = strategies;
		this.wild = wild;
	}

	/** Exactly the preset: its weight row and its traits, no overrides. */
	public static Profile of(Player_Type template) {
		return new Profile(template, TacticWeights.templateRow(template), template.traits(),
				new EnumMap<Tactic, Integer>(Tactic.class), new EnumMap<Player_Traits, Integer>(Player_Traits.class), 0, null,
				new EnumMap<Strategy, Enum<?>>(Strategy.class), Wild.NONE, DEFAULT_LEVEL, 0, DEFAULT_MOON_OPTIMISM,
				DEFAULT_MOON_HEART_LAST);
	}

	/** This family's weight. CORE is always 1. */
	public double weight(Tactic tactic) {
		if (tactic == Tactic.CORE) {
			return 1.0;
		}
		Double w = weights.get(tactic);
		return w == null ? 1.0 : w.doubleValue();
	}

	/** How often (0-100) this family is in play on a hand. */
	public int reliability(Tactic tactic) {
		Integer r = reliability.get(tactic);
		return r == null ? 100 : r.intValue();
	}

	/** How often (0-100) this style is in play on a hand. */
	public int stylePercent(Player_Traits style) {
		Integer m = moods.get(style);
		if (m != null) {
			return m.intValue();
		}
		return traits.contains(style) ? 100 : 0;
	}

	/** Fresh copy -- callers load it into a live player's p_traits. Holds
	 * only the ALWAYS-on styles; moods are added per hand by rollHand. */
	public EnumSet<Player_Traits> traits() {
		return EnumSet.copyOf(traits);
	}

	/**
	 * This hand's dice: which families sit out, and which mood styles are
	 * on. Draws from `rng` only for a setting strictly between 0 and 100, so
	 * a profile with no step-2 settings consumes no random numbers and
	 * replays every seeded game exactly as before.
	 */
	void rollHand(Random rng, EnumSet<Tactic> skipped, EnumSet<Player_Traits> liveTraits) {
		skipped.clear();
		for (Map.Entry<Tactic, Integer> e : reliability.entrySet()) {
			int pct = e.getValue().intValue();
			if (pct <= 0 || (pct < 100 && rng.nextInt(100) >= pct)) {
				skipped.add(e.getKey());
			}
		}
		for (Map.Entry<Player_Traits, Integer> e : moods.entrySet()) {
			if (rng.nextInt(100) < e.getValue().intValue()) {
				liveTraits.add(e.getKey());
			} else {
				liveTraits.remove(e.getKey());
			}
		}
	}

	public Profile withWeight(Tactic tactic, double weight) {
		if (tactic == Tactic.CORE) {
			throw new IllegalArgumentException("CORE is always 1 and cannot be overridden");
		}
		if (weight < 0 || weight > MAX_WEIGHT) {
			throw new IllegalArgumentException("weight " + weight + " outside 0.." + MAX_WEIGHT);
		}
		Map<Tactic, Double> w = new EnumMap<Tactic, Double>(weights);
		w.put(tactic, Double.valueOf(weight));
		return new Profile(template, w, traits, reliability, moods, blunderPercent, paybackTaper, strategies, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	public Profile withStyle(Player_Traits style, boolean on) {
		return withStylePercent(style, on ? 100 : 0);
	}

	/** 0 = never, 100 = always, anything between = a per-hand mood. */
	public Profile withStylePercent(Player_Traits style, int percent) {
		if (!OVERRIDABLE_STYLES.contains(style)) {
			throw new IllegalArgumentException(style + " is not an overridable style");
		}
		checkPercent(percent);
		EnumSet<Player_Traits> t = EnumSet.copyOf(traits);
		Map<Player_Traits, Integer> m = new EnumMap<Player_Traits, Integer>(Player_Traits.class);
		m.putAll(moods);
		t.remove(style);
		m.remove(style);
		if (percent == 100) {
			t.add(style);
		} else if (percent > 0) {
			m.put(style, Integer.valueOf(percent));
		}
		return new Profile(template, weights, t, reliability, m, blunderPercent, paybackTaper, strategies, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	public Profile withReliability(Tactic tactic, int percent) {
		if (tactic == Tactic.CORE) {
			throw new IllegalArgumentException("CORE is always in play");
		}
		if (tactic == Tactic.COUNTED_CERTAINTIES) {
			throw new IllegalArgumentException("use countCardsPercent for COUNTED_CERTAINTIES");
		}
		checkPercent(percent);
		Map<Tactic, Integer> r = new EnumMap<Tactic, Integer>(Tactic.class);
		r.putAll(reliability);
		if (percent == 100) {
			r.remove(tactic);
		} else {
			r.put(tactic, Integer.valueOf(percent));
		}
		return new Profile(template, weights, traits, r, moods, blunderPercent, paybackTaper, strategies, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	public Profile withReliability(Skill skill, int percent) {
		Profile p = this;
		for (Tactic t : skill.families) {
			p = p.withReliability(t, percent);
		}
		return p;
	}

	/** Player level 1..5 (see {@link #level}). */
	public Profile withLevel(int newLevel) {
		if (newLevel < MIN_LEVEL || newLevel > MAX_LEVEL) {
			throw new IllegalArgumentException("level must be " + MIN_LEVEL + ".." + MAX_LEVEL);
		}
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, strategies, wild,
				newLevel, moonLookPercent, moonOptimism, moonHeartLast);
	}

	public Profile withBlunder(int percent) {
		checkPercent(percent);
		return new Profile(template, weights, traits, reliability, moods, percent, paybackTaper, strategies, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	/** Queen payback on with this taper (each 0..100, never rising from one
	 * hand to the next), or off with null. */
	public Profile withPayback(int[] taper) {
		if (taper != null) {
			if (taper.length != 3) {
				throw new IllegalArgumentException("payback needs three numbers, one per hand");
			}
			for (int i = 0; i < 3; i++) {
				checkPercent(taper[i]);
				if (i > 0 && taper[i] > taper[i - 1]) {
					throw new IllegalArgumentException("payback must taper: each hand no higher than the one before");
				}
			}
			taper = taper.clone();
		}
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, taper, strategies, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	/** This profile's payback taper (a copy), or null when it's off. */
	public int[] paybackTaper() {
		return paybackTaper == null ? null : paybackTaper.clone();
	}

	/** This profile's option for a strategy component. */
	public Enum<?> strategy(Strategy component) {
		Enum<?> o = strategies.get(component);
		return o == null ? component.defaultOption : o;
	}

	/** Does this profile play `option`? e.g. uses(Strategy.KitPass.FREE). */
	public boolean uses(Enum<?> option) {
		return strategy(Strategy.of(option)) == option;
	}

	public Profile withStrategy(Enum<?> option) {
		Strategy component = Strategy.of(option);
		Map<Strategy, Enum<?>> m = new EnumMap<Strategy, Enum<?>>(Strategy.class);
		m.putAll(strategies);
		if (option == component.defaultOption) {
			m.remove(component);
		} else {
			m.put(component, option);
		}
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, m, wild, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	/** Base chance (0-100) per component per hand of straying from usual. */
	public Profile withWildness(int percent) {
		checkPercent(percent);
		return withWild(new Wild(percent, wild.opening, wild.openingHands, wild.leadingPct, wild.losingPct, wild.stray));
	}

	/** Extra wildness in the first few hands of each game. */
	public Profile withOpeningWildness(int percent) {
		checkPercent(percent);
		return withWild(new Wild(wild.base, percent, wild.openingHands, wild.leadingPct, wild.losingPct, wild.stray));
	}

	/** How many hands at the start of each game get the opening wildness. */
	public Profile withOpeningHands(int hands) {
		if (hands < 0 || hands > Wild.MAX_OPENING_HANDS) {
			throw new IllegalArgumentException("opening hands " + hands + " outside 0.." + Wild.MAX_OPENING_HANDS);
		}
		return withWild(new Wild(wild.base, wild.opening, hands, wild.leadingPct, wild.losingPct, wild.stray));
	}

	/** Wildness while leading / while in last place, as a percent of
	 * normal (50 = half as wild, 150 = half again). */
	public Profile withStandingsWildness(int leadingPct, int losingPct) {
		checkStandingsPct(leadingPct);
		checkStandingsPct(losingPct);
		return withWild(new Wild(wild.base, wild.opening, wild.openingHands, leadingPct, losingPct, wild.stray));
	}

	private static void checkStandingsPct(int pct) {
		if (pct < 0 || pct > Wild.MAX_STANDINGS_PCT) {
			throw new IllegalArgumentException("standings wildness " + pct + "% outside 0.." + Wild.MAX_STANDINGS_PCT);
		}
	}

	public int openingHands() {
		return wild.openingHands;
	}

	public int leadingWildnessPct() {
		return wild.leadingPct;
	}

	public int losingWildnessPct() {
		return wild.losingPct;
	}

	/** Where this player tends to go when it strays on `component`:
	 * relative weights (any positive numbers), or null for "evenly among the
	 * other options". */
	public Profile withStray(Strategy component, Map<Enum<?>, Integer> weights) {
		Map<Strategy, Map<Enum<?>, Integer>> m = new EnumMap<Strategy, Map<Enum<?>, Integer>>(Strategy.class);
		m.putAll(wild.stray);
		if (weights == null || weights.isEmpty()) {
			m.remove(component);
		} else {
			Map<Enum<?>, Integer> copy = new LinkedHashMap<Enum<?>, Integer>();
			for (Map.Entry<Enum<?>, Integer> e : weights.entrySet()) {
				if (Strategy.of(e.getKey()) != component) {
					throw new IllegalArgumentException(e.getKey() + " is not a " + component + " option");
				}
				if (e.getValue().intValue() <= 0) {
					throw new IllegalArgumentException("stray weights must be positive");
				}
				copy.put(e.getKey(), e.getValue());
			}
			m.put(component, Collections.unmodifiableMap(copy));
		}
		return withWild(new Wild(wild.base, wild.opening, wild.openingHands, wild.leadingPct, wild.losingPct, m));
	}

	private Profile withWild(Wild w) {
		return new Profile(template, weights, traits, reliability, moods, blunderPercent, paybackTaper, strategies, w, level, moonLookPercent, moonOptimism, moonHeartLast);
	}

	public int wildness() {
		return wild.base;
	}

	public int openingWildness() {
		return wild.opening;
	}

	/**
	 * Where this player strays on `component` this hand, given that it does
	 * stray: from its stray weights if set, otherwise evenly among the
	 * options other than its usual one. Draws one number from `rng`.
	 */
	Enum<?> strayOption(Strategy component, Random rng) {
		Map<Enum<?>, Integer> weights = wild.stray.get(component);
		if (weights != null) {
			int total = 0;
			for (Integer w : weights.values()) {
				total += w.intValue();
			}
			int r = rng.nextInt(total);
			for (Map.Entry<Enum<?>, Integer> e : weights.entrySet()) {
				r -= e.getValue().intValue();
				if (r < 0) {
					return e.getKey();
				}
			}
		}
		Enum<?> usual = strategy(component);
		List<Enum<?>> others = new ArrayList<Enum<?>>();
		for (Enum<?> o : component.options()) {
			if (o != usual) {
				others.add(o);
			}
		}
		return others.get(rng.nextInt(others.size()));
	}

	private static void checkPercent(int percent) {
		if (percent < 0 || percent > 100) {
			throw new IllegalArgumentException("percent " + percent + " outside 0..100");
		}
	}

	/**
	 * Applies one roster line. Keys:
	 *   weight.<FAMILY> = 0..1.5
	 *   style.<Style>   = on | off | 0..100
	 *   reliable.<SKILL or FAMILY> = 0..100
	 *   blunder         = 0..100
	 *   moonLook        = 0..100   (chance per hand of the side-suit moon look)
	 *   moonOptimism    = 0..100   (how remote a moon chance still gets a try; default 50)
	 *   moonHeartLast   = 0..100   (with the A K of Hearts, chance to play the extra Heart last; default 100)
	 *   payback         = on | off | a,b,c   ("on" = the template's taper)
	 *   strategy.<COMPONENT> = one of that component's options (Strategy)
	 *   wildness         = 0..100   (chance per component per hand to stray)
	 *   wildness.opening = 0..100   (added in the first hands of each game)
	 *   wildness.openingHands = 0..20   (how many first hands; default 3)
	 *   wildness.leading = 0..300   (% of wildness while leading; default 50)
	 *   wildness.losing  = 0..300   (% of wildness in last place; default 150)
	 *   stray.<COMPONENT> = OPTION weight, OPTION weight ...   (where it strays)
	 * Throws IllegalArgumentException on anything invalid; the roster warns
	 * and skips the line.
	 */
	public Profile withSetting(String key, String value) {
		String v = value.trim();
		if (key.startsWith(WEIGHT_PREFIX)) {
			return withWeight(Tactic.valueOf(key.substring(WEIGHT_PREFIX.length())), Double.parseDouble(v));
		}
		if (key.startsWith(STYLE_PREFIX)) {
			Player_Traits s = Player_Traits.valueOf(key.substring(STYLE_PREFIX.length()));
			if ("on".equalsIgnoreCase(v)) {
				return withStyle(s, true);
			}
			if ("off".equalsIgnoreCase(v)) {
				return withStyle(s, false);
			}
			return withStylePercent(s, parsePercent(v, "style value must be on, off or 0..100"));
		}
		if (key.startsWith(RELIABLE_PREFIX)) {
			String name = key.substring(RELIABLE_PREFIX.length());
			int pct = parsePercent(v, "reliability must be 0..100");
			for (Skill s : Skill.values()) {
				if (s.name().equals(name)) {
					return withReliability(s, pct);
				}
			}
			return withReliability(Tactic.valueOf(name), pct);
		}
		if (BLUNDER_KEY.equals(key)) {
			return withBlunder(parsePercent(v, "blunder must be 0..100"));
		}
		if (MOON_LOOK_KEY.equals(key)) {
			return withMoonLook(parsePercent(v, "moonLook must be 0..100"));
		}
		if (MOON_OPTIMISM_KEY.equals(key)) {
			return withMoonOptimism(parsePercent(v, "moonOptimism must be 0..100"));
		}
		if (MOON_HEART_LAST_KEY.equals(key)) {
			return withMoonHeartLast(parsePercent(v, "moonHeartLast must be 0..100"));
		}
		if (LEVEL_KEY.equals(key)) {
			return withLevel(parsePercent(v, "level must be 1..5"));
		}
		if (PAYBACK_KEY.equals(key)) {
			if ("off".equalsIgnoreCase(v)) {
				return withPayback(null);
			}
			if ("on".equalsIgnoreCase(v)) {
				return withPayback(Payback.defaultTaper(this));
			}
			String[] parts = v.split(",");
			int[] taper = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				taper[i] = parsePercent(parts[i].trim(), "payback must be on, off or three numbers like 100,60,25");
			}
			return withPayback(taper);
		}
		if (WILDNESS_KEY.equals(key)) {
			return withWildness(parsePercent(v, "wildness must be 0..100"));
		}
		if (OPENING_WILDNESS_KEY.equals(key)) {
			return withOpeningWildness(parsePercent(v, "wildness.opening must be 0..100"));
		}
		if (OPENING_HANDS_KEY.equals(key)) {
			return withOpeningHands(parsePercent(v, "wildness.openingHands must be a whole number"));
		}
		if (LEADING_KEY.equals(key)) {
			return withStandingsWildness(parsePercent(v, "wildness.leading must be a whole number"), wild.losingPct);
		}
		if (LOSING_KEY.equals(key)) {
			return withStandingsWildness(wild.leadingPct, parsePercent(v, "wildness.losing must be a whole number"));
		}
		if (key.startsWith(STRAY_PREFIX)) {
			Strategy c = Strategy.valueOf(key.substring(STRAY_PREFIX.length()));
			Map<Enum<?>, Integer> weights = new LinkedHashMap<Enum<?>, Integer>();
			for (String part : v.split(",")) {
				String[] ow = part.trim().split("\\s+");
				if (ow.length != 2) {
					throw new IllegalArgumentException("stray needs OPTION weight pairs, like FREE 2, HOLD_BACK 1");
				}
				weights.put(c.option(ow[0]), Integer.valueOf(parsePercent(ow[1], "stray weights must be whole numbers")));
			}
			return withStray(c, weights);
		}
		if (key.startsWith(STRATEGY_PREFIX)) {
			return withStrategy(Strategy.valueOf(key.substring(STRATEGY_PREFIX.length())).option(v));
		}
		throw new IllegalArgumentException("unknown setting " + key);
	}

	private static int parsePercent(String v, String message) {
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(message);
		}
	}

	/** Every setting that differs from the template, as roster key/value
	 * pairs -- exactly what withSetting() reads back. A reliability shared by
	 * every family of a Skill is written once, under the Skill's name. */
	public List<String[]> settings() {
		Profile base = of(template);
		List<String[]> out = new ArrayList<String[]>();
		for (Tactic t : Tactic.values()) {
			if (t != Tactic.CORE && weight(t) != base.weight(t)) {
				out.add(new String[] { WEIGHT_PREFIX + t.name(), fmt(weight(t)) });
			}
		}
		for (Player_Traits s : OVERRIDABLE_STYLES) {
			int mine = stylePercent(s), theirs = base.stylePercent(s);
			if (mine != theirs) {
				String v = mine == 100 ? "on" : mine == 0 ? "off" : String.valueOf(mine);
				out.add(new String[] { STYLE_PREFIX + s.name(), v });
			}
		}
		for (Skill s : Skill.values()) {
			int first = reliability(s.families.iterator().next());
			boolean same = true;
			for (Tactic t : s.families) {
				same &= reliability(t) == first;
			}
			if (same) {
				if (first != 100) {
					out.add(new String[] { RELIABLE_PREFIX + s.name(), String.valueOf(first) });
				}
			} else {
				for (Tactic t : s.families) {
					if (reliability(t) != 100) {
						out.add(new String[] { RELIABLE_PREFIX + t.name(), String.valueOf(reliability(t)) });
					}
				}
			}
		}
		if (level != DEFAULT_LEVEL) {
			out.add(new String[] { LEVEL_KEY, String.valueOf(level) });
		}
		if (moonLookPercent != 0) {
			out.add(new String[] { MOON_LOOK_KEY, String.valueOf(moonLookPercent) });
		}
		if (moonOptimism != DEFAULT_MOON_OPTIMISM) {
			out.add(new String[] { MOON_OPTIMISM_KEY, String.valueOf(moonOptimism) });
		}
		if (moonHeartLast != DEFAULT_MOON_HEART_LAST) {
			out.add(new String[] { MOON_HEART_LAST_KEY, String.valueOf(moonHeartLast) });
		}
		if (blunderPercent != 0) {
			out.add(new String[] { BLUNDER_KEY, String.valueOf(blunderPercent) });
		}
		if (paybackTaper != null) {
			out.add(new String[] { PAYBACK_KEY,
					paybackTaper[0] + "," + paybackTaper[1] + "," + paybackTaper[2] });
		}
		for (Strategy c : Strategy.values()) {
			if (strategies.containsKey(c)) {
				out.add(new String[] { STRATEGY_PREFIX + c.name(), strategies.get(c).name() });
			}
		}
		if (wild.base != 0) {
			out.add(new String[] { WILDNESS_KEY, String.valueOf(wild.base) });
		}
		if (wild.opening != 0) {
			out.add(new String[] { OPENING_WILDNESS_KEY, String.valueOf(wild.opening) });
		}
		if (wild.openingHands != Wild.DEFAULT_OPENING_HANDS) {
			out.add(new String[] { OPENING_HANDS_KEY, String.valueOf(wild.openingHands) });
		}
		if (wild.leadingPct != Wild.DEFAULT_LEADING_PCT) {
			out.add(new String[] { LEADING_KEY, String.valueOf(wild.leadingPct) });
		}
		if (wild.losingPct != Wild.DEFAULT_LOSING_PCT) {
			out.add(new String[] { LOSING_KEY, String.valueOf(wild.losingPct) });
		}
		for (Strategy c : Strategy.values()) {
			Map<Enum<?>, Integer> w = wild.stray.get(c);
			if (w != null) {
				StringBuilder sb = new StringBuilder();
				for (Map.Entry<Enum<?>, Integer> e : w.entrySet()) {
					sb.append(sb.length() == 0 ? "" : ", ").append(e.getKey().name()).append(' ').append(e.getValue());
				}
				out.add(new String[] { STRAY_PREFIX + c.name(), sb.toString() });
			}
		}
		return out;
	}

	/** What differs from the template, e.g. "JACK_HUNT 1.5, +Avoids_Queen,
	 * Aggressive 40%, reliable.JACK 60%, blunder 10%" -- empty when the
	 * profile IS the template. Used for display. */
	public List<String> overrides() {
		List<String> out = new ArrayList<String>();
		for (String[] kv : settings()) {
			String k = kv[0], v = kv[1];
			if (k.startsWith(WEIGHT_PREFIX)) {
				out.add(k.substring(WEIGHT_PREFIX.length()) + " " + v);
			} else if (k.startsWith(STYLE_PREFIX)) {
				String s = k.substring(STYLE_PREFIX.length());
				out.add("on".equals(v) ? "+" + s : "off".equals(v) ? "-" + s : s + " " + v + "%");
			} else if (PAYBACK_KEY.equals(k)) {
				out.add("payback " + v);
			} else if (k.startsWith(STRATEGY_PREFIX)) {
				out.add(k.substring(STRATEGY_PREFIX.length()) + "=" + v);
			} else if (k.startsWith(STRAY_PREFIX)) {
				out.add(k + " (" + v + ")");
			} else if (OPENING_HANDS_KEY.equals(k) || LEVEL_KEY.equals(k)) {
				out.add(k + " " + v);
			} else {
				out.add(k + " " + v + "%");
			}
		}
		return out;
	}

	static String fmt(double w) {
		return w == Math.rint(w) ? String.valueOf((long) w) : String.valueOf(w);
	}

	@Override
	public String toString() {
		List<String> o = overrides();
		return o.isEmpty() ? template.toString() : template + " [" + String.join(", ", o) + "]";
	}
}
