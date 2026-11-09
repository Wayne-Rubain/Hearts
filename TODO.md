# Hearts_2 -- Open Items

**Split from a single 863-line TODO.md on 2026-08-15.** Everything IMPLEMENTED,
FIXED, RESOLVED, or REJECTED now lives in `CHANGELOG.md` -- keep it there, not
here. The split happened because a stale "planned feature" section sitting in
this file caused a wrong status report on two separate occasions (2026-08-09
and 2026-08-10, both noted in the changelog). This file should only ever
describe what is NOT yet done.

---

## Where we stopped (2026-10-17, Wayne on a break)

Done today (see CHANGELOG 2026-10-17): GameLog + games_ledger.csv, ForcedDeal replay, blunders never
suicidal, dead-moon fix + abandoned moon sheds low Hearts, cheapest-card moon break, player LEVEL (1-5) with
the level-graded moon ladder, expert limits for level 4-5 (commit 468ed90).
Next when Wayne returns:
- The game in `bin/` was built BEFORE the expert limits (468ed90): rebuild and relaunch before playing.
  The hand 4 deal is saved as `~/.hearts2/saved_deal_shark_hand4.txt` (copy to `forced_deal.txt` to replay).
- Keep reviewing Wayne's games from `~/.hearts2/gamelogs/` and watch `games_ledger.csv`: if Wayne wins too
  often or the AIs never try the moon (only 1 commit seen so far, Shark hand 4), rework the AI traits.
- Leads are not covered by the blunder guard yet; level is only read by moon defense and expert limits so
  far -- other "advanced player" ideas (the parked play-to-win trait) can now key off `Profile.isExpert()`.

---

## Reference: ruleset -- Omnibus Hearts (not standard Hearts)

This project's target ruleset is **Omnibus Hearts**, which adds one card to
standard Hearts:

- **The Jack of Diamonds is worth -10 points** if captured -- a NEGATIVE
  bonus, unlike Hearts (+1 each) and the Queen of Spades (+13). Players want
  to WIN this card, not avoid it -- inverts the normal "avoid everything"
  strategy for Diamonds specifically.
- **Shooting the Moon** (all 13 Hearts + the Queen of Spades) still gives
  -26. The Jack of Diamonds bonus is independent and stacks -- a moon-shooter
  who also captures the Jack scores -36 on that hand.
- A player who ISN'T shooting the moon can still grab the Jack away from a
  moon-shooter as a defensive/consolation move -- doesn't stop the shot, but
  banks -10 for whoever grabs it.

Fully implemented and verified (`Scoring.java`, `Rules.java`) -- see
`CHANGELOG.md`'s 2026-08-09 rules audit for the empirical verification
(124/124 correct trick winners, 9/9 hands conserving points, zero rule
violations across 98 tricks).

---

## Open bugs / UX

### South's cards visually wrong (disappearing) -- open, separate from the table-slot bug below
Reported 2026-08-06 as cards *disappearing* near hand-end. **Not reproduced
since** in that exact form. Originally this section also carried a
2026-08-16 "wrong-icon variant" entry, but the user corrected that report
on a second look -- the stale icon was in the TABLE-SLOT display, not a
hand-selection button, and a mis-identified initial diagnosis was committed
to this file in error. See `CHANGELOG.md`'s 2026-08-16 entry for the real
bug and its fix. This entry now covers only the original, still-unreproduced
2026-08-06 disappearing-cards report. Detection logging already in place
(`HAND DESYNC` line, per-card enable-pattern dump, `remove_button()` logging
every removal) -- worth a fresh look if it recurs, checking whether it
correlates with hand-end specifically.

### Disabled/illegal cards are hard to visually distinguish
Flagged 2026-07-31 during a live playthrough. Illegal cards in South's hand
are disabled via plain `JButton.setEnabled(false)`, so Swing auto-generates a
grayscale icon. Numbered cards read fine grayed-out; face cards (Jack/Queen/
King) have ornate line art that grays out into something easily mistaken for
a face-down card-back. Not yet designed -- options to consider: a custom
disabled icon per card (lower opacity instead of grayscale, or a subtle
overlay) rather than relying on the LookAndFeel default, possibly paired with
the (already-built) keyboard card-selection feature so illegal cards don't
need to be visually parsed as carefully in the first place.

---

## Open tactic refinements

### Passing-craft per-type nuances -- Avoids_Queen half SHIPPED 2026-08-16
`TacticWeights`'s `PASSING_CRAFT` row is still a binary gate (weight 0 =
naive pass, anything else = the full strategic pipeline). Of the two
nuances originally noted here:

- **`Avoids_Queen`** -- DONE, see `CHANGELOG.md`. Shipped as
  `Deal_Hand.trustsQueenProtection()`: the type declines to count Spade
  protection, so it ships even a well-protected Queen instead of holding
  and controlling her. Deliberately narrow -- the changelog entry records
  why the obvious "scale the Queen-shaped scores" version measured as a
  provable no-op.
- **`Moon_Shooter` keeps its own kit when not committed** -- SHIPPED
  2026-08-23, see `CHANGELOG.md`. Landed exactly as scoped here: a mild
  `moonFuel()`-proportional reluctance in `dangerScore()`
  (`Deal_Hand.moonKitRetentionBias()`), trait-gated, NOT a switch to
  `offensivePass()`. Its blocking prerequisite
  (`reevaluateMoonShotAfterExchange()`) had shipped 2026-08-16.

### Jack-of-Diamonds "realistic chance" viability dial -- SHIPPED 2026-08-23, see CHANGELOG.md
Both halves are now done. The Queen-trap half shipped 2026-08-16
(`AICardScorer.jackPullRisksTheQueen()`, gating the JACK_HUNT pull lead);
the positive "can I realistically WIN it" half shipped 2026-08-23 as
`HandEvaluator.hasJackCaptureChance()`, gating the same pull lead. The
held case needed no new code -- `hasJackOfDiamondsProtection()` already
covered it via `AI.shouldProtectDiamondSuit()`, which is what this item
originally predicted it would map onto.

**Still open, if this gets picked up again:** the per-player personality/
tolerance dial. Both gates are currently absolute predicates that every
strategic type applies identically; the original note flagged that a
player's appetite for chasing the Jack is naturally a personality axis,
the way `moonGamblePercent` and `Counts_cards` became per-roster-player
rates. Nothing about the shipped gates blocks that -- it would sit
outside them, the same outer-gate shape those two use.

### `Counts_cards` -- SHIPPED 2026-08-16, see CHANGELOG.md
The orphan trait is resolved: revived as a per-INDIVIDUAL-roster-player
ability (0-100% rate, re-rolled every hand), gating exactly the
`TacticWeights.COUNTED_CERTAINTIES` tactic family (counted Queen lead,
counted Jack capture, safe high-Spade unload, free capture, and the new
minimal-win scorer) via `AICardScorer.effectiveWeight()`. Everything else
in the file -- moon defense, exit-card preservation, the Jack-giveaway
grading, the high-Spade-lead exception -- stays universal, per the user's
explicit instruction not to lose those.

**Still open, if this gets picked up again:** the gate today only covers
the 6 scorers that already exist under `COUNTED_CERTAINTIES`. If a future
tactic wants to reason from void/count data *probabilistically* (not
provably-certain -- e.g. "this seat's shown void in two suits, so they
likely don't hold the Queen either"), it isn't automatically covered and
would need to either join `COUNTED_CERTAINTIES`'s tag or get its own
explicit check against `Player.countsCardsThisHand`.

### Ace-of-Hearts flush + moon-gamble trait -- SHIPPED 2026-08-16, see CHANGELOG.md
User proposed leading a boss card into a known void hoping to flush the
Ace of Hearts out of hiding (investigated first: this specific idea
would NOT have worked in the hand that prompted it, but the general
mechanism was sound and missing). Shipped as `AICardScorer`'s "PULL THE
ACE OF HEARTS OUT" moon-offense lead -- the mirror image of the existing
`QUEEN_FLUSH` tactic, gated the same provable-void way, but requiring a
GUARANTEED WIN rather than a guaranteed loss (the flushed Ace has to land
in the shooter's own pile, not some third seat's). Also shipped a new
per-roster-player `moonGamblePercent` trait (0-100%, same outer-gate
shape as `Counts_cards`) plus a standings-aware boost
(`Deal_Hand.effectiveMoonGamblePercent()`) that raises a trailing seat's
effective rate, per the user's explicit request.

**Not built, flagged for later if wanted:** the same leader-favors-
discarding-danger-cards reasoning that earned the flush's LEADER_BONUS
could plausibly extend to the ordinary void-discard follow logic too (a
leader who's void and happens to be holding the Ace of Hearts might
prefer discarding it over some other safe card, independent of any
shooter's lead) -- deliberately NOT built here since it's a different
scorer (follow-side, not this lead-side flush) and wasn't part of what
was asked for.

### Borderline moon-shot Spade caution -- SHIPPED 2026-08-17, see CHANGELOG.md
Deep-dive web research (user request) into expert Hearts consensus on
leading the Ace of Spades: a shooter on the weaker King-of-Hearts route
(no Ace) should hold its Spade control back rather than spend it hunting
the Queen -- defenders usually hand a suspected shooter the Queen for free
anyway, and leading the Ace/King of Spades in sequence is called out by
name as exactly the play that telegraphs a shooter's hand for no real
gain. Shipped as a new `AICardScorer` MOON_OFFENSE guard plus
`Player.moonShotHasAceOfHearts` (set alongside the commitment itself,
finalized post-exchange the same way the commitment flag is). Stands down
the instant the uncertainty resolves either way -- the shooter gets the
real Ace, or it falls to anyone. Deliberately narrow: only the Ace/King
are held back, not Spades as a suit, since a low-Spade lead doesn't lock
in the Queen's cost or spend the control cards.

### Counted-out Jack-of-Diamonds cash-in -- SHIPPED 2026-08-16, see CHANGELOG.md
Same live-hand review, a second finding: a Cautious seat holding the Jack
of Diamonds PLUS total protection (the Ace, King, and Queen too -- nothing
anywhere could beat it) still led its weakest Diamond instead of cashing
the Jack in, purely because the "counted-out lead/capture" scorers were
tagged `JACK_HUNT`, and Cautious's "no flashy plays" personality zeroes
that whole family -- including the already-safe case, not just the risky
"pull it from an opponent's hand" one. Retagged both counted-out scorers
(lead and follow side) to `JACK_GUARD`, which every real strategic type
keeps at full weight; only `BAD`/`BRAIN_DEAD` still skip it, correctly.
The genuinely speculative Jack-hunt tactics (pull lead, opportunistic
capture of a Jack someone else exposed) stayed on `JACK_HUNT`, unchanged.

### Future tactic ideas (surfaced 2026-08-07, not yet built)
- **Opportunistic mid-hand moon adoption** -- commit currently happens only
  at passing; a hand that develops perfectly (early captures land, control
  cards intact) could upgrade into an attempt mid-hand. **Caution:** the
  one-time-commitment-at-hand-start design was deliberate, and the
  2026-08-09/08-10 fixes (see `CHANGELOG.md`) show how much subtle state the
  commitment flag already carries (KEEP-hand staleness, the alive-check
  drift). Re-opening the decision mid-hand reintroduces exactly the
  re-evaluation complexity the original design avoided -- only build this
  with a strict, provable trigger (e.g. "captured points already + the Heart-
  control gate passes on remaining cards"), and rank it last among these
  ideas.
- **Control-card retention** -- SHIPPED 2026-08-23, see `CHANGELOG.md`.
  Both directions built as designed (stranding shed vs. tempo hold, equal
  magnitudes so they cancel when they conflict), and the investigation
  turned up a real defect on the way: the lead-side unload was paying a
  flat Tier-1 +1200 in the Queen-ALREADY-CAPTURED state, where there is
  no liability left to unload at all. Split into
  `AICardScorer.highSpadeUnloadIsUrgent()` (the genuinely urgent case
  only) plus the new graded tempo scorer.

  **Still open, if this gets picked up again:** the stranding term is
  binary-ish (singleton full, doubleton half) and takes no account of how
  LATE the hand is. The reasoning for shedding early -- opponents still
  hold cards in every suit, so they have little to throw onto a Spade
  trick I win -- genuinely strengthens as voids accumulate, and a
  hand-progress term (cards remaining, or void count) would express that
  better than the flat count does. Deliberately not built: it adds a
  third tunable to a tactic whose two existing ones have no live-play
  data behind them yet.
- **Endgame claim** -- SHIPPED 2026-10-08 as the endgame solver, see
  `CHANGELOG.md` (redesigned: a provable sweep collects every remaining card
  whatever the order, so "sequencing" it can't change any points).

  **Still open, if picked up again:** the 4-card window only runs when the
  voids have pinned the possible splits down to `MAX_DEALS_AT_FOUR`; a
  smarter search (alpha-beta-style pruning for the acting seat, or caching
  repeated positions) could open it wider. And opponents are modeled as
  seeing every card -- a stronger model would let them reason only from what
  THEY can see, at real cost in complexity.
- **Proactive "bleed out" Queen-of-Spades resolution** -- SHIPPED 2026-10-09,
  see `CHANGELOG.md`. Original note kept below for history.

  (Original, surfaced 2026-08-26,
  from a user deep-dive into Hearts strategy consensus following the tactic
  2d fix -- see `CHANGELOG.md`'s 2026-08-26 entry). Researched, not
  designed: a defender holding the Queen WITH real Spade length (the Ace
  and/or King plus several low ones -- distinct from a bare Queen-plus-one
  hand, which has no such option) can lead its own high Spades out early,
  before being cornered, to accelerate how fast the suit empties table-wide
  and buy better odds of a safe unload later, rather than passively waiting
  for either a costless duck or a proven-safe `COUNTED_QUEEN_LEAD`. Source:
  [Mark's Hearts Tips](https://mark.random-article.com/hearts/one-liners.html),
  "Bleed out the Spades" -- lead the Ace, then the King, then the low
  spades, keeping the Queen for last.

  This is a genuinely different case from the existing
  `LEAD_HIGH_SPADE_LIVE_QUEEN` danger penalty, which exists for good reason
  (the 2026-08-07 bug: North held ONLY the Ace and Queen and led the Ace,
  stripping its own protection with nothing left to duck the Queen with
  afterward) -- that penalty is correct for a THIN Spade holding and should
  stay. The gap is a hand with enough low Spades that leading the Ace/King
  away still leaves ducking material behind, where the flat penalty may be
  overcautious. Would need its own gate (real Spade length, not just
  holding the top cards) and is genuinely speculative -- a probabilistic
  bet on future suit depletion, not a provable play like the counted-out
  lead -- so it likely wants the same standings-aware treatment
  `MoonThreat.queenDenialWorthIt()` already gives the reactive
  take-the-Queen decision. Not designed in detail; rank alongside the other
  items in this list, not ahead of them.
- **Proactive lead-seizing to control stopper timing** (surfaced 2026-08-16,
  from a user hand review) -- right now a defender only reacts when it's
  already forced to follow or already has the lead; nothing makes a
  defender deliberately WIN a trick in one suit specifically to gain the
  lead and control when a reserved stopper (Heart or Spade, see
  `CHANGELOG.md`'s 2026-08-16 moon-defense entries) gets played, rather
  than just passively waiting to be forced onto it. Explicitly deferred --
  the hand that surfaced it didn't even need this (the shooter won 13/13
  tricks, so no defender ever got a chance to win anything regardless).
  Harder than the reservation fixes: requires weighing "is winning THIS
  off-suit trick worth it just for lead control" against ordinary safety,
  not just re-ordering which already-losing card to shed.

(**End-of-game awareness**, **Targeted Queen placement**, and **Exit-card
preservation** were all on this list until 2026-08-15/16 -- implemented,
see `CHANGELOG.md`. The targeted placement shipped as its standings-only
version in the `SOCIAL` tactic family; the later grudge/friendship blend
from `DESIGN_roster_and_trashtalk.md` Phase 4 should extend that same
scorer slot rather than adding a second one.)

---

## Planned big features (design-only, nothing built)

### trashTalk -- AI commentary during play
Idea from 2026-07-30: give each computer opponent in-game commentary/taunts
during play, flavored by their assigned `Player.Player_Traits`. Full design
in `DESIGN_roster_and_trashtalk.md` section 4 -- a scaled-down Valve
response-rule system (event + fact bag -> criteria-matched rule -> response
group, sorted most-specific-first), external line file (not hardcoded Java),
and strict frequency control (silence as default, hard per-hand budget,
per-speaker cooldown, no-repeat window) -- Hoyle Card Games shipped ~8,500
lines and still felt repetitive after an hour, so volume is not the lever,
frequency/specificity is.

### Persistent AI player roster -- REMAINING phases only
**v1 shipped 2026-08-16** (see `CHANGELOG.md`): 10 persistent identities
(AI_01..AI_10, each referencing an existing `Player_Type` preset), seat
dropdowns select roster members, human-editable `roster.txt` +
`history.txt` under `%USERPROFILE%\.hearts2\` (versioned, forgiving
parser, atomic writes), and the shared per-pair tendency-counter store
recorded from the GameEventBus. Still OPEN from the full design
(`DESIGN_roster_and_trashtalk.md`):
- **AI players, the user's plan (2026-10-10)** -- replaces the design
  doc's ranked-trait resolver. Steps 1 and 2 SHIPPED (every AI is a
  Profile: template + overrides; per-hand reliability, moods, near-miss
  blunders, and Queen payback; see `CHANGELOG.md`). Remaining, in order:
  2a. INVESTIGATE (found 2026-10-10, 2400 whole seeded games each, one
     variant vs three Normals, counting 50%; plain Normal = 24.8% won):
     a Normal with every skill family off won 30.4%. One group at a time:
     PASSING off 43.7% (!), MOON off 30.9%, QUEEN 25.0%, STANDINGS 24.7%,
     SAFETY off 17.8%, JACK off 10.5%. Moon split: MOON_DEFENSE off 31.0%,
     MOON_OFFENSE off 24.4% -- defense is the costly half. Careful passing
     also loses where everyone else passes naively (Normal vs three
     PASSING-off seats: 16.8%), so the strategic pass pipeline itself is
     worse than "pass your three highest cards". FIRST ROUND FIXED
     2026-10-10 (blocker, void, height, no insurance -- 38% in the
     measurement; see CHANGELOG). SECOND ROUND FIXED 2026-10-11 (keep
     two ducking Hearts; Ace/King of Spades to the leader -- see
     CHANGELOG). MOON_DEFENSE measured rule by rule 2026-10-11: only "don't
     feed the shooter" and "take a point trick" cost games, by firing on
     the first Heart -- now the MOON_ALARM strategy component. STRATEGY
     COMPONENTS shipped 2026-10-11 (see CHANGELOG): every rule we replaced
     or tried is now a per-AI option, so AIs can differ in WHAT they do,
     not just how reliably. CLOSED 2026-10-11: skill ablation re-run on
     the new baseline -- every group helps or is neutral (CHANGELOG).
  2b. Queen payback follow-ups, only if wanted: "save the Queen for the
     target" (hold her through a non-target trick when a target is likely
     to be winning one soon -- risky, would need measuring), and payback
     LEADS (lead Spades to smoke the Queen toward the target).
  3. LEARNING: slow growth with experience plus targeted jumps from
     lessons (e.g. moon defense rises after being mooned), diminishing
     near each AI's own ceiling; learns from AIs and people alike. Raises
     reliability % and lowers blunders -- never grants a family the
     profile doesn't allow. Stored PER PERSON: each human profile gets its
     own history file ("world") where the AIs start from roster.txt; the
     existing history becomes Wayne's world. Needs a "who's playing"
     human-profile picker (one human per game; different people on this
     computer at different times).
  4. Author the ten AIs (lineup sketched in conversation: beginner, steady,
     careful counter, Jack hunter, moon chaser, Queen-phobic, wildcard,
     late bloomer, expert, shark) and VERIFY the strength order with
     simulated round-robins / ELO -- more traits is not automatically
     stronger. User (2026-10-11): no single expert -- several strong AIs,
     each a DIFFERENT mixture of strategy options, unique like real
     people. WILDNESS shipped 2026-10-11 (see CHANGELOG): per-AI
     straying from usual options, opening-hands boost, standings scaling.
     For step 3: wildness should ease with career experience (user: top
     players are wilder early in their career too). Still open: a shark
     that picks its MOON_ALARM per opponent -- waits on the memory design.
     MOON MEMORY DESIGN (user, 2026-10-11, in discussion -- not built):
     - OWN EYES ONLY: an AI knows only what it saw in games it sat in;
       never the shared StatsStore/OpponentModel (stats screen only).
       "Too AI" otherwise -- the human must feel they play people. Applies
       to all future learning.
     - Remembers like a person, not rates: completed moons; moons that
       knocked it out of 1st or won the game (MOST memorable); failed
       attempts; who stopped them; who stops MY moons.
     - Impressions per player (AIs and the human alike): shoots never /
       sometimes / always; reckless (tries a lot, fails) vs selective
       (only when it can make it); stopper.
     - Uses: defense -- alarm early vs selective shooters, milder vs
       reckless, late vs non-shooters; offense -- shoot less with a known
       stopper at the table, less still when they're positioned to stop
       it -- by SEAT (e.g. plays right after me) AND by STANDINGS (e.g.
       in 2nd, every reason to stop me); user: both.
     - Memory lasts GAMES, not time; length by player type, changeable.
       Per-AI dials: observant, memory length, overreaction, how often
       they voice a moon suspicion (never 0: every player talks, some
       more than others; every AI says something).
     - Never impossible to beat: only shifts choices, no hidden info;
       reliability still applies; can be fooled by a fake moon; fades.
     - Table talk: every player talks, some more than others. The
       "sometimes / never" part applies ONLY to calling out a suspected
       moon shooter (some players never warn the table about that).
       AI-to-AI too.
  5. Bring "play to win" back from branch `win_chance_objective` as an
     advanced-only family.
  Still open: what makes an AI "advanced" (counts-cards rate vs an explicit
  skill-tier label).
- **Proper names/flavor** for the roster members (currently AI_01..AI_10
  by explicit user choice, "internally for now").
- **AI reads the tendency counters** -- partly done 2026-10-10 (see
  `CHANGELOG.md`): moon tendency is now recorded by standings situation and
  scored by `roster/OpponentModel`, but NO AI decision reads it -- the two
  hooks tried (keep a top Heart from a likely shooter; take the Queen
  earlier against one) were measured harmful / unmeasurable and removed.
  Any future consumer must clear the duplicate test, and should not act
  EARLY on a rare event. Not yet split by game stage (closeness to 100).
- **"Play to win" trait for advanced players** -- parked on branch
  `win_chance_objective` (win-chance table, Queen decision and endgame
  solver objective on it; measured neutral on win rate across all seats).
  User wants AIs to carry SUBSETS of the playing traits with advanced ones
  having all; this lands as one of those traits. Open design question
  (user: "discuss later as we work on the AI players"): what makes a player
  advanced -- a high counts-cards rate, or an explicit skill tier.
- **Episodes + affinity/grudges with decay** (phase 3 -- the Queen-payback
  grudge, 3-hand taper, shipped 2026-10-10 as a first concrete piece), the
  grudge-blended `SOCIAL` extension (phase 4 -- the standings-only
  version shipped 2026-08-15), and **trashTalk** (phase 5, section 4 of
  the design doc).

### Player stats tracking -- v1 SHIPPED 2026-08-16, extensions SHIPPED 2026-10-10
Comeback rate, ELO, passing outcomes and lead success (overall and by
suit) are done -- see `CHANGELOG.md`. Still open from the original wish
list: void-suit success rates (how often a void was exploited for a free
discard). A new counter over the existing store, no new architecture.
