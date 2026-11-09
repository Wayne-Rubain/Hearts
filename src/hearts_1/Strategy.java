package hearts_1;

/**
 * Strategy components, added 2026-10-11 (user: "keep all of these and use
 * them as changeable components of the AI players ... the players had diff
 * strategies, some will be better than others"). Each component is one
 * decision the AI makes, with several named ways of making it -- the rule we
 * measured best (the default), the older rules it replaced, and the
 * alternatives that were tried. A profile picks one option per component
 * (roster key strategy.COMPONENT = OPTION), so two AIs with the same skills
 * can still play differently, like real people. There is no single expert:
 * several AIs can be strong with different mixtures.
 *
 * When this was added every default was exactly the play before it (proven
 * by replaying seeded hands card-for-card). Two defaults then changed on
 * 2026-10-11 with the user's approval, after measuring weakest of their
 * options: KIT_PASS to HOLD_BACK and MOON_ALARM to SHOOTER_READ.
 *
 * Measured figures: one seat vs three Normals, 2400 seeded whole games, on
 * the baseline of the day (see CHANGELOG 2026-10-10 and 2026-10-11); compare
 * options within a component, not across components.
 */
public enum Strategy {

	/** Passing a moon kit (the Queen of Spades / top Hearts / Spade control
	 * adding up to MOON_FUEL_LIMIT). See Deal_Hand.withholdMoonFuel(). */
	KIT_PASS(KitPass.values(), KitPass.HOLD_BACK),
	/** Low and middling Hearts in the pass. See Deal_Hand.heartPassScore(). */
	LOW_HEARTS(LowHearts.values(), LowHearts.KEEP_TWO),
	/** Clubs and Diamonds in the pass. See Deal_Hand.clubDiamondPassScore(). */
	SIDE_SUITS(SideSuits.values(), SideSuits.BY_HEIGHT),
	/** Emptying a Club/Diamond suit when short on Spades. */
	VOID_HUNT(VoidHunt.values(), VoidHunt.WHEN_SHORT_ON_SPADES),
	/** Who gets an unprotected Ace/King of Spades. See
	 * Deal_Hand.spadeControlPassScore(). */
	SPADE_CONTROL(SpadeControl.values(), SpadeControl.TO_LEADER),
	/** How many low Spades make the Queen safe to keep. See
	 * Deal_Hand.queenOfSpadesPassScore(). */
	QUEEN_KEEP(QueenKeep.values(), QueenKeep.FOUR_LOW_SPADES),
	/** Hoarding the top Hearts before anyone has taken a point. See
	 * AICardScorer's MOON-STOPPING tactic 0. */
	HEART_INSURANCE(HeartInsurance.values(), HeartInsurance.OFF),
	/** When a lone point-taker counts as a real moon threat for the two
	 * costly moon-defense rules (don't feed the shooter; take a point trick
	 * to break the shot). See MoonRead. */
	MOON_ALARM(MoonAlarm.values(), MoonAlarm.SHOOTER_READ),
	/** Queen-phobic signature behavior (2026-11-05, decision 3 in
	 * docs/decisions_2026-11.md). OFF is everyone's play. See
	 * Deal_Hand.signaturePassBias() and AICardScorer's FISH FOR THE QUEEN. */
	QUEEN_PHOBIA(QueenPhobia.values(), QueenPhobia.OFF, true),
	/** Jack Hunter signature behavior (2026-11-05, decision 3). OFF is
	 * everyone's play. See Deal_Hand.signaturePassBias() and AICardScorer's
	 * DIAMOND PRESSURE. */
	JACK_CHASE(JackChase.values(), JackChase.OFF, true);

	/** HOLD_BACK keeps the top Heart back so the receiver never gets the
	 * whole kit (30.4%) -- the DEFAULT since 2026-10-11 (user approved).
	 * BLOCKER: the kit always travels with a Heart of 5 or lower, the user's
	 * 2026-10-10 rule (26.1% once the keep-two-ducking-Hearts rule landed --
	 * the blocker spends one of those Hearts). FREE passes it as chosen
	 * (32.1% -- the AIs don't punish an unblocked kit the way a sharp player
	 * would). */
	public enum KitPass { BLOCKER, FREE, HOLD_BACK }

	/** KEEP_TWO: never below two Hearts under the 10 (user rule, 33.6%).
	 * BY_HEIGHT: Hearts below the Jack scored by rank, 2 = 0 (31.9%).
	 * KEEP_LOW: Hearts 2-5 kept as blockers (31.4%). PASS_FREELY: every Heart
	 * scores 8 and up, so 9-3-2 of Hearts go ahead of an Ace of Clubs -- the
	 * old leak (26.1%). */
	public enum LowHearts { KEEP_TWO, BY_HEIGHT, KEEP_LOW, PASS_FREELY }

	/** BY_HEIGHT: Aces and Kings go first (27.1% vs 24.8%). FLAT: every card
	 * the same, short suits preferred -- the old rule. */
	public enum SideSuits { BY_HEIGHT, FLAT }

	/** WHEN_SHORT_ON_SPADES: three or fewer Spades below the Queen makes
	 * emptying a suit a big bonus (user rule, 30.8% vs 24.8%). NEVER: off. */
	public enum VoidHunt { WHEN_SHORT_ON_SPADES, NEVER }

	/** TO_LEADER: 16 to the leader, 14 to 2nd -- they catch the Queen, and a
	 * leader won't shoot (user rule). ANYONE: the same to everyone.
	 * SHIELD_LEADER: the 2026-08-09 rule, reluctant toward the leader, 2nd
	 * and my closest rival. All within noise -- flavor. */
	public enum SpadeControl { TO_LEADER, ANYONE, SHIELD_LEADER }

	/** Low Spades (below the 10) needed to keep the Queen instead of passing
	 * her. All within noise -- flavor. NEVER behaves like Avoids_Queen. */
	public enum QueenKeep { FOUR_LOW_SPADES, FIVE_LOW_SPADES, NEVER }

	/** OFF: react only once someone takes a point (27.0% vs 24.8% with ON).
	 * ON: hold the Queen-and-up Hearts back from discards while nobody has
	 * taken anything -- the paranoid player's habit. */
	public enum HeartInsurance { OFF, ON }

	/** FIRST_HEART: the first lone point-taker (26.1%). FOUR_HEARTS: they've
	 * taken 4+ Hearts (29.5%). QUEEN_AND_HEART: the Queen and a Heart
	 * (28.8%). FOUR_OR_QUEEN: either (28.9%). SURE_WINNER_SAME: after a
	 * Heart was dumped on them, they LED a sure winner in that same suit with
	 * the Queen still out -- the user's read -- or the Queen and a Heart
	 * (28.7%). SURE_WINNER_ANY: the same in any suit (28.5%). KEPT_LEAD:
	 * they won another trick after their first Heart with the Queen still
	 * out, or the Queen and a Heart (28.2%). SHOOTER_READ: SURE_WINNER_SAME
	 * or 4+ Hearts (28.8%) -- the DEFAULT since 2026-10-11 (user approved):
	 * within noise of the best and built on the user's read of a real
	 * shooter. Never-on is 29.8% -- the AIs rarely shoot, so these games
	 * undervalue every alarm. */
	public enum MoonAlarm {
		FIRST_HEART, FOUR_HEARTS, QUEEN_AND_HEART, FOUR_OR_QUEEN,
		SURE_WINNER_SAME, SURE_WINNER_ANY, KEPT_LEAD, SHOOTER_READ
	}

	/** PASS_HIGH_SPADES: the Ace and King of Spades go in the pass as eagerly as
	 * a high Heart (Spade control is what wins the trick the Queen lands on);
	 * the moon-toolkit rule still keeps them out of a pass carrying the Queen
	 * or Hearts. FISH: with the Queen still out, not held, and no Ace or King
	 * of Spades in hand, lead a low Spade to force her out. BOTH: both. */
	public enum QueenPhobia { OFF, PASS_HIGH_SPADES, FISH, BOTH }

	/** KEEP_DIAMONDS: never pass the Ace, King or Queen of Diamonds, the cards
	 * that capture the Jack. PRESSURE: with the Jack still out, not held, and a
	 * Diamond above the Jack in hand, lead a low Diamond to strip the suit so
	 * the Jack has nowhere to hide. BOTH: both. */
	public enum JackChase { OFF, KEEP_DIAMONDS, PRESSURE, BOTH }

	/** Does this player pass the Ace and King of Spades eagerly this hand? */
	public static boolean passesHighSpades(Player p) {
		return p.uses(QueenPhobia.PASS_HIGH_SPADES) || p.uses(QueenPhobia.BOTH);
	}

	/** Does this player lead low Spades to fish for the Queen this hand? */
	public static boolean fishesForQueen(Player p) {
		return p.uses(QueenPhobia.FISH) || p.uses(QueenPhobia.BOTH);
	}

	/** Does this player keep its Jack-capturing Diamonds in the pass? */
	public static boolean keepsJackWeapons(Player p) {
		return p.uses(JackChase.KEEP_DIAMONDS) || p.uses(JackChase.BOTH);
	}

	/** Does this player lead low Diamonds to pressure the Jack? */
	public static boolean pressuresJack(Player p) {
		return p.uses(JackChase.PRESSURE) || p.uses(JackChase.BOTH);
	}

	/** Options measured clearly weaker than their component's best (2+ points of
	 * win rate in the figures above). Expert players (Profile.EXPERT_LEVEL and up)
	 * never stray into these (user rule, 2026-10-17): a top player varies its
	 * play, but not into the leaks. SPADE_CONTROL and QUEEN_KEEP have none (all
	 * within noise). */
	private static final java.util.Set<Enum<?>> WEAK = new java.util.HashSet<Enum<?>>(java.util.Arrays.asList(
			KitPass.BLOCKER, LowHearts.PASS_FREELY, SideSuits.FLAT, VoidHunt.NEVER,
			HeartInsurance.ON, MoonAlarm.FIRST_HEART));

	public static boolean isWeak(Enum<?> option) {
		return WEAK.contains(option);
	}

	private final Enum<?>[] options;
	public final Enum<?> defaultOption;
	/** A signature component (2026-11-05) is part of who a player is, so
	 * wildness never strays it -- and draws no random number for it, which
	 * keeps every existing seeded game card-for-card the same. */
	public final boolean signature;

	private Strategy(Enum<?>[] options, Enum<?> defaultOption) {
		this(options, defaultOption, false);
	}

	private Strategy(Enum<?>[] options, Enum<?> defaultOption, boolean signature) {
		this.options = options;
		this.defaultOption = defaultOption;
		this.signature = signature;
	}

	public Enum<?>[] options() {
		return options.clone();
	}

	/** The option of this component named `name` (case-insensitive). */
	public Enum<?> option(String name) {
		for (Enum<?> o : options) {
			if (o.name().equalsIgnoreCase(name.trim())) {
				return o;
			}
		}
		StringBuilder sb = new StringBuilder();
		for (Enum<?> o : options) {
			sb.append(sb.length() == 0 ? "" : ", ").append(o.name());
		}
		throw new IllegalArgumentException(name() + " must be one of " + sb);
	}

	/** The component an option belongs to. */
	public static Strategy of(Enum<?> option) {
		for (Strategy s : values()) {
			if (option.getDeclaringClass() == s.defaultOption.getDeclaringClass()) {
				return s;
			}
		}
		throw new IllegalArgumentException(option + " is not a strategy option");
	}
}
