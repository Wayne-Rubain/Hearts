# Decisions for Wayne, 2026-11-02: lead guard, two near-clones, Bonehead

The four questions left open by `docs/lineup_2026-11.md` (on `cloud/lineup-2026-11`) and by the lead
blunder guard (`cloud/lead-blunder-guard`), with measurements and a recommendation for each. Nothing here
changes play. All four are Wayne's call.

## How it was measured

- **One player against three Normals:** a roster player, as drafted or with one setting edited, against
  three plain Normals. 2,000 whole seeded games, seat rotated (standard error about 0.8 points on a win
  rate). The other seats count cards at 50% and have the level 3 moon interest of 3%. Also counted for the
  roster player: moon tries, Queens eaten per hand, Jacks taken per hand (fair share 0.25 each).
- **Guard rules:** which rule of `AI.isSuicidalLead` vetoes a blunder, by level, over 3,000 games of random
  4-player tables from the draft ten.
- **Widened guard:** the third rule applied at every level instead of experts only, with a paired `Lineup`
  (same seeds as the 11/02 round robin, 5,040 games per player).
- **Play difference:** in how many seeded games an edit changes any card played.

These used scratch tools built on the `HandObserver.captures()` hook from `cloud/lineup-2026-11`. The tools
are not committed; they can be added under `tools/measure/` if Wayne wants to rerun them.

---

## 1. Should levels 1 to 3 be stopped from blunder-leading a sure-winning Queen of Spades?

Today the third rule ("a sure winner whose own points are 4+ worse than the best lead") is experts only.

How often each rule fires, per 1,000 hands, if the third rule applied to everyone:

| Rule | Level 1 | Level 2 | Level 3 | Level 4 | Level 5 |
|---|---|---|---|---|---|
| Queen led while the Ace or King is out | 0.95 | 0.87 | 0.63 | 0.20 | 0.05 |
| Ace or King led while the Queen is out | 0.47 | 0.44 | 0.43 | 0.05 | 0.00 |
| Sure-winning Queen led (13 points) | 1.47 | 1.97 | 0.80 | 0.20 | 0.07 |
| Sure Jack of Diamonds given up | 1.28 | 1.49 | 0.93 | 0.20 | 0.17 |

The last two rows are 0 at levels 1 to 3 under the current (expert-only) rule.

Paired `Lineup`, win rate change from the build without the guard:

| Player | Guard as built | Guard widened |
|---|---|---|
| Careful Counter | +0.09 | -0.05 |
| Steady | +0.06 | +0.22 |
| Shark | +0.09 | +0.16 |
| Queen-phobic | +0.11 | -0.06 |
| Jack Hunter | -0.02 | +0.10 |
| Moon Chaser | -0.01 | +0.11 |
| Late Bloomer | -0.05 | -0.09 |
| Wildcard | -0.23 | -0.15 |
| Beginner | +0.02 | -0.12 |
| Bonehead | -0.07 | -0.13 |

**Recommendation: widen it to every level.**
- At levels 1 and 2, leading a sure-winning Queen is the most common suicidal lead still allowed. It
  happens more often than the two rules already in the guard put together.
- The current rule is inconsistent. It blocks the Queen when she *might* lose to an Ace or King, but allows
  her when she is *certain* to take 13 points.
- The follow guard already stops the matching follow at every level: nobody may drop the Queen onto a
  Spade trick she would win.
- Strength doesn't change: no win rate moves more than 0.22 points either way. This is about feel.
- A moon shooter loses nothing. A veto only falls back to another allowed card, or to the best card.
- Bonehead is unaffected either way: its random 1-in-3 card skips the guard.

## 2. Should the guard also block giving up a sure Jack of Diamonds?

**Recommendation: keep it, and apply it at every level together with decision 1.**
- It is rare: 0.2 per 1,000 hands for experts, 0.9 to 1.5 at levels 1 to 3.
- The follow guard already treats it as suicidal at every level. Its last-seat check counts the Jack's -10
  in the value of the trick, so ducking a trick that would win a sure Jack is a 10-point swing, and that
  is blocked.
- It lets the lead guard mirror the follow guard: block a sure swing of 10 or more points, for everyone.
  With a lead, the possible swings are 1, 13, or 10 and up, so the separate 4-point expert limit never
  makes a difference. One rule covers both decisions.
- The case against: losing the Jack is a missed bonus, not points taken. If "suicidal" should mean only
  *taking* points, drop the Jack part and keep the Queen part.

## 3. Jack Hunter and Queen-phobic look alike

**Recommendation: they really are near-clones; the sheet settings can't separate them; give each a new,
visible signature behavior.**

Queen-phobic against three Normals. Every Queen setting gives the same result:

| Queen-phobic variant | Win % | Queens eaten per hand |
|---|---|---|
| As drafted | 14.5 | 0.263 |
| Avoids-Queen off, NORMAL template | 14.2 | 0.264 |
| QUEEN_KEEP NEVER to FOUR_LOW_SPADES | 14.5 | 0.263 |
| QUEEN_FLUSH 1.5 to 1 | 14.5 | 0.263 |
| SPADE_CONTROL ANYONE to TO_LEADER | 14.4 | 0.263 |
| HEART_INSURANCE ON to OFF | 15.5 | 0.262 |
| Steady as drafted, for comparison | 20.7 | 0.250 |

- Turning the Avoids-Queen trait off changed a single card in only 2 of 40 seeded games.
- Why: an unprotected Queen is already everyone's first card to pass. The trait only changes the
  protected-Queen case, which `QUEEN_KEEP=NEVER` already covers. Its follow-side rule (shed the Queen
  under a higher Spade) is what everyone does anyway.
- Its sheet description says it "dumps Spade control early". Nothing in the code does that.

Jack Hunter against three Normals:

| Jack Hunter variant | Win % | Jacks taken per hand | Queens eaten per hand |
|---|---|---|---|
| As drafted | 12.7 | 0.255 | 0.283 |
| JACK_HUNT weight 1.5 to 1 | 12.7 | 0.255 | 0.283 |
| Aggressive mood 20 to 0 | 16.5 | 0.260 | 0.265 |

- The `JACK_HUNT` weight changes nothing. Those scorers use fixed tier scores, so a 1.5 multiplier
  can't change which card ranks first. `Deal_Hand.java` already documents the same problem for
  Avoids-Queen. The weight is also at its cap (1.5).
- What actually shapes Jack Hunter is its Aggressive 20% mood, which costs it about 4 points.

This works against principle 1 (the AIs must differ), and no sheet setting will fix it. Ideas for a
signature behavior each, for Wayne to choose from (new selectable options; no default changes):
- **Queen-phobic:** always pass the Ace and King of Spades along with the Queen (really dumping Spade
  control), or lead Spades early to flush the Queen out.
- **Jack Hunter:** keep or collect high Diamonds in the pass, and lead Diamonds to hunt the Jack even when
  not certain to win the trick.

Once Wayne picks, each can be built as an option and measured before any roster changes.

## 4. Bonehead at 4.7%

**Recommendation: keep it for now; revisit with more live games.**

| Bonehead variant | Win % vs three Normals | Moon tries / 100 hands | Moons made / try |
|---|---|---|---|
| As drafted | 1.3 | 7.5 | 0.03 |
| Moon interest 22 to 3 | 1.4 | 1.7 | 0.03 |
| Wildness 60 to 30 | 1.5 | 7.3 | 0.03 |
| NORMAL template (no random card) | 7.5 | 7.8 | 0.08 |

- Only one lever matters: the random card it plays 1 time in 3 (the `Bad` trait). Moon interest and
  wildness change nothing.
- It is meant to be the floor: the player you're happy to beat, who can hurt you by accident. In the round
  robin it still wins about 1 game in 21, so upsets happen. Its hopeless moon tries fit the character.
- One finished live game is too little to judge whether it is fun.
- If it should be softer, the only effective change is a lower random-card rate. That rate is hard-coded at
  1 in 3, and `Bad` was left alone on purpose, so it would need a new roster setting (for example
  `randomCardPercent`).

---

## Next steps, depending on the decisions

1. Decisions 1 and 2 approved: change the third lead rule on `cloud/lead-blunder-guard` to "a sure swing
   of 10 or more points, every level", with tests and a fresh `Equiv` check.
2. Decision 3: write candidate signature behaviors for Queen-phobic and Jack Hunter in detail; build and
   measure the ones Wayne picks.
3. Decision 4: no change until there are more live games.
