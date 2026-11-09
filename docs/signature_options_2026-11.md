# Signature options for Queen-phobic and Jack Hunter, 2026-11-05

Decision 3 in `docs/decisions_2026-11.md`: the two players looked alike because their signature settings
(Avoids-Queen, `QUEEN_KEEP=NEVER`, `QUEEN_FLUSH` and `JACK_HUNT` weights) changed almost nothing. This
branch (`cloud/signature-options`) adds new behaviors as selectable options. **Nothing is switched on:**
every option defaults to OFF and the draft roster is unchanged. Choosing them for a roster player is
Wayne's call.

## The options

Two new strategy components, set per player with `strategy.QUEEN_PHOBIA` and `strategy.JACK_CHASE`
(also new rows in `tools/roster_sheet/roster_sheet.py`):

| Component | Option | What the player does |
|---|---|---|
| QUEEN_PHOBIA | OFF | Ordinary play (everyone today) |
| | PASS_HIGH_SPADES | Passes the Ace and King of Spades as eagerly as a high Heart. The moon-toolkit rule still keeps them out of a pass that carries the Queen or Hearts. |
| | FISH | With the Queen still out, not in hand, and no Ace or King of Spades held, leads its lowest Spade to force the Queen out |
| | BOTH | Both of the above |
| JACK_CHASE | OFF | Ordinary play (everyone today) |
| | KEEP_DIAMONDS | Never passes the Ace, King or Queen of Diamonds, the cards that capture the Jack |
| | PRESSURE | With the Jack still out, not in hand, and a Diamond above the Jack held, leads its lowest Diamond below the Jack to strip the Jack's cover |
| | BOTH | Both of the above |

Both are **signature** components: wildness never strays them, and draws no random number for them.
That is what keeps existing play identical: `Equiv` over 300 hands matches `f181cda`, and a draft-ten
`Lineup` (where every player has wildness) plays card-for-card the same as before. Tests:
`SignatureOptionsTest`, plus `StrategyTest` now checks that signature components never stray.
`AllTests`: 50 suites, 0 failed.

This branch is built on `cloud/lead-blunder-guard` (which contains `cloud/integration-2026-11`), so merge
that one first.

## Measurements

**How often each option changes play** (60 seeded games, the option on vs off for that one player):

| Option | Games where play changes | First change, on average |
|---|---|---|
| QUEEN_PHOBIA FISH | 49 of 60 | hand 5.6 |
| QUEEN_PHOBIA PASS_HIGH_SPADES | 24 of 60 | hand 9.0 |
| JACK_CHASE KEEP_DIAMONDS | 57 of 60 | hand 4.2 |
| JACK_CHASE PRESSURE | 45 of 60 | hand 7.5 |

For comparison, the old Avoids-Queen trait changed play in 2 of 40 games.

**One player against three Normals** (2,000 games, standard error about 0.8):

| Player and option | Win % | Avg final | Queens eaten / hand | Jacks taken / hand |
|---|---|---|---|---|
| Queen-phobic as drafted | 14.5 | 77.3 | 0.263 | 0.244 |
| + PASS_HIGH_SPADES | 14.0 | 77.5 | 0.263 | 0.243 |
| + FISH | 15.3 | 75.0 | 0.254 | 0.244 |
| + BOTH | 14.8 | 75.0 | 0.253 | 0.243 |
| Jack Hunter as drafted | 12.5 | 79.3 | 0.283 | 0.255 |
| + KEEP_DIAMONDS | 12.8 | 78.2 | 0.294 | 0.275 |
| + PRESSURE | 11.6 | 79.5 | 0.285 | 0.253 |
| + BOTH | 13.4 | 78.8 | 0.293 | 0.274 |

- FISH is the one Queen-phobic option that shows: fewer Queens eaten and 2.3 points better per game.
  PASS_HIGH_SPADES changes play but not the outcome.
- KEEP_DIAMONDS is the one Jack Hunter option that shows: 8% more Jacks taken. PRESSURE does not take more
  Jacks and costs about 1 point of win rate.

**Trial round robin** with Queen-phobic on FISH and Jack Hunter on KEEP_DIAMONDS (scratch copy of the
draft roster; paired with the unchanged roster on the same seeds; 1,680 games each, standard error about
1.1):

| Player | Win %, unchanged roster | Win %, trial roster | Queens / game | Jacks / game |
|---|---|---|---|---|
| Careful Counter | 44.2 | 42.8 | 3.51 to 3.49 | 4.63 to 4.56 |
| Steady | 36.5 | 35.7 | 3.75 to 3.80 | 4.61 to 4.56 |
| Shark | 36.1 | 34.9 | 4.17 to 4.16 | 5.00 to 4.92 |
| **Jack Hunter** | 28.1 | **32.3** | 4.36 to 4.46 | **5.07 to 5.53** |
| **Queen-phobic** | 27.9 | **28.5** | **4.10 to 3.90** | 4.89 to 4.85 |
| Moon Chaser | 26.5 | 26.9 | 5.32 to 5.31 | 4.93 to 4.87 |
| Late Bloomer | 17.9 | 17.7 | 4.73 to 4.73 | 4.75 to 4.66 |
| Wildcard | 17.0 | 15.5 | 5.12 to 5.10 | 4.67 to 4.60 |
| Beginner | 11.0 | 11.7 | 4.56 to 4.58 | 3.45 to 3.41 |
| Bonehead | 4.7 | 4.0 | 4.61 to 4.65 | 2.26 to 2.24 |

- **They no longer look alike.** Jack Hunter now takes the most Jacks by a clear margin (5.53 per game,
  0.6 more than anyone else). Queen-phobic now eats fewer Queens than Shark and is close to Steady.
- **Jack Hunter got stronger:** +4.2 points, to 32.3%, close to Shark and Steady. Against three Normals
  the same option gained only 0.3 points, so the gain comes from the roster's weaker players. If Jack
  Hunter should stay a level 3 middle player, some of that could be taken back elsewhere (for example its
  Aggressive mood or reliabilities).

## For Wayne's decision

1. Turn on Queen-phobic `strategy.QUEEN_PHOBIA = FISH` (or BOTH) and Jack Hunter
   `strategy.JACK_CHASE = KEEP_DIAMONDS` (or BOTH) in the roster? My suggestion: FISH and KEEP_DIAMONDS,
   the two options that show in the numbers.
2. Jack Hunter's jump to 32.3%: accept it, or tone it down elsewhere.
3. Keep or drop the two options that did little (PASS_HIGH_SPADES, PRESSURE). Keeping them costs nothing
   and gives other players variety.
