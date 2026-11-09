# Hearts_2 — Design & Implementation Checkpoint

Checkpoint date: 2026-08-02. Purpose: durable snapshot of the architecture,
design decisions, and implementation status so a "deep dive" review (plan
vs. implementation, recommendations, whether the approach needs to change)
can be done from this file instead of re-deriving everything from chat
history every time context resets.

This file is a **companion to `TODO.md`**, not a replacement — `TODO.md`
tracks forward-looking planned features and open design discussions
(persistent AI roster, trashTalk, keyboard input, moon-shot-attempt design).
This file is a **snapshot of what exists today and how the pieces fit
together**, written so the still-pending deep-dive review has a fixed
starting point.

---

## 1. Ruleset: Omnibus Hearts

Standard Hearts plus one rule: capturing the **Jack of Diamonds is -10
points** (a bonus you want, inverting the normal avoidance instinct for
that one card). Queen of Spades = +13, each Heart = +1, Shooting the Moon
(all 13 Hearts + Queen) = -26, independent of and stacking with the Jack
(-36 total if a moon-shooter also grabs the Jack). Full rationale and the
worked scoring example are in `TODO.md`'s top section — not repeated here.

## 2. Architecture / layering

```
Rules.java        -- legal-move enforcement (follow suit, hearts-broken,
                      first-trick restrictions). Ground truth for what a
                      player CAN play; never makes strategy decisions.

VoidTracker.java   -- pure belief-state layer sitting on top of Rules.
                      Hard deduction (not probabilistic): if a play doesn't
                      match the led suit, that seat is void in it, full
                      stop, because Rules already forces following suit
                      when possible. tc.void_suits: Map<PLAYER,EnumSet<Suit>>,
                      reset fresh every hand. recordPlay() called at every
                      play site (lead or follow, human or computer).

Scoring.java       -- trick-winner determination (highest card of led
                      suit) + end-of-hand scoring, full Omnibus rules
                      including the moon-shot/Jack-of-Diamonds stacking
                      and the "tragic moon shot" consolation case.
                      standingDelta(tc, me) compares a player's running
                      total against the average of the other three --
                      the standings-awareness signal AI.java and
                      Deal_Hand.java both read.

HandEvaluator.java -- pure hand-quality scoring, no game-state mutation.
                      moonShotHandQuality(Hand): Spade control + long
                      runnable suit + Heart strength - isolated-suit
                      penalty. shouldAttemptMoonShot(hand, tc, me):
                      threshold check, standings-adjusted via
                      Scoring.standingDelta. hasJackOfDiamondsProtection
                      (two overloads: Hand, and List<Card> for AI.java's
                      legal-options lists) -- do I hold enough low
                      Diamonds to duck behind until Ace/King are cleared.

Deal_Hand.java     -- the passing phase. passCards(allHands, tc, direction)
                      and an overload that also takes a human's manual
                      selection and returns what they received.
                      chooseCardsToPass() branches offensivePass() (moon-
                      shot mode: keep long-suit control, keepWorthiness())
                      vs. defensivePass() (topDangerCards()/dangerScore(),
                      with a universal safety valve: never bundle Spade
                      control together with Heart/Queen exposure in the
                      same pass, for ANY recipient, not just a leader).
                      Re-sorts all 4 hands via Hand.sortem2() after the
                      exchange (matches Deck.deal()'s sort convention).

AI.java            -- card selection: chooseLead() / chooseFollow().
                      Decision order (see section 3 below): trait override
                      (Brain_Dead/Bad/Aggressive) -> attempting_moon_shot
                      -> Jack-of-Diamonds protect/hunt -> void-awareness ->
                      baseline safest-longest-suit heuristic. Also holds
                      moon-shooter-STOPPING logic (deny an opponent
                      mid-shoot, wired into Aggressive specifically).

Player.java        -- Player_Traits EnumSet (Brain_Dead, Bad, Aggressive,
                      Cautious, Vengeful, Last_Place, First_Place,
                      Payback_p1/2/3, Need_to_Stop_Shooter, Need_to_Shoot,
                      etc.) plus attempting_moon_shot (set once during
                      passing, read by AI.java for the rest of the hand).
                      Most of the standings/history traits (Vengeful,
                      Payback_*) are still UNUSED — see section 5.

Hearts_JFrame.java -- GUI/orchestration. Owns the passing-phase flow
                      (beginPassingPhase() -> South's card-selection UI or
                      straight to completePassingAndStartHand() for KEEP
                      -> startFirstTrick()), South's hand button state
                      machine, and Play Card / Clear Table one-shot
                      button gating.
```

## 3. AI.java decision order (as currently wired)

1. Trait override checks: `Brain_Dead` (near-random legal play), `Bad`
   (weak heuristics), `Aggressive` (moon-shooter-stopping logic — deny
   opponents mid-shoot, avoid collecting the Queen unless denying,
   standings-aware).
2. `player.attempting_moon_shot` — if true, switch to `moonShotFollow()` /
   equivalent lead logic: keep winning tricks in the controlled suit,
   avoid shedding Hearts/Queen defensively since the goal is to collect
   them, `lowestNonHeartNonQueen()` as the safe discard when not
   deliberately collecting.
3. Jack-of-Diamonds protect/hunt, via `shouldProtectDiamondSuit()` and
   `highestExcludingProtectedDiamonds()`. Real bug found+fixed here this
   session: originally only protected Ace/King of Diamonds while
   "hunting" (not holding the Jack); needed to ALSO protect them when
   they're the protection basis for a Jack already held —
   `protectCaptureWeapons = protectJack || !holdsJack`.
4. Void-awareness: `anyOtherSeatVoidIn()` / `anyLaterSeatVoidIn()` avoid
   leading into a seat known-void in that suit when it's disadvantageous.
5. Baseline: low cards from long suits, with per-card graded danger
   penalties. (Until 2026-08-07 this was `safestLongestSuitAmong()` — a
   pick-a-suit-then-play-its-lowest-card two-step whose exclusion chain
   produced three identical live-play bugs; it was dissolved into per-card
   lead scorers in `AICardScorer.java`, see the lead-scorer block there.)

This ordering itself (trait override → moon-shot → Jack-of-Diamonds →
void-awareness → baseline) is a real design decision worth scrutinizing in
the deep-dive review — see section 6.

## 4. What's implemented and verified vs. not

**Implemented and wired end-to-end (compiles, scratch-tested, and in at
least one case live-playtested):**
- Rules engine (follow-suit, hearts-broken, first-trick restrictions)
- Trick-winner determination + full Omnibus scoring (`Scoring.java`) —
  verified against 8 constructed scenarios including the -7-point worked
  example from the original research doc
- Void-tracking, wired into AI's lead strategy
- Moon-shooter-stopping, wired into the Aggressive trait
- Moon-shot-attempt hand evaluation + decision, wired into AI's actual
  trick play
- Jack-of-Diamonds hunt/protect, wired into AI's actual trick play
- Full passing phase, including a human (South) card-selection UI
  (toggle-select, Pass Cards/Continue two-stage flow, received-card
  highlighting)

**Explicitly NOT yet integration-tested (per TODO.md's own note):**
- `won_cards` accumulation across a full real 13-trick hand, and
  `End_Of_Hand` triggering correctly, compiles clean but has not been
  verified with an actual full GUI playthrough start-to-finish since the
  scoring engine was wired in.

**Not implemented at all (see TODO.md for full design discussion on
each):**
- Persistent AI player roster (named, cross-game identity/memory) —
  this is what `Vengeful`/`Payback_p1/2/3`/`Last_Place`/`First_Place`
  traits are FOR but currently have no memory system behind them
- Ranked (vs. unordered EnumSet) trait priority
- trashTalk / AI commentary
- Keyboard card selection for South
- Disabled/illegal-card visual clarity fix (face cards look like
  card-backs when grayscale-disabled)

## 5. Known gaps worth flagging in the deep-dive review

These are observations from reviewing the current wiring, meant as raw
material for the still-pending "deep dive" recommendations pass, not
conclusions already acted on:

1. **AI.java's decision order is a fixed if/else chain, not a scored/
   weighted system.** Trait override, moon-shot, Jack-of-Diamonds, and
   void-awareness are checked in a hardcoded sequence. This works while
   there are 4 concerns, but each new concern (e.g. future trashTalk
   reactions, ranked-trait resolution) multiplies the branching. Worth
   deciding NOW whether to keep layering `if` branches or move to a
   scored-candidate-move system (each concern contributes a score/veto to
   each legal card, pick the best) before the roster/ranked-trait feature
   makes the branching unmanageable.
2. **`attempting_moon_shot` is a single boolean with no in-hand
   "abandon" path wired yet.** TODO.md's design explicitly calls for a
   critical-abort condition (revert to defensive play the instant another
   player captures a Heart/Queen) reusing `Scoring.inProgressMoonShooter()`
   — confirm whether this abort check actually exists in AI.java's
   moon-shot branch or was only designed, not wired. (Needs a direct code
   check — not confirmed in this session's context.)
3. **Full-hand integration test is still outstanding**, per TODO.md's own
   admission — scoring/passing/AI all individually scratch-tested, but no
   confirmed live full-13-trick-hand-through-scoring GUI run since the
   scoring engine landed. This is the highest-leverage next verification
   step before trusting any of the above in a real game.
4. **Ranked-trait / persistent-roster work is a foundational blocker**,
   not a nice-to-have — TODO.md itself notes several already-defined
   traits (`Vengeful`, `Payback_*`) are inert scaffolding until this
   lands, and recommends sequencing it before trashTalk. Worth confirming
   this sequencing is still the plan.
5. **Passing-phase safety valve ("never bundle Spade control with Heart/
   Queen exposure") was generalized to apply to every recipient**, not
   just a leader — confirm this generalization didn't weaken the
   leader-targeting tactic it was originally paired with (i.e., check
   there's no case where avoiding the bundle now prevents a pass that
   would have been correct specifically because the recipient was a
   leader worth loading up).

## 6. Session bug-fix history (for context, not action items)

- Hand-sort-after-passing bug: `Deal_Hand.passCards()` didn't call
  `Hand.sortem2()` after the exchange, unlike `Deck.deal()`. Fixed.
- Missing card icon on human-passed cards: `toggleCardSelection()` built
  a fresh `Card` via setters but never copied the icon, unlike
  `remove_button()`'s existing correct pattern. Fixed.
- Play-Card-button re-enable bug: `startFirstTrick()` unconditionally
  re-enabled Play Card right before `Play_Hand_1`, which could hijack
  South's own pending lead turn with a stale card when South held 2♣ and
  led first. Fixed by removing the premature enable (Play Card now only
  re-enables via Clear Table).
- Jack-of-Diamonds protection bug (caught by a scratch test before
  shipping): see section 3, item 3.

## 7. Deep-Dive Review Complete (2026-08-02)

The full deep-dive review has been completed (saved as a PDF outside the repo).

### Key Findings:

- **Architecture is sound** — no fundamental rethinking needed; the layering
  (Rules → VoidTracker → Scoring → HandEvaluator → Deal_Hand → AI → GUI)
  is clean and well-separated
- **Three critical logic bugs** identified and ready for fixing (see section 8)
- **Architectural decision made: scored-candidate system** — selected for
  future scalability with ranked traits and cross-game memory features
  (detailed in section 8)
- **Full-hand integration test is the BLOCKING priority** — gates confidence
  in all three logic fixes, ~30 minutes to complete

## 8. Bug Tracking & Fix Plan

Three bugs need fixing before the game can be trusted to play realistically.

### BUG-1: Moon-Shot Abort Condition (CRITICAL)

**Status:** FIXED (2026-08-02, commit e7f319f) | **Priority:** 1 | **Actual effort:** ~15 min

**Issue:** A player committed to `attempting_moon_shot = true` will play
moon-shot strategy for every remaining trick, even if another player has
already captured a Heart or Queen of Spades, making the moon shot
mathematically impossible.

**Current state:** AI.java line 150 unconditionally calls moonShotFollow()
without checking inProgressMoonShooter(). The function exists and is correct
but unused.

**Fix approach:**
1. In chooseFollow() before calling moonShotFollow(), check if
   inProgressMoonShooter() still points to this player
2. If not, fall through to normal defensive play (abandon moon-shot)
3. Scratch test: commit to moon-shot on trick 1, opponent captures Heart on
   trick 3, verify moon-shot abandons on trick 4+

**Related:** Scoring.inProgressMoonShooter() (already correct), TODO.md
moon-shot design section

---

### BUG-2: Jack-of-Diamonds Hunt Abandonment (IMPORTANT)

**Status:** FIXED (2026-08-02, commit e7f319f) | **Priority:** 2 | **Actual effort:** ~15 min

**Issue:** If a player is protecting/hunting the Jack of Diamonds, there's
no logic to abandon if the Jack is already captured by someone else.

**Current state:** shouldProtectDiamondSuit() decides once (during a lead
choice), then highestExcludingProtectedDiamonds() preserves Ace/King on
free discards. No re-check on follow decisions, no detection of "Jack
already captured."

**Fix approach (parallel to BUG-1):**
1. Add helper in Scoring.java: hasJackBeenCaptured(wonCardsByPlayer)
2. In shouldProtectDiamondSuit(): early return false if Jack captured
3. In highestExcludingProtectedDiamonds(): same check before protecting
   Ace/King
4. Scratch test: hold Jack+Ace of Diamonds with protection, opponent
   captures Jack, verify Ace is no longer protected

**Related:** TODO.md Jack-of-Diamonds strategy section, HandEvaluator.
hasJackOfDiamondsProtection()

---

### BUG-3: Missing Full-Hand Integration Test (BLOCKING)

**Status:** NOT DONE | **Priority:** 0 (BLOCKS others) | **Estimated effort:** 30 min

**Issue:** The scoring engine, trick-winner logic, passing phase, and AI
strategy are all individually scratch-tested and compile clean. But there
is NO confirmed playthrough of a full 13-trick hand through the live GUI
since the scoring engine landed (2026-07-30).

**Current state:** resolveTrick() correctly calls Scoring.determineTrickWinner()
and accumulates won_cards. Play_Hand_1() triggers End_Of_Hand() on trick 13.
Unknown whether End_Of_Hand() calls Scoring.computeHandScores() or whether
scores persist to p_total_points.

**Fix approach:**
1. Play a full 13-trick hand through the GUI manually (South human,
   North/East/West AI)
2. Verify hand-end screen shows correct scores for all 4 players
3. Play a second hand; verify scores from hand 1 persist to p_total_points
4. Check console output for exceptions, warnings, null-pointer issues
5. Document any bugs found (even visual glitches)

**Why this blocks BUG-1 and BUG-2:** Can't confidently fix logic bugs
without knowing the baseline is solid. A fix might break something else in
the full sequence.

---

### Architectural Decision: Scored-Candidate System

**Status:** IMPLEMENTED (2026-08-06, branch `AI_Player_dev`) | **Priority:**
Design now, implement after BUG-1/2/3 | **Estimated effort:** 60–90 min
(implementation)

**Implemented as described below.** `AICardScorer.java` now holds the scoring
infrastructure and every scorer; `AI.chooseLead()`/`chooseFollow()` are down to
the Brain_Dead and Bad early exits plus a call into it. Notes on how it
actually landed:

- **Brain_Dead and Bad stayed as pre-scoring early exits**, deliberately. They
  aren't strategic preferences to be weighed against other concerns — they're
  competence tiers that bypass strategy entirely (and Bad is deliberately
  random), so scoring them would misrepresent what they model.
- **`PlayMode` (MOON_SHOT / AGGRESSIVE / BASELINE) captures genuine
  exclusivity.** Some strategies can't be summed: a committed moon-shooter
  WANTS the point cards baseline play spends the hand avoiding, so those
  contributions must never mix. Each scorer opens with a guard declaring which
  mode(s) it applies in. That guard is one local condition owned by that one
  concern — unlike the old chain, where a branch's position silently governed
  every strategy below it (exactly how the 2026-08-05 lead-path moon-shot abort
  bug survived as long as it did).
- **Weight tiers** are banded so a lower tier can never outvote a higher one no
  matter how many lower scorers agree: Tier 1 (~1000) provable/committed play,
  Tier 2 (~300–500) strategic overrides, Tier 3 (~50–100) baseline safety,
  Tier 4 (~1) rank tie-breaks. Bands are separated by more than the maximum
  swing of everything beneath them (card rank contributes at most 12).
- **`AI.isMoonShotAlive()` was extracted** so the "committed AND still
  mathematically alive" pairing has exactly one definition and can't drift
  between the lead and follow paths again.
- **Verified equivalent, not assumed.** A scratch harness compared the
  pre-refactor logic against the scored system across **200,000 randomly
  generated situations each for lead and follow (400,000 decisions total)** —
  random hands (size 1–13), trait combinations, standings, won-card
  distributions, void states, hearts-broken/first-trick states, and play
  positions 2–4. **Zero mismatches.** Only after that did the legacy methods
  and the helpers that existed solely to serve them
  (`moonShotFollow`, `lowestNonHeartNonQueen`, `highestExcludingProtectedDiamonds`,
  `highestOfSuit`, `lowestOfSuit`, `lowestOf`, `highestBelow`) get deleted;
  they remain in git history at the commit before their removal.
- **Next:** the three trick-play items queued in `TODO.md` (standings-aware
  redirect-first moon-stopping, counted-out Queen-of-Spades lead, counted-out
  Jack-of-Diamonds lead) are now each a new scorer added to a list rather than
  another branch. The fourth (passing-phase recipient-moon-enablement) lives in
  `Deal_Hand.java` and is unaffected by this refactor.

Original design notes follow.

**Issue:** The AI decision order is a hardcoded if/else chain in chooseLead()
and chooseFollow():
1. Brain_Dead / Bad (trait override)
2. attempting_moon_shot
3. Jack-of-Diamonds protect/hunt
4. void-awareness
5. baseline

This works with 4 concerns, but TODO.md's roadmap includes ranked traits,
cross-game memory, trashTalk, PIMC simulation. Each new concern multiplies
branching.

**Decision: Move to scored-candidate system**

Each legal card gets a score from each concern (moon-shot +10 if it wins
the Jack, void-awareness −5 if it risks the Queen, etc.), then pick the
highest. Scales cleanly to ranked traits (each trait contributes a score
multiplier) and social reactions (each historical relationship adds a bonus/
penalty).

**Implementation plan:**
- Do NOT implement immediately — finish fixing BUG-1/2/3 first
- Model after Scoring.computeHandScores() which already uses this pattern
- Create a CardScorer interface/class that each concern implements
- Migrate chooseLead() and chooseFollow() to use scored selection
- Sequence before ranked-trait feature lands (will need trait scores)

**Related:** AI.java class comment (lines 14–100), TODO.md decision-order
notes, Scoring.computeHandScores() as a design model
