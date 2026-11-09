package hearts_1.roster;

import hearts_1.Player.Player_Type;
import hearts_1.Profile;

/**
 * One persistent roster identity, added 2026-08-16 -- the first shipped
 * piece of DESIGN_roster_and_trashtalk.md's feature #6. Deliberately
 * minimal for v1: a stable id (AI_01..AI_10, never displayed as anything
 * else yet, never reused), a display name (currently equal to the id --
 * proper names come later, the user asked for AI_01..AI_10 internally for
 * now), and a reference to one of the existing Player_Type presets.
 *
 * 2026-10-10: each member now carries a Profile -- a preset TEMPLATE plus
 * optional per-family weight and style OVERRIDES -- instead of a bare
 * preset. The user chose templates + overrides over the design doc's
 * ranked-trait resolver (section 2.1): the same control, chosen directly,
 * with no conflict-resolution engine to maintain.
 */
public final class RosterPlayer {

	/** Stable identity -- the key every stats counter is recorded under.
	 * Renaming a display name must never change this. */
	public final String id;
	public final String displayName;
	/** This player's full playing profile -- a template plus any overrides
	 * (2026-10-10, see hearts_1.Profile). */
	public final Profile profile;
	/** The profile's template, kept for display and the stats screen. */
	public final Player_Type type;
	/** How often (0-100) this individual plays a hand actively tracking
	 * remaining cards / void suits -- added 2026-08-16, user request. 0
	 * means never (the roster's explicit "No"); an individual re-rolls
	 * against this rate every hand (see Player.countsCardsThisHand), so a
	 * 50 isn't "half-strength," it's "genuinely on some hands and not
	 * others." Gates ONLY the TacticWeights.COUNTED_CERTAINTIES tactic
	 * family (see AICardScorer.effectiveWeight) -- deliberately separate
	 * from that family's own per-Player_Type weight, and from every other
	 * tactic family, which this never touches. */
	public final int countCardsPercent;

	/** How often (0-100) this individual takes a SPECULATIVE moon-offense
	 * gamble -- currently just the Ace-of-Hearts flush lead (see
	 * AICardScorer's PULL THE ACE OF HEARTS OUT scorer) -- added 2026-08-16,
	 * user request. Same shape as countCardsPercent: an individual re-rolls
	 * against this rate every hand (Player.takesMoonGambleThisHand), boosted
	 * upward the further this seat is trailing the field (see Deal_Hand.
	 * effectiveMoonGamblePercent()) per the user's explicit instruction --
	 * "when a person is trailing far behind... they would take these type of
	 * chances." Gates ONLY that one scorer, separate from the
	 * TacticWeights.MOON_OFFENSE weight every tactic in the family already
	 * has. */
	public final int moonGamblePercent;

	/** Per-hand chance (0-100) of being "interested" in a moon shot even when the
	 * hand would not normally qualify (2026-10-17, user design): the lower the
	 * level the more often, boosted when trailing the leader (see
	 * Deal_Hand.effectiveMoonInterestPercent). */
	public final int moonInterestPercent;

	/** Level defaults for moonInterestPercent, used when the roster file does
	 * not say. Calibrated with tools/measure MoonCount. */
	public static int defaultMoonInterest(int level) {
		switch (level) {
			case 1: return 22;
			case 2: return 8;
			case 3: return 3;
			case 4: return 1;
			default: return 1;
		}
	}

	public RosterPlayer(String id, String displayName, Profile profile, int countCardsPercent,
			int moonGamblePercent) {
		this(id, displayName, profile, countCardsPercent, moonGamblePercent,
				defaultMoonInterest(profile.level));
	}

	public RosterPlayer(String id, String displayName, Profile profile, int countCardsPercent,
			int moonGamblePercent, int moonInterestPercent) {
		this.moonInterestPercent = moonInterestPercent;
		this.id = id;
		this.displayName = displayName;
		this.profile = profile;
		this.type = profile.template;
		this.countCardsPercent = countCardsPercent;
		this.moonGamblePercent = moonGamblePercent;
	}

	/** A plain template with no overrides. */
	public RosterPlayer(String id, String displayName, Player_Type type, int countCardsPercent,
			int moonGamblePercent) {
		this(id, displayName, Profile.of(type), countCardsPercent, moonGamblePercent);
	}

	/** What the seat-assignment dropdowns render. */
	@Override
	public String toString() {
		String counting = countCardsPercent <= 0 ? "no counting" : "counts " + countCardsPercent + "%";
		String gambling = moonGamblePercent <= 0 ? "no gambling" : "gambles " + moonGamblePercent + "%";
		return displayName + " (" + profile + ", " + counting + ", " + gambling + ")";
	}
}
