package hearts_1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hearts_1.Hearts_1.CardValue;
import hearts_1.Hearts_1.PLAYER;
import hearts_1.Hearts_1.Suit;
import hearts_1.Hearts_JFrame.Table_Cards;
import hearts_1.Player.Player_Traits;

/**
 * Scored-candidate card selection, added 2026-08-06. This is the
 * architecture AI.java's card choices now run through, replacing the
 * hardcoded if/else chain those two methods used to be (see hearts.md
 * section 8's "Architectural Decision: Scored-Candidate System", decided
 * 2026-08-02 and deliberately deferred until BUG-1/2/3 were closed).
 *
 * THE MODEL: every legal card is scored by every registered scorer, the
 * contributions are summed, and the highest total wins. One concern = one
 * scorer. Adding a new consideration means adding a scorer to a list, not
 * threading another branch through an already-nested if/else -- which is the
 * whole point, since TODO.md already has four more trait ideas queued
 * (standings-aware moon-stopping, counted-out Queen-of-Spades lead,
 * counted-out Jack-of-Diamonds lead, and the passing-phase one that lives in
 * Deal_Hand.java rather than here) and the persistent-roster feature will
 * eventually need each trait to carry a per-player WEIGHT. A weighted blend
 * is natural here (multiply a scorer's output by that player's rank/weight
 * for its trait) and was impossible in the old exclusive-branch design.
 *
 * WHY THERE ARE STILL MODES: some of the existing strategies are genuinely
 * mutually exclusive rather than additive -- a player committed to shooting
 * the moon WANTS the point cards that normal play spends the whole hand
 * avoiding, so "collect hearts" and "avoid hearts" must not both contribute
 * to the same total. PlayMode captures exactly that exclusivity, decided once
 * per decision, and each scorer declares which mode(s) it applies in via its
 * own opening guard. That guard is a single local condition belonging to that
 * one concern -- not a position in a chain the way the old code worked, where
 * inserting a branch in the wrong place silently changed every strategy below
 * it (which is precisely how the 2026-08-05 lead-path moon-shot abort bug and
 * the Hearts/Queen-of-Spades lead bug both survived as long as they did).
 *
 * WEIGHT TIERS: magnitudes are banded so a lower-tier concern can never
 * outvote a higher-tier one, no matter how many lower-tier scorers happen to
 * agree. Bands are separated by more than the maximum possible swing of every
 * band beneath them (card rank contributes at most 12, since CardValue
 * ordinals run TWO=0..ACE=12):
 *   Tier 1 (~1000)  provable/committed play -- moon-shot mode.
 *   Tier 2 (~300-500) strategic overrides -- Aggressive taking a trick,
 *                   Avoids_Queen shedding, void-discard danger priority.
 *   Tier 3 (~50-100) baseline safety -- ducking, lead-suit preference.
 *   Tier 4 (~1)     card-rank tie-breaks, deliberately small enough that
 *                   they only ever separate cards every other tier tied on.
 *
 * Pure logic, no Swing dependency -- same pattern as Rules.java/Scoring.java/
 * VoidTracker.java/HandEvaluator.java. The actual game-state predicates all
 * still live in AI.java and are called from here rather than duplicated, so
 * there remains exactly one definition of "is this a protected Diamond
 * asset", "is a later seat void in the led suit", and so on.
 */
public final class AICardScorer {

	private AICardScorer() { }

	// ---- Tier 1: committed/provable play -------------------------------
	private static final double MOON_SHOT_SUIT = 1000;
	private static final double MOON_SHOT_DIRECTION = 1000;
	private static final double MOON_SHOT_SAFE_DISCARD = 1000;
	/** A shooter cashing a Heart that counting PROVES will win the trick.
	 * Sits above the run-the-longest-suit preference: the shooter's own
	 * Hearts can only be captured by winning the trick they are played in,
	 * so a provably-winning Heart must be cashed while control is still
	 * held -- holding it to the end risks being forced to play it into a
	 * lost trick, which kills the shot at the finish line. (2026-08-07,
	 * from live play: the HUMAN blew a moon exactly this way, sitting on a
	 * boss 7 of Hearts that would have completed the sweep.) */
	private static final double MOON_CASH_BOSS_HEART = 1500;
	/** Holding the Ace/King of Spades BACK rather than spending it to hunt
	 * the Queen, while the moon attempt is still on the weaker King-of-
	 * Hearts route and the Ace of Hearts is still a live unknown
	 * (2026-08-17, from a deep-dive strategy review). Sized to dominate
	 * Tier 1's own MOON_SHOT_SUIT bonus (1000, plus up to 12 of rank) with
	 * real margin -- this needs to outweigh "Spades happens to be my
	 * longest LEGAL suit" outright, not just nudge it, since the two are
	 * the same candidate card. See the scorer's own doc for the strategy
	 * research behind it. */
	private static final double MOON_HOLD_SPADE_CONTROL = 1100;
	/** Side-suit look shooter (2026-11-08, Wayne): lead the one Heart that is
	 * not a sure winner early, once, to see whether everyone plays under it --
	 * before going after the Queen. Above every other moon lead. */
	private static final double MOON_LOOK_TEST_HEART = 3000;
	/** Side-suit look shooter, Heart tested (or none to test): lead the Ace or
	 * King of Spades to catch the Queen -- "people will get rid of their high
	 * spades so they don't take the Q of spades". Above running the long suit. */
	private static final double MOON_LOOK_SPADE_LEAD = 1300;
	/** Side-suit look shooter: lose a low loser early while it is safe (Wayne,
	 * 2026-11-08: get rid of the low cards early, "but being careful that you
	 * don't lose a trick with a heart thrown on it, it's a balancing act").
	 * Below the Heart test, above running the long suit and the Spade lead. */
	private static final double MOON_LOOK_SHED_LOSER = 2000;
	/** Look shooter holding two or more sure-winner Hearts (A K...): keep the
	 * extra Heart for the very last lead (Wayne, 2026-11-08). */
	private static final double MOON_LOOK_HOLD_HEART_LAST = 3000;
	/** ...and cash the A K late, after the side-suit winners (offsets
	 * MOON_CASH_BOSS_HEART while a side-suit sure winner is still held). */
	private static final double MOON_LOOK_HEARTS_LATE = 1600;
	/** "Safe": no other seat known void in the suit, and at least this many
	 * of its cards still out in other hands (about two each). */
	private static final int SHED_MIN_OUTSTANDING = 6;
	/** Flushing the Ace of Hearts out of a void seat's hand and onto a lead
	 * I'm guaranteed to win (2026-08-16, user request from a live-hand
	 * review). Sits below MOON_CASH_BOSS_HEART -- an already-guaranteed
	 * Heart trick is the bread-and-butter play -- but above the ordinary
	 * strategic overrides in Tier 2, since removing the shot's single
	 * biggest uncontrolled threat is worth more than any of them. See the
	 * scorer's own doc for why this only ever fires on a PROVEN void, never
	 * a guess. */
	private static final double MOON_ACE_FLUSH_LEAD = 900;
	/** Added on top of MOON_ACE_FLUSH_LEAD when the void seat is the
	 * standings leader (2026-08-16, user's own reasoning): a seat already
	 * winning has the strongest incentive of anyone at the table to shed a
	 * card that could eventually cost it a point, rather than hold it back
	 * as a defensive weapon it might not even need. */
	private static final double MOON_ACE_FLUSH_LEADER_BONUS = 250;

	// ---- Tier 2: strategic overrides -----------------------------------
	private static final double AGGRESSIVE_TAKE = 500;
	private static final double VOID_DISCARD_QUEEN = 500;
	private static final double AVOIDS_QUEEN_SHED = 300;
	private static final double VOID_DISCARD_HEART = 300;
	private static final double PROTECTED_DIAMOND_PENALTY = 50;

	// ---- Moon-shot stopping (2026-08-06) -------------------------------
	// Sized to overwhelm the ordinary void-discard priority above rather than
	// merely compete with it: feeding a shooter is not a trade-off to be
	// balanced, it is the single worst thing a defender can do. The Queen
	// penalty is the larger of the two on purpose -- see the scorer.
	private static final double MOON_DENY_FEED_QUEEN = 1400;
	private static final double MOON_DENY_FEED_HEART = 900;
	/** Taking a Heart to break the shot. Must beat the duck preference. */
	private static final double MOON_DENY_TAKE = 600;
	/** Taking the Queen to break the shot -- last resort, so it sits below
	 * MOON_DENY_TAKE and below Aggressive's own take. */
	private static final double MOON_DENY_TAKE_QUEEN = 250;
	/** Reserving this seat's single highest Heart while FORCED to follow a
	 * Heart lead it can't win (2026-08-16, user-refined). It can't beat
	 * THIS trick, but a lower future lead is a different question -- and
	 * unlike MOON_DENY_FEED_HEART's void-discard inversion (which flips the
	 * WHOLE order to lowest-first, safe because a void discard can never
	 * accidentally win a trick), reversing the whole order here would
	 * strip this seat's own low ducking cards early and risk the exact
	 * endplay problem exit-card preservation exists to prevent. Reserving
	 * ONLY the single best card keeps ordinary highest-first duck order
	 * for everything else, so both protections hold at once. Sized to
	 * dominate the duck-preference tier (up to ~112) with margin. */
	private static final double MOON_DENY_FOLLOW_HEART_RESERVE = 300;
	/** Same idea, for the Ace/King of Spades (2026-08-16, user request): a
	 * defender following an unwinnable Spade lead against a threatening
	 * shooter should also weigh reserving its own capture weapon, since the
	 * Queen hasn't necessarily surfaced yet and this may be the card that
	 * catches her later. Gated on MoonThreat.queenDenialWorthIt() itself,
	 * not just threateningShooter -- there's no point reserving a card to
	 * capture the Queen with if doing so wouldn't be worth making once the
	 * moment came. That gate is exactly the user's own caveat ("unless
	 * they are really concerned about getting the Queen of Spades"): when
	 * capturing her isn't worth it, this stands down and ordinary duck
	 * preference (shed high first, avoid the endplay risk) applies as
	 * usual. Also stands down whenever safeToUnloadHighSpade() already
	 * says shedding is fine (Queen gone, held by me, or unreachable) --
	 * that scorer's reward and this one's penalty must never both apply to
	 * the same card. */
	private static final double MOON_DENY_FOLLOW_SPADE_RESERVE = 300;

	/** HEART_INSURANCE ON only (strategy option, 2026-10-11): holding the
	 * Queen-and-up Hearts back from discards while nobody has captured a
	 * point card. Sized to push a top Heart BELOW the low Hearts
	 * (VOID_DISCARD_HEART + max rank = ~312) and below any safe non-Heart
	 * discard, so the blocker only goes when there is nothing else to throw.
	 * Lapses the moment any point card is captured. */
	private static final double HEART_BLOCKER_RETENTION = 350;

	/** Taking a POINTLESS trick away from a live shooter in the moon's
	 * endgame, purely for the lead. The reviewed hand's last chance: trick
	 * 12, East could beat the shooter's winning 5 of Spades with the 10 --
	 * no points in the trick, so the deny-take scorer saw nothing to deny --
	 * but taking the LEAD would have forced the shooter's final boss Heart
	 * out as a discard onto East's own trick. Fires only when the few
	 * Hearts still at large (3 or fewer) are topped by one this seat cannot
	 * beat: the moon is one lead away from finishing, and tempo is the only
	 * denial left. Above the duck preference, below MOON_DENY_TAKE. */
	private static final double MOON_DENY_TEMPO = 550;

	/** Leading a suit the shooter is void in. Cheapest tactic of all, but
	 * since the 2026-08-07 lead restructure the shooter's void ALSO triggers
	 * the ordinary known-void lead penalty (leading into a void is normally
	 * a mistake -- here it's the whole point), so this must clear that
	 * penalty with margin left over to outrank clean-suit preference. It
	 * deliberately does NOT clear the high-Spade or Queen penalties:
	 * redirecting a moon is never worth handing yourself the Queen. */
	private static final double MOON_LEAD_REDIRECT = 400;

	/** Drawing the Queen out with a low lead into another seat's known void
	 * (2026-08-07, user-requested tactic). Like the redirect above it must
	 * clear the known-void penalty it deliberately triggers, with margin
	 * left to beat clean-suit preference -- but it sits BELOW the redirect:
	 * when a moon needs stopping, that comes first. */
	private static final double QUEEN_FLUSH_LEAD = 300;

	/** Signature leads (2026-11-05, decision 3 in docs/decisions_2026-11.md):
	 * Queen-phobic's fishing lead and Jack Hunter's Diamond pressure. Sized
	 * like the Queen flush: beats an ordinary clean lead, never a real danger. */
	private static final double SIGNATURE_FISH_LEAD = 250;
	private static final double SIGNATURE_PRESSURE_LEAD = 250;

	/** Queen-of-Spades bleed-out (2026-10-09, TODO.md item from the user's
	 * 2026-08-26 strategy deep-dive; Mark's Hearts Tips, "Bleed out the
	 * Spades"). A Queen holder with real Spade length leads its Ace, then its
	 * King, then low Spades, keeping the Queen for last -- draining the suit
	 * from everyone else so she can't be forced out later. Tier 3 sized: it
	 * should beat an ordinary clean lead elsewhere, never a real danger. The
	 * high cards get the larger bonus so the bleed goes top-down. */
	private static final double QUEEN_BLEED_HIGH_LEAD = 200;
	private static final double QUEEN_BLEED_LOW_LEAD = 100;
	/** Low Spades (below the Queen) the holder must keep to duck with. Two,
	 * so leading one still leaves one behind -- a bare Queen is the 2026-08-07
	 * disaster this gate exists to avoid. */
	private static final int QUEEN_BLEED_MIN_LOW_SPADES = 2;
	/** Test hook for A/B simulation -- seats that never bleed. Not a game
	 * setting. */
	static final java.util.EnumSet<PLAYER> QUEEN_BLEED_DISABLED_SEATS = java.util.EnumSet.noneOf(PLAYER.class);

	// ---- Tier 1: counted-out certainties (2026-08-07) -------------------
	// These fire only when card counting PROVES the outcome, so they belong
	// above every heuristic preference -- including a moon shot, since a
	// proven play is never the wrong play.
	private static final double COUNTED_QUEEN_LEAD = 2000;
	private static final double COUNTED_JACK_CAPTURE = 2000;
	/** Unloading a high Spade once the Queen provably cannot land on it.
	 * Lower than the two above: it is a proven-SAFE play rather than a proven
	 * WINNING one, so it should not override them.
	 *
	 * NARROWED 2026-08-23 on the LEAD side (see highSpadeUnloadIsUrgent and
	 * the high-Spade tempo scorer): this full weight now fires only while the
	 * Queen is still LIVE and provably unreachable, which is the only state in
	 * which there is an actual liability to be rid of. The far commoner
	 * "Queen already captured" case is no longer an emergency at all and is
	 * scored by the graded tempo scorer instead. The FOLLOW side keeps the
	 * original combined test unchanged -- shedding a high Spade onto somebody
	 * else's trick is the cheap, natural way to do it and carries none of the
	 * lead's guaranteed self-capture. */
	private static final double COUNTED_SAFE_HIGH_SPADE = 1200;

	// ---- Endgame solver (2026-10-08, see EndgameSolver.java) ------------
	/** A card that EndgameSolver shows is the best play in the WORST case
	 * over every way the unseen cards could lie -- an exhaustive look-ahead
	 * to the end of the hand, scoring moons, the Queen and the Jack exactly.
	 * It already accounts for everything the heuristic scorers approximate,
	 * so it sits above every one of them, the counted certainties included. */
	private static final double ENDGAME_WORST_CASE_BEST = 3000;
	/** Among cards the solver can't separate on worst case, the one with the
	 * strictly best AVERAGE over the possible splits. A real but unproven
	 * edge, so it is sized like the other counted follow-ups (Tier 2): enough
	 * to beat the duck preference, not enough to override a hard danger. */
	private static final double ENDGAME_AVERAGE_BEST = 400;

	// ---- High-Spade tempo once the Queen is gone (2026-08-23) -----------
	// TODO.md's "Control-card retention" item plus the user's 2026-08-23
	// live-play refinement of it. Deliberately Tier 3 sized: both halves are
	// judgement calls about a card that is already SAFE, so neither may
	// override a real danger or a counted certainty above -- they exist to
	// settle the question the old flat +1200 was answering wrongly, not to
	// start dictating leads. Equal magnitudes on purpose, so a stranded high
	// Spade that ALSO has a live use for the lead nets to zero and ordinary
	// lead scoring decides, which is the honest answer when the two
	// considerations genuinely conflict.
	/** Cash a high Spade before it is stranded as my last one and gets forced
	 * out at a moment somebody else chooses. */
	private static final double HIGH_SPADE_STRANDING_SHED = 150;
	/** ...against keeping it as a guaranteed re-entry when I have something
	 * to spend the lead on. */
	private static final double HIGH_SPADE_TEMPO_HOLD = 150;
	/** At or below this many Spades in hand, the stranding risk is live. Two
	 * (boss plus one duck) is scored at half strength -- see the scorer. */
	private static final int HIGH_SPADE_STRANDING_COUNT = 2;
	/** Winning a follow trick that counting PROVES is mine and whose
	 * contents are a net GAIN (the Jack of Diamonds outweighing any
	 * Hearts riding along) -- free money, added 2026-08-16 after a live
	 * hand where a Cautious West, playing LAST with the Ace of Diamonds
	 * over a 2-J-Q Diamond trick, ducked with the 7 and handed the human
	 * a free -10: the only take-a-good-trick scorer was gated on the
	 * Aggressive TRAIT, and the counted Jack scorer only covers holding
	 * the Jack itself. Sized so even Cautious's halved
	 * COUNTED_CERTAINTIES weight (0.5) comfortably clears the duck
	 * preference (~112 max) it has to overcome. */
	private static final double COUNTED_FREE_CAPTURE = 400;
	/** Winning a follow trick with the CHEAPEST sufficient card rather than
	 * the highest, once counting proves nobody left to act can beat it
	 * (2026-08-16, user request: "they'd only need to just beat the cards
	 * on the table... if they know no other player after it can beat it").
	 * Every existing "take this trick" scorer (Aggressive, moon-deny,
	 * free-capture) locks onto ctx.highestLegal specifically -- reasonable
	 * when the win isn't provably safe, wasteful once it is. Sized to
	 * outrank AGGRESSIVE_TAKE(500)/MOON_DENY_TAKE(600)/COUNTED_FREE_CAPTURE(400)
	 * for the cheaper card, so an advanced seat spends its Ace to win a
	 * trick only when a lower card genuinely couldn't do the same job.
	 * Also requires the trick to be point-safe to win at all (see the
	 * scorer's own trickValueWith guard) -- "safe to win" and "worth
	 * winning" are different questions, and this only answers the first
	 * once the second is already true. */
	private static final double COUNTED_MINIMAL_WIN = 700;
	/** Leading a guaranteed-winning high Diamond to draw the Jack out. Sits
	 * just under the Spade unload: dodging the Queen's +13 edges out chasing
	 * the Jack's -10 when both are available. */
	private static final double JACK_PULL_LEAD = 1100;
	/** Playing the Jack of Diamonds into a trick somebody else will win --
	 * i.e. gifting them 10 points. Only has to outweigh the duck preference
	 * that causes it. */
	private static final double JACK_GIVEAWAY_PENALTY = 400;
	/** Grading for JACK_GIVEAWAY_PENALTY, added 2026-08-15 per a user
	 * observation: an unavoidable giveaway is not equally bad regardless of
	 * recipient. Handing -10 to a seat already ahead of me stretches the gap
	 * I have to close; handing it to a seat behind me costs me nothing extra
	 * relative to them. Deliberately modest next to the 400 base -- this
	 * grades HOW BAD an already-forced giveaway is, it never makes shedding
	 * the Jack look SAFE (worst case 500, best case 300, both still miles
	 * above DUCK_UNDER_TRICK). See jackRecipientAdjustment() below. */
	private static final double JACK_GIVEAWAY_AHEAD_EXTRA = 100;
	private static final double JACK_GIVEAWAY_BEHIND_RELIEF = 100;

	// ---- Tier 3: baseline safety ---------------------------------------
	private static final double DUCK_UNDER_TRICK = 100;
	/** Winning a trick I must win with my own Queen of Spades when a higher
	 * Spade would win it too (2026-11-08). Below MOON_DENY_TAKE_QUEEN (taking
	 * her on purpose to break a moon) and the endgame solver's bonuses, which
	 * know when keeping her would only get me stuck with her later. */
	private static final double WIN_WITH_OWN_QUEEN = 150;
	private static final double AGGRESSIVE_LEAD_RANK = 50;

	// ---- Lead-side graded dangers (2026-08-07 restructure) --------------
	// The lead path used to pick a SUIT first (AI.safestLongestSuitAmong(),
	// a chain of binary exclusions) and then play the lowest card of it.
	// That two-step shape caused three separate live-play bugs of identical
	// form: the suit-chooser had no idea which card its choice would play,
	// so when every suit was excluded and the fallback fired, the "lowest
	// card" turned out to be a lone Ace of Spades into a live Queen, or the
	// Jack of Diamonds into a waiting King. Now every danger is priced on
	// the CARD it actually applies to, and the penalties are GRADED rather
	// than binary -- so a squeezed hand leads its least-bad card instead of
	// whatever an arbitrary fallback reached first. The ordering encodes
	// which sacrifice to make when forced, most expensive at the top:
	private static final double LEAD_QUEEN_OF_SPADES = 900;
	private static final double LEAD_HIGH_SPADE_LIVE_QUEEN = 800;
	/** A Heart that is provably the BOSS -- counting shows nothing above it
	 * is still out, so leading it is guaranteed self-capture. */
	private static final double LEAD_HEART_GUARANTEED_WIN = 400;
	private static final double LEAD_KNOWN_VOID = 150;
	private static final double LEAD_PROTECTED_DIAMOND = 150;
	/** A Heart that CAN still lose the trick. Graded by rank on top of this
	 * base (see LEAD_HEART_RANK): a low Heart with higher Hearts still out
	 * is nearly certain to lose, which merely hands an opponent 1 point --
	 * cheap, deliberately the mildest sacrifice of the set (2026-08-07 user
	 * correction: a hand rich in low Hearts should happily lead them). A
	 * King is close to certain to win the trick, so its graded total
	 * (~215) lands near the old flat penalty. */
	private static final double LEAD_HEART_BASE = 50;
	/** Bonus for leading a non-boss Heart once a committed moon has died; see the
	 * ABANDONED MOON scorer. Large enough to beat a low off-suit lead. */
	private static final double ABANDONED_MOON_SHED = 300;
	private static final double LEAD_HEART_RANK = 15;
	/** Per card held of the candidate's suit -- the old "lead from your
	 * longest suit" policy expressed per-card. Max swing 130 (13 cards), so
	 * length differences can reorder equally-clean suits but never buy a
	 * card out of a danger penalty above. */
	private static final double LEAD_SUIT_LENGTH = 10;

	// ---- Tier 4: tie-breaks --------------------------------------------
	private static final double RANK_TIE_BREAK = 1;

	/** Exit-card preservation (2026-08-15, TODO.md's "keep a guaranteed-
	 * losing card late in the hand" item). Tier 4's "lead low" rule above
	 * bleeds every suit dry from the bottom up with no memory of how many
	 * safe cards are left overall -- exactly how a hand ends up holding
	 * nothing but boss cards late on, forced to win every trick it touches
	 * while opponents dump Hearts/the Queen on it. Sized to sit in Tier
	 * 3/4's own neighborhood: a real nudge among otherwise-safe leads, never
	 * enough to override an actual danger penalty above. */
	private static final double EXIT_CARD_RESERVE_PENALTY = 60;

	// ---- End-of-game awareness (2026-08-15, see EndGame.java) ----------
	// Both scale by EndGame.dangerLevel() (0..1), so neither does anything
	// until a seat is actually inside the danger zone. Sized to be able to
	// reach into Tier 2 at full danger -- self-preservation this close to
	// TARGET_SCORE should be able to override ordinary aggression, not just
	// nudge the Tier 3 duck preference -- while doing nothing at all far
	// from the target.
	/** Per net point a winning follow would cost, at maximum danger (a
	 * Queen-of-Spades trick, danger 1.0, prices out to -520 -- comparable to
	 * AGGRESSIVE_TAKE/VOID_DISCARD_QUEEN above, able to flip those
	 * decisions when the game is genuinely on the line). */
	private static final double END_GAME_SELF_CAUTION_PER_POINT = 40;
	/** Bonus for a void discard that lands on the seat closest to
	 * TARGET_SCORE, at maximum danger. Modest relative to VOID_DISCARD_QUEEN
	 * (500) -- the ordinary "shed the Queen when void" rule already fires
	 * regardless of target, so this mostly breaks close ties in favor of
	 * hastening the game's end while a specific opponent is exposed, rather
	 * than overriding the base shedding priority outright. */
	private static final double END_GAME_TARGET_QUEEN = 150;
	private static final double END_GAME_TARGET_HEART = 60;

	// ---- Social: attack the standings leader (2026-08-15) --------------
	// Same shape and magnitude as the END_GAME targeting above, deliberately
	// -- both are "direct a void discard's points at a specific seat"
	// tactics, just keyed on a different question (who's ahead of me right
	// now vs. who's closest to 100). See TacticWeights.Tactic.SOCIAL.
	private static final double SOCIAL_TARGET_QUEEN = 150;
	private static final double SOCIAL_TARGET_HEART = 60;
	// Queen payback (2026-10-10, Payback.java), at full grudge strength. The
	// Queen matches the leader bonus; a Heart is a token jab.
	private static final double PAYBACK_QUEEN = 150;
	private static final double PAYBACK_HEART = 30;

	/**
	 * Which mutually-exclusive strategy is driving this decision. Decided
	 * once per card choice, in the same priority order the old if/else chain
	 * used, so that ordering stays visible in one place instead of being
	 * implied by the physical layout of the code.
	 */
	public enum PlayMode {
		/** Committed to shooting the moon AND the attempt is still alive. */
		MOON_SHOT,
		/** The Aggressive trait's trick-hungry style (lead side only -- on
		 * the follow side Aggressive is a conditional override inside normal
		 * play, not a whole separate mode, which is why BASELINE covers it
		 * there). */
		AGGRESSIVE,
		/** Ordinary point-avoidance play. */
		BASELINE
	}

	/**
	 * Everything a lead scorer might need, computed once per decision rather
	 * than recomputed by each scorer for each candidate card.
	 */
	public static final class LeadContext {
		public final List<Card> legalOptions;
		public final Table_Cards tc;
		public final Player player;
		public final PlayMode mode;
		/** Longest suit held -- the suit a moon-shooter runs to keep control. */
		public final Suit longestSuit;
		/** True while a Jack-of-Diamonds asset (the Jack itself, or an
		 * Ace/King capture weapon) makes leading Diamonds a strategic cost. */
		public final boolean protectDiamonds;
		/** True once the Queen of Spades sits in somebody's won pile -- the
		 * one state in which high Spades are genuinely harmless to lead. */
		public final boolean queenOfSpadesGone;
		/** EndgameSolver's per-card bonus, filled in by pickLead() once per
		 * decision when the solver applies -- empty otherwise. */
		Map<Card, Double> endgameBonus = Collections.emptyMap();
		/** This seat's whole remaining hand when known (before Hearts are
		 * broken legalOptions hides the Hearts), else null. Set by rankLead(). */
		List<Card> fullHand;

		LeadContext(List<Card> legalOptions, Table_Cards tc, Player player) {
			this.legalOptions = legalOptions;
			this.tc = tc;
			this.player = player;
			this.mode = leadModeFor(tc, player);
			this.longestSuit = AI.longestSuitAmong(legalOptions);
			this.protectDiamonds = AI.shouldProtectDiamondSuit(legalOptions, tc);
			this.queenOfSpadesGone = CardCounter.alreadyWonBySomeone(tc, CardValue.QUEEN, Suit.SPADE);
		}
	}

	/**
	 * Everything a follow scorer might need. `highestOnTable` and `canWin`
	 * are only meaningful while actually following suit; on a void discard
	 * there's no suit-matching card on the table to measure against.
	 */
	public static final class FollowContext {
		public final List<Card> legalOptions;
		public final Table_Cards tc;
		public final Player player;
		public final int playPosition;
		public final Suit leadSuit;
		public final boolean followingSuit;
		public final PlayMode mode;
		public final Card highestOnTable;
		public final Card highestLegal;
		public final boolean canWin;
		/** True once the Jack of Diamonds is off the table for good -- no
		 * reason to keep shielding Ace/King of Diamonds after that. */
		public final boolean jackAlreadyCaptured;
		/** This seat's full remaining hand, not just legalOptions -- which is
		 * suit-restricted to the led suit while actually following (see
		 * CardCounter's class doc). Added 2026-08-16 so MoonThreat.
		 * queenDenialWorthIt() can check Diamond holdings (the Jack-of-
		 * Diamonds offset) even when Spades are led and legalOptions can't
		 * see Diamonds at all. */
		public final Hand myHand;
		/** EndgameSolver's per-card bonus -- see LeadContext.endgameBonus. */
		Map<Card, Double> endgameBonus = Collections.emptyMap();

		FollowContext(List<Card> legalOptions, Hand myHand, Table_Cards tc, int playPosition, Player player) {
			this.legalOptions = legalOptions;
			this.myHand = myHand;
			this.tc = tc;
			this.player = player;
			this.playPosition = playPosition;
			this.leadSuit = tc.p1_card.suit;
			this.followingSuit = AI.allSameSuit(legalOptions, leadSuit);
			this.mode = AI.isMoonShotAlive(tc, player) ? PlayMode.MOON_SHOT : PlayMode.BASELINE;
			this.highestLegal = AI.highestOf(legalOptions);
			this.highestOnTable = AI.highestOfSuitOnTable(tc, leadSuit, playPosition);
			this.canWin = this.highestOnTable != null
					&& this.highestLegal.cardValue.compareTo(this.highestOnTable.cardValue) > 0;
			this.jackAlreadyCaptured = Scoring.hasJackBeenCaptured(AI.wonCardsByPlayer(tc));
		}
	}

	/** Each scorer declares which tactic family it belongs to; its output is
	 * multiplied by the acting player's weight for that family (see
	 * TacticWeights.java, added 2026-08-07). The default is CORE -- baseline
	 * competence, always weight 1 -- so only the gated tactics override it. */
	public interface LeadCardScorer {
		double score(Card candidate, LeadContext ctx);
		default TacticWeights.Tactic tactic() {
			return TacticWeights.Tactic.CORE;
		}
	}

	public interface FollowCardScorer {
		double score(Card candidate, FollowContext ctx);
		default TacticWeights.Tactic tactic() {
			return TacticWeights.Tactic.CORE;
		}
	}

	/** Moon-shot lead is only live while the attempt itself is still
	 * mathematically alive -- see AI.isMoonShotAlive(). Aggressive fully
	 * replaced baseline play in the old chain, so it's a mode here too. */
	private static PlayMode leadModeFor(Table_Cards tc, Player player) {
		if (AI.isMoonShotAlive(tc, player)) {
			return PlayMode.MOON_SHOT;
		}
		// 2026-10-17 (user rule): a moon attempt that has died means minimize the
		// damage -- no trick-hungry Aggressive leads, lead low and get out of the lead.
		if (AI.moonAttemptAbandoned(tc, player)) {
			return PlayMode.BASELINE;
		}
		if (player.p_traits.contains(Player_Traits.Aggressive)) {
			return PlayMode.AGGRESSIVE;
		}
		return PlayMode.BASELINE;
	}

	private static int rank(Card c) {
		return c.cardValue.ordinal();
	}

	/** A Heart in this seat's hand that something unplayed still beats -- the
	 * side-suit look's one Heart to test. */
	static boolean isUntestedHeart(LeadContext ctx, Card c) {
		List<Card> mine = ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions;
		return c.suit == Suit.HEART && CardCounter.anyAboveUnaccounted(ctx.tc, mine, 1, Suit.HEART, c.cardValue);
	}

	static boolean isSafeLoserToShed(LeadContext ctx, Card c) {
		List<Card> mine = ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions;
		if (!CardCounter.anyAboveUnaccounted(ctx.tc, mine, 1, c.suit, c.cardValue)) {
			return false; // a sure winner, not a loser
		}
		for (Card m : mine) {
			if (m.suit == c.suit && m.cardValue.compareTo(c.cardValue) < 0) {
				return false; // shed the lowest of the suit first
			}
		}
		if (c.suit == Suit.SPADE && !CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.QUEEN, Suit.SPADE)
				&& !CardCounter.holds(mine, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		for (PLAYER seat : PLAYER.values()) {
			if (seat != ctx.player.tbl_player && ctx.tc.void_suits != null && ctx.tc.void_suits.get(seat) != null
					&& ctx.tc.void_suits.get(seat).contains(c.suit)) {
				return false;
			}
		}
		// No way back in, no shedding (2026-11-09, Wayne's 11-Club moon replayed
		// with AIs in his seat): once the others are out of my long suit nobody
		// will ever lead it, so a loser I lead hands the lead away for good.
		// Shed only while some sure winner of mine is in a suit others still hold.
		if (!hasEntryBack(ctx, mine, c.suit)) {
			return false;
		}
		return outstanding(ctx, mine, c.suit) >= SHED_MIN_OUTSTANDING;
	}

	/** Cards of `suit` not in my hand and not yet won by anyone. */
	static int outstanding(LeadContext ctx, List<Card> mine, Suit suit) {
		int out = 13;
		for (Card m : mine) {
			if (m.suit == suit) {
				out--;
			}
		}
		for (Player p : ctx.tc.players.values()) {
			for (Card w : p.won_cards.hand) {
				if (w.suit == suit) {
					out--;
				}
			}
		}
		return out;
	}

	/** Every other seat is out of `suit`: shown void, or -- for a player
	 * counting cards this hand -- none left outside my hand (2026-11-09,
	 * Wayne's game: an Aggressive West, counting, kept leading Clubs nobody
	 * else had and collected the Hearts everyone threw on them). */
	static boolean othersAllOutOf(LeadContext ctx, Suit suit) {
		List<Card> mine = ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions;
		if (ctx.player.countsCardsThisHand && outstanding(ctx, mine, suit) == 0) {
			return true;
		}
		if (ctx.tc.void_suits == null) {
			return false;
		}
		for (PLAYER seat : PLAYER.values()) {
			if (seat == ctx.player.tbl_player) {
				continue;
			}
			java.util.EnumSet<Suit> v = ctx.tc.void_suits.get(seat);
			if (v == null || !v.contains(suit)) {
				return false;
			}
		}
		return true;
	}

	/** A Heart or the Queen of Spades still in another hand. */
	static boolean pointsStillOut(LeadContext ctx) {
		List<Card> mine = ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions;
		if (outstanding(ctx, mine, Suit.HEART) > 0) {
			return true;
		}
		return !CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.QUEEN, Suit.SPADE)
				&& !CardCounter.holds(mine, CardValue.QUEEN, Suit.SPADE);
	}

	/** A sure winner of mine, outside `shedSuit`, in a suit someone else still
	 * holds cards of -- a way to win the lead back after giving it up. */
	static boolean hasEntryBack(LeadContext ctx, List<Card> mine, Suit shedSuit) {
		for (Card m : mine) {
			if (m.suit != shedSuit && !CardCounter.anyAboveUnaccounted(ctx.tc, mine, 1, m.suit, m.cardValue)
					&& outstanding(ctx, mine, m.suit) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Two or more sure-winner Hearts in hand (the A K, or what is left of the
	 * top once higher ones are played): the look shooter pulls the Hearts late
	 * instead of testing its extra Heart early. */
	static boolean pullsHeartsLate(LeadContext ctx) {
		// Per player (Profile.moonHeartLast): some test the extra Heart early instead.
		if (!ctx.player.moonHeartLastThisHand) {
			return false;
		}
		int sure = 0;
		for (Card c : ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions) {
			if (c.suit == Suit.HEART && !isUntestedHeart(ctx, c)) {
				sure++;
			}
		}
		// Once the shooter has cashed its own Ace of Hearts the plan is set: the
		// extra Heart is still played last.
		return sure >= 2 || CardCounter.holds(ctx.player.won_cards.hand, CardValue.ACE, Suit.HEART);
	}

	static boolean onlyHeartsLeft(LeadContext ctx) {
		for (Card c : ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions) {
			if (c.suit != Suit.HEART) {
				return false;
			}
		}
		return true;
	}

	/** A non-Heart card nothing unplayed can beat. */
	static boolean holdsSideSuitWinner(LeadContext ctx) {
		List<Card> mine = ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions;
		for (Card c : mine) {
			if (c.suit != Suit.HEART && !CardCounter.anyAboveUnaccounted(ctx.tc, mine, 1, c.suit, c.cardValue)) {
				return true;
			}
		}
		return false;
	}

	/** An extra Heart still waiting for its early test (not when the hand
	 * pulls the Hearts late and plays it last). */
	static boolean hasUntestedHeart(LeadContext ctx) {
		if (pullsHeartsLate(ctx)) {
			return false;
		}
		for (Card c : ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions) {
			if (isUntestedHeart(ctx, c)) {
				return true;
			}
		}
		return false;
	}

	/** Would winning this Spade trick with `candidate` -- the King or Ace --
	 * risk the Queen of Spades being dropped on it by a seat still to act?
	 * True while the Queen is unaccounted for (not won, not on the table,
	 * not in my own hand) and not every later seat is proven void in Spades.
	 * Added 2026-10-08 for the Aggressive take guard. Package-private for
	 * AggressiveQueenGuardTest. */
	static boolean spadeWinRisksLiveQueen(FollowContext ctx, Card candidate) {
		if (ctx.leadSuit != Suit.SPADE || candidate.suit != Suit.SPADE
				|| candidate.cardValue.compareTo(CardValue.QUEEN) <= 0) {
			return false;
		}
		if (CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.QUEEN, Suit.SPADE)
				|| CardCounter.onTableThisTrick(ctx.tc, ctx.playPosition, CardValue.QUEEN, Suit.SPADE)
				|| CardCounter.holds(ctx.myHand != null ? ctx.myHand.hand : ctx.legalOptions,
						CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		return !CardCounter.allLaterSeatsVoidIn(ctx.tc, ctx.playPosition, Suit.SPADE);
	}

	/** Is `candidate` a plausible "loser" -- not provably the boss of its
	 * suit, so a real card to lose a trick with rather than a card that
	 * would win the moment it's played? Added 2026-08-15 for exit-card
	 * preservation. Deliberately not a PROOF the card will lose (the
	 * opponent holding a higher card might duck under it with something
	 * lower instead) -- same "best available info, not full lookahead"
	 * standard the rest of this file already uses (MoonThreat.canStillWinTrick,
	 * the wouldCollect* helpers). `options` is the scope CardCounter is
	 * allowed to reason over -- see its class doc; for a lead this is always
	 * ctx.legalOptions. Package-private so ExitCardTest can exercise it
	 * directly, same pattern as HandEvaluator.hasMoonHeartControl. */
	static boolean isReserveCandidate(Table_Cards tc, List<Card> options, Card candidate) {
		return CardCounter.anyAboveUnaccounted(tc, options, 1, candidate.suit, candidate.cardValue);
	}

	/** How many of my current lead options are reserve candidates (see
	 * isReserveCandidate). Computed once per lead decision, not per
	 * candidate -- the count doesn't change as different candidates are
	 * scored against the same fixed set of options. Package-private, same
	 * reason as isReserveCandidate above. */
	static int reserveCardCount(Table_Cards tc, List<Card> options) {
		int count = 0;
		for (Card c : options) {
			if (isReserveCandidate(tc, options, c)) {
				count++;
			}
		}
		return count;
	}

	/** Would leading a King or Ace of Spades right now risk having the Queen
	 * dropped on it? True by default whenever the Queen is still live --
	 * false once she's already in a won pile (`queenOfSpadesGone`,
	 * precomputed on LeadContext). If I hold the Queen myself the moon
	 * question below never applies -- the risk there is stripping my OWN
	 * protection, not someone else dropping it on me.
	 *
	 * MOON-SHOOTER EXCEPTION (2026-08-16, tightened 2026-08-16 after a live
	 * hand exposed it as too loose): the original version waived the risk
	 * for ANY seat that happened to be the sole holder of a point card so
	 * far -- Scoring.inProgressMoonShooter() fires off a single incidental
	 * Heart, no different from a genuine moon attempt. In the hand that
	 * exposed this, that "shooter" held exactly one Heart, wasn't even
	 * attempting a moon (HandEvaluator's own attempt decision had already
	 * said no), and didn't hold the Queen at all -- a completely different
	 * seat did, and freely dumped it onto the lead this exception had just
	 * waved through. Real Hearts strategy consensus (web-researched
	 * 2026-08-16) backs the tighter bar: a single Heart is always enough to
	 * break a real shot, so nobody sane treats one incidental point card as
	 * proof of a moon in progress, and even Omnibus's own Jack-of-Diamonds
	 * bonus is explicitly called out as NOT license to chase the Queen --
	 * "minus 10 does not cover plus 13, so queen discipline still comes
	 * first." That is exactly the standard MoonThreat.queenDenialWorthIt()
	 * already enforces on the follow side (a real Hearts-banked threshold,
	 * a Jack-of-Diamonds offset discount, and a standings safety check), so
	 * the lead-side exception now defers to it instead of keeping its own,
	 * weaker copy: waived only when a shooter is in progress AND actually
	 * eating the Queen from them would itself be judged worth it. Still
	 * deliberately the raw shooter signal (Scoring.inProgressMoonShooter),
	 * not MoonThreat.threateningShooter -- that helper also filters out a
	 * shooter I'm already safely ahead of, which is the wrong question
	 * here: the shooter's hoarding behavior doesn't depend on how it would
	 * affect MY standings if their shot lands (queenDenialWorthIt's own,
	 * more precise standings check -- would eating the Queen itself drop me
	 * behind someone -- already covers that ground). Package-private for
	 * HighSpadeLeadTest. */
	/**
	 * Would leading a boss Diamond to hunt the Jack hand me the Queen of
	 * Spades instead (2026-08-16, TODO.md's "Jack-of-Diamonds realistic
	 * chance viability dial")? The pull lead deliberately wins its own
	 * trick, which is exactly what makes it dangerous: every seat void in
	 * Diamonds gets a free discard onto a trick I am already committed to
	 * winning, and the Queen is the best discard a void hand can make. The
	 * bonus is -10 and the Queen is +13, so walking into her turns a hunt
	 * that "worked" into a net +3 loss -- which is the precise trap TODO.md
	 * flagged ("a player aggressively winning Diamond tricks to chase the
	 * Jack is vulnerable to opponents deliberately feeding them the Queen of
	 * Spades on the back of that eagerness"), and matches the real Omnibus
	 * strategy consensus researched 2026-08-16: "never chase the jack into
	 * the queen of spades -- minus 10 does not cover plus 13, so queen
	 * discipline still comes first."
	 *
	 * Three ways the risk is provably absent, all reusing the existing
	 * Queen-tracking helpers rather than guessing:
	 *   - she is already in somebody's won pile (`queenOfSpadesGone`);
	 *   - I hold her myself, so nobody else can drop her on me;
	 *   - no opponent who could still be holding her is void in Diamonds,
	 *     so everyone must follow suit and nobody gets a discard at all.
	 *
	 * That last one is exactly aVoidSeatCouldHoldTheQueen(), the same
	 * predicate the QUEEN_FLUSH lead uses -- deliberately shared, since the
	 * two tactics are mirror images: the flush leads a LOW card into a void
	 * hoping the Queen gets dumped, and this hunt leads a HIGH one where
	 * that same dump is the disaster. Package-private for JackPullGuardTest.
	 */
	static boolean jackPullRisksTheQueen(Table_Cards tc, List<Card> legalOptions, PLAYER me,
			boolean queenOfSpadesGone) {
		if (queenOfSpadesGone) {
			return false;
		}
		if (CardCounter.holds(legalOptions, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		return aVoidSeatCouldHoldTheQueen(tc, me, Suit.DIAMOND);
	}

	/**
	 * Is the Queen-of-Spades bleed-out on for this lead? All of:
	 *   - not a moon shot (a shooter wants the Queen) and no other seat's moon
	 *     is in progress (moon defense comes first);
	 *   - I hold the Queen and she is still in play -- so nobody else can drop
	 *     her on my Ace or King, which is what makes leading them free;
	 *   - at least QUEEN_BLEED_MIN_LOW_SPADES Spades below her to duck with --
	 *     the line between real length and the bare Ace-and-Queen hand of the
	 *     2026-08-07 bug, which must keep its penalty;
	 *   - no opponent is known void in Spades (he'd discard Hearts onto my
	 *     trick), and at least one Spade is still out there to drain;
	 *   - this seat's type plays QUEEN_FLUSH tactics at all;
	 *   - not an Aggressive lead. Paired A/B (2026-10-09, 6000 deals): an
	 *     Aggressive seat already leads high, so the bleed changed only 297
	 *     of 2491 plays and moved its points by 0.00 +/- 0.03. No measurable
	 *     effect, so per the user it stays off and Aggressive plays exactly
	 *     as before.
	 * Package-private for QueenBleedTest.
	 */
	static boolean queenBleedApplies(LeadContext ctx) {
		if (ctx.mode == PlayMode.MOON_SHOT || ctx.mode == PlayMode.AGGRESSIVE || ctx.queenOfSpadesGone
				|| QUEEN_BLEED_DISABLED_SEATS.contains(ctx.player.tbl_player)
				|| effectiveWeight(ctx.player, TacticWeights.Tactic.QUEEN_FLUSH) == 0) {
			return false;
		}
		if (!CardCounter.holds(ctx.legalOptions, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		int low = 0;
		for (Card c : ctx.legalOptions) {
			if (c.suit == Suit.SPADE && c.cardValue.compareTo(CardValue.QUEEN) < 0) {
				low++;
			}
		}
		if (low < QUEEN_BLEED_MIN_LOW_SPADES) {
			return false;
		}
		if (AI.anyOtherSeatVoidIn(ctx.tc, ctx.player.tbl_player, Suit.SPADE)) {
			return false;
		}
		PLAYER shooter = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(ctx.tc));
		if (shooter != null && shooter != ctx.player.tbl_player) {
			return false;
		}
		boolean[] seen = CardCounter.accountedRanks(ctx.tc, ctx.legalOptions, 1, Suit.SPADE);
		for (boolean s : seen) {
			if (!s) {
				return true; // somebody still holds a Spade
			}
		}
		return false;
	}

	static boolean highSpadeLeadRisksTheQueen(Table_Cards tc, List<Card> legalOptions, Player me,
			boolean queenOfSpadesGone) {
		if (queenOfSpadesGone) {
			return false;
		}
		boolean iHoldTheQueen = CardCounter.holds(legalOptions, CardValue.QUEEN, Suit.SPADE);
		if (!iHoldTheQueen) {
			PLAYER shooter = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(tc));
			if (shooter != null && shooter != me.tbl_player
					&& MoonThreat.queenDenialWorthIt(tc, me, shooter, legalOptions)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Can the Queen of Spades be avoided entirely on the trick in progress?
	 * Added 2026-08-07 for the safe high-Spade unload. Three ways to know:
	 *   - it has already been played in a completed trick, so it is gone;
	 *   - I am holding it, so nobody else can play it;
	 *   - (lead side only) every opponent is provably void in Spades, which
	 *     means none of them can be holding the Queen -- the Queen is itself
	 *     a Spade.
	 *
	 * The third route is allowed only when leading. On the follow side, seats
	 * void in the led Spade suit discard instead of following, and a trick my
	 * high Spade is about to win would collect those discards, Hearts
	 * included. See the follow-side scorer.
	 *
	 * Regardless of route, this returns false if the Queen is already face up
	 * in the current trick: winning it then is precisely what we're avoiding.
	 */
	/**
	 * What winning this trick would actually cost, in Omnibus points: each
	 * Heart +1, the Queen of Spades +13, the Jack of Diamonds -10. Counts the
	 * cards already face up plus the card being considered, since that one
	 * lands in the trick too. A NEGATIVE result means winning the trick is a
	 * net gain. Added 2026-08-07 so the Jack-capture scorer can price the
	 * whole trick rather than just noticing the Jack.
	 */
	private static int trickValueWith(FollowContext ctx, Card candidate) {
		Card[] onTable = { ctx.tc.p1_card, ctx.tc.p2_card, ctx.tc.p3_card, ctx.tc.p4_card };
		int value = 0;
		for (int i = 0; i < ctx.playPosition - 1 && i < onTable.length; i++) {
			value += cardPointValue(onTable[i]);
		}
		return value + cardPointValue(candidate);
	}

	private static int cardPointValue(Card c) {
		if (c == null) {
			return 0;
		}
		if (Rules.isQueenOfSpades(c)) {
			return 13;
		}
		if (c.suit == Suit.DIAMOND && c.cardValue == CardValue.JACK) {
			return -10;
		}
		return c.suit == Suit.HEART ? 1 : 0;
	}

	/** How much to adjust a forced Jack-of-Diamonds giveaway by, based on
	 * `recipient`'s standing relative to `me` (2026-08-15). Negative when
	 * `recipient` is ahead of me (their total is LOWER -- handing them -10
	 * stretches the gap I have to close, so avoid it harder), positive when
	 * they're behind me (their total is HIGHER -- costs me nothing extra
	 * relative to them, more tolerable to let go now). Zero if tied or
	 * `recipient` is unknown/self. */
	private static double jackRecipientAdjustment(Table_Cards tc, PLAYER me, PLAYER recipient) {
		if (recipient == null || recipient == me) {
			return 0;
		}
		int myTotal = tc.players.get(me).p_total_points;
		int theirTotal = tc.players.get(recipient).p_total_points;
		if (theirTotal < myTotal) {
			return -JACK_GIVEAWAY_AHEAD_EXTRA;
		}
		if (theirTotal > myTotal) {
			return JACK_GIVEAWAY_BEHIND_RELIEF;
		}
		return 0;
	}

	/** Does this seat provably hold the moon-stopper -- a Heart that outranks
	 * every Heart still at large in other hands (2026-08-15, user
	 * refinement)? A shooter must capture ALL 13 Hearts, so they eventually
	 * have to win the trick this card is played in -- and they can't, because
	 * nothing they could hold beats it. While that's true, feeding the
	 * shooter points isn't a catastrophe, it's a TRAP: every Heart and the
	 * Queen they collect converts to points they EAT the moment the stopper
	 * breaks the shot. Conservative by construction: the shooter's own unseen
	 * Hearts count as at-large, so a King is never trusted while the Ace is
	 * unaccounted for. Package-private for MoonStopperTest. */
	static boolean holdsMoonStopper(Table_Cards tc, List<Card> myCards, int playPosition) {
		return stopperRankAmong(tc, myCards, playPosition) >= 0;
	}

	/** The ordinal of my boss Heart if I hold the stopper, else -1. */
	private static int stopperRankAmong(Table_Cards tc, List<Card> myCards, int playPosition) {
		boolean[] accounted = CardCounter.accountedRanks(tc, myCards, playPosition, Suit.HEART);
		int topAtLarge = -1;
		for (int r = accounted.length - 1; r >= 0; r--) {
			if (!accounted[r]) {
				topAtLarge = r;
				break;
			}
		}
		int myTop = -1;
		for (Card c : myCards) {
			if (c.suit == Suit.HEART && c.cardValue.ordinal() > myTop) {
				myTop = c.cardValue.ordinal();
			}
		}
		return (myTop >= 0 && myTop > topAtLarge) ? myTop : -1;
	}

	/** May `candidate` be fed to a visible shooter because the shot is
	 * covered anyway (2026-08-15)? The Queen is expendable whenever the
	 * stopper is held -- she converts to +13 against the shooter once the
	 * shot breaks. A Heart is expendable only if it is NOT itself the
	 * stopper: some OTHER Heart of mine must still outrank everything at
	 * large after this one is gone. Package-private for MoonStopperTest. */
	static boolean expendableAgainstShooter(Table_Cards tc, List<Card> myCards, int playPosition, Card candidate) {
		int stopper = stopperRankAmong(tc, myCards, playPosition);
		if (stopper < 0) {
			return false;
		}
		if (Rules.isQueenOfSpades(candidate)) {
			return true;
		}
		// The stopper itself is the one card that must never be fed. Any
		// lower Heart leaves the boss in hand and the shot still covered.
		return candidate.suit == Suit.HEART && candidate.cardValue.ordinal() < stopper;
	}

	/** The moon's endgame, seen from the acting seat: 3 or fewer Hearts
	 * remain at large (neither captured nor among my legal options), and the
	 * TOP one is a card I cannot beat -- so if the shooter holds it and
	 * keeps the lead, the sweep completes and nothing I hold can stop it.
	 *
	 * Accounting caveat: legalOptions is only the follow-suit subset, so
	 * when following a non-Heart suit this seat's own Hearts read as "at
	 * large" and un-beatable-by-me. That errs conservative on purpose --
	 * the tempo take it triggers is still a safe, pointless trick, and
	 * seizing the lead while holding the blocker yourself merely spends a
	 * high card, whereas failing to seize it when the shooter holds the
	 * boss Heart loses the hand outright. */
	private static boolean moonEndgameHeartsAtLarge(FollowContext ctx) {
		boolean[] accounted = CardCounter.accountedRanks(
				ctx.tc, ctx.legalOptions, ctx.playPosition, Suit.HEART);
		int atLarge = 0;
		int topLive = -1;
		for (int r = 0; r < accounted.length; r++) {
			if (!accounted[r]) {
				atLarge++;
				topLive = r;
			}
		}
		if (atLarge == 0 || atLarge > 3) {
			return false;
		}
		for (Card c : ctx.legalOptions) {
			if (c.suit == Suit.HEART && c.cardValue.ordinal() > topLive) {
				return false; // I hold the blocker myself -- no rush
			}
		}
		return true;
	}

	/** King or Ace of Spades in hand -- the exposure that motivates flushing
	 * the Queen out (see the queen-flush lead scorer). */
	private static boolean holdsHighSpade(List<Card> cards) {
		return CardCounter.holds(cards, CardValue.KING, Suit.SPADE)
				|| CardCounter.holds(cards, CardValue.ACE, Suit.SPADE);
	}

	/** Some OTHER seat is known void in `suit` and is NOT known void in
	 * Spades -- i.e. a seat whose free discard could actually be the Queen.
	 * A seat void in Spades provably cannot hold her: the Queen is a Spade. */
	private static boolean aVoidSeatCouldHoldTheQueen(Table_Cards tc, PLAYER self, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			if (p != self
					&& VoidTracker.isVoid(tc, p, suit)
					&& !VoidTracker.isVoid(tc, p, Suit.SPADE)) {
				return true;
			}
		}
		return false;
	}

	/** The mirror image of aVoidSeatCouldHoldTheQueen(), for the Ace of
	 * Hearts instead of the Queen of Spades (2026-08-16, user request from
	 * a live-hand review -- "I think I would have led with the K of
	 * Diamonds and hope someone would have dropped the A of hearts on that
	 * trick"). Returns the specific seat rather than a boolean, so the
	 * caller can check whether that seat happens to be the standings
	 * leader (see MOON_ACE_FLUSH_LEADER_BONUS) -- picks the first match if
	 * more than one seat qualifies, which is good enough since the caller
	 * only needs ONE credible target to justify the lead. */
	private static PLAYER voidSeatThatMightHoldTheAce(Table_Cards tc, PLAYER self, Suit suit) {
		for (PLAYER p : PLAYER.values()) {
			if (p != self
					&& VoidTracker.isVoid(tc, p, suit)
					&& !VoidTracker.isVoid(tc, p, Suit.HEART)) {
				return p;
			}
		}
		return null;
	}

	private static boolean safeToUnloadHighSpade(Table_Cards tc, java.util.List<Card> myCards,
			int playPosition, PLAYER me, boolean leading) {
		// Holding the Queen myself makes unloading WORSE, not safer. The King
		// and Ace are the only cards standing between me and being forced to
		// eat my own Queen: lead them away and the Queen is bare, and the
		// next Spade trick I am made to win takes it.
		//
		// BUG FIX (2026-08-07, found in live play -- North held only the Ace
		// and Queen of Spades and led the Ace, stripping its own protection
		// for no reason when it had other suits available). The original
		// version treated "I hold the Queen" as PROOF the unload was safe, on
		// the reasoning that no opponent could then drop the Queen on me.
		// That much is true and entirely beside the point.
		if (CardCounter.holds(myCards, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		// Face up in this very trick -- winning it is exactly what to avoid.
		if (CardCounter.onTableThisTrick(tc, playPosition, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		// The Queen is gone for good, so a high Spade can no longer catch
		// anything.
		if (CardCounter.alreadyWonBySomeone(tc, CardValue.QUEEN, Suit.SPADE)) {
			return true;
		}
		// Nobody left to play holds a Spade, so nobody left can be holding
		// the Queen -- the Queen IS a Spade. Note this is the case that makes
		// leading a high Spade safe at all while the Queen is still live: if
		// an opponent could follow suit and held the Queen, they would gladly
		// drop it under my Ace.
		//
		// Those void seats will discard instead, and what they discard can be
		// Hearts that land on a trick I am about to win. That cost is
		// accepted deliberately: a Heart is 1 point and the Queen is 13, so
		// collecting a few Hearts to shed a card that would otherwise force
		// me to eat the Queen is a clear win. An earlier version of this
		// treated the Heart risk as a reason to refuse the unload on the
		// follow side, which had the trade exactly backwards.
		return leading
				? CardCounter.allOpponentsVoidIn(tc, me, Suit.SPADE)
				: CardCounter.allLaterSeatsVoidIn(tc, playPosition, Suit.SPADE);
	}

	/**
	 * Is LEADING a high Spade genuinely urgent, as opposed to merely harmless
	 * (2026-08-23)? Only while the Queen is still live and provably cannot
	 * reach me on this trick.
	 *
	 * WHY THIS SPLIT EXISTS. safeToUnloadHighSpade() answers "can the Queen
	 * land on this?", and the lead scorer used to treat a No as a +1200
	 * instruction to lead the card immediately. That reasoning is sound in
	 * exactly one of the two states that produce a No, and backwards in the
	 * other:
	 *
	 *   - Queen still live, but every opponent provably void in Spades: a
	 *     real, rare window. The card IS a standing liability -- some later
	 *     Spade trick forces me to win it with the Queen aboard -- and this
	 *     is a provable chance to be rid of it. Urgent, full weight.
	 *
	 *   - Queen already in somebody's won pile: there is no liability left to
	 *     unload. She cannot ride in on anything, because she is gone. Yet
	 *     this is the branch that fires almost every time in practice (the
	 *     other one is near-unreachable on a lead: if all three opponents are
	 *     void in Spades and the Queen is neither mine nor captured, there is
	 *     nowhere left for her to be). So the old scorer's dominant real-world
	 *     behaviour was to spend a boss Spade winning a trick nobody was
	 *     threatening it with -- collecting whatever void seats chose to
	 *     discard, and surrendering a guaranteed re-entry, for no benefit at
	 *     all. That case now routes to the graded tempo scorer instead.
	 *
	 * Package-private for HighSpadeTempoTest.
	 */
	static boolean highSpadeUnloadIsUrgent(Table_Cards tc, List<Card> myCards, PLAYER me) {
		// Same reason as safeToUnloadHighSpade: my own Queen makes leading
		// these away strictly worse, never safer.
		if (CardCounter.holds(myCards, CardValue.QUEEN, Suit.SPADE)) {
			return false;
		}
		if (CardCounter.alreadyWonBySomeone(tc, CardValue.QUEEN, Suit.SPADE)) {
			return false; // nothing left to be urgent about
		}
		return CardCounter.allOpponentsVoidIn(tc, me, Suit.SPADE);
	}

	/**
	 * How badly is this high Spade at risk of being STRANDED as my last one
	 * (2026-08-23, the user's refinement of TODO.md's control-card retention
	 * item)? Returns 0 for no risk, 1.0 for the full singleton case.
	 *
	 * The tempo argument for holding a high Spade after the Queen is gone
	 * assumes the card stays OPTIONAL -- that I choose the trick it goes into.
	 * That assumption dies the moment it becomes my only Spade: the next Spade
	 * lead forces it out, and if it is boss I win a trick I did not choose, at
	 * a time somebody else picked, taking whatever the void seats have decided
	 * to throw. Late in a hand that is exactly when their throws are Hearts.
	 *
	 * Three conditions, all required, each one the user's own:
	 *   1. The card is provably boss -- nothing above it unaccounted for.
	 *      A King with the Ace still out is NOT a forced take: it can lose
	 *      the trick perfectly happily, which is a fine outcome. This is the
	 *      "Ace-fallen check", and it is why the Ace itself always qualifies
	 *      and the King only sometimes does.
	 *   2. I am at or below HIGH_SPADE_STRANDING_COUNT Spades. A singleton
	 *      scores full; a doubleton scores half, since one low Spade still
	 *      buys me one duck.
	 *   3. Spades are still live somewhere -- if every remaining Spade is in
	 *      my own hand, nobody can lead the suit and nothing can force
	 *      anything. No risk, no reason to spend the card.
	 */
	private static double highSpadeStrandingRisk(LeadContext ctx, Card candidate) {
		boolean provablyBoss = !CardCounter.anyAboveUnaccounted(
				ctx.tc, ctx.legalOptions, 1, Suit.SPADE, candidate.cardValue);
		if (!provablyBoss) {
			return 0;
		}
		int mySpades = AI.countOfSuit(ctx.legalOptions, Suit.SPADE);
		if (mySpades > HIGH_SPADE_STRANDING_COUNT) {
			return 0;
		}
		boolean[] accounted = CardCounter.accountedRanks(ctx.tc, ctx.legalOptions, 1, Suit.SPADE);
		boolean spadesStillLive = false;
		for (int i = 0; i < accounted.length; i++) {
			if (!accounted[i]) {
				spadesStillLive = true;
				break;
			}
		}
		if (!spadesStillLive) {
			return 0;
		}
		return mySpades <= 1 ? 1.0 : 0.5;
	}

	/**
	 * Do I have anything to actually SPEND the lead on (2026-08-23)? The whole
	 * case for holding a high Spade as a re-entry card rests on this: a
	 * guaranteed trick win is only an asset if winning it at a moment of my
	 * choosing buys me something. Absent a use, the "tempo" is imaginary and
	 * the card is just a Heart magnet waiting to be forced out.
	 *
	 * Two live uses, both already first-class concerns elsewhere in this file
	 * rather than new inventions:
	 *   - a Jack-of-Diamonds hunt I can realistically sustain, which needs the
	 *     lead to fire its pull (HandEvaluator.hasJackCaptureChance, added the
	 *     same day for TODO.md's other open Jack item);
	 *   - a shooter who needs stopping, where seizing the lead is how a
	 *     defender controls when its stoppers get played
	 *     (MoonThreat.threateningShooter).
	 */
	private static boolean leadHasALiveUse(LeadContext ctx) {
		if (MoonThreat.threateningShooter(ctx.tc, ctx.player) != null) {
			return true;
		}
		return HandEvaluator.hasJackCaptureChance(ctx.legalOptions, ctx.tc);
	}

	// ====================================================================
	// Lead scorers
	// ====================================================================

	private static final List<LeadCardScorer> LEAD_SCORERS = buildLeadScorers();

	private static List<LeadCardScorer> buildLeadScorers() {
		List<LeadCardScorer> scorers = new ArrayList<LeadCardScorer>();

		// Endgame solver (2026-10-08). The look-ahead runs once per decision
		// in pickLead(); this just hands out what it found. COUNTED_CERTAINTIES
		// because it is counting taken to its conclusion -- a seat not
		// counting cards this hand doesn't get it.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
			public double score(Card candidate, LeadContext ctx) {
				Double b = ctx.endgameBonus.get(candidate);
				return b == null ? 0 : b.doubleValue();
			}
		});

		// Tier 1 -- moon shot: run the top of the longest suit repeatedly.
		// A lone high card off-suit wins one trick then leaves nothing to
		// lead with; the long suit sustains control and keeps forcing
		// opponents to discard hearts onto the shooter.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT) {
					return 0;
				}
				double s = (candidate.suit == ctx.longestSuit) ? MOON_SHOT_SUIT : 0;
				return s + RANK_TIE_BREAK * rank(candidate); // highest within that suit
			}
		});

		// SIDE-SUIT LOOK: SHED A LOSER EARLY (2026-11-08, Wayne). Lead the lowest
		// card of a side suit that is not a sure winner while everyone still has
		// to follow it, so nobody can throw a Heart (or the Queen) on the trick
		// the shooter gives up. Never a low Spade while the Queen may be in
		// another hand (its holder drops her on whoever wins).
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || !ctx.player.moonByLook || candidate.suit == Suit.HEART) {
					return 0;
				}
				return isSafeLoserToShed(ctx, candidate) ? MOON_LOOK_SHED_LOSER - RANK_TIE_BREAK * rank(candidate) : 0;
			}
		});

		// SIDE-SUIT LOOK: TEST THE ONE HEART (2026-11-08, Wayne's rule). A look
		// shooter holds at most one Heart that is not a sure winner (see
		// HandEvaluator.lookHeartsOk). Lead it as soon as Hearts may be led,
		// early, while the others still play under it ("people think you're
		// trying to get rid of your hearts"). It can only be tested once: if
		// someone beats it the moon is over and AI.moonAttemptAbandoned()
		// turns play to damage control.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || !ctx.player.moonByLook || candidate.suit != Suit.HEART) {
					return 0;
				}
				if (pullsHeartsLate(ctx)) {
					// Wayne, 2026-11-08 (A K 10 with a lot of winners): "pull all the
					// winners and use the A K late to pull remaining hearts and then
					// play 10 last hoping that no one has hearts left or low ones."
					if (isUntestedHeart(ctx, candidate)) {
						return onlyHeartsLeft(ctx) ? 0 : -MOON_LOOK_HOLD_HEART_LAST;
					}
					return holdsSideSuitWinner(ctx) ? -MOON_LOOK_HEARTS_LATE : 0;
				}
				return isUntestedHeart(ctx, candidate) ? MOON_LOOK_TEST_HEART : 0;
			}
		});

		// MOON-OFFENSE: HOLD SPADE CONTROL BACK ON A BORDERLINE HAND
		// (2026-08-17, user request from a deep-dive strategy review).
		// Tier 1 above will happily run the Ace of Spades the moment
		// Spades is the longest LEGAL suit -- which, early in a hand
		// before Hearts are broken, is often forced regardless of whether
		// Spades is genuinely the shooter's strong suit. That is exactly
		// right for a shooter already holding the Ace of Hearts: nothing
		// left to wait for, so running Spades to draw the Queen out is
		// pure upside. It is NOT right for the weaker King-of-Hearts route,
		// and real Hearts strategy consensus is unusually consistent on
		// this specific point (web-researched 2026-08-16/17):
		//   - A suspected shooter is USUALLY handed the Queen for free
		//     anyway -- defenders would rather deny a Heart (costs them 1
		//     if wrong) than contest the Queen with their own high Spade
		//     (costs them 13 if wrong), so an active hunt often spends a
		//     guaranteed trick solving a problem that resolves itself.
		//   - "Leading the Ace and King of Spades in sequence" is called
		//     out by name as the kind of obvious play that telegraphs a
		//     shooter's intentions to the table.
		//   - The standard advice for a shooter's own Spade control is to
		//     HOLD it and catch the Queen passively on a trick somebody
		//     ELSE leads, not to lead it yourself.
		//   - Guides are explicit that a hand missing the strong markers
		//     (the Ace of Hearts chief among them) should default to
		//     caution, not commitment.
		// The live hand that prompted this: North held King-high Hearts
		// (no Ace), led the Ace of Spades on trick 2 purely because Hearts
		// were still unbroken and Spades was its next-longest legal suit,
		// and captured the Queen on trick 2 -- locking in +13 of exposure
		// before it was ever known whether the Ace of Hearts would surface
		// on its own.
		//
		// Stands down the instant either uncertainty resolves -- once this
		// seat holds the real Ace of Hearts (Player.moonShotHasAceOfHearts,
		// set alongside the commitment itself, see Deal_Hand), or once the
		// Ace has already fallen to ANYONE -- at which point there is
		// nothing left to wait for and ordinary Tier 1 takes back over
		// undiminished.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.ACE && candidate.cardValue != CardValue.KING) {
					return 0;
				}
				if (ctx.player.moonByLook) {
					// Side-suit look (2026-11-08, Wayne): hold the Spades back only
					// until the one Heart is tested; then lead them to catch the Queen.
					if (hasUntestedHeart(ctx)) {
						return -MOON_HOLD_SPADE_CONTROL;
					}
					boolean queenOut = !CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.QUEEN, Suit.SPADE)
							&& !CardCounter.holds(ctx.fullHand != null ? ctx.fullHand : ctx.legalOptions,
									CardValue.QUEEN, Suit.SPADE);
					return queenOut ? MOON_LOOK_SPADE_LEAD + RANK_TIE_BREAK * rank(candidate) : 0;
				}
				if (ctx.player.moonShotHasAceOfHearts) {
					return 0; // real Heart control -- no reason to hold back
				}
				if (CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.ACE, Suit.HEART)) {
					return 0; // the uncertainty is already resolved, one way or the other
				}
				return -MOON_HOLD_SPADE_CONTROL;
			}
		});

		// Tier 1 -- moon shot: CASH A BOSS HEART. The shooter's own Hearts
		// can only be captured by winning the trick they're played in, so
		// once counting shows a held Heart cannot be beaten (every Heart
		// above it is captured or in this hand), lead it now, while control
		// is certain. Highest boss first: pulling the opponents' Hearts
		// under it promotes the rest of the hand's Hearts toward boss too.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || candidate.suit != Suit.HEART) {
					return 0;
				}
				boolean boss = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.HEART, candidate.cardValue);
				return boss ? MOON_CASH_BOSS_HEART + RANK_TIE_BREAK * rank(candidate) : 0;
			}
		});

		// Tier 1 -- moon shot: PULL THE ACE OF HEARTS OUT (2026-08-16, user
		// request from a live-hand review). The shooter's own Hearts are
		// safe -- nobody else can take them without winning the trick they
		// sit in -- but the Ace of Hearts in ANOTHER seat's hand is the one
		// uncontrolled threat that can end the shot at any moment, and
		// nothing about running the longest suit or cashing a boss Heart
		// does anything to remove it.
		//
		// The mechanism: lead a card in some OTHER suit that is (a)
		// PROVABLY going to win -- nothing left unaccounted for outranks
		// it -- and (b) a suit some seat is already KNOWN to be void in
		// while NOT known void in Hearts, so that seat might still be
		// holding the Ace. A free discard is the Ace's best exit for a
		// seat with no reason to keep it, and because this lead is
		// guaranteed to win, whatever lands on it -- Ace included -- goes
		// into the SHOOTER's own pile, not some third seat's. That last
		// part is not optional: shooting the moon means capturing every
		// Heart PERSONALLY, so an Ace that surfaces on a trick anyone else
		// wins is exactly as fatal to the attempt as never flushing it at
		// all. (This is why the user's own first instinct -- lead the King
		// of Diamonds hoping for a drop -- would not have worked in the
		// hand that prompted this: the King was not a guaranteed winner,
		// another seat held the Ace of Diamonds, and a dropped Ace of
		// Hearts would have gone to THEM, not the shooter.)
		//
		// Deliberately never fires on a guess -- see voidSeatThatMightHold
		// TheAce(), the exact mirror of aVoidSeatCouldHoldTheQueen(). A
		// speculative lead into an UNCONFIRMED void risks losing the trick
		// (and the lead) for nothing, which is precisely what leading an
		// unproven King of Diamonds would have risked.
		//
		// Gated on Player.takesMoonGambleThisHand -- this is still a
		// gamble even once the void is proven (the void seat might simply
		// not be holding the Ace, or might hold it and choose not to part
		// with it), so it is an opt-in per-individual trait rather than
		// something every moon-shooter tries automatically. See
		// Deal_Hand.effectiveMoonGamblePercent() for how a trailing seat's
		// effective rate climbs above its roster baseline.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || candidate.suit == Suit.HEART) {
					return 0;
				}
				if (!ctx.player.takesMoonGambleThisHand) {
					return 0;
				}
				if (CardCounter.holds(ctx.legalOptions, CardValue.ACE, Suit.HEART)
						|| CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.ACE, Suit.HEART)) {
					return 0; // nothing left to flush out
				}
				boolean guaranteedWinner = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, candidate.suit, candidate.cardValue);
				if (!guaranteedWinner) {
					return 0; // must win it myself or the Ace lands in a THIRD seat's pile
				}
				PLAYER target = voidSeatThatMightHoldTheAce(ctx.tc, ctx.player.tbl_player, candidate.suit);
				if (target == null) {
					return 0;
				}
				double bonus = EndGame.isCurrentLeader(ctx.tc, target) ? MOON_ACE_FLUSH_LEADER_BONUS : 0;
				// Cheapest guaranteed winner first, same as JACK_PULL_LEAD --
				// no reason to spend an Ace here when a King would do.
				return MOON_ACE_FLUSH_LEAD + bonus - RANK_TIE_BREAK * rank(candidate);
			}
		});

		// Tier 3 -- Aggressive leads its highest card, suit irrelevant.
		//
		// POINT-SAFETY GUARD (2026-08-16, from live play): trick-hungry is
		// not point-hungry. The bare rank bonus used to apply to EVERY
		// card, and because Aggressive's BASIC_SAFETY weight is 0.5, the
		// halved danger penalties below could no longer outvote it -- an
		// all-Hearts East led its boss ACE of Hearts (a guaranteed
		// self-capture, +3 points on the trick) purely because it ranked
		// highest, and the same arithmetic would volunteer the Queen of
		// Spades (+600 rank vs the halved -450 penalty) or an Ace of
		// Spades into a live Queen. The follow-side Aggressive take
		// scorer already refuses tricks that collect points; this is the
		// same rule on the lead side: no rank bonus for a card whose lead
		// invites points onto the trick it's about to win.
		//
		// REMOTE-MOON-CHANCE GUARD (2026-08-16, user refinement): "trick-
		// hungry" shouldn't mean volunteering a HIGH Heart with no upside
		// either. Live hand: West (3 Hearts, no Ace/King -- no realistic
		// shot at a moon) led the Queen of Hearts purely because it
		// out-ranked its other options, and the Ace beat it. The rank
		// bonus for a Heart now requires HandEvaluator.hasMoonHeartControl
		// -- the SAME hard gate the real moon-attempt decision already
		// uses for "does this hand have a genuine chance." With no
		// remote chance, Hearts get no rank bonus at all: Aggressive
		// prefers a high card in any OTHER suit instead, and if forced
		// into a Heart anyway, the graded danger scorer below (still
		// active, unaffected by this guard) naturally picks the LOWEST
		// one -- exactly "play one of the small hearts instead."
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.AGGRESSIVE) {
					return 0;
				}
				if (Rules.isQueenOfSpades(candidate)) {
					return 0;
				}
				if (candidate.suit == Suit.SPADE
						&& (candidate.cardValue == CardValue.KING || candidate.cardValue == CardValue.ACE)
						&& highSpadeLeadRisksTheQueen(ctx.tc, ctx.legalOptions, ctx.player, ctx.queenOfSpadesGone)) {
					return 0;
				}
				if (candidate.suit == Suit.HEART) {
					if (!CardCounter.anyAboveUnaccounted(
							ctx.tc, ctx.legalOptions, 1, Suit.HEART, candidate.cardValue)) {
						return 0; // boss Heart -- leading it is guaranteed self-capture
					}
					if (!HandEvaluator.hasMoonHeartControl(ctx.legalOptions)) {
						return 0; // no remote moon chance -- don't chase Hearts for rank
					}
				}
				if (othersAllOutOf(ctx, candidate.suit) && pointsStillOut(ctx)) {
					return 0; // a sure win that everyone else dumps Hearts on (2026-11-09)
				}
				return AGGRESSIVE_LEAD_RANK * rank(candidate);
			}
		});

		// ================================================================
		// RESTRUCTURED 2026-08-07: the baseline lead used to be "pick the
		// safest longest SUIT, then play its lowest card" -- two steps, with
		// the suit-chooser blind to which card its choice would actually
		// play. Three live-play bugs shared that exact shape (lone Ace of
		// Spades into a live Queen; Jack of Diamonds into a waiting King;
		// Queen-holder stripping its own Ace protection). Everything below
		// scores CARDS directly: a suit-length pull toward long suits, a
		// lowest-card pull within them, and one graded penalty per danger.
		// When a hand is squeezed and every option is bad, the sums pick the
		// least-bad card -- there is no exclusion chain left to fall out of.
		// ================================================================

		// Tier 3 -- baseline prefers the suit it holds most of. Per-card now:
		// each candidate is pulled toward being led with a strength equal to
		// how many of its suit are held. This IS the old longest-suit rule,
		// just expressed where the dangers below can push back against it.
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE) {
					return 0;
				}
				return LEAD_SUIT_LENGTH * AI.countOfSuit(ctx.legalOptions, candidate.suit);
			}
		});

		// Tier 4 -- baseline leads low (bleeding a suit dry cheaply, so
		// later discards come free).
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE) {
					return 0;
				}
				return -RANK_TIE_BREAK * rank(candidate);
			}
		});

		// EXIT-CARD PRESERVATION (2026-08-15, TODO.md item): the "lead low"
		// rule just above has no memory of how many safe cards are left
		// hand-wide -- left alone, it burns through every reserve candidate
		// from the bottom up, suit after suit, until nothing but boss cards
		// remain. Only fires when `candidate` would be my LAST reserve card
		// (see reserveCardCount) -- with two or more still in hand, using
		// one up is exactly the "later discards come free" benefit the lead-
		// low rule is going for, and this stays out of the way. Sized to
		// discourage, not forbid: a genuine danger elsewhere in Tier 2 still
		// loses to the last reserve card being the least-bad choice.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.BASIC_SAFETY;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE) {
					return 0;
				}
				if (!isReserveCandidate(ctx.tc, ctx.legalOptions, candidate)) {
					return 0; // provably the boss of its suit -- no reserve value to spend
				}
				if (reserveCardCount(ctx.tc, ctx.legalOptions) > 1) {
					return 0; // plenty of reserve left even after this one goes
				}
				return -EXIT_CARD_RESERVE_PENALTY;
			}
		});

		// DANGER: leading the Queen of Spades. If everyone ducks -- and
		// everyone will try -- the trick is mine and the 13 points with it.
		// The single worst card to volunteer, so it carries the largest
		// penalty. The counted-out Queen lead above still overrides this
		// when counting PROVES someone must take it off me. Guarded off
		// MOON_SHOT only: a shooter leading the Queen to win it early is a
		// legitimate play (seen in live testing and praised).
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.BASIC_SAFETY;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || !Rules.isQueenOfSpades(candidate)) {
					return 0;
				}
				return -LEAD_QUEEN_OF_SPADES;
			}
		});

		// DANGER: leading the King or Ace of Spades before the Queen is
		// captured. Two failure modes, one penalty: if an opponent holds the
		// Queen they drop it on the trick this card is about to win; if *I*
		// hold the Queen, leading away my only higher Spade strips the
		// protection that was keeping me from eating it. Either way the card
		// must not be volunteered until the Queen is in a won pile -- at
		// which point the safe-unload scorer above actively rewards shedding
		// it. Replaces AI.wouldLeadHighSpadeIntoLiveQueen(), which only
		// fired when a K/A happened to be the LOWEST Spade held. See
		// highSpadeLeadRisksTheQueen()'s own doc for the moon-shooter
		// exception this danger stands down for.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.BASIC_SAFETY;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.KING && candidate.cardValue != CardValue.ACE) {
					return 0;
				}
				// A Queen holder with real length leading its own top Spades is
				// the bleed-out, not a danger -- nobody else can drop her on it,
				// and the low Spades left behind keep her protected. See
				// queenBleedApplies() and the bleed scorer below.
				if (queenBleedApplies(ctx)) {
					return 0;
				}
				return highSpadeLeadRisksTheQueen(ctx.tc, ctx.legalOptions, ctx.player, ctx.queenOfSpadesGone)
						? -LEAD_HIGH_SPADE_LIVE_QUEEN : 0;
			}
		});

		// QUEEN-OF-SPADES BLEED-OUT (2026-10-09). Top-down: the highest of my
		// Ace/King first, then -- once neither is left -- my lowest Spade.
		// Never the Queen herself (her own lead penalty stands) and never a
		// low Spade while a top one is still waiting, so the order is the
		// one in Mark's tip: Ace, King, then the low ones, Queen last.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.QUEEN_FLUSH;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.SPADE || Rules.isQueenOfSpades(candidate) || !queenBleedApplies(ctx)) {
					return 0;
				}
				Card top = null;
				Card lowest = null;
				for (Card c : ctx.legalOptions) {
					if (c.suit != Suit.SPADE) {
						continue;
					}
					if (c.cardValue.compareTo(CardValue.QUEEN) > 0) {
						if (top == null || c.cardValue.compareTo(top.cardValue) > 0) {
							top = c;
						}
					} else if (c.cardValue.compareTo(CardValue.QUEEN) < 0) {
						if (lowest == null || c.cardValue.compareTo(lowest.cardValue) < 0) {
							lowest = c;
						}
					}
				}
				if (top != null) {
					return candidate == top ? QUEEN_BLEED_HIGH_LEAD : 0;
				}
				return candidate == lowest ? QUEEN_BLEED_LOW_LEAD : 0;
			}
		});

		// DANGER: volunteering a Heart -- but the real danger is WINNING the
		// trick, not the lead itself. A heart lead loses only if somebody
		// plays a higher Heart, so:
		//   - If counting shows every Heart above this one is already
		//     accounted for, this card is the boss and the trick is
		//     guaranteed mine -- a certain self-inflicted point (plus any
		//     void-seat discards riding along). Heavy flat penalty.
		//   - Otherwise the penalty grows with rank: the 2 is nearly certain
		//     to lose (an opponent eats 1 point -- cheap, fine, sometimes
		//     the best lead in the hand), while a King will usually win.
		// Originally a flat penalty; regraded 2026-08-07 after the user
		// pointed out that a hand full of low Hearts SHOULD lead them --
		// knowing you cannot win the trick is exactly what makes it safe.
		// Applies to AGGRESSIVE too; trick-hungry is not the same as
		// handing yourself points.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.BASIC_SAFETY;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || candidate.suit != Suit.HEART) {
					return 0;
				}
				boolean guaranteedToWin = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.HEART, candidate.cardValue);
				return guaranteedToWin
						? -LEAD_HEART_GUARANTEED_WIN
						: -(LEAD_HEART_BASE + LEAD_HEART_RANK * rank(candidate));
			}
		});

		// ABANDONED MOON: SHED THE LOW HEARTS (2026-10-17, user rule from live
		// hand 4). A committed shooter whose moon has died is holding a pile of
		// Hearts it no longer wants to collect. The way out is to lead the LOW
		// ones -- they lose the trick, hand the points to someone else, and get
		// this seat out of the lead -- never to cash the boss Hearts, which win
		// the trick and invite the Queen of Spades onto it. Boss Hearts (nothing
		// above them left) get no bonus; the lowest Heart gets the most.
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.HEART || !AI.moonAttemptAbandoned(ctx.tc, ctx.player)) {
					return 0;
				}
				boolean boss = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.HEART, candidate.cardValue);
				return boss ? 0 : ABANDONED_MOON_SHED - 10 * rank(candidate);
			}
		});

		// DANGER: leading a suit some other seat is already known void in --
		// their free discard can load the trick with the Queen or a Heart.
		// Mild: only a provably-losing low Heart ranks as a cheaper
		// sacrifice. (For the one case where leading into a void is the
		// POINT -- a moon shooter's void -- the redirect scorer below
		// outbids this on purpose.)
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.VOID_AWARENESS;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE) {
					return 0;
				}
				return AI.anyOtherSeatVoidIn(ctx.tc, ctx.player.tbl_player, candidate.suit)
						? -LEAD_KNOWN_VOID
						: 0;
			}
		});

		// STRATEGIC COST: leading Diamonds while holding a Jack-of-Diamonds
		// asset worth protecting (see AI.shouldProtectDiamondSuit()). Costs
		// no points directly, so it is the cheapest concern here -- broken
		// first when a hand is squeezed. The Jack-pull scorer above overrides
		// it outright when a high Diamond is PROVEN to win.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || candidate.suit != Suit.DIAMOND) {
					return 0;
				}
				return ctx.protectDiamonds ? -LEAD_PROTECTED_DIAMOND : 0;
			}
		});

		// COUNTED-OUT QUEEN OF SPADES LEAD. Leading the Queen is normally the
		// worst play available -- if everyone else follows with a lower Spade
		// you win your own trick and eat the 13 points. But once counting
		// shows every Spade below the Queen is either already played or in my
		// own hand, the only Spades left anywhere are the King and Ace.
		// Whoever holds one must follow suit and has nothing low enough to
		// duck with, so they are forced to beat the Queen and take it. This
		// hands the Queen off deliberately instead of waiting for a safe
		// discard that may never come.
		//
		// The second condition matters as much as the first: at least one of
		// the King/Ace must still be OUT there. If both are already gone, no
		// opponent can beat the Queen and leading it wins my own trick --
		// exactly the disaster this tactic is meant to avoid.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
			public double score(Card candidate, LeadContext ctx) {
				// Guard added 2026-08-07: this tactic exists to HAND the Queen
				// to whoever must beat it -- for a moon shooter that is the
				// exact opposite of the goal (the shooter needs to WIN her),
				// and this scorer's Tier-1 weight would have overridden every
				// moon preference and torpedoed the attempt.
				if (ctx.mode == PlayMode.MOON_SHOT || !Rules.isQueenOfSpades(candidate)) {
					return 0;
				}
				boolean nothingCanDuck = CardCounter.allBelowAccountedFor(
						ctx.tc, ctx.legalOptions, 1, Suit.SPADE, CardValue.QUEEN);
				boolean someoneMustBeatIt = CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.SPADE, CardValue.QUEEN);
				return (nothingCanDuck && someoneMustBeatIt) ? COUNTED_QUEEN_LEAD : 0;
			}
		});

		// COUNTED-OUT JACK OF DIAMONDS LEAD. The Jack is worth -10 to
		// whoever captures it, so unlike every other danger card the goal is
		// to WIN it. Lead it the moment the trick is provably mine, either
		// because nothing left outranks it or because nobody can follow at
		// all. Until then AI's Jack-protection logic keeps holding it back.
		//
		// TACTIC FAMILY (2026-08-16, user correction from a live-hand
		// review): tagged JACK_GUARD, not JACK_HUNT, even though it's a
		// LEAD -- the two prior scorers above chase the Jack out of an
		// OPPONENT's hand, genuinely risky since the outcome isn't certain
		// until the trick resolves. This one fires only once counting has
		// already PROVEN the trick is safe, which is the same shape as
		// ordinary Jack protection, just executed rather than held. The
		// hand that surfaced this: a Cautious seat held the Jack plus the
		// Ace, King and Queen of Diamonds -- total, guaranteed control of
		// the suit from the deal onward -- and led its weakest Diamond
		// instead, losing the trick and the lead for nothing, purely
		// because JACK_HUNT's "no flashy plays" weight of 0 blocked this
		// scorer too. A Cautious seat should absolutely still cash a Jack
		// it already has locked down; what it skips is the SPECULATIVE
		// pursuit two scorers up. User's own framing: "cash the Jack out
		// on first lead... someone might dump a Queen since they might be
		// void of Diamonds... then lead your lowest diamonds to get
		// someone else to lead another suit" -- the second half needs no
		// new code, since the ordinary longest-suit-then-lead-low baseline
		// (Tier 3/4) already keeps running a suit this dominant on its own
		// once the Jack itself is out of the way.
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.DIAMOND || candidate.cardValue != CardValue.JACK) {
					return 0;
				}
				boolean nothingOutranksIt = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.DIAMOND, CardValue.JACK);
				boolean nobodyCanFollow = CardCounter.allOpponentsVoidIn(
						ctx.tc, ctx.player.tbl_player, Suit.DIAMOND);
				return (nothingOutranksIt || nobodyCanFollow) ? COUNTED_JACK_CAPTURE : 0;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
		});

		// NEVER LEAD THE JACK OF DIAMONDS AWAY. Leading it hands the -10 to
		// whoever wins the trick, so it is only ever correct when the trick
		// is provably mine -- which is precisely what the counted-out Jack
		// lead above already rewards.
		//
		// BUG FIX (2026-08-07, found in live play -- East led the Jack while
		// South held both the King and Ace of Diamonds and simply took it).
		// Two things left it unguarded. The giveaway penalty added earlier
		// the same day was a FOLLOW scorer only, so leading was never
		// checked. And shouldProtectDiamondSuit(), which steers the baseline
		// away from Diamonds, returns false for an UNPROTECTED held Jack --
		// so Diamonds stayed eligible as a lead suit, and since the Jack was
		// the lowest Diamond held, "lead the lowest of the chosen suit"
		// picked it. Same shape as the lone-Ace-of-Spades lead: the
		// lowest-card rule reaching for a card that should never be
		// volunteered.
		scorers.add(new LeadCardScorer() {
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.DIAMOND || candidate.cardValue != CardValue.JACK) {
					return 0;
				}
				boolean nothingOutranksIt = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.DIAMOND, CardValue.JACK);
				boolean nobodyCanFollow = CardCounter.allOpponentsVoidIn(
						ctx.tc, ctx.player.tbl_player, Suit.DIAMOND);
				// Provably winning is handled (and rewarded) above; anything
				// short of that is a gift.
				return (nothingOutranksIt || nobodyCanFollow) ? 0 : -JACK_GIVEAWAY_PENALTY;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
		});

		// PULL THE JACK OF DIAMONDS OUT. Holding the Queen, King or Ace of
		// Diamonds while the Jack is still out there is a standing
		// opportunity: lead the high Diamond, force everyone holding Diamonds
		// to follow, and either the Jack falls into a trick I win (-10) or a
		// round of Diamonds gets stripped out and the holder is one card
		// closer to having nothing left to hide it behind.
		//
		// Only fires on a card guaranteed to win -- nothing higher in the
		// suit is still unaccounted for -- since leading a high Diamond that
		// somebody beats hands them the Jack instead.
		//
		// The risk is accepted on purpose: seats void in Diamonds will
		// discard onto a trick I am about to win, and that can be a Heart or
		// even the Queen of Spades. Chasing the Jack is worth that exposure.
		// Note this deliberately overrides shouldProtectDiamondSuit(), which
		// steers the baseline AWAY from leading Diamonds to preserve exactly
		// these cards as capture weapons -- a weapon that is never fired is
		// not doing any work, and this scorer only fires when it is certain
		// to win the trick.
		//
		// Not for a player already holding the Jack: keeping it protected is
		// a different job, handled by the counted-out Jack lead above.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_HUNT;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.DIAMOND
						|| candidate.cardValue.compareTo(CardValue.QUEEN) < 0) {
					return 0;
				}
				if (CardCounter.holds(ctx.legalOptions, CardValue.JACK, Suit.DIAMOND)) {
					return 0; // I have it -- protect it, don't hunt it
				}
				boolean jackStillOut = !CardCounter.accountedRanks(
						ctx.tc, ctx.legalOptions, 1, Suit.DIAMOND)[CardValue.JACK.ordinal()];
				if (!jackStillOut) {
					return 0;
				}
				// VIABILITY GATE (2026-08-16): winning this trick is certain,
				// but capturing the Jack is NOT -- the holder may simply
				// follow with a low Diamond. What IS certain is that every
				// void seat gets a free discard onto a trick I've committed
				// to winning. Don't chase a maybe--10 into a live Queen.
				// See jackPullRisksTheQueen().
				if (jackPullRisksTheQueen(ctx.tc, ctx.legalOptions,
						ctx.player.tbl_player, ctx.queenOfSpadesGone)) {
					return 0;
				}
				// VIABILITY GATE, positive half (2026-08-23, TODO.md's
				// "Jack-of-Diamonds realistic chance viability dial" --
				// the half the 2026-08-16 Queen-trap guard left open).
				// The guard above answers "will this hunt hurt me"; this
				// answers "can it actually work". A singleton boss Diamond
				// buys exactly ONE pull, and if the Jack simply ducks under
				// it, the weapon is spent and every void seat got a free
				// Heart discard onto a trick I committed to winning, for
				// nothing. See HandEvaluator.hasJackCaptureChance().
				if (!HandEvaluator.hasJackCaptureChance(ctx.legalOptions, ctx.tc)) {
					return 0;
				}
				boolean guaranteedWinner = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, Suit.DIAMOND, candidate.cardValue);
				// Cheapest guaranteed winner first -- spend the Queen before
				// the King, the King before the Ace.
				return guaranteedWinner ? JACK_PULL_LEAD - RANK_TIE_BREAK * rank(candidate) : 0;
			}
		});

		// URGENT HIGH-SPADE UNLOAD (lead side). Holding the King or Ace of
		// Spades WHILE THE QUEEN IS STILL LIVE is a liability: sooner or later
		// a Spade trick comes along that they are forced to win, and the Queen
		// rides in on it. When a provable window opens to shed one -- every
		// opponent void in Spades, so she cannot possibly be dropped on me --
		// take it.
		//
		// NARROWED 2026-08-23 (see highSpadeUnloadIsUrgent for the full
		// reasoning): this used to fire on safeToUnloadHighSpade(), which is
		// also true once the Queen has simply been captured. In that state
		// there is no liability left to unload at all, so a +1200 "lead it
		// now" was spending a boss Spade to win a trick for no reason. That
		// case is now the tempo scorer's, immediately below.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.KING && candidate.cardValue != CardValue.ACE) {
					return 0;
				}
				return highSpadeUnloadIsUrgent(ctx.tc, ctx.legalOptions, ctx.player.tbl_player)
						? COUNTED_SAFE_HIGH_SPADE
						: 0;
			}
		});

		// HIGH-SPADE TEMPO ONCE THE QUEEN IS GONE (2026-08-23, TODO.md's
		// "Control-card retention" item plus the user's live-play refinement
		// of it). Once the Queen sits in somebody's won pile, a King or Ace of
		// Spades stops being a liability and becomes a guaranteed trick win --
		// i.e. a re-entry card that lets its holder choose which trick to take
		// and when to seize the lead. Shedding it on reflex the instant it
		// turns "safe" throws that away; hoarding it blindly walks into the
		// opposite trap. Both directions are priced here, and they cancel when
		// they genuinely conflict:
		//
		//   HOLD  when I have something to spend the lead on -- a sustainable
		//         Jack-of-Diamonds hunt, or a shooter who needs stopping (see
		//         leadHasALiveUse). This is the tempo value the original TODO
		//         item was about.
		//
		//   SHED  as the card nears being my LAST Spade while Spades are still
		//         live and it is provably boss (see highSpadeStrandingRisk).
		//         This is the user's refinement, and it is the more important
		//         half: a stranded boss Spade is not a tempo card at all,
		//         because the next Spade lead forces it out on somebody else's
		//         schedule and hands me whatever the void seats threw. Getting
		//         it out early -- while opponents still hold cards in every
		//         suit and have little to throw -- is strictly cheaper than
		//         being made to take it late among the Hearts.
		//
		// Deliberately does nothing at all for a seat not counting cards this
		// hand (COUNTED_CERTAINTIES): every input here is a counting
		// deduction, and the old scorer was gated the same way, so this
		// changes nothing for non-counters -- their high Spades simply fall
		// through to ordinary lead scoring, which holds them. Also stands down
		// in MOON_SHOT mode, where a shooter's Spade control is governed by
		// its own dedicated scorers (2026-08-17).
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || !ctx.queenOfSpadesGone) {
					return 0;
				}
				if (candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.KING && candidate.cardValue != CardValue.ACE) {
					return 0;
				}
				double s = HIGH_SPADE_STRANDING_SHED * highSpadeStrandingRisk(ctx, candidate);
				if (leadHasALiveUse(ctx)) {
					s -= HIGH_SPADE_TEMPO_HOLD;
				}
				return s;
			}
		});

		// FISH FOR THE QUEEN (2026-11-05, Queen-phobic signature, Strategy
		// QUEEN_PHOBIA FISH/BOTH; nobody else uses it). With the Queen still out,
		// not in my hand, and no Ace or King of Spades to be caught holding, lead
		// my lowest Spade: every round of Spades drains the Queen holder's cover
		// until she has to fall -- onto someone else's higher Spade, since mine
		// is the low one. Pointless once every other seat is out of Spades.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.QUEEN_FLUSH;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.queenOfSpadesGone || candidate.suit != Suit.SPADE
						|| !Strategy.fishesForQueen(ctx.player)) {
					return 0;
				}
				if (CardCounter.holds(ctx.legalOptions, CardValue.QUEEN, Suit.SPADE)
						|| CardCounter.holds(ctx.legalOptions, CardValue.KING, Suit.SPADE)
						|| CardCounter.holds(ctx.legalOptions, CardValue.ACE, Suit.SPADE)) {
					return 0;
				}
				if (CardCounter.allOpponentsVoidIn(ctx.tc, ctx.player.tbl_player, Suit.SPADE)) {
					return 0;
				}
				return candidate == lowestOfSuit(ctx.legalOptions, Suit.SPADE) ? SIGNATURE_FISH_LEAD : 0;
			}
		});

		// DIAMOND PRESSURE (2026-11-05, Jack Hunter signature, Strategy
		// JACK_CHASE PRESSURE/BOTH; nobody else uses it). With the Jack of
		// Diamonds still out and not mine, and a Diamond above the Jack in my
		// hand to capture her later, lead my lowest Diamond below the Jack:
		// each round strips the holder's cover, so she ends up falling under my
		// high Diamond instead of hiding until a void discard.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_HUNT;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || candidate.suit != Suit.DIAMOND
						|| candidate.cardValue.compareTo(CardValue.JACK) >= 0
						|| !Strategy.pressuresJack(ctx.player)) {
					return 0;
				}
				if (CardCounter.alreadyWonBySomeone(ctx.tc, CardValue.JACK, Suit.DIAMOND)
						|| CardCounter.holds(ctx.legalOptions, CardValue.JACK, Suit.DIAMOND)) {
					return 0;
				}
				if (!CardCounter.holds(ctx.legalOptions, CardValue.ACE, Suit.DIAMOND)
						&& !CardCounter.holds(ctx.legalOptions, CardValue.KING, Suit.DIAMOND)
						&& !CardCounter.holds(ctx.legalOptions, CardValue.QUEEN, Suit.DIAMOND)) {
					return 0;
				}
				if (CardCounter.allOpponentsVoidIn(ctx.tc, ctx.player.tbl_player, Suit.DIAMOND)) {
					return 0;
				}
				return candidate == lowestOfSuit(ctx.legalOptions, Suit.DIAMOND) ? SIGNATURE_PRESSURE_LEAD : 0;
			}
		});

		// DRAW THE QUEEN OUT (2026-08-07, user-requested). Holding the King
		// or Ace of Spades while the Queen is live is a standing liability:
		// every Spade trick they win is a chance for the Queen to ride in.
		// So engineer her exit -- lead a LOW card in a suit some other seat
		// is known void in. That seat gets a free discard, and the Queen is
		// the best discard a void hand can make; since my lead provably
		// cannot win the trick, whoever does win it eats the 13, and my
		// high Spades walk free.
		//
		// This is the deliberate inverse of the known-void penalty, the
		// same relationship the moon redirect has: leading into a void is a
		// mistake precisely because of what gets dumped -- unless the dump
		// is the point. The penalties still stack underneath (the void
		// penalty always, the Heart penalty if the flush card is a Heart),
		// so the flush only happens when the graded sums say the freedom is
		// worth the price.
		//
		// Guards, in order: a moon shooter WANTS the Queen, so never in
		// MOON_SHOT mode; pointless once she is captured, or if I hold her
		// myself; Spade leads are a different tactic entirely (forcing the
		// suit, not offering a discard); no reason to spend a trick on it
		// without a King/Ace exposed; the void seat must actually be able
		// to HOLD the Queen -- a seat void in Spades provably cannot, the
		// Queen IS a Spade; and the flush card must be provably beatable,
		// because winning this trick means eating the very dump I invited.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.QUEEN_FLUSH;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || ctx.queenOfSpadesGone) {
					return 0;
				}
				if (candidate.suit == Suit.SPADE) {
					return 0;
				}
				if (!holdsHighSpade(ctx.legalOptions)
						|| CardCounter.holds(ctx.legalOptions, CardValue.QUEEN, Suit.SPADE)) {
					return 0;
				}
				if (!aVoidSeatCouldHoldTheQueen(ctx.tc, ctx.player.tbl_player, candidate.suit)) {
					return 0;
				}
				boolean provablyBeatable = CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, 1, candidate.suit, candidate.cardValue);
				return provablyBeatable ? QUEEN_FLUSH_LEAD : 0;
			}
		});

		// MOON-STOPPING, tactic 1 of 3 (the cheapest): lead a suit the
		// shooter is already known void in. They cannot win the trick, so it
		// costs nothing and denies them a trick outright, while whatever
		// danger lands in it goes to a less threatening opponent. Applies in
		// AGGRESSIVE mode too -- stopping a moon is a standings decision, not
		// a personality one.
		scorers.add(new LeadCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
			public double score(Card candidate, LeadContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT) {
					return 0; // I'm the one shooting
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				return MoonThreat.shooterVoidIn(ctx.tc, shooter, candidate.suit)
						? MOON_LEAD_REDIRECT
						: 0;
			}
		});

		return scorers;
	}

	// ====================================================================
	// Follow scorers
	// ====================================================================

	private static final List<FollowCardScorer> FOLLOW_SCORERS = buildFollowScorers();

	private static List<FollowCardScorer> buildFollowScorers() {
		List<FollowCardScorer> scorers = new ArrayList<FollowCardScorer>();

		// Endgame solver (2026-10-08) -- see the matching lead scorer.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
			public double score(Card candidate, FollowContext ctx) {
				Double b = ctx.endgameBonus.get(candidate);
				return b == null ? 0 : b.doubleValue();
			}
		});

		// Tier 1 -- moon shot, following suit: take every trick that can be
		// taken (the point cards ARE the goal), otherwise throw the least
		// useful card away.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || !ctx.followingSuit) {
					return 0;
				}
				return ctx.canWin
						? MOON_SHOT_DIRECTION * rank(candidate)
						: -MOON_SHOT_DIRECTION * rank(candidate);
			}
		});

		// Tier 1 -- moon shot, void discard: never hand a Heart or the Queen
		// to whoever wins this trick; that breaks the attempt outright.
		// Falls back to the plain lowest card only if everything left is a
		// Heart/Queen.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_OFFENSE;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.MOON_SHOT || ctx.followingSuit) {
					return 0;
				}
				boolean safe = candidate.suit != Suit.HEART && !Rules.isQueenOfSpades(candidate);
				double s = safe ? MOON_SHOT_SAFE_DISCARD : 0;
				return s - RANK_TIE_BREAK * rank(candidate); // cheapest safe card
			}
		});

		// Tier 2 -- Aggressive fights for a trick it can win, but not when a
		// seat still to play is known void in the led suit (they'd be free to
		// load it with the Queen or a Heart), and never when winning would
		// collect the Queen itself. A trick carrying a Heart is only worth
		// taking on purpose if it actually denies an in-progress moon shooter
		// who's close enough to matter.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || !ctx.canWin) {
					return 0;
				}
				if (candidate != ctx.highestLegal) {
					return 0;
				}
				if (!ctx.player.p_traits.contains(Player_Traits.Aggressive)
						|| AI.moonAttemptAbandoned(ctx.tc, ctx.player)) {
					return 0;
				}
				if (AI.anyLaterSeatVoidIn(ctx.tc, ctx.playPosition, ctx.leadSuit)) {
					return 0;
				}
				if (AI.wouldCollectQueenOfSpades(ctx.tc, ctx.playPosition, ctx.highestLegal)) {
					return 0;
				}
				// BUG FIX (2026-10-08, live hand 6): the check above only sees a
				// Queen ALREADY on the table. West, Aggressive, second to play
				// on a 10-of-Spades lead, won with the King while holding the
				// 3 -- and East, last to play, dumped the Queen on it. Winning
				// a Spade trick with the King or Ace invites exactly that while
				// the Queen is still out and a seat yet to act may hold her.
				if (spadeWinRisksLiveQueen(ctx, ctx.highestLegal)) {
					return 0;
				}
				if (AI.wouldCollectAHeart(ctx.tc, ctx.playPosition, ctx.leadSuit, ctx.highestLegal)) {
					PLAYER shooter = Scoring.inProgressMoonShooter(AI.wonCardsByPlayer(ctx.tc));
					boolean otherPlayerIsShooting = shooter != null && shooter != ctx.player.tbl_player;
					if (!otherPlayerIsShooting || AI.safelyAheadOfShooter(ctx.tc, ctx.player, shooter)) {
						return 0; // not worth eating a Heart for
					}
				}
				return AGGRESSIVE_TAKE;
			}
		});

		// Tier 2 -- Avoids_Queen unloads the Queen of Spades the moment it's
		// safe to (a Spade trick already carrying something higher), instead
		// of holding it hoping for a void discard that may never come.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (ctx.leadSuit != Suit.SPADE || !Rules.isQueenOfSpades(candidate)) {
					return 0;
				}
				if (!ctx.player.p_traits.contains(Player_Traits.Avoids_Queen)) {
					return 0;
				}
				if (ctx.highestOnTable == null
						|| candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) >= 0) {
					return 0; // playing it here would WIN it -- not safe
				}
				return AVOIDS_QUEEN_SHED;
			}
		});

		// Tier 3 -- the baseline duck: get as high as possible without
		// actually taking the trick. If every legal card would win it, this
		// inverts to "take it as cheaply as you can".
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.BASIC_SAFETY;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				boolean losesTrick = ctx.highestOnTable != null
						&& candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) < 0;
				if (losesTrick) {
					return DUCK_UNDER_TRICK + RANK_TIE_BREAK * rank(candidate);
				}
				// "As cheaply as you can" counts the card's own points (2026-11-08,
				// Wayne's game, hand 9 trick 7: North held K Q of Spades, both won,
				// and it won with the Queen for 13 because she ranks lower). Win
				// with the higher Spade and keep the Queen to discard later.
				if (Rules.isQueenOfSpades(candidate) && anotherCardWins(ctx, candidate)) {
					return -RANK_TIE_BREAK * rank(candidate) - WIN_WITH_OWN_QUEEN;
				}
				return -RANK_TIE_BREAK * rank(candidate);
			}
		});

		// END-OF-GAME "STAY UNDER" (2026-08-15, see EndGame.java): once my
		// own running total is inside the danger zone, winning a
		// point-carrying trick gets an extra danger-scaled penalty on top of
		// the ordinary duck preference above -- close to TARGET_SCORE, one
		// more Queen-of-Spades trick can end the game outright, so caution
		// should sharpen well before that, not just apply at the same flat
		// rate as when the game just started. A committed shooter is exempt
		// -- it WANTS these points, and its own restraint near the target is
		// handled separately by HandEvaluator's danger gate on the attempt
		// decision itself, before it ever reaches this scorer. Net-negative
		// tricks (a Jack of Diamonds outweighing the Hearts/Queen alongside
		// it) are exempt too -- winning one of those SUBTRACTS points, which
		// is exactly what a seat near the target wants more of, not less.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.END_GAME;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode == PlayMode.MOON_SHOT || !ctx.followingSuit
						|| candidate != ctx.highestLegal || !ctx.canWin) {
					return 0;
				}
				double danger = EndGame.dangerLevel(ctx.tc, ctx.player.tbl_player);
				if (danger <= 0) {
					return 0;
				}
				int cost = trickValueWith(ctx, candidate);
				if (cost <= 0) {
					return 0;
				}
				return -END_GAME_SELF_CAUTION_PER_POINT * danger * cost;
			}
		});

		// NEVER GIVE THE JACK OF DIAMONDS AWAY. It is worth -10 to whoever
		// WINS the trick it lands in, so playing it into a trick somebody
		// else takes is a straight gift of 10 points.
		//
		// BUG FIX (2026-08-07, found in live play -- South led the Queen of
		// Diamonds, West held both the Jack and the 10, and played the JACK).
		// The duck scorer above plays "the highest card that still loses",
		// which is right for shedding danger and exactly wrong here: it reads
		// the Jack as just another high card to dump. Jack of Diamonds ranks
		// above the 10, so it won the duck comparison, 109 to 108. Nothing
		// counteracted it because AI.isProtectedDiamondAsset() is only
		// consulted on the void-discard path, never while following suit.
		//
		// Applies whenever the Jack cannot win: following suit but unable to
		// beat the table, or discarding while void (a discard never wins a
		// trick). If the Jack is the only legal card it still gets played --
		// this only ever makes it the LAST choice, never an illegal one.
		//
		// GRADED BY RECIPIENT STANDING (2026-08-15 user request): only in the
		// !beatsTable case, where the recipient is already determined by
		// cards already on the table (EndGame.currentTrickWinnerSeat) -- the
		// beatsTable-but-unproven branch below hands the Jack to whichever
		// LATER seat beats it, which hasn't happened yet and isn't a
		// specific seat to grade against.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE) {
					return 0;
				}
				if (candidate.suit != Suit.DIAMOND || candidate.cardValue != CardValue.JACK) {
					return 0;
				}
				boolean beatsTable = ctx.followingSuit
						&& ctx.highestOnTable != null
						&& candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) > 0;
				if (!beatsTable) {
					PLAYER recipient = EndGame.currentTrickWinnerSeat(ctx.tc, ctx.playPosition);
					return -JACK_GIVEAWAY_PENALTY
							+ jackRecipientAdjustment(ctx.tc, ctx.player.tbl_player, recipient);
				}
				// Beating the table is not winning the trick -- seats still to
				// play can beat the Jack too. Demand the same PROOF the lead
				// side does (2026-08-07, from the reviewed moon hand: East,
				// forced to win a low Diamond lead holding only the Jack and
				// Ace, spent the "cheapest winner" Jack with the King and
				// Queen unaccounted and two seats behind it -- the King took
				// it, gifting 10 points, when the Ace was a guaranteed win
				// that kept the Jack). Playing last passes vacuously: nothing
				// can come after.
				boolean nothingLeftOutranksIt = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, ctx.playPosition, Suit.DIAMOND, CardValue.JACK);
				boolean nobodyLeftCanFollow = CardCounter.allLaterSeatsVoidIn(
						ctx.tc, ctx.playPosition, Suit.DIAMOND);
				return (nothingLeftOutranksIt || nobodyLeftCanFollow) ? 0 : -JACK_GIVEAWAY_PENALTY;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
		});

		// Tier 2 -- void discard danger priority: the Queen of Spades is the
		// single most valuable thing to be rid of, then the highest Heart.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				if (Rules.isQueenOfSpades(candidate)) {
					return VOID_DISCARD_QUEEN;
				}
				if (candidate.suit == Suit.HEART) {
					return VOID_DISCARD_HEART;
				}
				return 0;
			}
		});

		// END-OF-GAME "PUSH THE LEADER OVER" (2026-08-15, see EndGame.java):
		// a void discard tops up VOID_DISCARD_QUEEN/HEART above when it lands
		// on whichever seat is closest to TARGET_SCORE -- hastening the
		// game's end while that seat is exposed rather than treating every
		// void discard as interchangeable. Requires the danger seat to be
		// the one CURRENTLY winning the trick in progress, not just in the
		// danger zone in the abstract -- dumping onto a trick a safe seat is
		// about to win doesn't push anyone toward the target.
		//
		// GATED TO THE CURRENT LEADER ONLY (2026-08-15 user correction):
		// ending the game right now only benefits whoever is CURRENTLY
		// winning. A seat in 2nd or 3rd who dumps points onto whoever's
		// closest to 100 is helping END a game they might otherwise still
		// climb out of -- exactly backwards. See the mirrored "protect the
		// danger seat" scorer just below for what everyone else does
		// instead.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.END_GAME;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				if (!EndGame.isCurrentLeader(ctx.tc, ctx.player.tbl_player)) {
					return 0;
				}
				boolean isQueen = Rules.isQueenOfSpades(candidate);
				boolean isHeart = candidate.suit == Suit.HEART;
				if (!isQueen && !isHeart) {
					return 0;
				}
				PLAYER target = EndGame.mostEndangeredOther(ctx.tc, ctx.player.tbl_player);
				if (target == null
						|| EndGame.currentTrickWinnerSeat(ctx.tc, ctx.playPosition) != target) {
					return 0;
				}
				// MOON GUARD (2026-08-15): feeding a SHOOTING target doesn't
				// push them over 100 at all -- a completed moon takes them
				// 26 the other way. Only worth it while the stopper is held
				// (the fed points then land for real when the shot breaks).
				if (MoonThreat.threateningShooter(ctx.tc, ctx.player) == target
						&& !expendableAgainstShooter(ctx.tc, ctx.legalOptions, ctx.playPosition, candidate)) {
					return 0;
				}
				double danger = EndGame.dangerLevel(ctx.tc, target);
				return danger * (isQueen ? END_GAME_TARGET_QUEEN : END_GAME_TARGET_HEART);
			}
		});

		// END-OF-GAME "PROTECT THE DANGER SEAT" -- the mirror image, for
		// every seat that is NOT currently in the lead. Ending the game now
		// only locks in a placement they might still climb out of, so a
		// trailing seat with a genuine void-discard choice should mildly
		// PREFER not to be the one who tips the closest-to-100 seat over --
		// small enough to still shed the Queen/a Heart when there's no
		// better option (base VOID_DISCARD_QUEEN/HEART above still
		// dominates), but enough to break close ties toward keeping the
		// game alive instead of ending it out from under a comeback.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.END_GAME;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				if (EndGame.isCurrentLeader(ctx.tc, ctx.player.tbl_player)) {
					return 0;
				}
				boolean isQueen = Rules.isQueenOfSpades(candidate);
				boolean isHeart = candidate.suit == Suit.HEART;
				if (!isQueen && !isHeart) {
					return 0;
				}
				PLAYER target = EndGame.mostEndangeredOther(ctx.tc, ctx.player.tbl_player);
				if (target == null
						|| EndGame.currentTrickWinnerSeat(ctx.tc, ctx.playPosition) != target) {
					return 0;
				}
				double danger = EndGame.dangerLevel(ctx.tc, target);
				return -danger * (isQueen ? END_GAME_TARGET_QUEEN : END_GAME_TARGET_HEART);
			}
		});

		// SOCIAL "ATTACK THE LEADER" (2026-08-15, TODO.md's "Targeted Queen
		// placement" item, generalized to Hearts too on a user request): a
		// seat that is NOT currently in the lead gets a bonus for a void
		// discard that lands on whichever trick the standings leader (1st
		// place, ties included) is currently winning -- closing the gap
		// instead of treating every void discard as interchangeable. Applies
		// from hand 1, unlike the END_GAME pair above which stays inert
		// until someone nears TARGET_SCORE. The leader never targets itself
		// here (EndGame.isCurrentLeader gates it out), which is the correct
		// behavior -- nothing to attack when you're the one in front.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.SOCIAL;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				if (EndGame.isCurrentLeader(ctx.tc, ctx.player.tbl_player)) {
					return 0;
				}
				boolean isQueen = Rules.isQueenOfSpades(candidate);
				boolean isHeart = candidate.suit == Suit.HEART;
				if (!isQueen && !isHeart) {
					return 0;
				}
				PLAYER leader = EndGame.currentTrickWinnerSeat(ctx.tc, ctx.playPosition);
				if (leader == null || !EndGame.isCurrentLeader(ctx.tc, leader)) {
					return 0;
				}
				// MOON GUARD (2026-08-15 user refinement): a leader who is
				// shooting the moon must be DENIED points, not fed them --
				// every card that lands makes the shot (a -26 swing the
				// wrong way) more likely. The exception is holding the
				// provable stopper: then the shot is covered, and loading
				// the shooter up is exactly right -- they eat everything
				// when it breaks. Sub-stopper cards only; the deny-feed
				// scorer keeps its full penalty on the stopper itself.
				if (MoonThreat.threateningShooter(ctx.tc, ctx.player) == leader
						&& !expendableAgainstShooter(ctx.tc, ctx.legalOptions, ctx.playPosition, candidate)) {
					return 0;
				}
				return isQueen ? SOCIAL_TARGET_QUEEN : SOCIAL_TARGET_HEART;
			}
		});

		// QUEEN PAYBACK (2026-10-10, user design -- see Payback.java): a void
		// discard of the Queen (or a Heart, much less) onto a trick a grudge
		// target is winning, scaled by the grudge's taper and the target's
		// standing. Never on a trick the standings leader is winning: giving
		// the Queen to the leader always comes first, and the SOCIAL scorer
		// above already owns that trick. Same moon guard as above.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.SOCIAL;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				boolean isQueen = Rules.isQueenOfSpades(candidate);
				if (!isQueen && candidate.suit != Suit.HEART) {
					return 0;
				}
				PLAYER winning = EndGame.currentTrickWinnerSeat(ctx.tc, ctx.playPosition);
				if (winning == null || EndGame.isCurrentLeader(ctx.tc, winning)) {
					return 0;
				}
				double strength = Payback.strength(ctx.tc, ctx.player, winning);
				if (strength <= 0) {
					return 0;
				}
				if (MoonThreat.threateningShooter(ctx.tc, ctx.player) == winning
						&& !expendableAgainstShooter(ctx.tc, ctx.legalOptions, ctx.playPosition, candidate)) {
					return 0;
				}
				return strength * (isQueen ? PAYBACK_QUEEN : PAYBACK_HEART);
			}
		});

		// Tier 2 -- but don't throw away a Jack-of-Diamonds asset (the Jack
		// itself while it's protectable, or an Ace/King held as the weapon to
		// win it back) just because it's the highest card left. Lapses the
		// moment the Jack has actually been captured by someone.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit || ctx.jackAlreadyCaptured) {
					return 0;
				}
				return AI.isProtectedDiamondAsset(candidate, ctx.legalOptions)
						? -PROTECTED_DIAMOND_PENALTY
						: 0;
			}
		});

		// Tier 4 -- with nothing dangerous left to shed, a free discard should
		// still dump the biggest card available.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				return RANK_TIE_BREAK * rank(candidate);
			}
		});

		// COUNTED-OUT JACK OF DIAMONDS CAPTURE (follow side). Diamonds are
		// led and I hold the Jack: play it when the trick is provably mine.
		// That needs both halves -- nothing already on the table beats it,
		// AND nobody still to act can beat it (either every higher Diamond is
		// accounted for, or everyone left is void in Diamonds). Getting this
		// wrong hands somebody else the -10.
		//
		// Tagged JACK_GUARD, not JACK_HUNT -- same reasoning as the lead-side
		// twin above (2026-08-16 user correction): this only fires once
		// counting has already proven the capture is safe, which is
		// protection being cashed in, not a speculative hunt.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (candidate.suit != Suit.DIAMOND || candidate.cardValue != CardValue.JACK) {
					return 0;
				}
				if (ctx.highestOnTable != null
						&& ctx.highestOnTable.cardValue.compareTo(CardValue.JACK) > 0) {
					return 0; // already beaten on the table
				}
				boolean nothingLeftOutranksIt = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, ctx.playPosition, Suit.DIAMOND, CardValue.JACK);
				boolean nobodyLeftCanFollow = CardCounter.allLaterSeatsVoidIn(
						ctx.tc, ctx.playPosition, Suit.DIAMOND);
				return (nothingLeftOutranksIt || nobodyLeftCanFollow) ? COUNTED_JACK_CAPTURE : 0;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_GUARD;
			}
		});

		// CAPTURE A JACK OF DIAMONDS SOMEBODY ELSE PLAYED. The Jack is worth
		// -10 to whoever WINS the trick containing it, not to whoever played
		// it, so once it is face up the trick is worth taking rather than
		// ducking.
		//
		// BUG FIX (2026-08-07, found in live play -- South played the Jack,
		// West held the King of Diamonds, was last to play and therefore
		// certain to win, and ducked with the 5 instead, throwing away the
		// bonus). Both existing Jack scorers only fire when the JACK ITSELF is
		// the card being considered, i.e. only when I hold it. Nothing looked
		// for a Jack already on the table, so the ordinary duck preference --
		// which exists to avoid winning tricks -- took over and did precisely
		// the wrong thing. This is the "hunt" half of the Jack strategy that
		// TODO.md described and that was never actually wired up.
		//
		// Not unconditional: the trick has to be worth taking on balance. The
		// Jack is -10, but a Queen of Spades riding along is +13 and every
		// Heart is +1, so the whole trick is priced before committing.
		// Prefers the CHEAPEST card that still wins, so an Ace isn't spent
		// where a King would have done.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (!CardCounter.onTableThisTrick(ctx.tc, ctx.playPosition,
						CardValue.JACK, Suit.DIAMOND)) {
					return 0; // no Jack in this trick to capture
				}
				if (ctx.highestOnTable == null
						|| candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) <= 0) {
					return 0; // this card wouldn't win the trick
				}
				if (trickValueWith(ctx, candidate) >= 0) {
					return 0; // the rest of the trick costs more than the Jack pays
				}
				return COUNTED_JACK_CAPTURE - RANK_TIE_BREAK * rank(candidate);
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.JACK_HUNT;
			}
		});

		// COUNTED FREE CAPTURE (2026-08-16, from live play): the scorer just
		// above is gated by JACK_HUNT, which Cautious deliberately weighs at
		// ZERO -- and that's how a Cautious West, playing LAST over a
		// 2-J-Q Diamond trick with the Ace in hand, ducked with the 7 and
		// gifted the human a free -10. Opting out of speculative Jack
		// HUNTING is a legitimate personality choice; passing up a PROVEN
		// net-gain trick is not a personality, it's a blunder. So the
		// proven case fires again here under COUNTED_CERTAINTIES (which
		// every counting-capable type keeps): candidate provably wins the
		// trick -- beats the table AND nothing still out can top it (or
		// nobody left can follow; playing last passes vacuously) -- and the
		// trick's contents are a net gain. Overlaps the JACK_HUNT scorer
		// for types that have both; they agree, so the overlap is harmless.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (ctx.highestOnTable == null
						|| candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) <= 0) {
					return 0; // wouldn't win the trick
				}
				boolean nothingLeftOutranksIt = !CardCounter.anyAboveUnaccounted(
						ctx.tc, ctx.legalOptions, ctx.playPosition, ctx.leadSuit, candidate.cardValue);
				boolean nobodyLeftCanFollow = CardCounter.allLaterSeatsVoidIn(
						ctx.tc, ctx.playPosition, ctx.leadSuit);
				if (!nothingLeftOutranksIt && !nobodyLeftCanFollow) {
					return 0; // winning is likely but not PROVEN -- hunt territory, not certainty
				}
				if (trickValueWith(ctx, candidate) >= 0) {
					return 0; // not free money
				}
				return COUNTED_FREE_CAPTURE - RANK_TIE_BREAK * rank(candidate);
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
		});

		// COUNTED MINIMAL WIN (2026-08-16, user request): when following
		// suit and about to win, spend the CHEAPEST card that still does
		// the job once counting proves the win can't be overtaken --
		// nobody still to act holds the led suit, so no later seat can
		// beat me and an off-suit discard can never win either. Applies
		// whether or not another scorer is ALSO trying to take this trick
		// (Aggressive, moon-deny, free-capture all lock onto
		// ctx.highestLegal specifically) -- this one grades every card
		// that would win, cheapest highest, so it can outbid a
		// highestLegal-only bonus for the lower card once the win is
		// genuinely uncontested. Doesn't need CardCounter's rank-counting
		// at all -- void seats can't beat ANY rank of the led suit, so a
		// bare "is everyone left void" check is sufficient and simpler
		// than the proof the Jack-capture/free-capture scorers need.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (ctx.highestOnTable == null
						|| candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) <= 0) {
					return 0; // wouldn't win the trick
				}
				if (!CardCounter.allLaterSeatsVoidIn(ctx.tc, ctx.playPosition, ctx.leadSuit)) {
					return 0; // someone left could still follow suit and beat me
				}
				if (trickValueWith(ctx, candidate) > 0) {
					return 0; // winning would cost points -- "safe to win" is not
					// the same question as "worth winning"; this scorer only
					// refines HOW to take a trick already worth taking, never
					// creates a new reason to voluntarily capture Hearts/the
					// Queen just because doing so happens to be uncontested.
				}
				return COUNTED_MINIMAL_WIN - RANK_TIE_BREAK * rank(candidate);
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
		});

		// SAFE HIGH-SPADE UNLOAD (follow side). Same rule as the lead side --
		// see safeToUnloadHighSpade() for why holding the Queen myself blocks
		// this rather than enabling it. The follow side additionally has to
		// watch for the Queen being face up in this very trick, since winning
		// it then is precisely the outcome being avoided.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit) {
					return 0;
				}
				if (candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.KING && candidate.cardValue != CardValue.ACE) {
					return 0;
				}
				return safeToUnloadHighSpade(ctx.tc, ctx.legalOptions, ctx.playPosition, ctx.player.tbl_player, false)
						? COUNTED_SAFE_HIGH_SPADE
						: 0;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.COUNTED_CERTAINTIES;
			}
		});

		// MOON-STOPPING, tactic 0 (keep the top Hearts back as insurance
		// BEFORE any shooter is visible) was REMOVED 2026-10-10: it cost
		// points on every ordinary hand to guard against a rare moon --
		// measured 27.0% games won without it vs 24.8% with it. The user's
		// rule instead: react once someone actually takes a Heart (the
		// withhold/deny scorers below), never hoard in advance.
		//
		// RESTORED 2026-10-11 as a strategy option (HEART_INSURANCE ON, the
		// paranoid player's habit; OFF is the default): while nobody has
		// captured anything, the Queen-and-up Hearts are held back from
		// discards and the low Hearts shed first.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (!ctx.player.uses(Strategy.HeartInsurance.ON)) {
					return 0;
				}
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				if (candidate.suit != Suit.HEART
						|| candidate.cardValue.compareTo(CardValue.QUEEN) < 0) {
					return 0;
				}
				return AI.anyPointCardCaptured(ctx.tc) ? 0 : -HEART_BLOCKER_RETENTION;
			}
		});

		// MOON-STOPPING, tactic 4: TEMPO. The deny-take scorer below only
		// values tricks that carry points -- but in the moon's endgame the
		// LEAD is the whole game. Reviewed hand, trick 12: the shooter led
		// a pointless 5 of Spades; East, playing last with the 10, ducked
		// ("winning it denies nothing") -- and the shooter used the kept
		// lead to cash its final boss Heart on trick 13. Winning that
		// pointless trick was checkmate the other way: the shooter's last
		// Heart would have been FORCED out as a discard onto East's trick.
		// Fires only in the provable endgame (see
		// moonEndgameHeartsAtLarge): 3 or fewer Hearts left uncaptured and
		// the top one unbeatable from this seat, so denying the lead is the
		// only defense remaining. Cheapest winning card preferred; never
		// fires on a trick that would hand over the Queen.
		scorers.add(new FollowCardScorer() {
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || !ctx.canWin) {
					return 0;
				}
				if (ctx.highestOnTable == null
						|| candidate.cardValue.compareTo(ctx.highestOnTable.cardValue) <= 0) {
					return 0; // this card doesn't beat the table
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				if (!MoonThreat.canStillWinTrick(ctx.tc, shooter, ctx.playPosition, ctx.leadSuit)) {
					return 0; // shooter can't take this trick -- no tempo to deny
				}
				if (AI.wouldCollectQueenOfSpades(ctx.tc, ctx.playPosition, candidate)) {
					return 0; // never buy tempo with the Queen
				}
				if (!moonEndgameHeartsAtLarge(ctx)) {
					return 0;
				}
				return MOON_DENY_TEMPO - RANK_TIE_BREAK * rank(candidate);
			}
		});

		// MOON-STOPPING, tactic 2 of 3: do not hand a shooter the cards they
		// need. This is the one that would have saved the 2026-08-06 hand --
		// East threw the Queen of Spades and then the Ace of Hearts as free
		// discards to a shooter it could see, while holding five harmless
		// alternatives.
		//
		// Three things happen here, in order of importance:
		//   - Any safe (non-point) card outscores every point card, so a
		//     harmless discard is always taken when one exists.
		//   - The Queen is penalised HARDER than Hearts. A shooter needs the
		//     Queen AND all thirteen Hearts; the Queen is a single card the
		//     defender controls outright, so withholding it breaks the shot
		//     by itself, whereas one low Heart is only a thirteenth of the
		//     other requirement.
		//   - Among Hearts, the ranking inverts to LOWEST-first. The ordinary
		//     discard rule sheds the highest Heart, which throws away the Ace
		//     -- the very card that guarantees a Heart trick can be taken
		//     later and the shot broken. Keep the big Hearts, shed the small.
		//
		// STOPPER EXCEPTION (2026-08-15, user refinement): denial is only
		// mandatory while the shot is genuinely live from this seat's point
		// of view. Holding the provable stopper (see holdsMoonStopper) means
		// every point card fed to the shooter comes back as points they EAT
		// when the stopper breaks the shot -- so an expendable Queen or a
		// sub-stopper Heart escapes the flat penalty entirely (letting the
		// SOCIAL/END_GAME targeting above actively aim it), keeping only the
		// lowest-first ordering among Hearts and the full penalty on the
		// stopper itself, which must never be fed.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || ctx.followingSuit) {
					return 0;
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				if (!MoonRead.alarmed(ctx.tc, ctx.player, shooter)) {
					return 0; // MOON_ALARM strategy: not a real moon yet by my read
				}
				if (MoonThreat.letsItPassForNow(ctx.tc, ctx.player, shooter)) {
					return 0; // far ahead and early: unload, someone will stop it later
				}
				if (!MoonThreat.canStillWinTrick(ctx.tc, shooter, ctx.playPosition, ctx.leadSuit)) {
					return 0; // they can't win this trick -- discarding is safe
				}
				boolean expendable = expendableAgainstShooter(
						ctx.tc, ctx.legalOptions, ctx.playPosition, candidate);
				if (Rules.isQueenOfSpades(candidate)) {
					return expendable ? 0 : -MOON_DENY_FEED_QUEEN;
				}
				if (candidate.suit == Suit.HEART) {
					// Cancels the +VOID_DISCARD_HEART/+rank pull above and
					// reverses it, so the lowest Heart is shed first --
					// stopper held or not, low Hearts still go before big.
					double flat = expendable ? 0 : MOON_DENY_FEED_HEART;
					return -flat - 2 * RANK_TIE_BREAK * rank(candidate);
				}
				return 0;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
		});

		// MOON-STOPPING, tactic 2b: the FOLLOWING-SUIT counterpart of tactic
		// 2 above, added 2026-08-16 from a real hand -- a defender holding
		// 3-4-7-J of Hearts against a shooter running Q-K-A-10-9 played the
		// Jack on the (unbeatable) Queen lead instead of saving it for the
		// Ten lead three tricks later, where the Jack would have won and
		// broken the shot. Tactic 2's inversion only ever fired on void
		// discards; this closes the same gap for a defender forced to
		// follow suit in Hearts.
		//
		// DELIBERATELY NOT a full lowest-first reversal like tactic 2's --
		// see MOON_DENY_FOLLOW_HEART_RESERVE's doc for why a void discard
		// can safely invert its whole order but a forced follow can't.
		// Reserves ONLY this seat's single highest Heart (ctx.highestLegal)
		// while a lower one is available instead; every other Heart still
		// sorts by the ordinary highest-first duck preference above,
		// unmodified. Gated on the same threateningShooter signal as tactic
		// 2 -- real captured points plus a standings-relevant threat, not a
		// bare suspicion -- and costs nothing to apply since the trick is
		// already unwinnable regardless of which Heart is played.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || ctx.canWin
						|| candidate.suit != Suit.HEART) {
					return 0;
				}
				if (candidate != ctx.highestLegal || ctx.legalOptions.size() < 2) {
					return 0; // not my best Heart, or no cheaper card to shed instead
				}
				if (MoonThreat.threateningShooter(ctx.tc, ctx.player) == null) {
					return 0;
				}
				return -MOON_DENY_FOLLOW_HEART_RESERVE;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
		});

		// MOON-STOPPING, tactic 2c: the SPADE counterpart of tactic 2b,
		// added 2026-08-16 (user request) -- a defender following an
		// unwinnable Spade lead against a threatening shooter should also
		// weigh reserving its own Ace/King rather than shedding it via the
		// ordinary highest-first duck preference, since the Queen may not
		// have surfaced yet and this could be the card that catches her
		// later. See MOON_DENY_FOLLOW_SPADE_RESERVE's doc for the two
		// gates this leans on: queenDenialWorthIt (skip reserving for a
		// capture that wouldn't be worth making) and safeToUnloadHighSpade
		// (skip whenever shedding is already provably fine).
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || ctx.canWin
						|| candidate.suit != Suit.SPADE) {
					return 0;
				}
				if (candidate.cardValue != CardValue.KING && candidate.cardValue != CardValue.ACE) {
					return 0;
				}
				if (candidate != ctx.highestLegal || ctx.legalOptions.size() < 2) {
					return 0;
				}
				if (safeToUnloadHighSpade(ctx.tc, ctx.legalOptions, ctx.playPosition, ctx.player.tbl_player, false)) {
					return 0; // shedding is already provably fine -- nothing to reserve for
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				if (!MoonThreat.queenDenialWorthIt(ctx.tc, ctx.player, shooter, ctx.myHand)) {
					return 0; // capturing the Queen wouldn't be worth it anyway -- shed normally
				}
				return -MOON_DENY_FOLLOW_SPADE_RESERVE;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
		});

		// MOON-STOPPING, tactic 2d: the FOLLOWING-SUIT counterpart of tactic
		// 2's QUEEN-OF-SPADES half specifically (2026-08-26, from a live
		// hand). West committed to a moon shot but never held the Queen --
		// it went to East in the pass. West ran the table to trick 10, led
		// the King of Spades, and East held only the Two and Queen of
		// Spades: forced to follow suit, unable to win either way, but with
		// a real choice of which one to duck under with. East played the
		// Queen. Nothing caught it: tactic 2 above only ever fires on a VOID
		// discard (it returns 0 whenever ctx.followingSuit), and tactic 2c
		// only reserves the ACE/KING of Spades, explicitly excluding the
		// Queen. With neither engaged, the baseline duck-under rule decided
		// it alone -- and that rule sheds the HIGHEST safe loser first
		// (ordinary play: keep your low cards in reserve), which is exactly
		// backwards for the one card a live shooter is hunting.
		//
		// Same magnitude as tactic 2's flagship penalty, not tactic 2c's
		// softer reserve value: handing the shooter the Queen directly ends
		// the hand right now, whether it happens via a void discard or a
		// forced-suit choice -- the mechanism differs, the damage doesn't.
		// Same expendable exception too, and for the identical reason: a
		// held Heart stopper already guarantees the shot can be broken
		// later, so there is nothing left to protect the Queen for.
		//
		// Deliberately NOT gated on candidate == ctx.highestLegal the way
		// 2b/2c are -- those reserve ONE specific card for LATER use, so
		// they only ever need to move the single best option. This is
		// different: whenever this scorer is even reachable (can't win,
		// Queen is a legal follow), the Queen is a losing card regardless
		// of what else is in hand, so it must be penalised directly against
		// every legal alternative, the same way tactic 2 penalises it
		// directly on the void-discard side.
		//
		// NOTE the expendable check below passes ctx.myHand.hand, not
		// ctx.legalOptions, unlike tactic 2's own call. Tactic 2 fires on a
		// void discard, where legalOptions is effectively the whole
		// non-led-suit hand and so already contains any Heart stopper. Here
		// legalOptions is suit-restricted to Spades (see CardCounter's class
		// doc), so it would never contain a Heart at all -- passing it would
		// make "expendable" silently unreachable and quietly defeat its own
		// exception. myHand is this seat's real remaining hand and is what
		// the stopper check actually needs to see.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || ctx.canWin
						|| !Rules.isQueenOfSpades(candidate)) {
					return 0;
				}
				if (ctx.legalOptions.size() < 2) {
					return 0; // singleton Queen -- no alternative Spade to duck with
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				if (!MoonThreat.canStillWinTrick(ctx.tc, shooter, ctx.playPosition, ctx.leadSuit)) {
					return 0; // already beaten in this trick -- feeding it costs nothing
				}
				boolean expendable = expendableAgainstShooter(
						ctx.tc, ctx.myHand.hand, ctx.playPosition, candidate);
				return expendable ? 0 : -MOON_DENY_FEED_QUEEN;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
		});

		// MOON-STOPPING, tactic 3 of 3: take the trick yourself to break the
		// shot. Only worth doing on a trick that actually carries points --
		// denying a shooter a pointless trick achieves nothing -- and only
		// when they could still win it.
		//
		// Taking a Heart is the normal way through. Taking the QUEEN is the
		// genuine last resort: it is +13 against a shot worth 26 the other
		// way, which is usually a bargain but not always, so it defers to
		// MoonThreat.queenDenialWorthIt() to check how the score lands across
		// all four players first -- stopping the moon is no victory if it
		// just hands a third player the lead.
		scorers.add(new FollowCardScorer() {
			public double score(Card candidate, FollowContext ctx) {
				if (ctx.mode != PlayMode.BASELINE || !ctx.followingSuit || !ctx.canWin) {
					return 0;
				}
				PLAYER shooter = MoonThreat.threateningShooter(ctx.tc, ctx.player);
				if (shooter == null) {
					return 0;
				}
				if (!MoonRead.alarmed(ctx.tc, ctx.player, shooter)) {
					return 0; // MOON_ALARM strategy: not a real moon yet by my read
				}
				if (MoonThreat.letsItPassForNow(ctx.tc, ctx.player, shooter)) {
					return 0; // far ahead and early: won't chance points to stop it yet
				}
				if (!MoonThreat.canStillWinTrick(ctx.tc, shooter, ctx.playPosition, ctx.leadSuit)) {
					return 0;
				}
				// 2026-10-17 (user design): WHETHER to take now, and with which card,
				// depends on the player's level -- see MoonThreat.breakTheShotWith.
				Card taker = MoonThreat.breakTheShotWith(ctx.tc, ctx.player, shooter, ctx.legalOptions,
						ctx.myHand, ctx.playPosition, ctx.leadSuit, ctx.highestLegal);
				if (taker == null || candidate != taker) {
					return 0;
				}
				boolean takesQueen = AI.wouldCollectQueenOfSpades(ctx.tc, ctx.playPosition, candidate);
				boolean takesHeart = AI.wouldCollectAHeart(ctx.tc, ctx.playPosition, ctx.leadSuit, candidate);
				if (!takesQueen && !takesHeart) {
					return 0; // no points in this trick -- winning it denies nothing
				}
				if (takesQueen) {
					return MoonThreat.queenDenialWorthIt(ctx.tc, ctx.player, shooter, ctx.myHand,
							AI.heartsOnTrick(ctx.tc, ctx.playPosition, candidate))
							? MOON_DENY_TAKE_QUEEN
							: 0;
				}
				return MOON_DENY_TAKE;
			}
			public TacticWeights.Tactic tactic() {
				return TacticWeights.Tactic.MOON_DEFENSE;
			}
		});

		return scorers;
	}

	// ====================================================================
	// Aggregation
	// ====================================================================

	/** TacticWeights.weight(), with the "counts cards" outer gate applied
	 * (2026-08-16, user request): a COUNTED_CERTAINTIES-tagged scorer is
	 * forced to zero on any hand this individual roster player isn't
	 * actively counting (Player.countsCardsThisHand, rolled per hand
	 * against Player.countCardsPercent) -- regardless of what its
	 * Player_Type's COUNTED_CERTAINTIES row says. This is the ONLY tactic
	 * family the roll affects; everything else built into this file (moon
	 * defense, exit-card preservation, the Jack-of-Diamonds giveaway
	 * grading, the high-Spade-lead exception, ...) stays universal, per
	 * the user's explicit "don't lose those features" instruction --
	 * TacticWeights.COUNTED_CERTAINTIES itself is untouched and keeps
	 * meaning "how much does this PERSONALITY want to act on a certainty
	 * once available," a separate question from "is this INDIVIDUAL
	 * counting at all this hand." See TODO.md's Counts_cards research note
	 * for why these are two different gates rather than one. */
	private static double effectiveWeight(Player player, TacticWeights.Tactic tactic) {
		if (tactic == TacticWeights.Tactic.COUNTED_CERTAINTIES && !player.countsCardsThisHand) {
			return 0;
		}
		return TacticWeights.weight(player, tactic);
	}

	/**
	 * Runs EndgameSolver once for this decision and turns its answer into
	 * per-card bonuses. Skipped outright (empty map, no search) whenever the
	 * scorer would be weighted to zero anyway -- a seat not counting cards
	 * this hand, or a type that never acts on counted certainties.
	 *
	 * Two bonuses, never more:
	 *   - ENDGAME_WORST_CASE_BEST to every card whose worst case is the best
	 *     available, but only if some other card's worst case is genuinely
	 *     worse. If every card has the same worst case, nothing is proven.
	 *   - ENDGAME_AVERAGE_BEST to the single card with the strictly best
	 *     average among those still tied, if there is one.
	 */
	static Map<Card, Double> endgameBonus(Table_Cards tc, Player player, List<Card> fullHand,
			List<Card> legalOptions, int playPosition) {
		if (fullHand == null || effectiveWeight(player, TacticWeights.Tactic.COUNTED_CERTAINTIES) == 0) {
			return Collections.emptyMap();
		}
		EndgameSolver.Result r = EndgameSolver.solve(tc, player, fullHand, legalOptions, playPosition);
		if (r == null) {
			return Collections.emptyMap();
		}
		int bestWorst = Integer.MAX_VALUE;
		int worstWorst = Integer.MIN_VALUE;
		for (Integer w : r.worst.values()) {
			bestWorst = Math.min(bestWorst, w.intValue());
			worstWorst = Math.max(worstWorst, w.intValue());
		}
		Map<Card, Double> bonus = new HashMap<Card, Double>();
		List<Card> tied = new ArrayList<Card>();
		for (Card c : legalOptions) {
			if (r.worst.get(c).intValue() == bestWorst) {
				tied.add(c);
				if (worstWorst > bestWorst) {
					bonus.put(c, Double.valueOf(ENDGAME_WORST_CASE_BEST));
				}
			}
		}
		Card bestAvg = null;
		boolean unique = false;
		for (Card c : tied) {
			double a = r.average.get(c).doubleValue();
			if (bestAvg == null || a < r.average.get(bestAvg).doubleValue()) {
				bestAvg = c;
				unique = true;
			} else if (a == r.average.get(bestAvg).doubleValue()) {
				unique = false;
			}
		}
		if (bestAvg != null && unique && tied.size() > 1) {
			Double base = bonus.get(bestAvg);
			bonus.put(bestAvg, Double.valueOf((base == null ? 0 : base.doubleValue()) + ENDGAME_AVERAGE_BEST));
		}
		if (System.getenv("HEARTS2_DEBUG_ENDGAME") != null) {
			StringBuilder sb = new StringBuilder("ENDGAME " + player.tbl_player + " deals=" + r.deals
					+ " leaves=" + r.leaves);
			for (Card c : legalOptions) {
				Double b = bonus.get(c);
				sb.append(" | ").append(c.cardValue).append('-').append(c.suit)
						.append(" worst=").append(r.worst.get(c))
						.append(String.format(" avg=%.1f", r.average.get(c).doubleValue()))
						.append(b == null ? "" : " BONUS=" + b.intValue());
			}
			System.out.println(sb);
		}
		return bonus;
	}

	/**
	 * Sums every lead scorer against every legal card and returns the best.
	 * Ties keep the first card seen, matching the first-max-wins behaviour of
	 * AI.highestOf()/lowestOf() that the old code resolved ties with.
	 */
	public static Card pickLead(List<Card> legalOptions, Table_Cards tc, Player player) {
		return pickLead(legalOptions, null, tc, player);
	}

	/** `myHand` is this seat's full remaining hand. legalOptions alone isn't
	 * enough for the endgame solver: before Hearts are broken it hides every
	 * Heart I hold, and the solver would count those as somebody else's.
	 * Null is allowed (older call sites, tests) -- the solver then runs only
	 * once Hearts are broken, when legalOptions IS the whole hand. */
	public static Card pickLead(List<Card> legalOptions, Hand myHand, Table_Cards tc, Player player) {
		return rankLead(legalOptions, myHand, tc, player).get(0);
	}

	/** Every legal lead, best first (2026-10-10, AI players step 2 -- the
	 * near-miss blunder needs the runners-up). Ties keep the order the cards
	 * were seen, so get(0) is exactly what pickLead always returned. */
	public static List<Card> rankLead(List<Card> legalOptions, Hand myHand, Table_Cards tc, Player player) {
		LeadContext ctx = new LeadContext(legalOptions, tc, player);
		List<Card> fullHand = myHand != null ? myHand.hand : (tc.hearts_broken ? legalOptions : null);
		ctx.endgameBonus = endgameBonus(tc, player, fullHand, legalOptions, 1);
		ctx.fullHand = fullHand;
		List<Card> ranked = new ArrayList<Card>();
		final Map<Card, Double> totals = new HashMap<Card, Double>();
		boolean debug = System.getenv("HEARTS2_DEBUG_SCORER") != null;
		for (Card candidate : legalOptions) {
			double total = 0;
			for (LeadCardScorer scorer : LEAD_SCORERS) {
				double w = effectiveWeight(player, scorer.tactic());
				if (w != 0) {
					double s = scorer.score(candidate, ctx);
					if (debug && s != 0) {
						System.out.println("DEBUG " + player.tbl_player + " " + candidate.cardValue + "-"
								+ candidate.suit + " tactic=" + scorer.tactic() + " raw=" + s + " w=" + w
								+ " contrib=" + (w * s));
					}
					total += w * s;
				}
			}
			if (debug) {
				System.out.println("DEBUG " + player.tbl_player + " " + candidate.cardValue + "-"
						+ candidate.suit + " TOTAL=" + total);
			}
			ranked.add(candidate);
			totals.put(candidate, Double.valueOf(total));
		}
		// List.sort is stable: equal totals keep first-seen order.
		// Plain < and > (not Double.compare) so ordering matches the old
		// "total > bestScore" test exactly.
		ranked.sort((a, b) -> {
			double ta = totals.get(a).doubleValue(), tb = totals.get(b).doubleValue();
			return tb > ta ? 1 : tb < ta ? -1 : 0;
		});
		return ranked;
	}

	/** Follow-side counterpart of pickLead(). `myHand` is this seat's full
	 * remaining hand -- see FollowContext.myHand's doc for why legalOptions
	 * alone isn't enough. */
	public static Card pickFollow(List<Card> legalOptions, Hand myHand, Table_Cards tc, int playPosition, Player player) {
		return rankFollow(legalOptions, myHand, tc, playPosition, player).get(0);
	}

	/** Follow-side counterpart of rankLead(). */
	public static List<Card> rankFollow(List<Card> legalOptions, Hand myHand, Table_Cards tc, int playPosition,
			Player player) {
		FollowContext ctx = new FollowContext(legalOptions, myHand, tc, playPosition, player);
		ctx.endgameBonus = endgameBonus(tc, player, myHand == null ? null : myHand.hand, legalOptions,
				playPosition);
		List<Card> ranked = new ArrayList<Card>();
		final Map<Card, Double> totals = new HashMap<Card, Double>();
		boolean debug = System.getenv("HEARTS2_DEBUG_SCORER") != null;
		for (Card candidate : legalOptions) {
			double total = 0;
			for (FollowCardScorer scorer : FOLLOW_SCORERS) {
				double w = effectiveWeight(player, scorer.tactic());
				if (w != 0) {
					double s = scorer.score(candidate, ctx);
					if (debug && s != 0) {
						System.out.println("DEBUG " + player.tbl_player + " " + candidate.cardValue + "-"
								+ candidate.suit + " tactic=" + scorer.tactic() + " raw=" + s + " w=" + w
								+ " contrib=" + (w * s));
					}
					total += w * s;
				}
			}
			if (debug) {
				System.out.println("DEBUG " + player.tbl_player + " " + candidate.cardValue + "-"
						+ candidate.suit + " TOTAL=" + total);
			}
			ranked.add(candidate);
			totals.put(candidate, Double.valueOf(total));
		}
		// List.sort is stable: equal totals keep first-seen order.
		// Plain < and > (not Double.compare) so ordering matches the old
		// "total > bestScore" test exactly.
		ranked.sort((a, b) -> {
			double ta = totals.get(a).doubleValue(), tb = totals.get(b).doubleValue();
			return tb > ta ? 1 : tb < ta ? -1 : 0;
		});
		return ranked;
	}

	/** Exposed for the equivalence scratch test -- lets a harness report which
	 * strategy drove a given decision when the scored result and the legacy
	 * result disagree. */
	public static PlayMode leadMode(Table_Cards tc, Player player) {
		return leadModeFor(tc, player);
	}

	/** Another legal card of the led suit that would also win the trick. */
	static boolean anotherCardWins(FollowContext ctx, Card candidate) {
		for (Card c : ctx.legalOptions) {
			if (c != candidate && c.suit == ctx.leadSuit && ctx.highestOnTable != null
					&& c.cardValue.compareTo(ctx.highestOnTable.cardValue) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Unused by the game itself; kept because CardValue is imported for
	 * documentation of the rank range the tie-break tiers assume. */
	static int maxRank() {
		return CardValue.ACE.ordinal();
	}

	/** My lowest card of `suit` among `cards`, or null. */
	static Card lowestOfSuit(List<Card> cards, Suit suit) {
		Card low = null;
		for (Card c : cards) {
			if (c.suit == suit && (low == null || c.cardValue.compareTo(low.cardValue) < 0)) {
				low = c;
			}
		}
		return low;
	}
}
