# Hearts_2: Project handoff for Claude Code cloud sessions

Written 2026-10-13, refreshed 2026-11-05 (checked against `AI_Player_dev` at `c71bd11`).
Public repo: `https://github.com/Wayne-Rubain/Hearts`. Read the "Working together" section first.

**Work from `main`.** It is the working branch of this public copy.

Read order for a cold start: this file, then `CLAUDE.md`, then `TODO.md` (open items only), then
`CHANGELOG.md` (everything shipped, with measurements), then `hearts.md` (older architecture snapshot) and
`DESIGN_roster_and_trashtalk.md` (roster and commentary design).

---

## 1. What the project is

A Java Swing Hearts game ("Omnibus Hearts": Jack of Diamonds is -10, Queen of Spades +13, Hearts +1, moon = -26).
Four seats, one human and three AIs. The current work is the AI players: making ten distinct AI
personalities that feel like different people, each with its own mix of strategy components, wildness,
mistakes and (planned) memory. Wayne's goal for each AI: win the game (placement does not matter).

## 2. Build, test, measure (no IDE needed)

```
javac -nowarn -d out -cp forms-1.3.0.jar $(find src test tools/measure -name "*.java")
java -cp "out:forms-1.3.0.jar" hearts_1.AllTests          # use ; instead of : on Windows
```
Expected: `53 suite(s) run, 0 failed` on `main` (2026-11-08, after OwnQueenWinTest). If that isn't what you get, stop and
report it. Java 21+ (author uses 25). `forms-1.3.0.jar` is in the repo.

Measurement harnesses (`tools/measure/hearts_1/`, package `hearts_1`, compiled with the command above):
GitHub runs the same build and `AllTests` on every push (`.github/workflows/tests.yml`), so a branch shows a
green check or a red X; still run them yourself before pushing.

Simulator stdout must go to `OutputStream.nullOutputStream()` (the harnesses already do this). A
`ByteArrayOutputStream` sink once hit the 2 GB limit.

- **DialSim** `DialSim <variant> <games> [opponentVariant] [gamble=N]`: one variant seat against three plain Normals,
  whole seeded games, seat rotated. Prints win % and average final score. Variants: `normal`,
  `s:COMP=OPT+COMP=OPT` (strategy options), `w:N` (wildness percent), `off_<SKILL>` (a skill group at 0%
  reliability: JACK, SAFETY, PASSING, MOON, QUEEN, STANDINGS), `blunder10`, `aggrMood50`, `payback`, etc.
  Use 2400 games. The standard error is about 1 point; ignore differences smaller than 2 points.
- **Lineup** `Lineup <rosterDir> <gamesPerCombo> <shard> <shards> [nogamble|gamble] [nointerest]`: round robin
  over every 4-player combination of a roster (210 for ten players; each player gets 84 games per unit of
  gamesPerCombo). Every player plays at their own count, moon-gamble and moon-interest rates; `nogamble` and
  `nointerest` turn those two off. `tools/roster_sheet/draft_roster.txt` is the draft ten (put it in a folder
  as `roster.txt`). Shard across processes for speed; the seeds depend only on the combination, so any
  sharding gives the same totals.
- **MoonCount** `MoonCount <rosterDir> <games> [seedBase]`: moon tries and moons made per player, per level,
  and by standing at the start of the hand. Random 4-player tables.
- **RosterAblate** `RosterAblate <rosterDir> <id> <games> ["key=value;key=value"]`: one roster player, with
  optional edited settings, against three plain Normals. Used to find which setting drives a result.
- **KnobAudit** `KnobAudit <rosterDir> <id> <games> [minChanged]`: changes every roster setting in turn and
  counts the games where any card changes; with `minChanged` it exits 1 if a setting falls below it. Use
  it after adding a setting, to check it does something (`docs/settings_audit_2026-11.md`).
- **Equiv** `Equiv <hands>`: fingerprints every card played in fixed seeded hands, to prove a refactor
  changed no play. The old reference file is stale (defaults changed in 610cf01), so regenerate a reference
  from a known-good commit before using it.
- Test side: `test/hearts_1/DuplicateTest` and `GameDuplicateTest` (same deals, tactic on vs off) and
  `HeadlessSimulator` (the engine the harnesses use).

Wayne's standing rule: **measure tactics with whole games, not with per-hand or per-point proxies.**
Several "obvious" improvements measured worse in whole games.

## 3. Architecture of the AI (what to know before touching it)

All in `src/hearts_1/`.
- `Profile`: a preset template (Normal, Cautious, Aggressive, ...) plus per-AI overrides. Holds skill
  reliabilities per group, mood percentages, blunder rate, payback, strategy options, wildness and level.
- **Level 1-5** (`Profile.level`, roster key `level`, default 3) is how much reasoning an AI does.
  `Profile.isExpert()` is true at level 4 and up. It drives the level-graded moon-defense ladder and the
  expert limits: the blunder rate is capped at 2% (level 4) and 1% (level 5), an expert's slip is only ever
  its 2nd-best card, and wildness never strays an expert into a `Strategy.isWeak` option.
- **Blunders** (`AI.nearMiss`): a near-miss is the 2nd-best card, or the 3rd one time in three. When
  *following*, `AI.isSuicidalFollow` vetoes a suicidal runner-up: never the Queen of Spades onto a Spade
  trick she would win, and as last seat never 10+ points worse than the best card (4+ for experts). Leads
  are not covered on `AI_Player_dev` (`AI.isSuicidalLead` now covers leads too). The `Bad` trait (random 1 card in 3) and
  `Brain_Dead` bypass all of this on purpose.
- **Moon interest** (`moonInterestPercent`, per hand, rolled in `Deal_Hand`): a chance to be interested in
  a moon even when the hand would not normally qualify. Level defaults 22/8/3/1/1 % for levels 1 to 5,
  doubling as the seat trails the leader by up to 30 points. A zero rate draws no random number.
- **Building pass** (`Deal_Hand.buildsTowardMoonShot`, logs `MOON_BUILD`): a moon-interested player passes
  toward a shot.
- `Strategy`: enum of components with options, one default each. `Player.uses(option)` answers "does this
  player use this option this hand" (profile choice, unless wildness made it stray this hand).
  Components: KIT_PASS (default HOLD_BACK), LOW_HEARTS (KEEP_TWO), SIDE_SUITS (BY_HEIGHT), VOID_HUNT
  (WHEN_SHORT_ON_SPADES), SPADE_CONTROL (TO_LEADER), QUEEN_KEEP (FOUR_LOW_SPADES), HEART_INSURANCE (OFF),
  MOON_ALARM (SHOOTER_READ). Every option is a real, selectable alternative; losing options are kept on
  purpose so weaker AIs can use them.
- `Deal_Hand`: the passing logic. `AICardScorer`: card play scoring (tactic weights in `TacticWeights`).
- `MoonRead`: public-information moon reads (heart discards, "led a sure winner on purpose", kept lead).
  Reset every hand. This is not memory across hands.
- Wildness (`Profile.Wild`, rolled in `Player.rollHand`): per hand, a chance to stray from the usual option
  of each component. Per-AI base, opening-hands boost, leading and losing multipliers, stray weights.
  Wildness off draws no random numbers, so wildness-off play is identical to before.
- Roster: `roster/Roster.java` reads `roster.txt`. Keys: `template`, `level`, `style.X`, `countCardsPercent`,
  `moonGamblePercent`, `moonInterestPercent`, `blunder`, `payback`, `reliable.X`, `weight.X`, `strategy.X`,
  `wildness`, `wildness.opening`, `wildness.openingHands`, `wildness.leading`, `wildness.losing`, `stray.X`,
  `moonLook`, `moonOptimism`, `moonHeartLast`.
  `tools/roster_sheet/check_roster_sheet.py` checks the sheet covers every key.
- **GameEngine** (2026-11-05): the play steps shared by the real game (`Play_Hand`) and the simulator:
  `chooseCard`, `placeCard`, `resolveTrick`. Change trick play here, never in one caller only.
  `tools/measure/hearts_1/UiSmoke` plays the real Swing game unattended (use `xvfb-run` in a container) to
  check a change didn't alter real play.
- **Moon look** (2026-11-08, `moonLook` / `moonOptimism`, off by default): a second way to read a hand for a
  moon, fitted on Wayne's own live tries (pass the low Hearts, keep the top ones, run a long side suit).
  `HandEvaluator.sideSuitMoonScore`, `moonLookPass`; see CHANGELOG 2026-11-08 for measurements.
  `moonHeartLast` (default 100): with the A K of Hearts, chance per hand to play the extra Heart last
  (Wayne's way) rather than test it early.
- **Live play records** (`src/hearts_1/events/`): `GameLog` writes each game's console log to
  `~/.hearts2/gamelogs/`, and `GameLedger` adds every finished game to `games_ledger.csv` there.
  `ForcedDeal` replays a deal: put the four DEAL lines from a log in `~/.hearts2/forced_deal.txt` and the
  next session's first hand is dealt from it.

Wayne's design principles for the AI (do not break these):
1. Several experts, each with a different mix of skills; not one true expert. AIs must differ.
2. The human must feel they play people. No shared memory between AIs and no hidden information. An AI
   may only use what it saw happen at the table.
3. Never make shooting the moon impossible.
4. Beginners and the weakest players are wilder; experts are wild early in a game; the losing player is
   wilder; wildness eases with a player's career experience.
5. Human-like mistakes are wanted, but a mistake must never be suicidal.

## 4. State of play

**Draft ten, round robin** (all players at their own rates). 10/17, on `AI_Player_dev`, 1,680 games each
(standard error about 1.2): Careful Counter 44.6, Steady 36.5, Shark 36.0, Jack Hunter 27.9, Queen-phobic
27.8, Moon Chaser 26.6, Late Bloomer 17.7, Wildcard 16.9, Beginner 11.1, Bonehead 4.9.
11/02, on `AI_Player_dev`, 5,040 games each (standard error 0.3 to 0.7): Careful
Counter 46.1, Steady 37.0, Shark 36.1, Queen-phobic 28.7, Jack Hunter 27.3, Moon Chaser 25.8, Late Bloomer
17.9, Wildcard 15.4, Beginner 11.1, Bonehead 4.7. Full report, with moon, Queen and Jack counts, in
`docs/lineup_2026-11.md`.
- Shark (level 5) and Steady (level 4) are tied. Shark's Aggressive 30% mood cost it about 9 points
  against three Normals (measured 10/14); Wayne decided on 10/17 to keep it (an expert who sometimes overplays).
- Every AI tries the moon at least 2.5 times per 100 hands; Moon Chaser about 15.
- Live play: Wayne plays the draft ten (his `~/.hearts2/roster.txt` matches `draft_roster.txt`). One
  finished game so far: Wayne won and nobody tried the moon.

**One seat against three Normals** (2,400 games, fair share 25%; measured 10/11, before levels and moon
interest, so use only for comparing options):
- Defaults (HOLD_BACK kit pass, SHOOTER_READ alarm) versus the old defaults: 32.3% for the new Normal;
  18.0% for an old-default seat against new Normals. Turning one skill group off: JACK 11.7, SAFETY 19.2,
  PASSING 21.5, MOON 24.0, QUEEN 25.8, STANDINGS 25.3, against 25.3 baseline.
- Options: KIT_PASS FREE 32.1 vs HOLD_BACK 30.4; LOW_HEARTS PASS_FREELY 18.2, KEEP_LOW 23.8, BY_HEIGHT 25.1;
  SIDE_SUITS FLAT 22.4; VOID_HUNT NEVER 22.5; HEART_INSURANCE ON 24.4. Wildness 15/30/60 costs
  25.8/23.9/21.9.

## 5. What is open

**Merged 2026-11-05 (c71bd11):** all cloud branches from 11/02 to 11/05 (roster-sheet-sync, lineup-2026-11,
lead-blunder-guard, integration, signature-options, settings-audit, GameEngine, ci-tests, handoff-refresh) came in as
one merge from `cloud/ci-tests`. No cloud branches are open; the old ones on GitHub can be deleted.

**Waiting on Wayne (do not do these in the cloud):**
- Fill in the roster spreadsheet (kept outside the repo; the draft
  values are also in `tools/roster_sheet/roster_sheet.py`). Names and tuning of AI-01 to AI-10 are Wayne's
  call. Then `tools/roster_sheet/sheet_to_roster.py <xlsx> <roster.txt>` writes the roster file. Never
  regenerate or ship the xlsx from a cloud session: rebuilding it overwrites Wayne's edits.
- Decisions from the 11/02 report: Jack Hunter and Queen-phobic look alike in the numbers, and Bonehead
  wins 4.7%.
- The Shark / moon memory design is agreed in principle but not final (`TODO.md`, "MOON MEMORY DESIGN" under the AI players plan). Do not
  build it until Wayne says the design is final. Settled points: each AI remembers only what it personally
  witnessed; memory length is counted in games, not time, and varies per player; impressions cover everyone
  (AI to AI too) and both seat position and standings; AIs remember reckless versus selective shooters,
  moons that took them out of first or won the game, and stoppers; they shoot less when a stopper is
  positioned to stop them. Table talk: **every player talks, some more than others**; only the call-out of a
  suspected moon shooter is "sometimes / never" per player.
- Learning step 3 (wildness easing with career experience): after the memory design.
- Reviewing Wayne's live games (`~/.hearts2/gamelogs/`, `games_ledger.csv`) happens on his PC.

**Not for now:** the two UI bugs in `TODO.md` ("South's cards visually wrong", "disabled cards hard to
distinguish") wait for the planned UI upgrade. Don't work on them.

**Good candidates for a cloud session (self-contained, measurable, no design decisions needed):**
1. Heavy measurement runs: more games on any pair of options Wayne names; shard Lineup across processes.
2. Test hygiene: add tests for any code you touch; keep `AllTests` at 0 failed.
3. Items in `TODO.md` "Future tactic ideas" are ideas only. Propose with a measurement; do not ship a change
   to default play without Wayne's approval (see below).

## 6. Working together (local session and cloud sessions on one repo)

- **Branches.** Work on a topic branch made from the latest `main`, then merge it. Never force-push or rewrite `main`.
- **Before starting:** `git pull`, then re-run the build and tests.
- **Files that collide:** `CHANGELOG.md` and `TODO.md` are edited by every session. Add your CHANGELOG entry
  at the top in its own paragraph and keep it short, so merges stay trivial. Do not reflow or reformat them.
- **Do not change Wayne's rules on your own.** Passing rules, moon-defense rules, and defaults are Wayne's
  design decisions (each was discussed and approved). If you think a rule is wrong, measure it and report the
  numbers; do not change the default. Adding a new selectable option is fine; changing which option is the
  default needs approval.
- **Questions versus commands.** "Can you do X?" means answer and wait.
- **Commit style.** Short subject, "(user rules)" or "(user design)" when it implements Wayne's decision;
  end the message with the `Co-Authored-By` line your session is given. Push right after committing.
- **Writing for Wayne.** Plain and short. In anything written in Wayne's own first-person voice, no dashes
  as punctuation.
- **Deliverables** go in the repo (for example under `tools/` or `docs/`); say where you put them.
- **Report honestly:** if a test fails or something was skipped, say so with the output.

## 7. Key files

| Path | What |
|---|---|
| `src/hearts_1/Deal_Hand.java` | Passing logic and the strategy option checks for passing |
| `src/hearts_1/AICardScorer.java` | Card-play scoring |
| `src/hearts_1/Strategy.java` | Strategy components and options |
| `src/hearts_1/MoonRead.java` | Public moon reads |
| `src/hearts_1/Profile.java` | Profiles, skills, wildness, roster key parsing |
| `src/hearts_1/Player.java` | Per-hand state, `rollHand`, `uses` |
| `src/hearts_1/AI.java` | Lead and follow entry points, blunders and the blunder guard |
| `src/hearts_1/events/` | `GameLog`, `GameLedger` (live play records) |
| `src/hearts_1/ForcedDeal.java` | Replay a deal from a log |
| `src/hearts_1/roster/` | Roster file load and save |
| `test/hearts_1/HeadlessSimulator.java` | Seeded whole-game engine for measurement |
| `tools/measure/hearts_1/` | DialSim, Lineup, Equiv, MoonCount, RosterAblate |
| `tools/roster_sheet/` | Spreadsheet builder and converter, draft roster |
| `TODO.md` / `CHANGELOG.md` | Open work / shipped work with measurements |
