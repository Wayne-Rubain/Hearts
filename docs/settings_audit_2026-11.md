# Settings audit, 2026-11-05

Project review item 3: does each roster setting actually change how a player plays? Tool:
`tools/measure/hearts_1/KnobAudit.java`.

## Method

For one roster player, each setting is changed in turn to a value that differs from the drafted one,
and 30 seeded whole games against three plain Normals are replayed. The count is how many of the 30
games had **any** card played differently. A real setting changes most games; a setting that changes
fewer than 3 of 30 does nothing worth tuning. Three base players, because some settings only matter at
some levels: Steady (level 4), Late Bloomer (level 2) and Queen-phobic (level 3).

Run it again with:

```
java -cp "out:forms-1.3.0.jar" hearts_1.KnobAudit <rosterDir> <id> 30 3
```

The last argument makes it exit with an error when any setting changes fewer than 3 games, and marks
those lines. The list of settings is built from `Strategy` and `TacticWeights`, so new options are
audited automatically. One player takes about 7 minutes.

## Summary

Of 77 setting changes, **16 do nothing for all three players**, and 5 more do nothing for some of them.

**No effect for anyone**
- **Weights** (`weight.X` at 0.5 or 1.5) for JACK_HUNT, MOON_DEFENSE, PASSING_CRAFT, END_GAME, SOCIAL and
  COUNTED_CERTAINTIES, and QUEEN_FLUSH at 1.5. The scorers use large fixed tier scores, so multiplying a
  score by 0.5 to 1.5 rarely changes which card ranks first. Weight 0 still works as an off switch
  (`PASSING_CRAFT` 0 is the naive "three highest cards" pass), but anything between 0.5 and 1.5 is
  cosmetic.
- **`reliable.STANDINGS`** at 50 (0 to 2 games).
- **`payback`** on or off (0 to 2 games). The Queen grudge almost never changes a card.
- **`moonGamblePercent`** at 50 (0 to 2 games), as already seen on 10/14.

**No effect for some players**
- **`blunder`** for experts: levels 4 and 5 are capped at 2% and 1%, so Steady's drafted 3% and any
  higher value act as 2%. It works normally below level 4 (29 to 30 games).
- **`MOON_ALARM`**: FIRST_HEART against any other alarm changes 25 of 30 games, but switching between the
  other seven alarms changes 0 to 4. In play it is really two behaviors: FIRST_HEART, and the rest.
- **`QUEEN_KEEP`** for Queen-phobic: the Avoids-Queen trait overrides it (0 games).
- **`SPADE_CONTROL`** ANYONE for Late Bloomer (2 games).

Everything else (the other strategy options, the other reliabilities, the styles, count cards, moon
interest, level, wildness, and the new signature options) changes 5 to 30 games.

## What was done

Only the roster sheet's help text, so nobody tunes a setting that does nothing:
`tools/roster_sheet/roster_sheet.py` now says which settings have no measured effect (the weight row,
Queen payback, STANDINGS, moon gamble, the blunder cap for experts, and MOON_ALARM's two groups). No play
changed.

## For Wayne's decision: fix or retire

Making a dead setting work changes how the players who use it play, so each needs approval.

1. **Weights.** Either retire the "Weight overrides" row (keep 0 as the on/off switch), or redefine a
   weight as the chance a tactic is used each time it applies, the way reliability already works (and
   reliability does change play). The draft roster uses weight overrides on five players.
2. **Queen payback.** Wayne's design (10/10), but it barely changes a card. If the grudge should show,
   it needs a stronger effect.
3. **Moon gamble.** Only gates the Ace-of-Hearts flush, which rarely comes up. Fold it into moon interest,
   or give it a visible behavior.
4. **STANDINGS reliability.** Its tactics rarely change the chosen card. Retire it, or strengthen the
   standings tactics.
5. **MOON_ALARM.** Eight options that play as two. Keep them as flavor, or trim to the ones that differ.
6. **Expert blunder cap.** Working as designed; the sheet now says so.

## Full results (games out of 30 where any card changed)

| Setting changed to | Steady (L4) | Late Bloomer (L2) | Queen-phobic (L3) | Verdict |
|---|---|---|---|---|
| `weight.BASIC_SAFETY=1.5` | 21 | 14 | 25 | works |
| `weight.BASIC_SAFETY=0.5` | 28 | 25 | 30 | works |
| `weight.VOID_AWARENESS=1.5` | 25 | 20 | 23 | works |
| `weight.VOID_AWARENESS=0.5` | 25 | 22 | 26 | works |
| `weight.JACK_GUARD=1.5` | 10 | 14 | 14 | works |
| `weight.JACK_GUARD=0.5` | 13 | 14 | 11 | works |
| `weight.JACK_HUNT=1.5` | 0 | 0 | 0 | **no effect** |
| `weight.JACK_HUNT=0.5` | 0 | 0 | – | **no effect** |
| `weight.COUNTED_CERTAINTIES=1.5` | 2 | 2 | 3 | weak for some |
| `weight.COUNTED_CERTAINTIES=0.5` | 2 | 2 | 2 | **no effect** |
| `weight.QUEEN_FLUSH=1.5` | 0 | 3 | – | weak for some |
| `weight.QUEEN_FLUSH=0.5` | 9 | 8 | 12 | works |
| `weight.MOON_OFFENSE=1.5` | 11 | 9 | 15 | works |
| `weight.MOON_OFFENSE=1` | 11 | 8 | – | works |
| `weight.MOON_DEFENSE=1.5` | 0 | 0 | 0 | **no effect** |
| `weight.MOON_DEFENSE=0.5` | 1 | 0 | 2 | **no effect** |
| `weight.PASSING_CRAFT=1.5` | 0 | 0 | 0 | **no effect** |
| `weight.PASSING_CRAFT=0.5` | 0 | 0 | 0 | **no effect** |
| `weight.END_GAME=1.5` | 0 | 0 | 0 | **no effect** |
| `weight.END_GAME=0.5` | 0 | 0 | 0 | **no effect** |
| `weight.SOCIAL=1.5` | 0 | 0 | 0 | **no effect** |
| `weight.SOCIAL=0.5` | 0 | 0 | 0 | **no effect** |
| `reliable.SAFETY=50` | 30 | 30 | 30 | works |
| `reliable.JACK=50` | 27 | 27 | 28 | works |
| `reliable.QUEEN=50` | 14 | 8 | 30 | works |
| `reliable.MOON=50` | 14 | 19 | 22 | works |
| `reliable.PASSING=50` | 30 | 30 | 30 | works |
| `reliable.STANDINGS=50` | 1 | 0 | 2 | **no effect** |
| `style.Aggressive=50` | 30 | 30 | 30 | works |
| `style.Avoids_Queen=50` | 30 | 30 | 30 | works |
| `style.Shooting_the_Moon=50` | 30 | 30 | 30 | works |
| `strategy.KIT_PASS=BLOCKER` | 28 | – | – | works |
| `strategy.KIT_PASS=FREE` | 29 | 25 | 28 | works |
| `strategy.LOW_HEARTS=BY_HEIGHT` | 30 | – | 30 | works |
| `strategy.LOW_HEARTS=KEEP_LOW` | 30 | 29 | 30 | works |
| `strategy.LOW_HEARTS=PASS_FREELY` | 30 | 30 | 30 | works |
| `strategy.SIDE_SUITS=FLAT` | 30 | – | 30 | works |
| `strategy.VOID_HUNT=NEVER` | 29 | – | 29 | works |
| `strategy.SPADE_CONTROL=ANYONE` | 6 | 2 | – | weak for some |
| `strategy.SPADE_CONTROL=SHIELD_LEADER` | 7 | – | 4 | works |
| `strategy.QUEEN_KEEP=FIVE_LOW_SPADES` | 7 | – | 0 | weak for some |
| `strategy.QUEEN_KEEP=NEVER` | 7 | 0 | – | weak for some |
| `strategy.HEART_INSURANCE=ON` | 25 | – | – | works |
| `strategy.MOON_ALARM=FIRST_HEART` | 22 | – | 24 | works |
| `strategy.MOON_ALARM=FOUR_HEARTS` | 12 | 25 | 12 | works |
| `strategy.MOON_ALARM=QUEEN_AND_HEART` | 1 | 25 | – | weak for some |
| `strategy.MOON_ALARM=FOUR_OR_QUEEN` | 0 | 25 | 0 | weak for some |
| `strategy.MOON_ALARM=SURE_WINNER_SAME` | 1 | 25 | 1 | weak for some |
| `strategy.MOON_ALARM=SURE_WINNER_ANY` | 1 | 25 | 4 | weak for some |
| `strategy.MOON_ALARM=KEPT_LEAD` | 6 | 24 | 12 | works |
| `strategy.QUEEN_PHOBIA=PASS_HIGH_SPADES` | 17 | 3 | 13 | works |
| `strategy.QUEEN_PHOBIA=FISH` | 24 | 23 | 24 | works |
| `strategy.QUEEN_PHOBIA=BOTH` | 28 | 23 | 27 | works |
| `strategy.JACK_CHASE=KEEP_DIAMONDS` | 30 | 20 | 29 | works |
| `strategy.JACK_CHASE=PRESSURE` | 24 | 24 | 17 | works |
| `strategy.JACK_CHASE=BOTH` | 30 | 27 | 30 | works |
| `level=1` | 30 | 19 | 28 | works |
| `blunder=10` | 0 | 29 | 30 | weak for some |
| `payback=on` | 2 | 0 | 0 | **no effect** |
| `countCardsPercent=90` | 30 | 30 | 30 | works |
| `moonGamblePercent=50` | 0 | 0 | 2 | **no effect** |
| `moonInterestPercent=20` | 27 | 16 | 24 | works |
| `wildness=40` | 30 | 30 | 30 | works |
| `wildness.opening=40` | 27 | 30 | 30 | works |
| `wildness.leading=200` | 17 | 13 | 11 | works |
| `wildness.losing=300` | 16 | 23 | 21 | works |
| `strategy.KIT_PASS=HOLD_BACK` | – | 25 | 27 | works |
| `strategy.LOW_HEARTS=KEEP_TWO` | – | 29 | – | works |
| `strategy.SIDE_SUITS=BY_HEIGHT` | – | 30 | – | works |
| `strategy.VOID_HUNT=WHEN_SHORT_ON_SPADES` | – | 26 | – | works |
| `strategy.SPADE_CONTROL=TO_LEADER` | – | 3 | 3 | works |
| `strategy.QUEEN_KEEP=FOUR_LOW_SPADES` | – | 5 | 0 | weak for some |
| `strategy.HEART_INSURANCE=OFF` | – | 23 | 26 | works |
| `strategy.MOON_ALARM=SHOOTER_READ` | – | 25 | 6 | works |
| `weight.JACK_HUNT=1` | – | – | 0 | **no effect** |
| `weight.QUEEN_FLUSH=1` | – | – | 0 | **no effect** |
| `weight.MOON_OFFENSE=0.5` | – | – | 4 | works |

