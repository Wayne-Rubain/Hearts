# Design study: persistent AI roster + trashTalk

Written 2026-08-09. **Design only — nothing here is implemented.** Covers the two
largest remaining `TODO.md` features:

- **#6 Persistent AI roster** — 10-20 named AI players with ranked trait mixtures,
  cross-game memory of both human and AI opponents, payback/revenge, and authored
  friendships.
- **#5 trashTalk** — in-game AI commentary flavored by personality.

They are treated together because they are one feature wearing two hats: the roster is
the memory, trashTalk is the mouth. Everything that makes a taunt land — "you gave me
the Queen on the last trick *again*" — is a memory lookup. Building the mouth first
produces generic noise; this is why `TODO.md` already sequences trashTalk last, and the
research below independently confirms that ordering.

---

## 1. How other games have solved this

### 1.1 Contextual dialogue: Valve's response-rule system

The canonical solution to "characters that say the right thing at the right moment" is
Valve's dynamic dialog system (Left 4 Dead, Portal, Team Fortress 2, Dota 2), presented
by Elan Ruskin at GDC 2012. The mechanism is small enough to reimplement in an afternoon
and is a near-exact fit for our problem:

- The game emits a **query**: a bag of key/value facts describing the moment — what just
  happened (`concept`), who is speaking, who it happened to, health, score, how many
  times this has already fired, etc.
- A **rule** is a set of criteria over those facts (`concept == "TookQueen"`,
  `speakerGrudge > 40`, `handNumber >= 3`). A rule points at a **response** — a small
  group of interchangeable lines.
- Rules are pre-sorted by **criteria count**. Matching walks most-specific → least-
  specific and takes the first rule whose criteria all hold. This produces the
  "cascade from special to general" behavior for free: an exquisitely specific line if
  one was authored for exactly this situation, otherwise a broader one, otherwise
  silence.
- Rules can **write facts back**, which is how characters remember. This is the hook
  that connects dialogue to persistent memory.

The design point worth stealing wholesale is that **specificity is computed, not
authored**. Nobody maintains a priority list; you add a line with more criteria and it
automatically outranks the general case.

### 1.2 Persistent grudges: the Nemesis System

*Shadow of Mordor*'s Nemesis System is the reference implementation of "an AI opponent
who remembers you across encounters." What made it work, stripped of the orc theming:

- Every opponent is a **named, persistent identity** with a rank and a history — not a
  respawned generic.
- The game records **specific episodes**, not just aggregate stats: who beat whom, how,
  and what happened afterwards. Encounters reference those specifics back at the player
  by name.
- Opponents **change over time** as a result — promotions, scars, new taunts — so the
  relationship visibly has a direction.
- The player's *playstyle* is called out ("you always use traps"), which is aggregate
  tendency data rather than episodic.

Lesson: episodes and tendencies are **two different memories with two different jobs**.
Episodes make dialogue feel personal; tendencies make play adapt. Our design keeps them
as separate stores.

### 1.3 Card games specifically: the Hoyle series

Sierra's Hoyle card games are the closest prior art to what this project is aiming at:
named opponents (many borrowed from Sierra's adventure games), each with **its own AI
and skill level tied to its identity**, plus catchphrases and characterized banter.
Hoyle Card Games shipped roughly **8,500 distinct spoken lines**.

The instructive part is the criticism: reviewers noted the opponents **started sounding
repetitive after about an hour** even at that line count. Nearly ten thousand lines was
not enough to hide the problem.

Lesson, and it is the single most important one for trashTalk: **volume does not fix
repetition — frequency and specificity do.** A game that speaks rarely and pointedly
reads as smarter than one that speaks constantly from a huge pool. Our design therefore
treats silence as the default response and puts a hard budget on barks per hand.

### 1.4 Personality as weights: utility AI

Dave Mark's Infinite Axis Utility System (and utility-based AI generally) builds
distinct personalities by giving different agents **different weights over the same set
of considerations**, rather than writing separate behavior trees per character. Same
considerations, different multipliers, different character.

This matters because it describes what this codebase already does. See §2.

### 1.5 Opponent modelling in card-game AI

Poker AI research (opponent modelling as in the Alberta *Loki*/*Poki* line of work, and
its modern descendants) tracks per-opponent statistical tendencies — how often this
specific player raises pre-flop, folds to continuation bets, bluffs from position — and
adapts strategy against that individual rather than playing a generic optimum. Measured
result: the opponent-modelling version of *Loki* substantially out-earned its
non-modelling twin over long runs.

The heavyweight machine-learning versions are irrelevant to a hobby Swing game, but the
shape transfers exactly and cheaply: a handful of **counters per opponent** ("how often
does West try to shoot the moon", "how often does North pass me the Queen") is enough to
justify real strategic adjustment, and it is a dozen integers, not a neural network.

### 1.6 Relationship values: opinion systems

The Sims and Crusader Kings both model relationships as a **signed scalar per ordered
pair** (A's opinion of B is not B's opinion of A), assembled from decaying event-driven
modifiers plus durable structural ones. Crusader Kings in particular exposes each
contributing modifier with its own decay rate, which is what keeps a grudge from being
either instantly forgotten or permanent.

Lesson: grudges must **decay**, or after a dozen games every AI hates everyone and the
signal is gone. Authored friendships, by contrast, should not decay.

---

## 2. The central insight: the personality engine already exists

`TacticWeights.java` is already, structurally, the personality system this project has
been planning. It is a table of per-`Player_Type` multipliers over nine tactic families,
applied to an additive scored-candidate AI. That is precisely the utility-AI personality
pattern from §1.4, and the file's own header comment already anticipates it:

> "this table is exactly the foundation the future personality/roster system needs: a
> personality will simply be its own weight row."

That is correct and it should be taken literally. **A personality is a weight row.**
The roster feature does not need a new decision architecture — it needs identity,
persistence, and a way to *generate* weight rows from ranked trait mixtures.

This collapses most of the perceived size of feature #6. What is genuinely new is:
persistence, identity, memory, relationships, and a strictly-bounded channel for
memory to influence play.

### 2.1 Resolving ranked trait mixtures into a weight row

`TODO.md`'s requirement: traits are a **ranked** mixture, and rank resolves conflicts —
"if a player is both Cautious and Aggressive, which one wins depends on which ranks
higher for that player."

Recommended scheme, in preference order:

1. Each **trait archetype** owns a *sparse* opinion set — it declares values only for the
   tactic families it actually cares about, leaving the rest unstated. (`Aggressive` has
   an opinion about `BASIC_SAFETY` and `JACK_HUNT`; it has no opinion about
   `PASSING_CRAFT`.)
2. Resolve a personality by walking its ranked traits **highest rank first**. The first
   trait with an opinion on a family sets that family's weight.
3. Lower-ranked traits that also hold an opinion on an already-set family apply a small
   **nudge** (±0.25, clamped to the 0–1.5 band) rather than being discarded entirely. A
   player who is Cautious-then-Aggressive should not play identically to one who is
   purely Cautious.
4. Anything no trait has an opinion about falls back to the `NORMAL` row.

This is more faithful to the stated requirement than a straight weighted average, which
would blend Cautious and Aggressive into an indistinguishable middle — exactly the
failure mode the ranking exists to prevent.

**Mandatory regression gate.** The eight existing `Player_Type` presets must remain
exactly reproducible as personalities under the new resolver. This project already has
the right pattern for proving that: the equivalence harness used for the
if/else → scored-candidate refactor, which verified 400,000 random decisions with zero
mismatches *before* the legacy code was deleted. Reuse it verbatim here — build the
resolver, prove every preset resolves to a byte-identical weight row and produces
identical play, and only then let personalities diverge.

### 2.2 What happens to the inert traits

`Player_Traits` contains scaffolding that has never done anything: `Vengeful`,
`Payback_p1/2/3`, `Last_Place`, `First_Place`, `Need_to_Stop_Shooter`, `Need_to_Shoot`,
`Counts_cards`, `Dave`. This feature is what they were waiting for.

- `Counts_cards` — unrelated to the roster; it is the already-identified hook for gating
  `COUNTED_CERTAINTIES` by skill. Independent, and should ship before this whole feature.
- `Vengeful` — becomes a real trait archetype that weights the new `SOCIAL` family high.
- `Payback_p1/2/3` — **recommend deprecating.** Fixed numbered slots ("payback against
  player 2") were a workaround for not having a relationship graph. With per-pair grudge
  values the target is computed, not hardcoded, and one `Vengeful` trait covers it.
  Keep the enum constants so nothing breaks; stop referencing them.
- `Last_Place` / `First_Place` / `Need_to_Stop_Shooter` / `Need_to_Shoot` — these are
  *situational* states, not personality traits: they describe the standings right now,
  not who the player is. They belong in the existing `round_traits` set, derived at
  hand start, not in a personality's ranked mixture. Worth correcting explicitly,
  because their presence in the same enum invites conflating the two.

---

## 3. Feature #6 — persistent roster, in five parts

### 3.1 Identity

- **AI players**: a roster of 10-20, each with a stable `id` (never displayed, never
  reused), a display `name`, a ranked trait list, and optional flavor fields (a
  catchphrase tag used by trashTalk, a portrait file later).
- **The human**: currently has no identity at all — South is just `PLAYER.SOUTH`. The
  roster is meaningless without one. Add a human profile (name, prompted once on first
  run, editable) persisted in the same file. Support more than one human profile from
  the start; it is nearly free and avoids a migration later if someone else plays.
- **Seat assignment** becomes "pick 3 roster members" rather than "pick 3 types". Keep
  the existing per-seat dropdowns, now listing roster members instead of raw types, plus
  a "random 3" convenience.

### 3.2 Persistence

Requirements that fall out of the feature: survives restarts, human-editable (authored
friendships are explicitly manual), and tolerant of the trait vocabulary changing —
which it will, constantly, in this project.

- **Format: a small, line-oriented, human-editable text format.** Explicitly *not* Java
  serialization — it is brittle exactly where this project is most volatile (enum and
  field changes silently break old saves). A hand-rolled `key = value` / sectioned text
  file needs no dependency and can be edited in Notepad, which the authored-friendship
  requirement effectively demands. JSON is fine too but drags in a dependency or a
  hand-written parser for no real gain here.
- **Location:** `%USERPROFILE%\.hearts2\` — `roster.txt` (identities + traits +
  authored friendships), `history.txt` (tendencies + episodes). Keeping authored and
  generated data in separate files means a corrupt history never costs the user their
  hand-written roster.
- **Versioning:** a schema-version line at the top of each file. Unknown trait names and
  unknown keys are **skipped with a logged warning, never fatal**. A save file from
  three sessions ago must still load after the enum has moved on.
- **Write timing:** at end of hand and end of game, not on exit — a Swing app that is
  force-closed or throws should not lose the session. Write to a temp file and rename,
  so an interrupted write cannot truncate a good file.

### 3.3 Memory, part 1 — tendencies (drives play)

Per *ordered pair* (observer → subject), a handful of counters. These are cheap,
bounded, and are what §1.5 says actually pays off:

| Counter | Feeds |
|---|---|
| moon attempts / successes | how early to suspect a shooter |
| times passed me the Queen | passing-phase caution toward that seat |
| times dumped points on me | grudge accrual, target selection |
| Jack-hunt frequency | Diamond-lead caution |
| duck-vs-take rate | how likely they are to eat a trick |
| hands / games played together | gates "we have history" dialogue |

Recording these is behavior-neutral and can ship well before anything consumes them.

### 3.4 Memory, part 2 — episodes (drives dialogue and grudges)

A capped ring buffer per pair (~20 entries) of *specific* notable events: hand number,
trick number, what happened, magnitude. "Game 12, hand 4, last trick: South handed me
the Queen for +13."

- **Salience score** per episode = cost × recency × deliberateness. Evict lowest-
  salience, not merely oldest, so the one time someone shot the moon on you survives a
  hundred forgettable tricks.
- Episodes are the raw material for both Nemesis-style callbacks in dialogue and the
  grudge value below.

### 3.5 Relationships

Per ordered pair, a signed **affinity** roughly −100…+100:

```
affinity(A→B) = authoredBase(A→B)            // manual, never decays
              + Σ episodeDelta × decay^gamesSince   // earned, fades
```

- Asymmetric by construction (A resenting B implies nothing about B).
- **Decay is not optional** (§1.6). Without it everyone ends up hating everyone and the
  signal flattens. A per-game decay factor around 0.8 gives a grudge a useful life of a
  few games.
- Authored friendships live in `roster.txt`, are excluded from decay, and are the user's
  to edit — matching the stated requirement that they are manual and stable.

### 3.6 How memory is allowed to affect play — the safety rail

This is the part most likely to damage a carefully tuned AI, so the constraint should be
explicit and structural rather than a matter of care:

> **Social factors may only break ties between plays that are already near-equivalent.
> They may never override a proven-correct play.**

Concretely:

- Add one new tactic family, `SOCIAL`, to the existing `TacticWeights` matrix. It is
  therefore gated per personality exactly like every other family for free — `Vengeful`
  personalities weight it high, most weight it 0, and `Bad`/`Brain_Dead` never see it.
- Social scorers live in the **lowest scoring tier** — above `RANK_TIE_BREAK` (1) but
  below `DUCK_UNDER_TRICK` (100). A grudge can decide *which* of two equally safe
  opponents eats the Queen; it can never persuade the AI to take the Queen itself, and
  it can never outrank a counted certainty.
- **First and best social scorer: targeted Queen placement.** `TODO.md` already lists
  this as a future idea ("prefer the trick the standings LEADER is winning"). Generalize
  "leader" to "the seat I most want to hurt", a blend of standings threat, grudge, and
  friendship. One scorer, immediately meaningful, structurally incapable of causing harm.

Everything else social should wait until that one has survived live play.

---

## 4. Feature #5 — trashTalk

A scaled-down Valve response-rule system (§1.1). The architecture is small; the
discipline is in the frequency control.

### 4.1 Engine

1. **Events.** The game emits a concept plus a fact bag at defined moments:
   `TRICK_WON`, `QUEEN_DROPPED_ON`, `JACK_CAPTURED`, `MOON_SUSPECTED`, `MOON_SUCCEEDED`,
   `MOON_BROKEN`, `PASS_RECEIVED`, `HAND_ENDED`, `GAME_ENDED`, plus self-deprecating ones
   like `SELF_ATE_QUEEN`. These are hook points in `Play_Hand`/`Scoring`/`Deal_Hand`, all
   of which already compute exactly these outcomes.
2. **Facts.** `concept`, `speaker`, `target`, speaker's personality and rank-1 trait,
   both scores and standings positions, trick/hand number, points in the trick,
   `affinity(speaker→target)`, `isFriend`, `gamesPlayedTogether`, `timesSpokenThisHand`,
   and history facts such as `targetMoonedMeBefore`.
3. **Rules** = criteria list → response group, sorted by criteria count, first full match
   wins. Specific beats general automatically.
4. **Lines live in an external text file**, not in Java. This is the "empower your
   writers" half of the Valve design and it matters more for a solo hobby project than
   for a studio: adding a joke should never mean a recompile. It also means the line
   file can grow indefinitely without touching code.

### 4.2 Frequency control — the part Hoyle got wrong

Per §1.3, this is where the feature succeeds or fails:

- **Silence is the default response.** Most events produce no line at all.
- **Hard budget:** a small maximum number of barks per hand (start at 2-3, tune down).
- **Per-speaker cooldown:** minimum tricks between one player speaking twice.
- **No-repeat window:** a line cannot recur until N other lines have played.
- **Personality gates volume**, not just content: `Cautious` barely speaks, `Aggressive`
  and `Vengeful` speak most, `Brain_Dead` says nothing ever (which is itself
  characterful and costs nothing to implement).
- Prefer specific over frequent: one line per hand that references an actual remembered
  event beats a dozen generic ones.

### 4.3 Presentation

- Reuse the existing status/log area first — it is already there and the card-tracker
  panel established the "toggleable extra info panel with a checkbox" convention in this
  UI. A mute checkbox alongside the card-tracker one fits the existing pattern exactly.
- Speech bubbles near each seat are nicer and strictly optional; treat as a later polish
  pass, not part of the feature.

---

## 5. Recommended sequencing

Each phase is independently shippable and separately testable. Phases 0-3 change **no
play behavior whatsoever**, which means zero regression risk against the live-playtest
discipline this project runs on.

| Phase | Content | Play impact |
|---|---|---|
| **0** | Human identity, persistence skeleton, load/save, versioned forgiving parser | none |
| **1** | Personality = resolved weight row; migrate the 8 presets to data; **prove equivalence with the 400k-decision harness** | none (by proof) |
| **2** | Roster of 10-20 named players; per-opponent tendency counters recorded | none (recorded, unused) |
| **3** | Episodes, affinity/grudge with decay, authored friendships | none (computed, unused) |
| **4** | `SOCIAL` tactic family; targeted Queen placement as the first and only social scorer | small, tier-bounded |
| **5** | trashTalk rule engine + external line file + frequency budget | none (cosmetic) |

Two things should jump the queue because they are small, independent, and unblock
confidence elsewhere:

- **Gate `COUNTED_CERTAINTIES` behind `Counts_cards`** — already specified in `TODO.md`,
  no design work left, and it is the last piece of the existing weight matrix.
- **Reclassify the situational traits** (`Last_Place`, `First_Place`, `Need_to_*`) out of
  the personality vocabulary before personalities become data (§2.2), so the roster file
  format never encodes the confusion.

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| **Tuning explosion** — 20 personalities × 10 families = 200 hand-tuned numbers nobody can reason about | Personalities are *composed* from ~8-10 trait archetypes, never authored cell by cell. Only the archetypes get tuned, and they already exist. |
| **Grudges degrade play quality** — the AI starts making bad plays for social reasons | The tier bound in §3.6 makes this structurally impossible, not merely unlikely. |
| **Save corruption / schema drift** breaks the game after a trait rename | Versioned files, forgiving parse (skip + warn, never fatal), atomic temp-and-rename writes, authored data in a separate file from generated data. |
| **Dialogue gets repetitive** — Hoyle failed at 8,500 lines | Budget and cooldown over volume; silence as default; specificity via computed rule ranking. |
| **Scope** — this is the largest feature in the project | Six independently shippable phases, the first four of which are provably play-neutral. |
| **Personality regression** during the weight-row migration | The existing equivalence-harness pattern, run before any legacy path is removed. |

---

## 7. Open questions for the user

1. **Roster authoring** — should the 10-20 AI players be hand-written by you up front, or
   should the game ship a starter roster you then edit?
2. **Do AI-vs-AI grudges matter to you**, or is only the AI↔human relationship
   interesting? AI-vs-AI doubles the relationship data and is only visible through
   dialogue; it may not earn its cost.
3. **Should tendencies affect play at all**, or only dialogue? Letting an AI remember
   "South always passes me the Queen" and adjust its passing is the most powerful use of
   the memory — and the most likely to feel unfair to a human who cannot do the same.
4. **Trash talk tone** — good-natured, sharp, or per-personality across that range? This
   decides the line file's whole voice and is worth settling before any lines get
   written.
5. **`Dave`** — the trait exists but does nothing. Is it a person to be represented in
   the roster, and if so what does he play like?

---

## Sources

- Elan Ruskin, *AI-driven Dynamic Dialog through Fuzzy Pattern Matching* (Valve, GDC 2012)
  — https://gdcvault.com/play/1015528/AI-driven-Dynamic-Dialog-through
  and https://cdn.cloudflare.steamstatic.com/apps/valve/2012/GDC2012_Ruskin_Elan_DynamicDialog.pdf
- Nemesis System design commentary — https://www.pcgamer.com/games/action/shadow-of-mordor-is-worth-revisiting-for-the-nemesis-system-alone/
  and https://medium.com/@jaygaracini/the-psychology-behind-the-nemesis-system-in-shadow-of-mordor-63eeaea34a2a
- Hoyle Card Games / Hoyle's Official Book of Games — https://en.wikipedia.org/wiki/Hoyle_Card_Games,
  https://en.wikipedia.org/wiki/Hoyle%27s_Official_Book_of_Games,
  https://www.gamespot.com/reviews/hoyle-card-games-review/1900-2895243/
- Dave Mark, Infinite Axis Utility System — https://www.gameai.com/iaus.php ;
  utility systems overview — https://en.wikipedia.org/wiki/Utility_system
- Poker opponent modelling — https://poker-ai.org/ai-in-poker-modeling-opponents-for-an-edge/ ,
  *Bayes' Bluff: Opponent Modelling in Poker* — https://arxiv.org/pdf/1207.1411
- NPC banter cooldown/repetition practice — https://www.nexusmods.com/baldursgate3/mods/7034
