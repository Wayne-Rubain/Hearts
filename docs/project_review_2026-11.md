# Project review, 2026-11-05

Where Hearts_2 stands, what the measurements say, and what to do next. Written after the 2026-11
cloud round (round robin, lead blunder guard, signature options).

## Where things stand

**Strengths**
- **Size and tests:** about 15,500 lines of game code and 9,900 lines of tests, in 50 test suites that
  all pass. Rules and scoring were checked against real games in August.
- **Measurement:** seeded whole-game simulation, round robins (`Lineup`), and card-for-card proof that a
  change didn't alter play (`Equiv`). Every decision this month came with numbers.
- **The personality system:** levels, blunders and their guards, wildness, strategy options, moon
  interest, and now signature options.

**Not merged yet** (as of this review): `cloud/integration-2026-11`, `cloud/lead-blunder-guard`,
`cloud/signature-options` and the branches built on them, each containing the one before. The 10/10
branch `win_chance_objective` (play-to-win) was parked on purpose.

## What the review found

### 1. Every personality is a weakened Normal

Against three plain Normals (fair share 25%): Careful Counter 26.6%, Steady about 20%, Shark 17%,
Queen-phobic 14.5%, Jack Hunter 12.7%. The plain Normal is as strong as the best AI on the roster and
stronger than all the rest.

The roster is built by taking skill away (reliabilities below 100, moods, blunders, wildness). That works
for beginners. But the goal is that each AI tries to win, with several different experts, and today the
experts are at best equal to a default player.

**Recommendation:** set a target that level 4 and 5 players beat three Normals, say 28% or more, each in
its own way. Candidate levers: the parked play-to-win objective as an expert-only option, card counting
at 100%, and signature options that measure as real gains (Jack Hunter's KEEP_DIAMONDS added 4 points in
the round robin).

### 2. About a quarter of the settings do nothing

Each of 53 roster settings was changed on Steady, and the seeded games (out of 30) where any card played
changed were counted:

| Settings | Games that changed |
|---|---|
| `weight` 0.5 or 1.5 for END_GAME, SOCIAL, PASSING_CRAFT, JACK_HUNT; QUEEN_FLUSH 1.5; MOON_DEFENSE 1.5 | 0 |
| `blunder` 10 on Steady (level 4) | 0 (experts are capped at 2%) |
| `moonGamblePercent` 50 | 0 |
| STANDINGS reliability 50; payback on; COUNTED_CERTAINTIES weights | 1 to 2 |
| Strategy options, other reliabilities, styles, count cards, moon interest | 6 to 30 |

The cause is structural: the scorers use large fixed tier scores, and multiplying a winning score by 0.5
to 1.5 rarely changes which card ranks first. Avoids-Queen and `JACK_HUNT` were not one-off bugs; the
sheet's "Weight overrides" row is mostly an illusion.

**Recommendation:** a permanent audit that flags any sheet setting that changes play in fewer than about
3 of 30 seeded games. Then, for each dead setting, either make it work (for example, a weight could
become the chance that a tactic fires, the way reliability already works) or remove it from the sheet.

### 3. Almost no testing against a human

Every number this month is AI against AI. There is one finished live game: Wayne won, and nobody tried the
moon. The goals (feels like real people, mistakes are human, the moon gets tried) can only be judged from
real games.

**Recommendation:** play about 10 games against the draft ten once the backlog is merged. Commit the game
logs and ledger to a `playtests/` folder in the repo, so a cloud session can analyze them (a cloud session
cannot reach `~/.hearts2` on Wayne's PC).

### 4. The game logic is written twice

`HeadlessSimulator` has its own copy of the trick loop, separate from the real game in `Play_Hand` and
`Hearts_JFrame` (2,277 lines that mix screen code with game flow). They already drifted once: the
simulator ignored `moonGamblePercent` until 10/14. Every measurement is only as good as the simulator's
copy of the real game.

**Recommendation:** pull out a single game engine (deal, pass, tricks, scoring) that both the simulator and
the UI call, proven unchanged with `Equiv`. This is also the groundwork for the UI upgrade: the new UI then
only has to draw the screen.

### 5. Process friction

- **Merge conflicts:** every branch conflicts in `CHANGELOG.md` and `TODO.md`. A `.gitattributes` with
  `merge=union` for those two files makes git keep both sides automatically.
- **No automatic test run:** there is no GitHub Actions workflow, so tests only run when someone runs
  them. A small workflow running `AllTests` on every push would catch a broken branch before it is merged.
- **Old docs:** `hearts.md` dates from August and is out of date, and `CHANGELOG.md` is 2,260 lines. Mark
  `hearts.md` as historical, so `HANDOFF_cloud.md` stays the single entry point.

## Recommended order

| # | What | Who | Size |
|---|---|---|---|
| 1 | Merge the backlog; add the `.gitattributes` union rule | Wayne, plus a small cloud change | Small |
| 2 | Decide the signature options for the roster; play about 10 games and commit the logs | Wayne | Medium |
| 3 | Settings audit; fix or hide the dead settings | Cloud | Medium |
| 4 | One shared game engine for the simulator and the UI, proven with `Equiv` | Cloud | Large; unblocks the UI upgrade |
| 5 | Expert strength target, starting with play-to-win as an expert-only option | Cloud measures, Wayne decides | Medium |
| 6 | Moon memory design, then table talk | Wayne's design first | Large |
| 7 | Automatic test run on GitHub | Cloud | Small |
| — | Bonehead (decision 4) and the UI upgrade | After playtests and the engine work | — |

Items 1 and 3 are cheap, and 3 makes every later tuning decision more reliable. Item 4 is the most
important long-term step: both the measurements and the UI upgrade rest on it.

Wayne asked for 1, 3 and 4 on 2026-11-05.
