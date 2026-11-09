# Hearts_2 -- Changelog

**Split out of TODO.md on 2026-08-15.** This file holds everything already
IMPLEMENTED, FIXED, RESOLVED, or REJECTED -- kept for history, design
rationale, and so the same mistake isn't proposed twice. `TODO.md` holds only
what is still open; see it for anything not covered here. Newest first.

---

## 2026-11-09 -- FIXED: Aggressive leads into a suit everyone else is out of (user report)

Wayne's game, hand 1: West (Shark) won the first ten tricks and took 17, most of it from leading the 10, 9
and 6 of Clubs when nobody else had a Club; North, East and South threw Hearts on them. West was not
shooting (no commit, look and gamble rolls off). Replayed from the log: plain West leads the 2 of Spades at
tricks 4 and 7; with Shark's Aggressive mood (30% per hand, not logged) it leads the King of Spades and the
10 of Clubs, as in the game. The Aggressive lead rule (`AGGRESSIVE_LEAD_RANK`, rank bonus for high cards)
had guards for the Queen and Hearts but not for a sure win everyone dumps on, which BASELINE's
VOID_AWARENESS covers. It now gives no rank bonus to a suit every other seat is out of (shown void, or none
left outside my hand for a player counting cards this hand: `othersAllOutOf`) while a Heart or the Queen is
still out (`pointsStillOut`). Replay: West leads the 7 of Spades instead. New `OwnQueenWinTest` case (fails
on the old code). Lineup, Wayne's roster, 504 games per player, before -> after: Shark wins 170 -> 176.5,
average final 46.2 -> 45.7; every other player within 3 wins. `AllTests` 53 suites.

## 2026-11-09 -- FIXED: look shooter gave away the lead it could never get back (from Wayne's 11-Club moon)

Wayne shot the moon (hand 3) by passing all three of his Hearts and running 11 Clubs. Replayed 20 times per
player with an AI in his seat (moon look forced on): the AIs set it up the same way (passed only the Hearts
14-20 times, committed every time they did) but made it only 3-4 times. Once the others were out of Clubs,
nobody would ever lead one, yet the "shed a loser early" rule (`isSafeLoserToShed`) led the 10 of Spades or
5 of Diamonds and gave the lead away for good. Now a look shooter only sheds a loser while it holds a sure
winner in a suit someone else still holds (`hasEntryBack`). Replay after: moons 13/20 (Moon Chaser), 10/20
(Wildcard), 16/20 (Shark), 16/20 (Careful Counter). Still imperfect: a lone small card of the long suit in
another hand (here West's 3 of Clubs) counts as a way back in. Lineup, Wayne's roster, 504 games per player,
before -> after: moons made 1356 -> 1364, wins within 7 for every player (this hand shape is rare). New
`MoonLookTest` case from the replay (fails on the old code). `AllTests` 53 suites.

## 2026-11-09 -- FIXED: a blunder could give the Jack of Diamonds away (user report)

Wayne's game, hand 12, trick 5: East (Steady; K Q J 8 of Diamonds) led the Jack and North, leading the game,
took her with the Ace: "the worst lead of all time." It was a blunder roll (choice #2 instead of the 8), and
the blunder guard only vetoed mistakes that cost the blunderer points, not gifts of -10 to another seat. Now
`AI.isSuicidalLead` vetoes leading the Jack of Diamonds while a higher Diamond is still out, and
`AI.isSuicidalFollow` vetoes discarding her or playing her under a higher Diamond already on the table (unless
the best card was the Jack anyway). Only blunders change; the AI's normal choice is untouched. Tests added to
LeadBlunderGuardTest and BlunderGuardTest. `AllTests` 53 suites.

## 2026-11-09 -- CHANGED: stop a moon with the Queen only to keep 1st place (user rule, replaces the entry below)

Wayne, on the "both endings" version below (shooter and stopper at 50, the other two at 40): "It's better for
the stopper to stay 10 behind the other 2 players than to take 13 plus pts... remember the 100 pt wall of
death... each pt you take closer to the wall is very bad." And: a moon can never win the game (everyone else
scores 0 that hand), and "if a person has high pts above 50... they are fighting the 100 wall." His good
case to stop: 1st place against a 2nd-place shooter, "especially if the shooter already has taken a bunch
of hearts." `MoonThreat.stoppingBeatsLettingItGo` now lets the moon go unless: the stopper is in 1st now
(ties count), the moon would put the shooter ahead of it, taking the Queen plus any Hearts on her trick keeps
it in 1st (the shooter's Hearts count against them), and the stopper is at 50 or below
(`STOPPER_MAX_TOTAL`). The take-Queen scorer passes the Hearts on the trick (`AI.heartsOnTrick`). Tests:
Wayne's 50/50/40/40 case lets the moon go; leader at 20 stops a shooter at 40 with 7 Hearts; leader at 55
lets it go; the bare Queen is taken but the Queen plus 6 Hearts is not; QueenDenialTest's timing-gate cases,
HighSpadeLeadTest (the high-Spade lead path reuses this check) and MoonSpadeReserveTest moved to standings
where the stopper leads. Lineup, Wayne's roster, 504 games per player, before both changes -> now: wins
within 4 for every player, moons made 1352 -> 1361. `AllTests` 53 suites.

## 2026-11-09 -- CHANGED: taking the Queen to stop a moon weighs both endings (user design)

Wayne: "it really needs to weigh where it is in the standing and also the person shooting the moon... the 13
pts the AI is going to take stopping them, that's 13 pts min, it may even be higher." `MoonThreat.
queenDenialWorthIt` keeps its Jack-of-Diamonds credit and its "is the shot real yet" gate, but its old final
check (refuse only if eating 13 drops me behind a third seat) is replaced by `stoppingBeatsLettingItGo`: it
projects every game total both ways. Let it go: shooter -26 (-36 with the Jack), everyone else 0 (a Jack
already won still -10). Stop it: I take the Queen plus any Hearts on her trick (Jack-discounted), everyone
keeps what they won, the shooter's Hearts count against them. (Wayne, 11/09: while a moon is on, the
stopper can't have taken a Heart, so its own points so far never matter; an earlier version of this entry
and its test said otherwise.) It takes
the Queen only if stopping leaves it closer to (or further ahead of) the best other total; an ending that
reaches 100 counts only as a win (I'm lowest) or a loss. Wayne's hand 10 (North 16, West -5, South 50
shooting, East 73) lets the moon go, as North did. Tests: QueenDenialTest's old "third seat" case now takes
the Queen (a level shooter would lead by 26); added hand 10, the 100 wall, and Hearts-on-the-Queen's-trick cases;
MoonSpadeReserveTest's "worth it" case moved from West at 80 (now near the wall: let it go) to West at 50.
Lineup, Wayne's roster, 6 games per combination (504 games per player), before -> after: wins within 3 for
every player, moons made 1352 -> 1348. Taking the Queen to stop a moon is a last resort and rare.
`AllTests` 53 suites.

## 2026-11-08 -- FIXED: AI won its own Queen of Spades when a higher Spade also won (user report)

Wayne's game, hand 9, trick 7: South led the 7 of Spades, West threw a Heart, and North (Careful Counter,
level 5) held the King and Queen of Spades. Both won the trick; North played the Queen and took 13 points
for nothing (no moon to stop). It had no Clubs left and could have thrown her on the Club lead two tricks
later. Cause: the BASIC_SAFETY follow rule's "take it as cheaply as you can" compared rank only, so the
Queen (lower rank) looked cheaper than the King. It now subtracts `WIN_WITH_OWN_QUEEN` (150) when the Queen
would win and another legal Spade would win too. Sits below `MOON_DENY_TAKE_QUEEN` (250, taking her on
purpose to break a moon) and the endgame solver's bonuses. New `OwnQueenWinTest` replays the hand from the
log: every player type at every level wins with the King (30 checks; all 30 fail on the old code).
Lineup, Wayne's roster, 2 games per combination (168 games per player), before -> after: no clear change
(the spot is rare); Careful Counter wins 74.5 -> 82.5, other players move within a few wins either way.
`AllTests` 53 suites.

## 2026-11-08 -- CHANGED: `moonHeartLast` rolls from its own random source

Wayne: "so testing is testing apples to apples." The roll drew from `AI.RNG`, so any rate between 0 and 100
shifted every later random number (passes, blunders, moon looks) and made rosters hard to compare. It now
uses `AI.HEART_LAST_RNG`, seeded by the simulator through `AI.seedRolls` next to `AI.RNG`. Check, Wayne's
live roster, MoonCount 1000 games, same seeds: all 0 = 7847 tries / 1141 made, all 1 = 7848 / 1142,
spread = 7829 / 1144, all 99 = 7773 / 1125, all 100 = 7771 / 1126. Rates now line up with the behavior they
stand for; 0 vs 100 is a small real difference. Seeded results for rosters without the key are unchanged.

## 2026-11-08 -- FIXED: roster.txt ignored `moonHeartLast`

The roster loader keeps its own list of profile keys and `moonHeartLast` was not on it, so a roster.txt value
was skipped and every player stayed at 100. Found by MoonCount giving identical totals for all-0, all-100 and
Wayne's spread. Added to `Roster.isProfileSetting`; `MoonLookTest` now loads it from a roster file.
Measured after the fix, Wayne's live roster, MoonCount 2000 games x 2 seed sets, moons made:
all 99 (behaves as hold-last) 2113 / 2124, all 1 (behaves as test-early) 2112 / 2085, spread 2070 / 2067.
Differences are within noise. Rosters at exactly 0 or 100 draw no random number and play identical deals
to each other (2181 / 2218 and 2196 / 2163), so they are not comparable to the runs that draw.

## 2026-11-08 -- ADDED: roster key `moonHeartLast` (per-player Heart order on a look moon)

Wayne asked for the A K + extra Heart order to be a per-player setting. `moonHeartLast` (0..100, default
100 = the rule below, unchanged): the chance each hand that a look shooter with two or more sure-winner
Hearts plays its extra Heart last; otherwise it tests the extra Heart early (and holds its Spade control
until then), as before that rule. Rolled with the moon look in `Deal_Hand`; draws a random number only for a
look player with a rate strictly between 0 and 100. Wayne's roster spread: Beginner, Bonehead, Moon Chaser
100; Late Bloomer 70; Jack Hunter 60; Queen-phobic, Wildcard 50; Steady 30; Shark 20; Careful Counter 0
(the stronger players lean to the early test, which made more moons against AI defenders). Roster sheet row
added. `MoonLookTest` 67 checks; `AllTests` 52 suites.

## 2026-11-08 -- CHANGED: look shooter with the A K of Hearts plays its extra Heart last (user rule)

Wayne, on his -36 moon (A K 10 of Hearts and a lot of winners): "I didn't test the 10 because I could
pull all the winners and use the A K late to pull remaining hearts and then play 10 last hoping that no
one has hearts left or low ones." A look shooter holding two or more sure-winner Hearts (or that has
already cashed its own Ace) no longer tests its extra Heart early: it runs its side-suit winners first,
then the A K (`MOON_LOOK_HEARTS_LATE` offsets the boss-Heart cash while a side-suit sure winner is held),
then the extra Heart last (`MOON_LOOK_HOLD_HEART_LAST`); its Spades are not held back for a test. A lone
non-Ace Heart, or the Ace plus one, is still tested early. Measured: replaying his hand-10 deal 20 times
per personality, moons made 44 (test early) -> 28 (hold last), because alarmed AI defenders keep their
best Heart for the end; over 400 random games of the live roster, 451 -> 439 moons made (no real
difference, the A K + extra case is rare). `MoonLookTest` 62 checks; `AllTests` 52 suites.

## 2026-11-08 -- ADDED: Queen-of-Spades moon warning, leader lets an early moon pass, look shooters shed losers (user rules)

From Wayne's -36 moon in live game 3 (hand 10), where he led the Queen of Spades himself at trick 7 and
West, far ahead, threw the Queen of Hearts on it. Wayne: the Queen lead IS a moon warning; West let it
pass because it led the game and he was not close to its score, "thinking that the other players or
they themselves would stop it later".
- `MoonRead.alarmed`: a player who led the Queen of Spades (on that trick, and for the rest of the
  hand, `Player.ledQueenOfSpades`) alarms every MOON_ALARM option. Applies to all players.
- `MoonThreat.letsItPassForNow`: a player leading the game by 10+ over the shooter skips the two costly
  defenses (refusing to throw Hearts on the shooter's trick, and taking a point trick to break the shot)
  while the shot is early (shooter under 4 Hearts, fewer than 8 tricks played). Free choices, like which
  Heart to keep on a trick already lost (`MoonFollowReserveTest`), are unchanged. Applies to all players.
  `StrategyTest.moonAlarmGatesTheDenial` now puts West within 10 of the shooter so it still tests the
  alarm alone.
- Look shooters only: lead the lowest card of a side suit that is not a sure winner while it is safe
  (no other seat known void in the suit, 6+ of its cards still out; never a low Spade while the Queen may
  be in another hand), "being careful that you don't lose a trick with a heart thrown on it". Ranks
  below the Heart test, above running the long suit.
`MoonLookTest` 56 checks; `AllTests` 52 suites. Check that normal strength is unchanged: Steady with the
look off against three Normals (`RosterAblate`, 2400 games) 21.2% won (21.6% before these rules, standard
error 0.8). Wayne's live roster with the draft look spread (`MoonCount` 400 games), tries per 100 hands:
Careful Counter 3.8, Steady 4.6, Queen-phobic 4.8, Shark 6.4, Jack Hunter 6.7, Bonehead 9.9, Late Bloomer
11.6, Beginner 13.0, Wildcard 17.6, Moon Chaser 27.6 (before the look: 2.5 to 3.6 for most, Moon Chaser 15).

## 2026-11-08 -- ADDED: moon look, a real player's way of reading a hand for a moon (user design)

From Wayne's own moon tries in two live games on 2026-11-07 (21 tries in 39 hands, 2 made). He passed
his low Hearts and kept the top ones, ran a long side suit headed by the Ace, held the Spade control or
the Queen, and decided once he saw the three cards passed in. The heart-length rule
(`HandEvaluator.shouldAttemptNormally`, which needs 4+ Hearts with the Ace and a quality of 26 for most
of the draft ten) would have tried none of the 21, including both moons he made.

New roster settings, both off by default, so every roster without them plays exactly as before (no
random number is drawn at 0): `moonLook` (0..100, the chance each hand that the player gives its hand
this look) and `moonOptimism` (0..100, default 50, how remote a chance still gets a try).
`HandEvaluator.sideSuitMoonScore` scores the hand (Aces and Kings, the best side-suit run, top Hearts,
minus 2 per Heart of Ten or lower, Spade control). Fitted on Wayne's hands: after the pass, a score of
10+ flags 17 of his 21 tries and none of his 18 other hands. The commit bar is 16 at optimism 0, 12 at
50, 8 at 100; the build bar before the pass is 3 lower and is judged on the hand left after the look's
own pass. A player with the look on passes its low Hearts first (`HandEvaluator.moonLookPass`, never
the Queen, King or Ace of Spades), and commits before the pass or after it
(`reevaluateMoonShotAfterExchange`, logged `by=MOON_LOOK`) when the hand clears the bar; like moon
interest, it then plays the shot even with a template moon weight of 0. Log lines: `MOON_LOOK`.
Sheet rows "Moon look %" and "Moon optimism" added to `tools/roster_sheet/roster_sheet.py` (blank).

Measured with `MoonCount` (400 games, draft ten, look on for all ten): tries per 100 hands went from
2.5 to 3.6 for the experts (Moon Chaser 15) to about 15 at look 50, 26 at look 100, 46 at look 100 with
optimism 100; the share made fell from about 1 in 5 to about 1 in 7 (look 50) and 1 in 16 (optimism
100). Cost in wins, one player against three plain Normals (`RosterAblate`, 2400 games, standard error
about 0.7): Steady 21.6% -> 14.0% (look 50) -> 8.3% (look 100); Shark 20.1% -> 12.2% (look 50);
Wildcard 4.6% -> 3.6% (look 50). The moon is a bad bet at these rates, as expected; the look is for
human feel, dialled per player. Tests: `MoonLookTest`; `AllTests` 52 suites.

Same day, from Wayne's description of moon hands with one or no Hearts and winners everywhere else
("pull all clubs minus the ace or king"): a side suit headed by the King or Queen with 3+ in sequence now
counts 1 per card of the sequence (it loses one trick to the Ace, then runs), and the look's pass never
gives away an Ace, a King, the Queen of Hearts or the Queen of Spades (it could pass a lone Ace of
Hearts before). The fit on his 39 hands is unchanged (none had such a run).

Then Wayne's Heart rules for this kind of moon (`HandEvaluator.lookHeartsOk`): no Hearts; or one Heart;
or the Ace (with any Hearts in sequence below it) plus at most one other Heart of 7 or higher ("Ace and Q
or J or 10 ... sometimes it even works better with a 7, 8 or 9, because people think you're trying to
get rid of your hearts"). Anything else is never a look shot ("the Q73, you can never shoot the moon with
that hand, the 7 & 3 are stoppers"); checked both before the pass (on the hand left after the look's
own pass) and at the commitment. Play, for a look shooter only (`Player.moonByLook`): lead the one Heart
that is not a sure winner as soon as Hearts may be led, once ("you can only really test it 1 time");
hold the Ace and King of Spades back until it is tested, then lead them to catch the Queen ("people will
get rid of their high spades"). If someone beats the Heart, the existing dead-moon damage control takes
over (avoid points, shed the low Hearts, hunt the Jack). Other shooters keep the 2026-08-17 rule of
holding Spade control back. `MoonLookTest` 48 checks; `AllTests` 52 suites. Re-measured (`MoonCount` 400 games,
look 50 on all ten): Steady 14.4 -> 10.4 tries per 100 hands, share made 13% -> 18%; at look 100 the
draft ten try about 18 per 100 hands (was 26) and make about 1 in 8. Steady at look 50 against three
Normals (`RosterAblate`, 2400 games): 14.0% -> 16.4% won (21.6% with the look off).

## 2026-11-05 -- ADDED: automatic test run on GitHub (cloud/ci-tests)

`.github/workflows/tests.yml` builds the project and runs `AllTests` (Java 21) on every push to
`AI_Player_dev`, `main` or a `cloud/` branch, and on pull requests; it can also be started by hand from
the Actions tab. A failing suite turns the commit red. The full test log is kept with each run.

## 2026-11-05 -- CHANGED (no play change): one shared game engine (cloud/game-engine)

New `GameEngine`: choosing a computer player's card, placing a card (with Hearts-broken and void
tracking), and resolving a trick (winner, payback and moon reads, won cards, next leader). The real game
(`Play_Hand`) and `HeadlessSimulator` both go through it now, so the simulator can no longer drift from
the real game. Hand setup and scoring were already shared. Proof nothing changed: `Equiv` matches
`f181cda`; a draft-ten `Lineup` is identical in every column; and the real Swing game, driven by the new
`UiSmoke` tool under a virtual display, plays a fixed deal line for line the same as before (222 game-log
lines), then 5 more hands without error. Tests: `GameEngineTest`; `AllTests` 51 suites.
`Hearts_JFrame.Place_Card_on_Table` is no longer called; it was left in place.

## 2026-11-05 -- MEASURED: settings audit (cloud/settings-audit, no play change)

New tool `KnobAudit`: changes each roster setting in turn and counts the seeded games (of 30) where any
card changes. Of 77 setting changes, 16 do nothing for all three players tested (Steady, Late Bloomer,
Queen-phobic): most `weight` values between 0.5 and 1.5, `reliable.STANDINGS`, `payback` and
`moonGamblePercent`. The roster sheet's help text now says so; whether to fix or retire each one is
Wayne's call. Full table: `docs/settings_audit_2026-11.md`.

## 2026-11-05 -- ADDED: signature options for Queen-phobic and Jack Hunter, off by default (cloud/signature-options)

New strategy components `QUEEN_PHOBIA` (PASS_HIGH_SPADES, FISH, BOTH) and `JACK_CHASE` (KEEP_DIAMONDS,
PRESSURE, BOTH); default OFF, roster unchanged. Signature components are never strayed by wildness and
draw no random number, so existing play is identical (`Equiv` matches `f181cda`; draft-ten `Lineup`
identical). In a trial round robin with Queen-phobic on FISH and Jack Hunter on KEEP_DIAMONDS, the two
separate: Jack Hunter takes the most Jacks (5.53 per game) and rises to 32.3% (+4.2), Queen-phobic eats
fewer Queens (4.10 to 3.90). Full numbers and the choices for Wayne: `docs/signature_options_2026-11.md`.

## 2026-11-05 -- ADDED: blunder guard for leads (cloud/lead-blunder-guard, user decision 2026-11-05)

`AI.isSuicidalLead` vetoes a blunder's runner-up lead, the same way `isSuicidalFollow` does for follows.
It uses public information only (cards played, own hand). Three rules: the Queen of Spades while the Ace
or King is still out; the Ace or King of Spades while the Queen is still out, unless the best lead was a
Spade; and a sure winner whose own points are 10+ worse than what the best lead is sure to take (4+ for
experts, the follow guard's limits). Wayne chose (decisions 1 and 2 in `docs/decisions_2026-11.md`) to
apply the third rule at every level, so it covers leading a sure-winning Queen (13) and giving up a sure
Jack of Diamonds (10) for everyone. Together the first and third rules mean a blunder never leads the
Queen of Spades. With every runner-up vetoed, the best card is played. The `Bad` random card is not
covered. No new random draws: `Equiv` over 300 hands matches `f181cda` exactly. Tests:
`LeadBlunderGuardTest`. Tool: `LeadVeto`.
Fires per 1,000 hands (4,000 games): level 1 4.06, level 2 4.88, level 3 2.93, level 4 0.66,
level 5 0.27 (Bonehead 0, since its blunder rate is 0). Paired `Lineup` against the build without the
guard (same seeds, 5,040 games each): no player moved more than 0.22 points of win rate or 0.16 of
average score.

## 2026-11-02 -- MEASURED: the four open decisions (cloud/decisions-2026-11, no play change)

`docs/decisions_2026-11.md`: lead guard widening (the sure-winning Queen lead is the most common suicidal
lead still allowed at levels 1 to 3; widening moves no win rate more than 0.22), the sure-Jack case, why
Jack Hunter and Queen-phobic look alike (their signature settings change nothing measurable), and
Bonehead (only its random card matters). Recommendations included; all four are Wayne's call.

## 2026-11-02 -- DOCS: HANDOFF_cloud.md refreshed (cloud/handoff-refresh)

Brought current and checked against the code at `f181cda`: work from `AI_Player_dev`, 47 suites, the
MoonCount and RosterAblate tools and the Lineup options, level and expert limits, the blunder guard, moon
interest, the building pass, GameLog/GameLedger/ForcedDeal, the round-robin numbers, and the open lists
(including the three open cloud branches). Shipped items were removed; the UI bugs wait for the UI upgrade.

## 2026-11-02 -- MEASURED: draft ten round robin, 5,040 games each (cloud/lineup-2026-11)

Full report in `docs/lineup_2026-11.md`. Careful Counter 46.1, Steady 37.0, Shark 36.1, Queen-phobic
28.7, Jack Hunter 27.3, Moon Chaser 25.8, Late Bloomer 17.9, Wildcard 15.4, Beginner 11.1, Bonehead 4.7
(se 0.3 to 0.7). No AI is under 1 moon try per 100 hands. Shark and Steady are tied. Jack Hunter and
Queen-phobic look alike in the numbers. `Lineup` now also prints hands, moon tries, moons made, Queens
eaten and Jacks taken per player (new `HandObserver.captures`); its win and score columns are unchanged.

## 2026-11-02 -- ADDED: roster sheet covers moonInterestPercent; check script (cloud/roster-sheet-sync)

`roster_sheet.py` gets a "Moon interest %" row next to "Moon gamble %" (blank =
level default 22/8/3/1/1). New `tools/roster_sheet/check_roster_sheet.py`
fails if a `draft_roster.txt` key has no sheet row or a draft value differs from
what the sheet would write; it reports 0 problems. Both scripts can now be
imported (`build(out)`, `roster_lines(rows)`); command-line use is unchanged.
The xlsx was not regenerated.

## 2026-10-17 -- ADDED: the pass builds toward a moon shot (user design: "the pass is very important")

Wayne: a player trailing the leader should also PASS cards that raise his chance of a hand he can shoot with.
Before this, only a player whose hand already cleared the commit bar passed offensively; an interested
player (see the moon-interest entry below) passed defensively and could only commit if the three cards it
received happened to help. Now `Deal_Hand.buildsTowardMoonShot`: a moon-interested player with three or more
Hearts including the Ace or King, who has not committed yet, uses the offensive pass in BUILDING mode
(`keepWorthiness(..., building)`: Spade control first, then long suit, Hearts kept above the junk, highest
Hearts first) and logs `MOON_BUILD`. After the exchange `reevaluateMoonShotAfterExchange` commits if the
received cards completed Heart control. Trailing already raises interest (doubling at 30+ behind), so a
far-behind player builds more often. COUNTED (1500 games, tries per 100 hands): level 1 8.1 -> 8.8, level 2
4.8 -> 5.6; made per try level 1 5.3% -> 6.5%, level 2 17.5% -> 18.0% (different trajectories, so a hint only).
`MoonInterestTest` extended (47 suites, 0 failed). Whole-game win rate (1680 games per player, se about 1.2 pp), interest off / on / on plus building pass: Careful Counter 45.4/44.9/44.6, Steady 39.8/37.1/36.5, Shark 35.8/34.4/36.0, Queen-phobic 30.5/27.9/27.8, Moon Chaser 27.9/27.9/26.6, Jack Hunter 26.5/27.7/27.9, Late Bloomer 16.4/18.1/17.7, Wildcard 13.9/16.6/16.9, Beginner 9.6/11.0/11.1, Bonehead 4.2/4.4/4.9. Strength order unchanged; the building pass is neutral (all within noise); the interest roll costs the strong a little and helps the weak a little.

## 2026-10-17 -- ADDED: moon interest, so every AI tries the moon now and then (user design)

Wayne: AIs must not be mechanical; some moon attempts work "if everything lines up" and a real player tries
them. Lower levels gamble most, experts least but still, and a player trailing the leader by a lot is more
likely to go for it. COUNT (new `MoonCount` tool, 1500 games): Careful Counter, Queen-phobic and Bonehead had
ZERO tries in ~10,000 hands because their templates carry MOON_OFFENSE weight 0 (`shouldAttemptMoonShot`
returns false at weight 0); level 1 tried least (0.9 per 100 hands) because reliable.MOON is low and Normal's
weight is 0.5. Step 1: small roster weights `weight.MOON_OFFENSE` 0.5 / 0.4 / 0.5 for those three (e1140e0).
Step 2 (this entry): per-hand "moon interest" roll. `Player.moonInterestPercent` (roster key
`moonInterestPercent`, default by level 22/8/3/1/1) is rolled in `Deal_Hand.beginHand`, raised linearly to double
at 30+ points behind the leader (`effectiveMoonInterestPercent`, public scores only). When it hits, the player
may commit on any hand with real Heart control and quality >= 12 even if template, reliability or the usual bar
say no, and `TacticWeights` keeps MOON_OFFENSE at >= 0.5 so the shot is played out. No random draw for a player
with rate 0, so existing seeded play is unchanged. `HeadlessSimulator` takes a per-seat interest array and a
`HandObserver` hook; `Lineup` has a `nointerest` arg. `MoonInterestTest` added (47 suites, 0 failed).
COUNTED (tries per 100 hands, 1500 games): level 1 0.9 -> 8.1, level 2 2.9 -> 4.8, level 4 2.1 -> 2.4;
leading 2.0, 1-14 behind 3.0, 15-29 behind 5.9, 30+ behind 9.2.
WIN RATE (Lineup round robin, 4200 games each side, each player 1680 games, se ~1.2 pp, interest off -> on):
Careful Counter 45.4 -> 44.9, Steady 39.8 -> 37.1, Shark 35.8 -> 34.4, Queen-phobic 30.5 -> 27.9, Moon Chaser
27.9 -> 27.9, Jack Hunter 26.5 -> 27.7, Late Bloomer 16.4 -> 18.1, Wildcard 13.9 -> 16.6, Beginner 9.6 -> 11.0,
Bonehead 4.2 -> 4.4. Strength order unchanged; the gamble costs the strong a little and helps the weak a little.

---

## 2026-10-17 -- ADDED: expert limits for level 4-5 (user rule)

Wayne: expert players should make fewer mistakes and never suicidal ones, and their wildness should not take
them into the weak options. For `Profile.isExpert()` (level >= 4): blunder rate capped at 2% (level 4) / 1%
(level 5) via `Profile.effectiveBlunderPercent()`; a blunder is only ever the 2nd-best card; as last player a
blunder may not cost 4+ points more than the best card (`AI.EXPERT_BLUNDER_COST`; everyone else keeps 10); and a
wildness stray into a measured leak (`Strategy.isWeak`: KIT_PASS=BLOCKER, LOW_HEARTS=PASS_FREELY,
SIDE_SUITS=FLAT, VOID_HUNT=NEVER, HEART_INSURANCE=ON, MOON_ALARM=FIRST_HEART) is dropped -- the player keeps its
usual option that hand. Found in the live game: Shark strayed into SIDE_SUITS=FLAT, HEART_INSURANCE=ON and
MOON_ALARM=FIRST_HEART in one hand.
MEASURED (paired Lineup, draft roster with levels, 4200 games per side, this build vs bb842d6): experts
stronger -- Steady 39.9% vs 37.4% (+2.6 pp, avg score 44.5 vs 47.3), Shark 37.5% vs 35.8% (+1.7, 48.3 vs 50.1),
Careful Counter 43.5% vs 42.7% (+0.8, 41.6 vs 42.6); the lower levels give that back (Wildcard -2.7, Jack Hunter
-1.5, others within 1). `ExpertLimitsTest` added (46 suites, 0 failed).

---

## 2026-10-17 -- ADDED: player LEVEL (1-5) and the level-graded moon-defense ladder (user design)

Wayne: nervous, lower-level AIs may panic and break a feared moon early; advanced ones reason and avoid taking
Hearts they don't need. New Profile setting `level` (roster key `level = 1..5`, default 3), the single answer to
the long-open "what makes an AI advanced". Seeded from the measured strength order: Beginner 1, Bonehead 1,
Late Bloomer 2, Wildcard 2, Jack Hunter 3, Moon Chaser 3, Queen-phobic 3, Steady 4, Careful Counter 5, Shark 5
(draft_roster.txt, roster_sheet.py, and Wayne's live roster.txt; backup of his old roster in
~/.hearts2/backup_20261017/roster_before_levels.txt).
MOON-STOPPING tactic 3 now asks `MoonThreat.breakTheShotWith` whether and with which card to take:
level 1-2 panic with the top card (the old behaviour); level 3 skips the take when every card it could follow
with beats the shooter's card, else uses the cheapest card above theirs; level 4-5 also read the shooter (first
Heart dumped on them and no sure winner led since, or leading a beatable Heart = not a shooter) and keep a
reliable STOPPER (top Heart still out + 2 other cards) instead of paying, unless urgent (shooter 4+ Hearts or the
Queen, or 4 or fewer cards left); with the shooter still to play they take with the cheapest card nobody can beat.
MEASURED (paired Lineup, draft roster, 4200 games per side, levels vs everyone at level 2): every player within
+/-0.8 pp win rate (noise); advanced players' average final scores 0.5-0.8 points lower (better): Careful Counter
42.6 vs 43.4, Steady 47.3 vs 47.8, Shark 50.1 vs 50.6. Neutral in AI-only play (AIs rarely shoot); the case it
fixes is the human moon read from live hand 4. `MoonLadderTest` added (45 suites, 0 failed).

---

## 2026-10-17 -- FIXED: breaking a moon shot now takes the CHEAPEST card that beats the shooter (user, live hand 4 replay)

MOON-STOPPING tactic 3 ("take the trick yourself to break the shot") only ever rewarded the HIGHEST legal card.
Shark (West, second to play, MOON_ALARM strayed to FIRST_HEART) answered South's 3 of Hearts with the Ace and
ate four Hearts; the 4 beats the 3, South (already played) could not win, and the same moon was broken for free
of the Ace. Now, when the shooter has already played a card of the led suit, the take uses the lowest legal card
above theirs; if the shooter is still to play, the highest stays (a small card could be overtaken).
`AbandonedMoonTest` extended (44 suites, 0 failed). Not measured with whole games. The weak FIRST_HEART stray for
experts is still an open question for Wayne.

---

## 2026-10-17 -- FIXED: a dead moon attempt was read as alive after the points split (live hand 4, Shark)

`AI.isMoonShotAlive` was `inProgressMoonShooter() == null || == me`, but that function is also null once point
cards are split between two or more seats, which is the moon's death. South took the K of Hearts (trick 3) and
North four more (trick 4); Shark stayed in MOON_SHOT mode and cashed Q, J, 6, 5 of Hearts, winning each trick
and eating the Queen South dumped (hand scored 11 for West). Alive now means no OTHER seat holds a Heart or the
Queen. New user rule on top: an abandoned attempt (`AI.moonAttemptAbandoned`) plays BASELINE, not Aggressive,
for leads and for the Aggressive follow-take, and a new ABANDONED MOON scorer leads the LOW non-boss Hearts
(lowest first) to shed them and get out of the lead. The same position now leads the 5 of Hearts.
Also added `ForcedDeal`: a `~/.hearts2/forced_deal.txt` (DEAL lines copied from a log, optional
`DIRECTION KEEP`) is used for the session's first hand, once, then renamed `.used.txt`. Tests:
`AbandonedMoonTest`, `ForcedDealTest` (44 suites, 0 failed). NOT yet measured with whole games.

---

## 2026-10-17 -- ADDED: per-game ledger (`GameLedger`, user request)

Wayne: if he wins too much, or the AIs never try to shoot the moon, the AI traits need rework -- so game
results must be kept over time. history.txt already keeps lifetime counters (gamesWon, moonCommitted, ...) but
no per-game view. `events/GameLedger.java` appends one CSV row per FINISHED game to
`%USERPROFILE%\.hearts2\gamelogs\games_ledger.csv` (never pruned): date, hands, winners, human place, and per
seat (N,E,S,W) name, total, moon tries, moon made, Queens eaten, Jacks taken. First row backfilled from the
log of the 15-hand game Wayne won 17 to 60/61/102 (no moon tries by anyone). `GameLedgerTest` added
(42 suites, 0 failed).

---

## 2026-10-17 -- CHANGED: a blunder can never be a suicidal play (user rule)

Found from the new game log: Shark (blunder 2%) was last to play on a Spade trick (2S 5S 7S) holding 3S and QS;
the near-miss blunder picked the 2nd-best card, QS, and ate 13 points. `AI.nearMiss` now takes a veto:
`AI.isSuicidalFollow` rejects (a) the Queen of Spades onto a Spade trick where she is the winning card, any
seat, and (b) as the last seat, a trick costing 10+ points more than the best card (Hearts 1, QS 13, JD -10).
A vetoed pick moves to the next runner-up; none allowed = the best card is played. Dice rolls and every
non-vetoed blunder are unchanged. Leads are not covered yet. User: expert-or-higher players may later get
lower blunder rates; they must never make suicidal errors. `BlunderGuardTest` added (41 suites, 0 failed).

---

## 2026-10-17 -- ADDED: per-game console log on disk (`GameLog`, user request)

Wayne wants to review whole games with Claude (e.g. a Shark lead of the A of Spades that gave up the J of
Diamonds), which was impossible because the play-by-play only went to the console. `events/GameLog.java`
tees System.out to `%USERPROFILE%\.hearts2\gamelogs\game_current.log` (flushed per line). A "NEW GAME" line
or the next program start archives it as `game_<timestamp>.log`; only the newest 5 archives are kept, so it
resets itself; `kept_*.log` (copied aside when a game is being reviewed) is never pruned. The log is the
same text LogReplayer reads (DEAL for all four seats, PASS, STRATEGY/SKILLS strays, TRICK, scores), so it
can be replayed. Output only, no play change. `GameLogTest` added (40 suites, 0 failed).

---

## 2026-10-14 -- FIXED (measurement only): HeadlessSimulator now applies moonGamblePercent

The simulator never set `Player.moonGamblePercent`, so the Ace-of-Hearts
flush and every other `takesMoonGambleThisHand` tactic was dead in every
measurement. `playSeededGame` takes an optional per-seat gamble array
(default all 0, so existing callers and wildness-off play are unchanged:
Equiv over 300 hands is identical before and after). `Lineup` now seats each
roster player at their own rate (`nogamble` as a 5th arg reproduces the old
run); `DialSim` takes `gamble=N` as a 4th arg. New MoonGambleTest suite.
Draft ten, 2016 games each, with vs without: no player moved more than 0.2
points (Moon Chaser 24.2% both ways). Gamble rate 100 on every seat changes
the play in 14 of 60 seeded games, so the trait is live but its net effect on
winning is small.

---

## 2026-10-14 -- MEASURED (no default or roster change): why the Shark ranks below Steady

New scratch tool `RosterAblate` (one roster player, optional edited settings,
vs three plain Normals, 2400 whole seeded games, se about 0.8). Draft Shark
(AI_10) wins 17.0%. The cause is the `style.Aggressive = 30` mood alone:
setting it to 0 gives 26.3%, level with Careful Counter (26.6%). Template
AGGRESSIVE vs NORMAL makes no difference (16.4%, same average final), nor does
payback off (17.0%) or Shooting_the_Moon 0 (16.4%). Other edits: wildness 0
18.6%, KIT_PASS HOLD_BACK 16.1%, count 50 15.6%. For reference Steady 19.3%.
Suggestion only, for Wayne: drop the Shark's Aggressive mood to about 0.
DECISION (Wayne, 2026-10-17): keep the Shark's Aggressive mood. An expert who
sometimes overplays is the human feel wanted; the lower win rate is accepted.

---

## 2026-10-11 -- CHANGED: defaults KIT_PASS -> HOLD_BACK, MOON_ALARM -> SHOOTER_READ (user approved)

Both old defaults measured weakest of their options on the new baseline
(BLOCKER 26.1% vs HOLD_BACK 30.4% / FREE 32.1%; FIRST_HEART 26.1% vs the
other alarms 28.2-29.5%). HOLD_BACK keeps the intent of the blocker rule
(the receiver never gets the whole kit) without spending one of the two
ducking Hearts; SHOOTER_READ is within noise of the best alarm and built on
the user's read. BLOCKER and FIRST_HEART stay as options.

Measured together (2400 whole games): new Normal vs three old-default
Normals 32.3% won (avg final 55.1); an old-default seat vs three new
Normals 18.0% (70.8); new vs new 25.3%.

Skill ablation re-run on the new baseline (one group off vs three Normals,
par 25.3%):

| group off | won | before the 10/10-11 work |
|---|---|---|
| JACK | 11.7% | 10.5% |
| SAFETY | 19.2% | 17.8% |
| PASSING | 21.5% | 43.7% |
| MOON | 24.0% | 30.9% |
| QUEEN | 25.8% | 25.0% |
| STANDINGS | 25.3% | 24.7% |

Every group now helps or is neutral -- PASSING and MOON, which used to cost
games, now earn them. TODO 2a closed.

Draft lineup (the roster spreadsheet, a first draft for
the user to edit) round robin, all 210 four-player combinations x 12 games:
Careful Counter 45.0%, Steady 37.3%, Shark 35.8%, Queen-phobic 30.5%, Jack
Hunter 29.7%, Moon Chaser 22.8%, Late Bloomer 18.8%, Wildcard 16.1%,
Beginner 9.5%, Bonehead 4.6%.

Tests updated for the new defaults (PassingRulesTest pins BLOCKER, its
subject); 39 suites green.

---

## 2026-10-11 -- IMPLEMENTED: wildness (an AI that doesn't always play the same)

User design: each AI may stray from its usual strategy options hand by
hand. Beginner/bonehead types stray more, top players less -- but top
players stray in the first few hands of each game while feeling out the
table; everyone strays more when losing in the current game and less when
winning. Every number is per AI (user: "make the multipliers and opening
hands per-AI settings").

| roster key | range | default | meaning |
|---|---|---|---|
| `wildness` | 0-100 | 0 | chance per component per hand of playing it some other way |
| `wildness.opening` | 0-100 | 0 | added during the first hands of each game |
| `wildness.openingHands` | 0-20 | 3 | how many first hands |
| `wildness.leading` | 0-300% | 50 | wildness while leading the game |
| `wildness.losing` | 0-300% | 150 | wildness while in last place |
| `stray.COMPONENT` | OPTION weight, ... | none | where it strays (relative weights); else evenly among the others |

Rolled once per hand (`Player.rollHand`, with the reliability and mood
dice, before passing) into `Player.strategyThisHand`; every strategy
decision now asks `Player.uses()`, which applies this hand's strays over
the profile. The game log prints `STRATEGY <seat> strayed this hand=...`.
`Player.handsThisGame` counts hands per game (reset by a new game). An AI
with no wildness draws no random numbers: 51,000 seeded hands identical to
`9463b94`. `StrategyTest` covers the settings, the per-hand chance (base,
opening, leading, last, cap) and straying. 39 suites green.

Measured (Normal with base wildness vs three Normals, 2400 whole games,
default standings, no opening): 0 = 26.1% won, 15 = 25.8%, 30 = 23.9%,
60 = 21.9%. Gentle, because many alternatives are close to the defaults
and some measured better -- wildness buys variety more than weakness; weak
players get weak from their usual choices, blunders and skipped skills.

Career growth (wildness easing with experience) belongs to the learning
step.

---

## 2026-10-11 -- IMPLEMENTED: strategy components (different AIs, different strategies)

User: "keep all of these and use them as changeable components of the AI
players ... I don't think the AI players should be playing with the exact
same strategies", and "there is not 1 true expert ... several players
expert skills, not the same exact skills ... a mixture of these skills,
that way they are unique, like real people."

New `Strategy` enum: eight components, each a decision with several named
ways of making it. A profile picks one per component -- roster key
`strategy.COMPONENT = OPTION` (case-insensitive; a bad option is warned and
skipped; `roster.txt`'s header now lists every component and option).
`Profile.uses(option)` is how the code asks.

| component | options (default first) |
|---|---|
| KIT_PASS | BLOCKER, FREE, HOLD_BACK |
| LOW_HEARTS | KEEP_TWO, BY_HEIGHT, KEEP_LOW, PASS_FREELY |
| SIDE_SUITS | BY_HEIGHT, FLAT |
| VOID_HUNT | WHEN_SHORT_ON_SPADES, NEVER |
| SPADE_CONTROL | TO_LEADER, ANYONE, SHIELD_LEADER |
| QUEEN_KEEP | FOUR_LOW_SPADES, FIVE_LOW_SPADES, NEVER |
| HEART_INSURANCE | OFF, ON |
| MOON_ALARM | FIRST_HEART, FOUR_HEARTS, QUEEN_AND_HEART, FOUR_OR_QUEEN, SURE_WINNER_SAME, SURE_WINNER_ANY, KEPT_LEAD, SHOOTER_READ |

The non-default options are the older rules replaced on 2026-10-10/11 and
the alternatives measured alongside them (figures in `Strategy.java` and
the two entries below). Restored for it: the top-Heart insurance scorer
(HEART_INSURANCE ON), the pre-blocker hold-back (KIT_PASS HOLD_BACK), the
2026-08-09 leader shield (SPADE_CONTROL SHIELD_LEADER).

MOON_ALARM gates the two costly moon-defense rules (don't feed the
shooter; take a point trick to break the shot) and is read by the new
`MoonRead`: per-hand public reads stored on Player (the suit where a
player's first Heart was dumped on them; whether they then LED a sure
winner -- the top unplayed card of its suit -- with the Queen still out;
the user's "lead a winner on purpose in that same suit"). SHOOTER_READ =
that read, or the Queen and a Heart, or 4+ Hearts.

Defaults reproduce the previous play exactly: 17 seeded fingerprints
(51,000 hands, every template, card-counting on and off, mixed tables)
identical to `9463b94`. New `StrategyTest` (profile setting, roster round
trip, one check per non-default option, moon reads, alarm gating the
denial). 39 suites green.

Not yet: which options each of the ten AIs uses (step 4), and a per-hand
"mixed" option for a wildcard player.

---

## 2026-10-11 -- CHANGED: keep two ducking Hearts; Ace/King of Spades to the leader (user rules)

Second round of the passing leaks found on 2026-10-10. Same measurement:
one seat vs three Normals (the 2026-10-10 rules), 2400 seeded whole games,
counting 50%.

| change | won | avg final |
|---|---|---|
| none | 26.1% | 61.2 |
| Hearts below the Jack scored by height (2 = 0 ... 10 = 8) | 31.9% | 53.7 |
| keep Hearts 2-5 as blockers | 31.4% | 55.7 |
| **floor: never below two Hearts under the 10** | **33.6%** | 53.2 |
| floor + height | 33.0% | 52.8 |
| height + keep 2-5 | 32.3% | 53.7 |
| Ace/King of Spades 12 to anyone | 26.0% | 61.1 |
| Ace/King of Spades 16 to leader, 14 to 2nd | 26.7% | 61.2 |
| **built: floor + Spades to leader + Jack-weapon fix** | **33.9%** | 53.0 |

- **Ducking-Heart floor** (`isDuckingHeart`, user rule): every Heart
  scored 8+ in the pass, so a hand with 9-3-2 of Hearts passed all three
  ahead of an Ace of Clubs. The object is to take no Hearts: stripped of
  low Hearts and passed a high one, the seat is forced to win the first
  Heart lead, maybe four Hearts at once. A Heart below the 10 now scores -5
  (keep) if passing it would leave fewer than two. One may still go, and
  the moon-kit blocker is unaffected. Scoring Hearts by height was also
  tried; it added nothing on top of the floor and was left out.
- **Ace/King of Spades** (`spadeControlPassScore`, user rule): the
  2026-08-09 rule scored them 4 to the leader or runner-up (vs 12),
  treating them as tools that strengthen the receiver. Backwards for a seat
  that isn't shooting: they're how a seat gets stuck catching the Queen,
  and a leader plays defensively and rarely tries the moon. Now 16 to the
  leader, 14 to 2nd, 12 to anyone else. Within noise in the measurement --
  the case is rare -- so taken on the user's reasoning. The
  `isDefensiveCautionRecipient` / `isRelaxedRecipient` / `closestRivalTo`
  helpers went with it.
- **Jack weapons**: the Ace/King of Diamonds while holding the Jack now
  score -1 instead of sharing filler's 1. They had only ever stayed because
  low Hearts outbid them; with the floor they tied with low Clubs and went.
- Tests: `PassingRulesTest` + keepTwoHeartsToDuckWith,
  spadeControlGoesToTheLeader; `MoonKitRetentionTest`'s hand gets the 6/5
  of Hearts for the 3/4 of Clubs. 38 suites green.

---

## 2026-10-10 -- CHANGED: passing and moon defense, from whole-game measurement (user rules)

Found while measuring step 2: switching skill groups off one at a time
(one seat vs three Normals, 2400 seeded whole games each, plain Normal =
24.8% won) showed PASSING off winning 43.7% and MOON_DEFENSE off 31.0%;
JACK and SAFETY clearly help (off: 10.5%, 17.8%). Careful passing also lost
where everyone else passed naively (16.8%), so the pipeline itself was the
problem. Discussed with the user, who supplied the rules; each was measured
alone and in combination with temporary per-seat switches before being
built in:

| change (one seat, vs old-rule Normals) | won | avg final |
|---|---|---|
| none | 24.8% | 60.9 |
| void a suit when short on Spades | 30.8% | 55.5 |
| pass the moon kit freely (no hold-back) | 29.2% | 55.7 |
| Clubs/Diamonds passed by height | 27.1% | 58.6 |
| no top-Heart "insurance" in play | 27.0% | 58.6 |
| kit only with a low-Heart blocker | 26.9% | 59.1 |
| keep the Queen only at 5+ low Spades / never | 24.7% / 24.6% | ~60.9 |
| void + low-Heart blocker + height + no insurance | **38.0%** | 48.0 |
| void + free kit + height + no insurance | 40.1% | 44.7 |

Built in (the 38% row -- the user's rule is that a kit ALWAYS travels with
a blocker, so the slightly stronger "free kit" row was not taken):

- **Blocker** (`withholdMoonFuel`): a pass carrying a moon kit must carry a
  Heart of 5 or lower -- e.g. Ace + King of Hearts + 2 of Hearts, or Queen
  of Spades + Ace of Hearts + 3 of Hearts; never Ace + King of Hearts +
  Queen of Spades. The least moon-relevant card is swapped out for my
  lowest such Heart. Only with no low Heart does the old hold-back of the
  top Heart run. The far-behind-recipient exception is gone ("you always
  have to pass a blocker").
- **Void when short on Spades** (`clubDiamondPassScore`): three or fewer
  Spades below the Queen makes emptying a Club/Diamond suit a +12 bonus,
  counting cards already picked (a three-card suit can go whole).
- **Height**: Clubs/Diamonds score by rank (Ace/King 6 ... Two 0), except
  the Ace/King of Diamonds while holding the Jack.
- **Removed** MOON-STOPPING tactic 0 (hold Queen-and-up Hearts as
  insurance before any point is captured). Moon defense now reacts only
  once someone has actually taken a point card.
- Unchanged: Queen-keeping rule (measured no effect either way).
- New `PassingRulesTest`; `MoonKitRetentionTest`'s hand given a fourth low
  Spade (three now triggers the void rule). 38 suites green.

---

## 2026-10-10 -- IMPLEMENTED: AI players step 2 -- per-hand reliability, moods, near-miss blunders, and Queen payback

User picks: reliability rolled once PER HAND (not per card); blunders are
NEAR MISSES (Bad keeps its random 1-in-3); moods are flat percentages for
now; reliability set by a few grouped knobs with single families still
settable.

- `Profile` gains three per-hand dials, all defaulting to "as before":
  - `reliable.<SKILL|FAMILY> = 0..100` -- how often a family is in play.
    Skills: SAFETY (basic safety + void awareness), JACK (guard + hunt),
    QUEEN (flush), MOON (offense + defense), PASSING, STANDINGS (end game +
    social). COUNTED_CERTAINTIES is excluded -- countCardsPercent already is
    its per-hand roll. `Player.rollHand()` (from `Deal_Hand.beginHand`)
    fills `Player.skippedThisHand`; `TacticWeights.weight()` reads those as 0.
  - `style.<Style> = 0..100` -- a style on for about that share of hands
    (on/off still work and mean 100/0).
  - `blunder = 0..100` -- per card, play the 2nd best card (3rd best one
    time in three). `AICardScorer.rankLead/rankFollow` return the ranked
    list; `pickLead/pickFollow` are unchanged (`get(0)`, same first-max tie
    order).
  - Dice are only rolled for a setting strictly between 0 and 100, so
    seeded games replay unchanged.
- **Queen payback** (new `Payback`, user design; revives the inert
  Payback_p1/2/3 idea): `payback = on|off|a,b,c`. Off by default.
  - Culprit: whoever dropped the Queen on a trick I won; if I played her on
    my own trick, whoever PASSED her to me (a passed Queen counts only if I
    take her). Same culprit again resets; several culprits are all tracked.
  - Taper over the next three hands; `on` = 100/60/25, Aggressive
    100/80/50, Cautious 70/35/10, Moon Shooter 80/40/15.
  - Target standing: 1st/2nd full, 3rd 0.25, last 0 -- unless within 15 of
    the leader (then full).
  - Passing to the target: the Queen (only when I'm 3rd/4th -- from 1st/2nd
    she can come straight back on an off-suit trick), Ace/King of Spades;
    low Spades kept. Passing elsewhere: a protected Queen is kept for play.
  - Play: Queen (150) or Heart (30) void discard onto a trick the target is
    winning, x strength. Never on the leader's trick -- giving the Queen to
    the leader always comes first (SOCIAL owns that).
  - Grudges last one game (`startNewGame` clears them).
- Simulator: `playSeededGame` takes `Profile[]`.
- **Equivalence:** the 51,000-hand fingerprint is IDENTICAL to the
  pre-profile build, both before and after payback was added. 37 suites
  green; new `PaybackTest` (34 checks) and ProfileTest grew to 43.
- **Measured** (one variant vs three Normals, whole seeded games, counting
  50%, games won):

  | variant | games | won | avg final |
  |---|---|---|---|
  | Normal | 1200 | 23.9% (se 1.2) | 61.6 |
  | reliable 75 (all skills) | 1200 | 21.3% (1.2) | 61.0 |
  | reliable 50 (all skills) | 1200 | 23.2% (1.2) | 59.7 |
  | reliable 0 (all skills OFF) | 2400 | 30.4% (0.9) | 51.0 |
  | blunder 10 | 1200 | 17.9% (1.1) | 68.2 |
  | Aggressive mood 50 | 1200 | 16.5% (1.1) | 70.5 |
  | Bad | 1200 | 9.9% (0.9) | 72.6 |
  | blunder 33 | 1200 | 5.6% (0.7) | 82.4 |
  | Brain Dead | 1200 | 0.1% (0.1) | 105.2 |
  | payback 100/60/25 | 2400 | 24.8% (0.9) | 60.9 |

  Blunders are a clean strength dial. Reliability does NOT behave like
  one: a Normal with every skill family switched off (CORE only, plus its
  counting) BEATS three full Normals -- 30.4% won vs 23.9% -- which says
  some family is costing Normal games against its own kind. Being traced
  family by family (see TODO.md); it matters for step 4 (more skills is
  not automatically stronger). Payback is neutral: grudges form ~4 times a
  game but change a card in only ~3% of games (its conditions rarely line
  up; ordinary passing already does most of it).

---

## 2026-10-10 -- IMPLEMENTED: AI players step 1 -- every AI is a Profile (template + overrides)

Start of the AI-players work (user: ten different AIs, each a blend of the
tactics "at our choice", some with random traits, some that get better).
The user chose TEMPLATES + OVERRIDES over the design doc's ranked-trait
resolver (DESIGN section 2.1): same control, chosen directly, no
conflict-resolution engine.

- New `Profile`: a template (one of the eight Player_Type presets -- its
  TacticWeights row and its style flags) plus per-family weight overrides
  (0..1.5) and style overrides (Aggressive / Avoids_Queen /
  Shooting_the_Moon on or off). Competence tiers (Bad, Brain_Dead) still
  come from the template only. Immutable.
- Every `Player` now carries a profile (default plain NORMAL), and
  `TacticWeights.weight()` reads it -- one path, no type/profile fork.
  `Set_Player(Player_Type)` is now `Set_Player(Profile.of(type))`.
- `roster.txt` schema 2: `template = X` (the old `type = X` still loads),
  `weight.<FAMILY> = n`, `style.<Style> = on|off`. Invalid override lines
  are warned and skipped; the player still loads. The seat dropdowns show
  the overrides.
- **Equivalence proof:** a fingerprint of every card played in 51,000
  seeded hands (each preset at 0% and 100% counting, plus a mixed table)
  is IDENTICAL on the pre-profile build and this one. New `ProfileTest`
  (24 checks) pins template fidelity, override isolation, rejection of bad
  values, a real behavior change from an override (Normal with
  PASSING_CRAFT 0 passes a protected Queen it otherwise keeps), and roster
  load / bad-line tolerance / round trip.

Decisions recorded for the next steps (user, same day): learning comes
from BOTH experience and lessons, each AI with its own ceiling; AIs learn
from other AIs as well as from people; and each real person gets their own
"world" -- their own history file in which the AIs start from roster.txt
and grow only from that person's games (people play this computer at
different times, one human per game).

---

## 2026-10-10 -- IMPLEMENTED: stats extensions, a situational opponent model, whole-game duplicate testing (and two AI hooks measured and removed)

User picked TODO items #2 (stats extensions) and #3 (AI reads the tendency
counters) together.

### Stats extensions (StatsRecorder / StatsReport)

- **Comeback rate** -- standings snapshotted the first time anyone reaches
  50 (user's choice of midpoint); whoever is last then gets a chance,
  winning converts it. A game's first hand (every total equals its hand
  score) resets the snapshot, which also recovers from abandoned games.
- **ELO** -- starts at 1500; each game counts as head-to-head results
  against the other three (lower total = win, tie = half), 32 points at
  stake per game split across the three, expectations from PRE-game
  ratings so seat order never matters.
- **Passing outcomes** -- every penalty card passed (Hearts, the Queen) is
  followed to the trick that captures it: landed on the recipient, came back
  to the passer, or went elsewhere. Also per pair ("pass points I landed on
  them").
- **Lead success** -- a lead is clean when the leader takes no penalty
  points from it (winning the Jack's -10 counts as clean), overall and by
  suit.
- Event facts added (additive): TRICK_WON `leader` + `cards`, PASS_RECEIVED
  `passedCards`, HAND_ENDED `totals`.

### Situational opponent model (roster/OpponentModel) -- recorded, read by the stats screen only

The user's own play: a LEADER rarely shoots the moon ("if you are winning
every other player is out to get you"), a LAST-place player tries it more
(the table is busy pulling down the players ahead), and how far behind
matters. A flat per-player rate would average that away -- the user wins
most of his games, so his ~1% raw rate badly underrates him when trailing.
So every hand is now recorded against each player's START-of-hand place
(lead / middle / last) and distance band behind the leader (0, 1-9, 10-24,
25+), per player and pooled table-wide. The read side estimates a
player's attempt rate in a situation in shrunk layers and compares it with
the OTHER players' rate (an absolute "3% is ordinary" bar was tried first
and read nobody as a shooter: in simulation even Moon_Shooter tries ~3%,
Normal ~0.4%). Large shrink weights (150/100/300 imaginary hands) because at
a ~2% base rate one moon is worth ~50 hands of evidence. The stats screen
shows raw tries per place and the model's read at lead / middle / last-25+.

**Two AI hooks were built on it and taken back out the same day:**
- Hook B -- never pass a likely shooter a top Heart. Duplicate test,
  Moon_Shooter vs three Normals, 2000 deals: **+0.20 pts/hand (9 se) WORSE**
  for the seat using it, +2.3 in the hands it changed. Keeping an Ace/King
  of Hearts wins Heart tricks; even the sim's heaviest shooter tries a moon
  in only ~3% of hands, so the insurance costs far more than it saves.
  (A graded fuel-limit discount was designed first and was a provable no-op:
  `defensivePass()`'s safety valve already blocks the only bundles it could
  catch.)
- Hook A -- accept the Queen a Heart or two earlier against a likely
  shooter. Never fired in single-hand duplicate deals (everyone starts level
  at 0, where the standings check always refuses), so unmeasurable there;
  removed rather than shipped blind on a 13-point decision.
- Lesson: the score identifies shooters fine; acting EARLY on a rare event
  is what costs.

### Tooling

- **`GameDuplicateTest`** -- the whole-GAME counterpart of DuplicateTest:
  entire seeded games (real standings carried hand to hand until someone
  reaches 100), baseline + on-for-one-seat, judged by GAMES WON (tied wins
  shared). Built because nothing standings-driven can fire in single-hand
  deals. Optional first-game arg so long runs split across processes.
  `HeadlessSimulator.playSeededGame()` added; the per-hand loop is shared
  with `playSeededHand()` (unchanged behavior).
- `DuplicateTest`: per-seat types (`T1,T2,T3,T4`) and a per-seat breakdown.
- Both duplicate tools now discard the simulator's console output instead of
  buffering it -- a long run otherwise grows the buffer to Java's 2 GB array
  limit and dies (it killed an 8000-game run).

### Parked: "play to win" objective, on branch `win_chance_objective`

User rule (2026-10-10): "this game hearts is all or nothing, it only
matters to win." A 5,000-game study measured mid-game win chance by gap to
the leader (1-5 back 36%, 6-10 31%, 16-20 20.5%, 36+ 4%); gap-to-leader
predicts the winner better than margin-vs-average, and near 100 a 10+
leader wins ~90% while 20+ back is ~2% -- the user's picture of the leader
pushing someone over and the close chasers keeping the game alive. Built:
`WinChance` (a measured 10x15 table, 515k snapshots / 8000 games, exact
once anyone reaches 100), the Queen-to-stop-a-moon decision rebuilt on it
(take her only if it raises my chance to WIN -- e.g. 2nd & 8 back beats 2nd
& 16 back), and the endgame solver's objective switched from place-then-
average-margin to win chance. Whole-game duplicate test, 1600 games, mixed
types: **win rate -0.08 +/- 0.25 points of percentage -- no measurable
effect**; +1.6 points/game taken on purpose (+/- 0.16). User decision:
don't ship it to everyone; it comes back as an "advanced player" trait when
the ranked-trait AI personalities are designed. Kept intact on the branch.

---

## 2026-10-09 -- TOOLING: `DuplicateTest`, the standard way to measure a tactic

User, after the bleed-out results: "we should always do that type of test,
dup, whenever we really want to see the impact in real life." Committed as
`test/hearts_1/DuplicateTest.java` (a measuring tool, not in AllTests).

Each deal is replayed from fixed seeds (deck via `Deck.shuffle_now(Random)`,
plus `AI.RNG`) five times: a baseline with the tactic off for every seat,
then on for exactly ONE seat at a time with the other three unchanged. It
reports that seat's points against the baseline, overall and in the hands
where play actually changed, with standard errors (2+ is the bar for
"real"). Stricter than the scratch `BleedPaired` used for the bleed-out,
which switched the tactic on for everyone at once. It self-checks that the
seeds replay card-for-card and refuses to report otherwise.
`HeadlessSimulator.playSeededHand()` is the seeded single-hand player it
uses. A tactic joins by exposing a per-seat disable set and an entry in
`DuplicateTest.TOGGLES`.

**Both new tactics re-confirmed under it** (100% counting):

| Tactic | Type | Deals | Pts/hand | Changed play | In those hands | Better / worse |
|---|---|---|---|---|---|---|
| endgame | Normal | 1000 | -0.174 +/- 0.028 (6.2 se) | 20.9% | -0.83 | 293 / 98 |
| bleed | Normal | 3000 | -0.042 +/- 0.013 (3.2 se) | 5.0% | -0.84 | 238 / 179 |
| bleed | Avoids_Queen | 3000 | -0.040 +/- 0.013 (2.9 se) | 5.1% | -0.78 | 240 / 186 |

---

## 2026-10-09 -- IMPLEMENTED: Queen-of-Spades bleed-out (TODO.md item)

From the user's 2026-08-26 strategy deep-dive (Mark's Hearts Tips, "Bleed
out the Spades"): a Queen holder with real Spade length leads the Ace, then
the King, then low Spades, keeping the Queen for last -- draining the suit
from everyone else so she can't be forced out later.

**`AICardScorer.queenBleedApplies()`** gates it: not a moon shot and no
other seat's moon in progress; I hold the Queen and she's still in play (so
nobody can drop her on my Ace or King -- what makes those leads free); at
least 2 Spades below her to duck with (the line between real length and the
bare Ace-and-Queen hand of the 2026-08-07 bug, which keeps its penalty);
no opponent known void in Spades; some Spade still out to drain; the type
plays QUEEN_FLUSH tactics (so not Cautious, Moon_Shooter, Bad); and not an
Aggressive lead (see below). When it applies, the high-Spade lead penalty
stands down, `QUEEN_BLEED_HIGH_LEAD` (200) goes to the highest Ace/King
held, and once neither is left `QUEEN_BLEED_LOW_LEAD` (100) goes to the
lowest Spade. The Queen's own lead penalty is untouched.

**Measured with a new PAIRED A/B** (scratch `BleedPaired`, not committed):
each deal shuffled from a seed and played twice, bleed on then off, with
`AI.RNG` seeded identically, so the two runs match card-for-card until the
bleed first changes a decision (verified: zero mismatches in deals where it
never fired). Compared only the seat the bleed applied to. 6000 deals per
type:

| Type | Pts ON | Pts OFF | Diff | Plays changed | Took the Queen ON / OFF |
|---|---|---|---|---|---|
| Normal | 3.13 | 3.39 | -0.26 +/- 0.10 | 1191 of 2432 | 420 / 452 |
| Avoids_Queen | 3.28 | 3.53 | -0.25 +/- 0.10 | 1220 of 2426 | 449 / 484 |
| Aggressive | 2.98 | 2.98 | 0.00 +/- 0.03 | 297 of 2491 | 438 / 435 |

An earlier UNPAIRED run (10000 hands) couldn't separate it from deal luck --
the tactic only fires in a fraction of hands -- which is why the paired
harness was built. Aggressive already leads high by nature, so the bleed
barely changed its plays and had no effect; per the user it is switched off
for Aggressive (and Aggressive_Avoids_Queen), which therefore play exactly
as before. `Deck.shuffle_now(Random)` added for the seeded shuffle.

**Tests:** `QueenBleedTest` (12 checks): Ace first, King next, then the
lowest Spade, never the Queen; the bare Ace-and-Queen hand still doesn't
lead the Ace; stands down for a Spade-void opponent, a Queen not mine,
Cautious, and both Aggressive types.

---

## 2026-10-08 -- FIXED: Aggressive won a Spade trick with the King into a live Queen

Live hand 6: South led the 10 of Spades. West (Aggressive, holding the 3 and
King of Spades, not attempting a moon) played second and won with the King;
East, last to act, dumped the Queen on it -- 13 points West never needed to
risk. User: West "took the Q Spades when it had other cards."

**Root cause:** the Aggressive take scorer refused a trick only when
`AI.wouldCollectQueenOfSpades()` said the Queen was ALREADY on the table. It
never asked whether she could still land after this card. **Fix:**
`AICardScorer.spadeWinRisksLiveQueen()` -- winning a Spade trick with the
King or Ace is refused while the Queen is unaccounted for (not won, not on
the table, not in my hand) and some seat still to act isn't proven void in
Spades. The ordinary duck then plays the 3. `AggressiveQueenGuardTest`
(4 checks) reproduces the hand; verified it FAILS with the guard disabled.
Not related to the same day's endgame solver, which only acts in the last
three tricks.

---

## 2026-10-08 -- IMPLEMENTED: endgame solver (TODO.md's "Endgame claim")

**Redesigned before building, with the user.** The TODO line asked to
"sequence for maximum points swing" once counting proves every remaining
trick is won. But Hearts has no trumps: a seat that provably wins every
remaining trick collects every remaining card in whatever order it plays
them, so sequencing a sweep can't change a single point. Order only matters
while the outcome is still open -- and late in a hand counting plus the
proven voids usually narrow "who holds what" to a handful of possibilities.

**What shipped: `EndgameSolver.java`.** With 3 cards or fewer in hand (4 if
the voids already pin the deal down to `MAX_DEALS_AT_FOUR` splits), it
enumerates every split of the unseen cards consistent with hand sizes and
proven voids, plays the rest of the hand out exhaustively for each of my
legal cards, and scores each card by its WORST result over the splits.
`AICardScorer.endgameBonus()` turns that into two bonuses:
`ENDGAME_WORST_CASE_BEST` (3000, above every other scorer including the
counted certainties -- it is an exact look-ahead that already prices moons,
the Queen and the Jack) for the card(s) with the best worst case, but only
when some other card's worst case is genuinely worse; and
`ENDGAME_AVERAGE_BEST` (400) for the single card with the strictly best
average among those still tied. Tagged `COUNTED_CERTAINTIES`, so it runs
only for a seat counting cards that hand (roster `countCardsPercent`).

**Objective (user's call, option "b"):** standings place first, then my
points relative to the other three -- not my own points alone, which would
have rated another seat's successful moon (everyone else scores 0 under
Omnibus rules) as harmless and overridden the moon defense.

**Opponent model (user's rule).** Not "paranoid": nobody swallows the Queen
just to cost me points. Each opponent plays to protect its own standings
place, then its margin. That reproduces the user's rule exactly: a Queen
holder dumps her at the first chance unless eating her to stop a moon keeps
it ahead of the would-be shooter -- covered three ways in
`EndgameSolverTest.queenHolderKeepsHerOnlyWhenItKeepsHimAheadOfTheShooter`.
Ties in an opponent's choice resolve to the one worst for me.

**Plumbing.** `AI.chooseLead()`/`AICardScorer.pickLead()` gained overloads
taking the full hand -- before Hearts are broken `legalOptions` hides every
Heart held, and the solver would have counted them as an opponent's. The old
signatures still work (solver then runs only once Hearts are broken).
`Scoring`'s four point constants went package-private so the solver scores
with the same numbers. The solver refuses (returns null, no effect) whenever
its card bookkeeping doesn't add up to 52, or a leaf budget is exceeded.

**Measured.** Headless A/B (`EndgameAB`, scratch harness, not committed): all four
seats the same type at 100% counting, solver ON for one pair of seats and OFF
for the other, then swapped to cancel seat effects; 300 twenty-hand games
(6000 hands) per type:

| Type | Pts/hand ON | Pts/hand OFF | Games won ON / OFF | Slowest solve |
|---|---|---|---|---|
| Normal | 3.57 | 3.87 | 169 / 131 | 376 ms |
| Aggressive | 3.42 | 4.00 | 177.5 / 122.5 | 443 ms |
| Cautious | 3.77 | 4.14 | 169 / 131 | 257 ms |

About 0.3-0.6 points a hand better from the last three tricks alone, and
56-59% of games won against an even 50%. Roughly 3.5 solves a hand per
counting seat; typical solve a few ms, worst under half a second.

**Tests:** `EndgameSolverTest` (scoring cross-check against
`Scoring.computeHandScores` on 2000 random hands, the standings utility, the
Queen rule, a Queen-dump-timing endgame solved and chosen by `pickLead`, no
bonus when not counting, mid-trick follow, refusal on bad bookkeeping and
oversized hands, and 40 headless hands at 100% counting asserting the solver
actually ran). `HeadlessSimulator` gained a counts-cards-rate overload --
at its old default of 0% no counted tactic ever fired in simulation.

---

## 2026-08-26 -- FIXED: Queen of Spades fed to a shooter on a forced Spade follow

User watched a live hand: WEST committed to a moon shot but never held the
Queen of Spades -- it went to EAST in the pass exchange. WEST ran the
table for 8 straight tricks (MOON_SUSPECTED fired at trick 3), then led
the King of Spades at trick 10. EAST held only the Two and Queen of
Spades -- forced to follow suit, unable to win either way (King beats
both), but with a genuine choice of which to duck under with. EAST played
the Queen. WEST's King beat it, completing the shot for -36. User: "it
didn't have to play the queen, it was the last card west needed."

**Root cause, found by tracing the actual game log.** Three moon-defense
scorers already exist for exactly this class of decision, and none of them
covered it:
- The flagship "never feed the Queen" penalty (tactic 2, weight
  `MOON_DENY_FEED_QUEEN` = 1400) only fires on a VOID discard -- it
  returns 0 whenever `ctx.followingSuit` is true.
- Tactic 2c (2026-08-16) reserves the Ace/King of Spades on a forced
  Spade follow, but explicitly excludes the Queen
  (`candidate.cardValue != KING && != ACE -> return 0`).
- With neither engaged, the baseline "duck under a lost trick" rule
  decided alone -- and that rule sheds the HIGHEST safe loser first
  (ordinary play: hold your low cards in reserve), which is exactly
  backwards for the one card a live shooter is hunting.

**Fix:** new tactic 2d in `AICardScorer.java`, the following-suit
counterpart of tactic 2's Queen-of-Spades half specifically. Same
magnitude as tactic 2 (1400, not tactic 2c's softer 300 reserve value) --
handing the shooter the Queen directly ends the hand right now regardless
of whether it happens via a void discard or a forced-suit choice, so the
penalty should match. Same `expendableAgainstShooter` exception too (a
held Heart stopper already guarantees the shot can be broken later, so
there's nothing left to protect the Queen for) -- but wired to
`ctx.myHand.hand` rather than `ctx.legalOptions`. That distinction
mattered: `legalOptions` during a Spade follow is suit-restricted (see
`CardCounter`'s class doc) and contains no Hearts at all, so reusing it
the way tactic 2 does would make the stopper check permanently blind and
silently defeat its own exception -- caught while wiring the test, not by
inspection.

New `MoonQueenFollowTest` (6 checks): reproduces the exact hand, plus the
no-shooter/already-beaten/stopper-expendable/singleton-Queen/
still-can-win-so-tactic-3-applies-instead edge cases. 30 suites green.

---

## 2026-08-23 -- IMPLEMENTED: three open tactic refinements (user-directed sweep)

User asked for a deep dive across TODO.md's "Open tactic refinements",
with authority to decide which were worth doing and to build them in
whatever order made sense. Three shipped, ordered so the shared helper
landed before its two consumers. A fourth item on that list
(`Counts_cards` probabilistic extension) was left alone deliberately --
it is speculative groundwork for tactics that don't exist yet.

### 1. Jack-of-Diamonds viability dial -- the positive half

The 2026-08-16 Queen-trap guard (`jackPullRisksTheQueen`) answered "will
this hunt hurt me". The other half of TODO.md's item -- "can it actually
WORK" -- was untouched, and the gap was real: the pull lead already
required the candidate to be a guaranteed winner, which sounds like it
settles the question but doesn't. Winning the TRICK is certain; capturing
the JACK is not, because its holder just follows with a low Diamond. What
IS certain is that every Diamond-void seat gets a free discard onto a
trick the puller has committed to winning.

New `HandEvaluator.hasJackCaptureChance()`, same "hard precondition, not
a score term" shape as `hasMoonHeartControl()`. A hunt is viable only
with a boss Diamond AND a way to sustain the pull: two boss Diamonds
(Ace plus King is two pulls on its own), or three-plus Diamonds of
length, or so few Diamonds still unaccounted for that the holder is
already near-bare. The held case needed nothing -- `hasJackOfDiamondsProtection()`
already covers it via `AI.shouldProtectDiamondSuit()`.

Caught while wiring it: a first version tested raw Diamond LENGTH only,
which wrongly rejected a two-card Ace-King holding -- the strongest
possible hunting shape. Length was always a proxy for "how many pulls can
I make"; counting the pulls directly is what the gate does now.

### 2. High-Spade lead rework -- control-card retention, both directions

TODO.md's "Control-card retention" item, plus the user's 2026-08-23
live-play refinement of it ("the King could force you to take a hand if
spades is lead and you don't have any other spades... it could be
dangerous to hold").

Investigating it turned up a genuine defect rather than just a missing
nuance. The lead-side scorer fired on `safeToUnloadHighSpade()`, which is
true in two quite different states, and paid a flat Tier-1 +1200 in both:

- Queen still LIVE but every opponent provably void in Spades -- a rare,
  provable window to shed a real liability. Deserves the weight.
- Queen already CAPTURED -- no liability left at all. She cannot ride in
  on anything, because she is gone.

The second is the branch that fires essentially every hand (the first is
near-unreachable on a lead: if all three opponents are void in Spades and
the Queen is neither mine nor captured, there is nowhere left for her to
be). So the scorer's dominant real-world behaviour was to spend a boss
Spade winning a trick nobody was threatening it with, collect whatever
the void seats threw, and surrender a guaranteed re-entry -- for nothing.

Split into `highSpadeUnloadIsUrgent()` (keeps the full 1200, first case
only) and a new graded Tier-3 tempo scorer for the second, weighing the
two opposed considerations the user described:

- HOLD when there is a live use for the lead -- a sustainable Jack hunt
  (reusing item 1's helper) or a shooter needing stopping.
- SHED as the card nears being my LAST Spade while Spades are still live
  and it is provably boss. This is the more important half: a stranded
  boss Spade isn't a tempo card at all, since the next Spade lead forces
  it out on someone else's schedule.

Equal magnitudes on purpose, so a Spade that is both stranded and useful
nets to zero and ordinary lead scoring decides. The "provably boss"
requirement is the user's own Ace-fallen point: a King with the Ace still
out is not a forced take, since losing that trick is a fine outcome.
FOLLOW side untouched -- shedding onto somebody else's trick is the cheap
way to do it and carries none of the lead's guaranteed self-capture.
Gated on COUNTED_CERTAINTIES exactly as the scorer it replaced was, so
non-counting seats see no change in either direction.

### 3. Moon_Shooter keeps its own kit when not committed

The second half of TODO.md's "passing-craft per-type nuances" (the
`Avoids_Queen` half shipped 2026-08-16). Its blocker was already cleared
by `reevaluateMoonShotAfterExchange()` (2026-08-16), which gives a
retained kit a second look to be found by.

`Deal_Hand.moonKitRetentionBias()`: a seat with the `Shooting_the_Moon`
trait that did NOT commit subtracts a small `moonFuel()`-proportional
amount from `dangerScore()`, so the Queen, top Hearts and Spade control
stop being the first things it volunteers. Deliberately mild per
TODO.md's own analysis -- a non-committed seat that hoards the full kit
and still fails the second look is holding pure liability -- so it
re-ranks genuine tie-breaks and nothing more. A bare unprotected Queen
still gets passed, and there is a committed test pinning that.

### Test notes

Two new suites (`HighSpadeTempoTest`, `MoonKitRetentionTest`), 29 suites
green. Three test-authoring traps worth recording, all caught by failures
rather than by inspection:

- `JackPullGuardTest`'s `pullingHand()` was a SINGLETON Ace of Diamonds,
  which item 1's gate correctly refuses to hunt with. Given real length
  so it still isolates the Queen-trap guard; the singleton case is now
  covered explicitly as a stand-down.
- Putting the captured Queen in one seat's won pile makes that seat the
  sole holder of points, so `inProgressMoonShooter()` reads it as a
  shooter -- which item 2 counts as a live use for the lead, silently
  cancelling the stranding shed under test. Split the points across two
  seats.
- A hand with three top Hearts makes EVERY type keep the Ace, because 12
  moon fuel trips `withholdMoonFuel()` (2026-08-07) -- an older rule
  about what the RECIPIENT gets, which masked item 3 entirely.

---

## 2026-08-17 -- IMPLEMENTED: hold Spade control back on a borderline moon-shot hand

User watched North commit to a moon shot on the weaker King-of-Hearts
route (no Ace) and lead the Ace of Spades on trick 2, capturing the Queen
immediately -- "you would only do it if you were guaranteed... they
should have been hoping that someone would dump the Ace on one of it
winning tricks before it attempted to go after the Q of spades." Asked
for a deep-dive into what expert Hearts players actually say about this
before building anything.

Web research findings, consistent across multiple independent sources:
- A suspected shooter is usually handed the Queen of Spades for free
  anyway -- defenders would rather deny a cheap Heart (costs them 1 if
  wrong) than contest the Queen with their own high Spade (costs them 13
  if wrong). An active hunt frequently spends a guaranteed trick solving
  a problem that resolves itself.
- "Leading the Ace and King of Spades in sequence" is named explicitly as
  exactly the kind of obvious play that telegraphs a shooter's hand.
- The standard advice for a shooter's own Spade control is to HOLD it and
  catch the Queen passively on a trick somebody ELSE leads, not to lead
  it yourself.
- Guides are explicit that a hand missing the strong markers (the Ace of
  Hearts chief among them) should default to caution over commitment.

None of this is a defensive nuance about avoiding the Queen -- it's
specifically about a SHOOTER's own offense, which is what makes it
directly applicable to North's play.

New `AICardScorer` MOON_OFFENSE guard: while shooting on the weaker route
and the Ace of Hearts is still a live unknown, the Ace/King of Spades no
longer draw Tier 1's "run the longest suit" bonus -- sized (1100) to
dominate that bonus outright for the SAME candidate card, not just nudge
it, since Tier 1 and this guard are scoring the identical Ace of Spades in
opposite directions. Deliberately narrow: only the Ace/King specifically,
not Spades as a suit -- a low Spade lead doesn't lock in the Queen's cost
or spend the control cards, so ordinary suit-length play through the low
end of a genuinely long Spade holding is untouched.

New `Player.moonShotHasAceOfHearts`, since `LeadContext` only carries
`legalOptions` (Hearts-filtered out here, since they're still unbroken --
the same gap `FollowContext.myHand` was added to close on the follow
side) and threading a full-hand parameter through `pickLead()`'s call
chain would have touched 23+ existing call sites for one boolean. Set
alongside the commitment flag itself in `Deal_Hand.beginHand()`, and
re-finalized in `reevaluateMoonShotAfterExchange()` for every currently-
attempting seat (not just newly-upgraded ones) from the real final hand --
a seat's own `offensivePass()` shields high Hearts, so this rarely flips
after the exchange, but "rarely" isn't "never."

Verified against the exact real hand before writing assertions: North's
reconstructed trick-2 state leads the Ace of Spades before the fix,
the low Seven of Spades after it (holding the Ace back, still running
the suit) -- and reverts to leading the Ace outright once given real
Ace-of-Hearts control, confirming the guard targets the specific
uncertainty and nothing else.

Tests: `BorderlineMoonSpadeHoldTest` (7 -- holds back on the borderline
hand, runs the Ace outright with real control, stands down once the Ace
has fallen to anyone, baseline mode untouched), `PostExchangeMoonTest`
extended (+3 -- the new flag is finalized correctly both pre- and
post-exchange). Full suite: 27 suites, 328 checks, 0 failed.

---

## 2026-08-16 -- FIXED: a Cautious seat with total Diamond control still led its weakest Diamond

Same live-hand review as the Ace-of-Hearts flush entry below, a second
finding from the same hand. EAST (Cautious) held the Jack of Diamonds
PLUS the Ace, King, and Queen -- literally every rank above the Jack,
total guaranteed control of the suit from the deal onward, nothing
anywhere could beat it -- and still led its weakest Diamond (the Two) at
trick 5, losing the trick and the lead to NORTH for nothing. That handoff
is exactly the moment NORTH took over the hand and never lost the lead
again for the remaining 9 tricks (see the unrelated same-day entry
tracing that sweep).

Root cause was tactic tagging, not logic. `AICardScorer` already had the
right scorer -- "COUNTED-OUT JACK OF DIAMONDS LEAD" fires the moment
counting proves nothing outranks the Jack, exactly EAST's situation -- but
it was tagged `JACK_HUNT` alongside the genuinely speculative tactics
("pull the Jack out of an opponent's hand," "opportunistically capture one
someone else exposed"). Cautious's personality row correctly zeroes
`JACK_HUNT` ("no flashy plays"), but that blocked the already-proven-safe
capture along with the real gambles, which was never the intent -- cashing
in a Jack you already fully control isn't a hunt, it's the payoff of
protection already held.

Fixed by retagging both counted-out scorers (`COUNTED-OUT JACK OF
DIAMONDS LEAD` and its follow-side twin, `COUNTED-OUT JACK OF DIAMONDS
CAPTURE`) from `JACK_HUNT` to `JACK_GUARD`. `JACK_GUARD` is 1 (full) for
every real strategic type -- NORMAL, CAUTIOUS, AGGRESSIVE, AVOIDS_QUEEN,
AGGRESSIVE_AVOIDS_QUEEN, MOON_SHOOTER -- and 0 only for BAD ("point-safety
basics only") and the bypassed BRAIN_DEAD, exactly the seats that
shouldn't be executing a counted certainty at all. The two genuinely
speculative tactics stayed on `JACK_HUNT`, unchanged, so Cautious still
correctly declines to chase a Jack it doesn't already hold.

User's own framing of why this is actually the SAFEST play with a strong
Diamond holding, not a risky one: cashing the Jack on the first lead
denies nobody anything they were going to keep anyway, and a seat void in
Diamonds is now live to dump a Queen of Spades or a Heart on it instead --
then lead the remaining low Diamonds next to keep stripping the suit and
force somebody else into leading something new. That second half needed
no new code: the ordinary longest-suit-then-lead-low baseline (Tier 3/4)
already continues running a suit this dominant on its own once the Jack
itself is out of the way.

Verified against the exact real scenario before writing assertions:
reconstructed EAST's trick-5 hand (Jack/Queen/King/Ace of Diamonds plus
five low ones, Cautious) and confirmed the lead flips from the Two to the
Jack once the retag lands. `SafeJackCashInTest`, 5 checks: Cautious now
cashes a fully-controlled Jack (the fix), BAD still never does (the
no-regression guard -- JACK_GUARD is 0 for BAD too, so this must be
unaffected), the follow-side twin captures a provably-safe Jack instead of
ducking under it, and Cautious still declines the genuinely risky pull-lead
when it doesn't hold the Jack at all. Full suite: 26 suites, 303 checks,
0 failed.

---

## 2026-08-16 -- IMPLEMENTED: Ace-of-Hearts flush lead + per-player moon-gamble trait

Follow-up to the same-day hand review that ended with "I think I would
have led with the K of Diamonds and hope someone would have dropped the
A of hearts on that trick." Investigated first, then built.

**The specific play wouldn't have worked in that hand.** Nobody was
confirmed void in Diamonds yet at tricks 2-3 (only the forced opening
trick had been played), and the King of Diamonds wasn't even a
guaranteed winner -- SOUTH held the real Ace of Diamonds the whole hand
and could have taken the trick outright, which would have sent any
dropped Ace of Hearts to SOUTH's pile, not the shooter's. Confirmed via
`Rules.legalLeads`/`CardCounter` against the real hand reconstruction,
not assumed.

**The underlying idea was sound and genuinely missing.** It's the exact
shape of the existing `QUEEN_FLUSH` tactic (lead into a proven void,
let the free discard hand over the dangerous card) -- just aimed at the
Ace of Hearts for the shooter's OFFENSE instead of the Queen of Spades
for a defender's safety. Two structural differences from QUEEN_FLUSH,
both necessary, not incidental:
- **Guaranteed WIN, not guaranteed loss.** QUEEN_FLUSH wants the lead
  provably beatable, so somebody ELSE eats the dumped Queen. This tactic
  needs the opposite: shooting the moon requires capturing every Heart
  PERSONALLY, so a flushed Ace has to land in the shooter's own pile.
  If a third seat wins the trick instead, the Ace is exactly as lost to
  the attempt as if nobody had flushed it at all -- which is precisely
  what the King-of-Diamonds proposal risked.
- **Never fires on a guess.** New `AICardScorer.voidSeatThatMightHoldTheAce()`,
  the exact mirror of the existing `aVoidSeatCouldHoldTheQueen()`, only
  returns a target once a void is actually proven via `VoidTracker`. An
  unconfirmed lead risks losing the trick (and the lead) for nothing --
  same risk the user's own proposed play carried in the hand that
  prompted this.

**New per-roster-player trait: `moonGamblePercent`** (0-100%, same
outer-gate shape as the existing `Counts_cards` ability -- re-rolled
every hand into `Player.takesMoonGambleThisHand`, 0 meaning the
roster's explicit "No" that standings can never override). Per the
user's explicit instruction ("we definitely need to factor in when a
person is trailing far behind that they would take these type of
chances"), `Deal_Hand.effectiveMoonGamblePercent()` boosts a seat's
effective rate the further it trails the field -- bounded the same
deliberate way `HandEvaluator`'s own standings term is bounded (that
file's own history records what an earlier UNBOUNDED version did: a
self-reinforcing spiral where falling behind made every later decision
worse). Also new: `MOON_ACE_FLUSH_LEADER_BONUS`, per the user's own
reasoning -- a seat already winning has the strongest incentive at the
table to shed a card that could eventually cost it a point, so a void
target who's also the standings leader gets extra weight. Verified the
bonus is not just wired but large enough to matter: a hand shaped so
the bare flush LOSES to running Hearts (505.5 vs. 444 weighted) flips to
WINNING (569 vs. 505.5) once the void target becomes the sole leader.

The 10-player starter roster (`Roster.installStarterRoster()`) and this
machine's live `roster.txt` both got a spread (0/20/0/40/0/60/0/80/0/30)
-- half never gamble, the rest spread widely enough that the standings
boost visibly matters differently for a low-base vs. high-base
individual in live play.

Tests: `AceOfHeartsFlushTest` (10 -- fires on a proven void + guaranteed
win, stands down on each of: gambling off, no void, unproven winner,
already holding the Ace, having already captured it myself, and the
exact no-void trick-2/3 shape from the real hand; plus the leader-bonus
flip), `MoonGambleTest` (6 -- level/ahead leave the base rate alone,
trailing boosts it, the boost is capped, a zero base is never
overridden, the result never exceeds 100), `RosterTest` extended (+12,
spread + full round-trip coverage of the new field). Full suite: 25
suites, 293 checks, 0 failed.

---

## 2026-08-16 -- FIXED: the moon-shot commitment was decided on a hand the seat would never play

Follow-up to the same-day passing-craft entry below, which identified this
as the prerequisite blocking the `Moon_Shooter` retention nuance and
flagged it as "arguably a latent correctness issue in its own right".
User asked for it directly. It is the correctness issue.

`Player.attempting_moon_shot` was set once, in `Deal_Hand.beginHand()`,
from the **pre-exchange** hand, and never revisited. Every seat therefore
committed (or declined) on the strength of three cards it was about to
give away, while blind to the three coming back. A seat dealt a hand one
card short of a shot could receive exactly that card and never notice --
there was no code path that could look again.

`Deal_Hand.reevaluateMoonShotAfterExchange()` now takes a second look at
the end of `passCards()`, once all four hands are final and re-sorted.
Same `HandEvaluator.shouldAttemptMoonShot()` call, same gates -- only the
input changes, from a hypothetical hand to the real one.

**Deliberately UPGRADE-ONLY**, and the asymmetry is the whole reason this
is a safe change rather than a destabilising one:

- A seat that already committed has ALREADY passed via `offensivePass()`,
  which keeps the Queen, the high Hearts and Spade control and ships its
  three most useless cards. That pass is irreversible. Un-committing
  would strand the seat holding exactly the danger kit it kept on
  purpose, with no plan for it -- strictly worse than either playing the
  shot out or never having committed at all. Abandoning a shot that has
  genuinely died is a different job entirely, already handled during
  trick play by `AI.isMoonShotAlive()` against real captured evidence.
- A seat that did NOT commit passed defensively, so nothing about its
  pass assumed a shot. Letting it commit now strands nothing, and it
  still has to clear every existing bar -- the MOON_OFFENSE weight gate,
  the hard `hasMoonHeartControl()` gate, and the standings/end-game
  adjusted quality threshold -- all now evaluated against the real hand.
  A seat that gave its high Hearts away simply fails Heart control and
  stays home, so this cannot manufacture a shot from nothing.

KEEP hands are untouched: `passCards()` returns before any of this, which
is correct -- with no exchange there is no new information and
`beginHand()`'s decision was already made on the final hand. The human
seat is skipped for the same reason `beginHand()` skips it.

Logged distinctly (`MOON <seat> attempting=true POST-EXCHANGE`) so a game
log shows plainly when a commitment came from the exchange -- the
2026-08-09 stale-flag bug was invisible for exactly as long as nothing
logged this decision. Publishes the same `MOON_COMMITTED` event, with no
double-count risk since it only fires for seats that did not publish in
`beginHand()`.

Verified end-to-end on a constructed hand before the assertions were
written (the scratch-diagnostic pattern this file keeps recommending):
NORTH dealt six LOW Hearts with no Ace or King fails Heart control and
declines at 20 quality; EAST passes it the Ace of Hearts plus two more;
the second look commits it at 24 quality holding six Hearts including the
Ace. Under the old one-shot decision that seat sat out a genuine shot.
`PostExchangeMoonTest`, 11 checks (the upgrade, the "still can't shoot"
guard across all four seats, an existing commitment surviving, the human
seat never auto-committed while holding a qualifying hand, and KEEP left
alone). Full suite: 23 suites, 270 checks, 0 failed.

---

## 2026-08-16 -- IMPLEMENTED: Jack-of-Diamonds pull viability gate, and the Avoids_Queen passing nuance

Two TODO.md items picked up together on a "think deeply about these, and
code them if you find a good way" request. Both landed, one of them not in
the shape it was originally written, and a third (the `Moon_Shooter` half
of the passing item) was found to rest on a false premise and deferred
with the prerequisite recorded.

**1. Jack-of-Diamonds pull viability gate** (`AICardScorer.
jackPullRisksTheQueen()`). The JACK_HUNT "pull the Jack out" lead spends a
boss Diamond to force the Jack into a trick the puller is certain to win.
Certain to WIN, though, is not certain to CAPTURE -- the Jack's holder can
simply follow with a low Diamond. What is certain is that every seat void
in Diamonds gets a free discard onto that trick, and the Queen of Spades
is the best discard a void hand can make. -10 does not cover +13, so the
hunt now stands down whenever that trap is live. This is exactly the
interaction TODO.md's own entry flagged ("vulnerable to opponents
deliberately feeding them the Queen of Spades on the back of that
eagerness") and it matches the Omnibus strategy consensus researched
earlier the same day: "never chase the jack into the queen of spades."

Three provable escape hatches keep the tactic alive rather than gutting
it: the Queen already won, the Queen held by me, or no Diamond-void seat
that could still be holding her. That last one is
`aVoidSeatCouldHoldTheQueen()` -- deliberately the SAME predicate the
QUEEN_FLUSH lead already uses, since the two tactics are exact mirror
images (the flush leads a LOW card into a void hoping the Queen gets
dumped; this hunt leads a HIGH one where that dump is the disaster).
Reusing it rather than writing a second near-identical helper keeps them
guaranteed consistent. `JackPullGuardTest`, 7 checks, covers the
predicate's four branches plus both scorer outcomes (stands down into the
trap, still fires without it).

**2. Avoids_Queen passing nuance** (`Deal_Hand.trustsQueenProtection()`).
Worth recording HOW this landed, because the obvious implementation was
built first and measured as a complete no-op. That version scaled the
Queen-shaped `dangerScore()` branches by a 1.5 emphasis, mirroring the
type's QUEEN_FLUSH weight in `TacticWeights`. A diagnostic comparing
Normal against Avoids_Queen across four hands produced byte-identical
passes every time. The reason is structural rather than a tuning miss: an
UNPROTECTED Queen already scores 15-25, at or above the best Heart (the
Ace, at 8 + 12 = 20), so she is already first pick -- and re-scaling a
first-place score cannot re-rank anything. Downstream,
`defensivePass()`'s toolkit-exclusion rule and `withholdMoonFuel()` then
reshape the chosen three anyway, absorbing what little was left. A
multiplier was simply the wrong mechanism, and shipping it would have
been a comment claiming behaviour the code did not have.

The one case a multiplier provably cannot reach is the PROTECTED Queen,
which `queenOfSpadesPassScore()` scores a flat **0** ("safe to hold, keep
and control her") unless the pass direction is ideal -- and 0 times
anything is 0. That is where the type now differs: an Avoids_Queen seat
declines to count Spade protection at all, drops through to the ordinary
unprotected branches, and ships her (still preferring the safe direction,
just no longer requiring it). Verified as a real split on a five-low-Spade
hand passed across: Normal keeps the Queen and passes two Hearts plus a
Club; Avoids_Queen sends the Queen. `AvoidsQueenPassTest`, 11 checks,
including an explicit no-regression sweep asserting all four other
strategic types still hold a protected Queen, and that an unprotected one
is passed identically by both.

**3. Moon_Shooter "keep your own kit when not committed" -- DEFERRED, not
built.** Investigated and found unsound as written.
`Player.attempting_moon_shot` is computed from the PRE-pass hand and is
never recomputed after the exchange, so a seat holding its kit back "in
case the incoming three complete it" can never discover that they did --
the flag is already false and nothing revisits it. Implementing the
nuance as specified would hand a non-shooting seat high Hearts and the
Queen for no reachable benefit: strictly worse than passing normally. The
real prerequisite is a post-exchange re-evaluation of
`shouldAttemptMoonShot()`, which is arguably a latent correctness issue
independent of any personality nuance (the commit decision is currently
made on a hand the seat will not play). Recorded in TODO.md with the
distinction from the separate, riskier "mid-hand moon adoption" idea
spelled out. Not built here because it changes the commit decision for
every type and deserves its own deliberate pass.

Full suite: 22 suites, 259 checks, 0 failed.

---

## 2026-08-16 -- FIXED: high-Spade-lead moon-shooter exception fired off one incidental Heart

User watched East lead the Ace of Spades and catch North's live Queen of
Spades, questioned why, and after a first pass mis-explained it as an
intentional "queen hunt" (there is no such scorer -- corrected once traced
directly through `HEARTS2_DEBUG_SCORER`), pushed further: West -- the seat
supposedly deterring the risk -- had captured exactly one Heart and wasn't
even attempting a moon shot (`MOON WEST attempting=false` in the log), and
the game was early with East in second-to-last place, only 4 points behind
West and 10 behind the leader. User asked for a deep web-research pass on
real Hearts strategy (with the Omnibus Jack-of-Diamonds bonus specifically
in view) before accepting any fix.

**Root cause, confirmed by direct scorer trace (not guesswork) both before
and after the fix:** `AICardScorer.highSpadeLeadRisksTheQueen()`'s
"moon-shooter exception" (added earlier the same day) waived the 800-point
`LEAD_HIGH_SPADE_LIVE_QUEEN` danger penalty for ANY seat that happened to
be `Scoring.inProgressMoonShooter()` -- the sole current holder of a point
card, with zero regard for how many, or whether that seat was actually
attempting a moon. Reconstructing East's exact hand from the game log and
running it through the real scorer confirmed the flip: with West's
incidental 1-Heart capture in place, the Ace of Spades scored -2.0 (won);
with it removed, -802.0 (lost, East leads a Diamond instead). The Queen
that then landed on East's lead was never West's to hoard in the first
place -- it was sitting with a completely uninvolved North the whole
time, who dumped it as ordinary baseline safe-discard play (see the
adjacent same-day entry below).

**Web research (sources in the conversation, not repeated here) backed the
user's instinct on both fronts:** general Hearts consensus treats a single
cheap Heart as fully sufficient to break any moon attempt -- "the loss of
one single heart foils a moon-shot" -- so nothing in the literature
supports the Queen (13x the cost) as a routine blocking tool, and
confidence that a shot is real should come from a visible behavioral
pattern, not a single incidental point-card capture. Omnibus-specific
commentary on the Jack of Diamonds bonus was equally direct: the Jack is
worth fighting for, but "minus 10 does not cover plus 13, so queen
discipline still comes first" -- exactly the "unless they have a plan to
take the J of diamonds too" framing the user proposed, and even then only
a partial offset, not a green light.

**The fix, once traced back into the codebase, turned out to already
exist in a stricter, better-reasoned form one function over:**
`MoonThreat.queenDenialWorthIt()` (the FOLLOW-side "should I actually
capture the Queen to deny a shooter" decision, also added earlier the
same day) already required the shooter to have banked a real majority of
Hearts (`HEARTS_URGENCY_THRESHOLD = 7`) -- unless a genuine
Jack-of-Diamonds offset (`jackOfDiamondsOffsetCredit()`) brought the net
cost down to 5 or less -- plus a standings-safety check before accepting
the Queen's cost. `highSpadeLeadRisksTheQueen()`'s LEAD-side exception
never used any of that; it was a bare, unguarded shooter-existence check.
Fixed by routing the LEAD-side exception through the same
`queenDenialWorthIt()` standard: waived only when a shooter is in
progress AND actually eating the Queen from them would itself be judged
worth it by the exact criteria already trusted elsewhere in this file.
New `MoonThreat.queenDenialWorthIt(Table_Cards, Player, PLAYER,
List<Card>)` overload added for the lead side, where the hand is already
a `List<Card>` (`LeadContext.legalOptions` IS the full hand while
leading) rather than a `Hand`. Deliberately still uses the raw
`Scoring.inProgressMoonShooter()` signal (not `MoonThreat.
threateningShooter()`) to find the candidate shooter, preserving the
original author's reasoning for that choice -- `queenDenialWorthIt()`'s
own, more precise standings check (would eating the Queen itself drop me
behind someone) already covers the ground `threateningShooter()`'s
safely-ahead filter would have covered less precisely.

`HighSpadeLeadTest`'s `safeWhileAnotherSeatIsShooting` case (the one
regression the fix broke, as expected -- it was asserting the old,
now-wrong behavior) split into two: one incidental Heart now correctly
asserts risky, and a new case with a genuine 7-Heart majority banked
(same standings shape as `QueenDenialTest.
allowsOnceTheShooterHasBankedAMajorityOfHearts`) asserts safe. Full
suite: 20 suites, 241 checks, 0 failed.

---

## 2026-08-16 -- FIXED: stale card icon lingering in South's table slot at hand start

User reported a red 8 sitting in South's own table-display slot (where
South's played card appears for the trick in progress) at the very start
of a new hand, before South had played anything -- playing South's actual
card (a lone Club) correctly replaced it. First diagnosis attempt (commit
`65535d3`) wrongly looked at South's HAND-SELECTION buttons instead and
found nothing (`remove_button` self-checks all clean, no `HAND DESYNC`);
user corrected the location -- it was the TABLE slot, not a hand button --
which redirected the investigation to the right place. `TODO.md`'s note
from that first attempt has been corrected to remove the misdiagnosis.

Root cause: `lblNorth`/`lblEast`/`lblSouth`/`lblWest` (the four table-slot
labels showing each seat's played card for the current trick) are only
ever cleared in one place in the whole file -- `jbClearTable`'s own
`ActionListener` (`setIcon(null)`/`setText(null)` on all four). Neither
`onTrickComplete()` nor `End_Of_Hand()` nor `jbStartHand`'s handler touch
them. `onTrickComplete()` only *enables* the "Clear Table" button after
the 4th card of a trick lands -- it doesn't require the user to actually
click it before doing anything else. After a hand's final (13th) trick,
there's no reason for the user to click "Clear Table" (the hand is
already over) before clicking "Start Hand" for the next one -- so the
previous hand's last-trick icons/text were never cleared, and lingered
on screen until each seat's own next play in the new hand happened to
overwrite that seat's label.

Fix: `jbStartHand`'s handler now clears all four table-slot labels'
icon and text itself, right alongside its existing hand-start resets
(`trick_nbr`, `hearts_broken`, `void_suits`) -- see
`src/hearts_1/Hearts_JFrame.java`. Pure Swing-rendering fix with no
scoreable game-state involved, so verified by rebuild + full test suite
(20 suites, 240 checks, 0 failed unaffected) plus live play, not a new
regression test.

---

## 2026-08-16 -- FIXED: Aggressive no longer chases Hearts without a real moon chance

User caught West (Aggressive) leading the Queen of Hearts and losing it to
the Ace, then correctly identified WHY it was wrong: West wasn't defending
against anyone's moon (no active shooter), and its own hand (Q/J/10 of
Hearts, no Ace, no King, only 3 total) had no realistic moon prospects
either -- it just led its highest card because that's Aggressive's bare
rule, "suit irrelevant." User's framing: Aggressive should chase Hearts
aggressively only with a genuine shot at the payoff; otherwise prefer any
other suit, and if truly forced into Hearts, play the smallest one.

Traced and reproduced through the real scorer before fixing (a debug env
var, `HEARTS2_DEBUG_SCORER`, was added to `pickLead`/`pickFollow` in the
process -- dumps every nonzero scorer contribution per candidate, kept
permanently since it resolved two separate mysteries tonight in minutes
each). The Aggressive lead scorer's Heart rank-bonus now also requires
`HandEvaluator.hasMoonHeartControl()` -- the SAME hard gate the real
moon-attempt decision already uses (Ace + 4 Hearts, or King + 5). New
`List<Card>` overload added alongside the existing `Hand` one (matching
the `hasJackOfDiamondsProtection` precedent) since a lead's legalOptions
is a list, not a Hand. Without the gate, Hearts get no rank bonus at all:
Aggressive prefers any other suit's high card instead, and if forced into
Hearts anyway, the existing graded Heart-danger scorer (unaffected by
this guard) naturally picks the lowest one on its own.

Verified against the exact real hand (West's post-trick-4 remaining
hand): with real alternatives available, West now leads a non-Heart
entirely; forced into Hearts-only, it leads the Ten, not the Queen. A
hand that DOES have real moon control (Ace + 5 Hearts) is confirmed
unaffected -- still runs its highest losable Heart as before.

Tests: `AggressiveHeartChanceTest` (4). 20 suites, 240 checks, 0 failed.

---

## 2026-08-16 -- IMPLEMENTED: per-individual "counts cards" ability (Counts_cards revived) + a new counted-minimal-win tactic

Resolves the `Counts_cards` orphan-trait question via a short research
detour (Bridge's GIB/PIMC family, Skat's inference-vs-evaluation split, a
documented 5-tier neural-net Hearts AI, and Dave Mark's IAUS -- the
utility-AI lineage `TacticWeights` descends from all consistently
separate "is this seat even allowed to know/deduce this" from "how much
does it want to act on it"). Implemented as a per-INDIVIDUAL-roster-
player ability, not a per-`Player_Type` one, per the user's explicit
"spread this ability out amongst the 10 AI players" -- two roster members
sharing a `Player_Type` can have completely different counting rates.

- **`RosterPlayer.countCardsPercent`** (0-100), persisted in `roster.txt`
  (clamped/defaulting to 0 on a bad value, matching the file's existing
  forgiving-parser convention). Starter roster spreads it deliberately: 4
  players never count, the other 6 at 25/50/50/75/75/100%.
- **`Player.countsCardsThisHand`** -- re-rolled fresh every hand
  (`Deal_Hand.beginHand()`, sharing `AI`'s existing `RNG`) against the
  seat's `countCardsPercent`. A 50% player is genuinely on some hands and
  off others, not permanently half-strength -- logged alongside the
  existing MOON attempt line for the same debuggability.
- **`AICardScorer.effectiveWeight()`** -- the actual gate, a single
  interception point in `pickLead`/`pickFollow` that zeroes any
  `COUNTED_CERTAINTIES`-tagged scorer when `countsCardsThisHand` is
  false, regardless of what the seat's `Player_Type` row says.
  `TacticWeights.COUNTED_CERTAINTIES` itself is untouched -- it still
  means "how much does this PERSONALITY want to act on a certainty once
  available," a different question from "is this INDIVIDUAL counting at
  all this hand." Every OTHER tactic family (moon defense, exit-card
  preservation, the Jack-giveaway grading, the high-Spade-lead exception)
  is completely unaffected -- verified by a dedicated regression test,
  per the user's explicit instruction not to lose any of that.
- **New tactic unlocked: COUNTED MINIMAL WIN.** User's own example: when
  about to win a trick and counting proves nobody left to act can beat
  it, spend the CHEAPEST sufficient card instead of the highest -- every
  existing "take this trick" scorer (Aggressive, moon-deny, free-capture)
  locks onto the highest legal card specifically. Found and fixed a real
  design bug before shipping it: the first version had no net-value
  guard, so it would have made a seat voluntarily WIN a Hearts trick just
  because doing so was uncontested -- "safe to win" and "worth winning"
  are different questions, and the scorer only answers the first once
  `trickValueWith()` confirms the second.

Tests: `CountsCardsGateTest` (7 -- gate on/off, the sibling tactics
staying universal, minimal-win firing only when the win is provably safe
and standing down otherwise), `RosterTest` extended (+13, round-trip and
starter-spread coverage). Verified live: a fresh `roster.txt` shows each
seat's rate in the dropdown, and the per-hand roll logs correctly.

19 suites, 232 checks, 0 failed.

---

## 2026-08-16 -- FIXED: two live-play blunders from the roster's first mixed-seat game (free-capture gating, Aggressive lead point-safety)

Found by the user reviewing the last hand of the second roster game (the
first with East/West seat types swapped -- East=AI_03 Aggressive,
West=AI_02 Cautious), both reproduced through the real scorers with a
scratch diagnostic before fixing (the established pattern: never
hand-trace ~20 stacked scorers):

**1. Cautious ducked a PROVEN free Jack (COUNTED FREE CAPTURE added).**
Trick 3: West, playing LAST over a 2-J-Q Diamond trick holding the Ace
and 7, ducked with the 7 -- handing the human a free -10. Root cause: the
capture-a-played-Jack scorer (2026-08-07) is gated by JACK_HUNT, which
Cautious deliberately weighs at ZERO. Opting out of speculative hunting
is personality; passing up a proven net-gain trick is a blunder. New
scorer under COUNTED_CERTAINTIES fires only when the win is PROVEN
(beats the table AND nothing at large can top it, or nobody left can
follow -- playing last passes vacuously) and the trick is net-negative.
Test-writing lesson kept in FreeCaptureTest: an ACE of the led suit is
proven at ANY position (nothing ever outranks it), so the unproven-case
fixture must use a King with the Ace still out.

**2. Aggressive led its boss Ace of Hearts into guaranteed self-capture
(point-safety guard added).** Trick 9: East, all-Hearts and already
holding 13 points from a forced self-eaten Queen (trick 8's Jack-of-
Spades lead left its Queen as the only legal Spade -- that part was
forced, not a bug), led the ACE over 2/6/8/9 and ate 2 more Hearts. The
Aggressive lead scorer was bare rank-hunger (+50/rank) with no guard,
and Aggressive's halved BASIC_SAFETY weight (0.5) cut the boss-heart
penalty to 200 vs the Ace's +600 -- the same arithmetic would volunteer
the Queen of Spades (+500 vs -450) or an Ace of Spades into a live
Queen. The rank bonus now skips point-inviting candidates (the Queen;
K/A of Spades while highSpadeLeadRisksTheQueen; counted boss Hearts),
mirroring the follow-side Aggressive take guard: trick-hungry is not
point-hungry. The highest LOSABLE card still gets the bonus, so the
personality keeps its edge on safe cards.

Also verified this game's stats recording end-to-end (23 hands across
both sessions, totals matching the logs exactly) and confirmed one
behavior worth knowing: quitting mid-game records hand-level stats only
-- game-level counters (placements, wins, streaks) only write when a
seat actually reaches TARGET_SCORE and GAME_ENDED fires.

Tests: FreeCaptureTest (3), AggressiveLeadGuardTest (5). Full suite: 18
suites, 216 checks, 0 failed.

---

## 2026-08-16 -- IMPLEMENTED: persistent AI roster v1 + player stats tracking (one shared store)

The two largest planned features, built together per the design decision
recorded in both TODO.md and DESIGN_roster_and_trashtalk.md section 3.3:
the roster's tendency counters and the human stats screen are nearly the
same data, so there is ONE store with two consumers, and the recording
layer was built exactly once on top of the GameEventBus (which was created
2026-08-15 for precisely this moment). New package `hearts_1.roster`:

- **`RosterPlayer`/`Roster`** -- 10 persistent identities, AI_01..AI_10
  (per the user: internal names for now, proper names later), each
  referencing one existing `Player_Type` preset. Deliberately NOT the
  design doc's ranked-trait mixtures yet -- referencing presets directly
  keeps the whole feature play-neutral and defers the personality
  resolver (with its mandatory 400k-decision equivalence gate) until
  blended personalities are actually wanted. Starter roster spreads the
  presets (one BAD for variety, no BRAIN_DEAD); `roster.txt` under
  `%USERPROFILE%\.hearts2\` is the user's to edit (types, names, the
  human profile) and is never rewritten after creation.
- **`StatsStore`** -- schema-free named-integer counters in two
  keyspaces: per-player (keyed by roster id / HUMAN) and per-ORDERED-pair
  ("A>B", asymmetric by construction). Persisted to `history.txt` --
  generated data kept separate from the authored roster so a corrupt
  history never costs the hand-edited file. Both files: versioned,
  forgiving parser (unknown keys/types/sections skipped with a warning,
  never fatal), atomic temp-and-rename writes, saved at hand/game end
  (never on exit).
- **`StatsRecorder`** -- the GameEventBus listener translating seat-keyed
  events into identity-keyed counters via the current seat assignment.
  Records: Queen drops and passes (per pair, both directions), Jack
  captures and giveaways, moon commitments/successes/stops, hands and
  games together, per-hand aggregates (perfect hands = score <= 0, slam
  hands = zero tricks, best/worst single hand, total points for the
  average), placements 1st-4th, pairwise finished-ahead-of, win streaks.
- **Honesty fix forced by the logs:** MOON_SUSPECTED fires in nearly
  every hand (the FIRST point capture always creates a lone point-holder)
  and is useless as an "attempts" signal. Added a real `MOON_COMMITTED`
  event (fires when an AI seat's hand-start evaluator commits; never for
  the human, whose intent is unknowable), and a stopped-moon filter: a
  MOON_BROKEN only counts as a stop when the former shooter had banked
  5+ Hearts (`StatsRecorder.STOPPED_MOON_MIN_HEARTS`).
- **Event facts enriched** (additive, no consumer broken): JACK_CAPTURED
  gains `playedBySeat`, MOON_BROKEN gains `breaker` + `shooterHearts`,
  GAME_ENDED gains `totals` (full final standings, without which
  placements were uncomputable).
- **GUI:** the three seat dropdowns now select roster members ("AI_01
  (NORMAL)") instead of raw types -- same `Set_Player` call underneath,
  so play is identical; raw-type testing means editing roster.txt. New
  "Stats" button opens a modeless monospace dialog (`StatsReport`)
  showing the human's overview plus a per-opponent breakdown, hiding
  never-played opponents.

Tests: `RosterTest` (15 -- starter creation, round trip, forgiving
parser, empty-file fallback), `StatsStoreTest` (14 -- accumulation,
ordered-pair asymmetry, negative values, garbage tolerance),
`StatsRecorderTest` (27 -- a full synthetic hand plus two game endings
covering every counter family, including the shooterHearts noise
filter). Full suite: 16 suites, 208 checks, 0 failed.

---

## 2026-08-16 -- IMPLEMENTED: moon-defense refinements from a live hand review (high-Spade-lead exception, Queen-denial Jack offset + timing gate, follow-suit Heart/Spade reservation)

User played a hand to completion (North shot the moon successfully in an
earlier game this session; this entry's own driving hand was SOUTH -- the
user -- shooting the moon with a "nothing but winners" 13/13 sweep) and
asked for a deep-dive review of whether the AI defenders played correctly.
Traced the full hand from the log: WEST held 3-4-7-J of Hearts against
SOUTH's Q-K-A-10-9 and played the Jack on the (unbeatable) Queen lead
instead of a cheaper card, meaning it had already discarded its one real
denial weapon three tricks before SOUTH's beatable Ten lead. Confirmed via
`AI.safelyAheadOfShooter`'s own math that WEST -- despite being in 1st place
overall -- was NOT safe from this specific shot (a completed moon would
have dropped SOUTH's total under WEST's, costing WEST the lead), so the
gap was real, not a defensible risk-adjusted choice. Four fixes, each
refined through user correction before landing:

1. **High-Spade-lead moon-shooter exception.** The existing "never lead
   K/A of Spades while the Queen is live" danger (2026-08-07,
   `LEAD_HIGH_SPADE_LIVE_QUEEN`) fired unconditionally. A shooter elsewhere
   never drops the Queen onto that lead -- they need her for their own
   attempt -- so the danger now stands down while a shooter (other than me)
   is in progress and I don't hold the Queen myself. New
   `AICardScorer.highSpadeLeadRisksTheQueen()`, extracted for direct
   testing (`HighSpadeLeadTest`, 5 checks).

2. **Queen-denial Jack-of-Diamonds offset + timing gate.** Grounded in
   real Hearts strategy consensus via web search: "opponents will tend to
   allow you to take the Queen of Spades even if they suspect you're
   shooting the moon" -- swallowing her defensively is a rare last resort,
   not routine. `MoonThreat.queenDenialWorthIt()` gained two gates ahead of
   the original standings check: (a) a Jack-of-Diamonds offset credit (full
   10 while holding the Jack with real protection, half 5 while merely
   holding the Ace/King of Diamonds) discounts the Queen's effective cost --
   this ruleset's own Omnibus twist, no standard-Hearts analog; (b) a
   timing gate requiring the shooter to have already banked a majority of
   the Hearts (7+/13) unless the discounted cost is already cheap (<=5).
   Required threading the acting player's full hand through
   `chooseFollow -> pickFollow -> FollowContext` (previously only the
   led-suit-filtered legal options were available, which can't see Diamond
   holdings when Spades are led) -- new `FollowContext.myHand`. New
   `QueenDenialTest` (8 checks).

3. **Follow-suit Heart reservation ("tactic 2b").** The 2026-08-06
   moon-denial inversion (shed lowest Heart first, not highest) only ever
   fired on VOID DISCARDS. A defender FORCED to follow a Heart lead it
   can't win fell through to ordinary highest-first duck preference,
   which is exactly backwards once a shooter is visible -- precisely what
   let WEST discard the Jack on the Queen lead. **Rejected a full
   lowest-first reversal** (the user's own correction): unlike a void
   discard, which can never accidentally win a trick, reversing the WHOLE
   follow-suit order would strip a defender's own low ducking cards early
   and reopen the exact endplay problem exit-card preservation exists to
   prevent. Shipped instead: reserve ONLY the single highest Heart while a
   cheaper one is available; every other Heart still sorts by ordinary
   highest-first duck order, unmodified. New `MoonFollowReserveTest` (4
   checks) replays the exact real hand and confirms both that the Jack is
   reserved AND that the non-reserved cards (7, then implicitly 4, then 3)
   still shed highest-first.

4. **Follow-suit Spade reservation ("tactic 2c"), user request.** Same
   idea for the Ace/King of Spades -- a defender following an unwinnable
   Spade lead should also weigh reserving its own Queen-capture weapon.
   Gated on `queenDenialWorthIt()` itself (the user's own framing: "unless
   they are really concerned about getting the Queen of Spades") rather
   than a new threshold -- no point reserving a card for a capture that
   wouldn't be worth making anyway -- and on `safeToUnloadHighSpade()`
   standing down whenever shedding is already provably fine (Queen gone,
   held by me, or unreachable), so the existing safe-unload reward and this
   new reservation penalty never both apply to the same card. New
   `MoonSpadeReserveTest` (4 checks).

Also confirmed (deep-dive, not a fix) with the user: real moon-shot
strategy runs provably-unbeatable cards first (Q, K, A) to pressure out any
hidden stopper before committing to riskier lower cards -- traced the new
reservation logic against that exact sequence and confirmed it holds (WEST
sheds 7, 4, 3 across the unbeatable tricks, forced onto the Jack on the
beatable Ten lead, and wins). Flagged for later, not built (see TODO.md):
proactive lead-seizing specifically to control a reserved stopper's
timing -- not needed for this hand, since the shooter won all 13 tricks and
no defender ever got a chance to win anything regardless.

Full suite: 13 suites, 152 checks, 0 failed.

---

## 2026-08-15 -- IMPLEMENTED: exit-card preservation

TODO.md's "keep at least one guaranteed-losing card late in the hand"
item. Traced the actual mechanism causing endplay in this codebase:
`AICardScorer`'s Tier 4 baseline lead rule ("leads low, bleeding a suit
dry cheaply") burns through low cards from the bottom up with no memory
of how many safe cards are left overall -- fine in isolation, but over
many tricks it can strip a hand down to nothing but boss (winning) cards.
New lead-side scorer only fires when a candidate would be this seat's
LAST remaining "reserve" card (one not provably the boss of its suit, via
`CardCounter.anyAboveUnaccounted`) -- with two or more reserves in hand,
nothing changes; with exactly one left, a mild penalty (60) discourages
leading it away while a boss-card alternative exists, without overriding
a genuine Tier-2 danger like the Queen of Spades. New `isReserveCandidate`/
`reserveCardCount`, package-private for direct testing. New `ExitCardTest`
(6 checks), including a pickLead-level case proving a low Spade and a boss
Ace of Clubs flip which one leads depending on whether the Spade is the
hand's only reserve.

---

## 2026-08-15 -- IMPLEMENTED: end-of-game awareness, attack-the-leader (SOCIAL), graded Jack giveaway, moon-stopper exception

One session, four connected standings-aware behaviors, each refined by a
user correction mid-build and verified by a clean full live game afterward
(the final playtest included a successfully completed AI moon shot with
everything active). New `EndGame.java` (pure logic, no Swing, same pattern
as `MoonThreat`/`VoidTracker`): `dangerLevel()` ramps 0..1 as a seat's
total enters the last 25 points before `TARGET_SCORE`, plus
`isCurrentLeader()` (ties count), `mostEndangeredOther()`, and
`currentTrickWinnerSeat()`. Two new `TacticWeights` families: `END_GAME`
(proximity-to-target play) and `SOCIAL` (standings-vs-each-other play,
active from hand 1 -- the first scorer in the family
`DESIGN_roster_and_trashtalk.md` Phase 4 will later extend).

**1. End-of-game awareness** (the TODO item unblocked 2026-08-09):
- *Stay under:* a follow that would win a point-carrying trick takes a
  danger-scaled penalty (40/point at danger 1.0 -- a Queen trick prices to
  -520, enough to flip Tier-2 aggression when the game is on the line).
  Net-negative tricks (Jack of Diamonds outweighing the points beside it)
  are exempt -- those SUBTRACT points, which a seat near 100 wants more of.
- *Moon caution:* `shouldAttemptMoonShot` adds up to +14 threshold as MY
  total nears the target, on top of (never instead of) the clamped
  standings term -- a failed attempt banks real points, which the
  relative-standings term alone couldn't see.
- *Push them over / keep them alive:* a void discard landing on the seat
  closest to 100 gets a bonus -- but ONLY from the current leader (user
  correction: ending the game only benefits whoever is winning right now).
  Every other seat gets the mirror-image penalty: a trailing seat should
  not be the one who ends a game it might still climb out of.

**2. Attack the leader (SOCIAL, user request):** any seat not currently in
1st gets a bonus (+150 Queen / +60 Heart, same magnitudes as the END_GAME
pair on purpose) for a void discard onto a trick the standings leader is
currently winning. Active from hand 1, unlike END_GAME.

**3. Graded Jack-of-Diamonds giveaway (user request):** when shedding the
Jack is already forced, the penalty now grades by who is about to receive
it -- harsher (-500) feeding a seat ahead of me (stretches the gap I must
close), lighter (-300) feeding a seat behind me. Only in the
already-determined-recipient case; the beats-table-but-unproven branch has
no specific seat to grade against.

**4. Moon-stopper exception (user refinement):** "deny the shooting leader
points -- UNLESS you hold a stopper." `holdsMoonStopper()`: a Heart
outranking every Heart still at large is a provable stopper (conservative:
the shooter's unseen Hearts count as at large, so a King is never trusted
while the Ace is unaccounted). Without it, both targeting scorers return 0
against a shooting target (belt-and-suspenders atop the -1400/-900
deny-feed penalties, which already dominate the +150/+60 bonuses -- but
the explicit guard survives future personality rows zeroing MOON_DEFENSE).
WITH it, denial inverts into a trap: deny-feed waives its flat penalty for
the Queen and sub-stopper Hearts (lowest-first ordering retained, full
penalty stays on the stopper itself), and the targeting bonuses actively
load the shooter up -- everything they collect lands for real when the
stopper breaks the shot. Also, feeding a shooting near-100 seat never
"pushes them over" at all -- a completed moon takes them 26 the OTHER way,
so the END_GAME push scorer is guarded identically.

Committed tests: new `EndGameTest` (16) and `MoonStopperTest` (10), plus
two new `MoonThresholdTest` scenarios pinning the danger term against the
standings floor (the old "meTotal=100 is just maximally desperate" fixture
became "maximally desperate AND maximally dangerous" -- the documented
semantic change). Full suite: 8 suites, 125 checks, 0 failed.

Found the day after the standings-clamp fix below, in live play, sitting
underneath it.

**1. Heart control was a bonus, not a precondition (commit `bde1b20`).** NORTH
committed to a moon holding `3H 7H JH` -- no Ace, King or Queen of Hearts --
led AS/KS/QS to pull Spades, deliberately took the Queen, then lost the first
Heart trick to EAST's KH and finished **+14**. `moonShotHandQuality()`
awarded `+2` per Heart honour and nothing else, leaving the score dominated
by Spades: that hand scored 18, of which 16 came from Spades and 2 from the
lone Jack. A hand with AS/KS and six Spades scores ~24 with *zero* Hearts.
The rule the score was missing is that **you cannot win a trick in a suit you
are void in**, so taking all 13 Hearts needs enough Hearts to win each
Heart-led round. `hasMoonHeartControl()` is now a gate consulted before the
score and before the threshold arithmetic (Ace plus `MOON_MIN_HEART_COUNT` =
4, or King plus `MOON_MIN_HEART_COUNT_WITHOUT_ACE` = 5), so no discount can
reach past it. Replays: 1 decision changes in the 7-hand log, 7 in the
15-hand log (all `true -> false`, one seat had committed holding a single
Heart), and the genuine quality-21 moon that succeeded is preserved. A seat
at 93 points was separately caught considering a moon with **no Hearts at
all** -- quality 19 against a floored threshold of 16, which the clamp alone
did not stop.

**2. Heart length went entirely unscored (commit `6a58be6`).** The honour
loop could not tell a three-card holding from a seven-card one with the same
top cards. `HEART_LENGTH_CREDIT` = 2 per Heart beyond `MOON_MIN_HEART_COUNT`.
Because the gate runs first, only hands already holding real Heart control
can collect it. Across all four game logs (45 hands) there are 4 surviving
commitments and none on a heartless hand; on the 23 hands already decided
under the gate it is completely inert.

**Rejected: gating that credit on Spade control.** The instinct was that
long Hearts without the Ace/King/Queen of Spades cannot take the Queen, so
cannot shoot. Wrong, per the user: **a shooter does not have to WIN the
Queen** -- a seat void in Spades can discard her onto a trick the shooter is
already taking with a Heart, and a long Heart suit is itself what supplies
the tricks she can land on. Gating on Spade control would suppress exactly
the hands best placed to receive her. Confirmed in a real hand: NORTH,
committed, led the 7 of Hearts; East and West were void and threw Diamonds;
SOUTH discarded the Queen onto it; NORTH won with the 7 and completed the
shot for -26, never having won a Spade trick.

**3. The HAND cell counted cards, not hands (commit `5de556d`).** `hand_cnt++`
lived inside `jbPlayCard`'s handler, so it advanced once per card played --
the cell read **120** on the tenth hand. Moved to the hand-start path in
`jbStartHand`; `startNewGame()` re-seeds it to 0. Display-only; nothing reads
`hand_cnt`. Verified over a full 13-hand game played to `GAME OVER`,
finishing correctly on 13.

Verified by `MoonGateCheck` (replays every dealt hand in a game log at that
hand's real standings) and `MoonGatePositive` (direct cases at level
standings), neither committed at the time (per the old convention -- see the
2026-08-15 note in `TODO.md`/this changelog's tooling entries about that
convention being retired).

---

## 2026-08-10 -- IMPLEMENTED (commit `c61b342`): keyboard card selection

`Hearts_JFrame.setupKeyboardCardSelection()` (defined at line 638, wired at
the end of the constructor) implements the scheme below on top of
`Update_South_Hand_Legality()`'s existing legality filtering.

Original design, from 2026-07-30 -- an alternative to mouse-clicking South's
hand-card buttons:
- **Rank keys:** `2`-`9` play that rank directly; `1` stands in for `10`
  (single-digit key needed); `j`/`q`/`k`/`a` for Jack/Queen/King/Ace.
- **Disambiguation when void in the led suit:** if South doesn't hold the led
  suit and pressing a rank key matches more than one card (same rank,
  different suits), gray out every other card down to just that rank's
  matches, then read the next keypress as a suit letter (`h`/`d`/`c`/`s`) to
  pick the exact card.
- **Arrow-key navigation while following suit:** Up = highest card of the led
  suit in hand; Down = lowest card of the led suit in hand; Right = the
  cheapest card in hand that still beats the current highest card on the
  table; Left = the card one step below whatever's currently
  selected/highlighted.

---

## 2026-08-09 -- RULES AUDIT: engine verified sound, 5 defects found and fixed

A full audit of the rules/scoring engine, done by reconstructing all 10 hands
of a real session log (dealt hands + pass exchanges) and replaying every
trick against the rules rather than only reading the code.

**Verified sound -- empirically, not by inspection:**

| Check | Result |
|---|---|
| Trick-winner determination | 124/124 correct |
| Point conservation per hand | 9/9 hands sum to exactly 16 (26 - 10 for the Jack) |
| Follow-suit compliance | 0 violations across 98 fully-verified tricks |
| Hearts-broken lead restriction | 0 violations |
| Forced 2 of Clubs opening lead | correct on all 10 hands |
| No points on trick 1 | 0 violations |
| Every card played was actually in hand | 0 phantom cards |

That last check is also a useful negative result for the "South's cards
disappearing" bug in `TODO.md`: across 98 tricks there was **no logic-level
hand desync**, which further supports it being purely a rendering fault.

Pass-direction rotation (LEFT->RIGHT->ACROSS->KEEP), `Find_Lead()` running
*after* passing, and dealer rotation were all confirmed correct too.

**Defects found and fixed the same day:**

1. **`attempting_moon_shot` was never refreshed on KEEP hands.** The flag's
   only writer was `chooseCardsToPass()`, which sits behind `passCards()`'s
   `direction == KEEP` early return -- and on a KEEP hand `Hearts_JFrame`
   never called `passCards()` at all. `Player.java`'s comment asserted the
   opposite invariant ("unconditionally overwrites it for every seat at the
   start of every hand"); that was false for every 4th hand. Confirmed from
   the log: 10 hands played, only 8 DEAL blocks, and the two missing ones
   were exactly the KEEP hands. Consequences were (a) no seat could ever
   *newly* commit to a moon shot on a KEEP hand -- moon offense silently
   disabled on 25% of hands, and (b) a seat that committed on the previous
   hand carried a stale `true` in, where `AI.isMoonShotAlive()` reads it as
   live (no point card captured yet => `inProgressMoonShooter()` is null =>
   "alive"), putting it in MOON_SHOT PlayMode with its 1000-weight scorers on
   a hand it never evaluated. Fixed by extracting the direction-independent
   start-of-hand work into a new `Deal_Hand.beginHand()`, called from
   `beginPassingPhase()` -- the one place both hand-start paths pass through
   for every direction.
2. **South's flag was never set at all**, since the human's cards bypass
   `chooseCardsToPass()`. Now explicitly cleared every hand by `beginHand()`.
   A human's intent is unknowable so there is nothing honest to evaluate --
   but it must not be left holding the previous hand's value.
3. **The game had no end condition.** No target score existed anywhere;
   totals accumulated forever and no winner was ever declared. Added
   `TARGET_SCORE = 100` with `checkForGameOver()` (lowest total wins, ties
   reported as ties) and `startNewGame()`, with "Start Hand" doubling as "New
   Game" once the game ends.
4. **KEEP hands logged nothing**, because the DEAL/PASS logging sat behind
   the same early return -- 26 of 124 tricks were unauditable as a direct
   result. Fixed with the same extraction as defect 1.
5. **Nothing logged the moon-shot commitment**, which is precisely why
   defect 1 was invisible in a game log. `beginHand()` now emits a
   `MOON <seat> attempting=<bool> quality=<n>` line per seat per hand.

Verified at the time by two scratch suites (not committed, per the old
convention -- see the 2026-08-15 tooling note below): `KeepHandCheck` (5
scenarios) and `GameOverCheck` (11 scenarios, driving the real
`Hearts_JFrame` by reflection). Plus a live launch confirming the real
startup path fires both hooks with no exceptions.

---

## 2026-08-09 -- FIXED: the moon-shot threshold's standings term was unbounded

Found by the first full game ever played to completion (15 hands, human won
1-101). The `MOON` logging added the same day (see the rules audit above) is
what made it visible at all.

`shouldAttemptMoonShot()` adjusted its threshold by
`STANDING_SENSITIVITY * standingDelta` with no bound. In a game to 100,
`standingDelta` routinely reaches 50-76, so the standings term (25-38)
completely swamped the base threshold (26). Observed on a plain **NORMAL**
seat (the user has never changed a seat off Normal):

| hand | W total | threshold | quality | committed |
|---|---|---|---|---|
| 1 | 0 | 26.0 | 17 | no |
| 6 | 40 | 12.7 | 13 | yes |
| 8 | 76 | -6.0 | 21 | yes |
| 13 | 60 | 0.7 | 5 | yes |
| 15 | 87 | -12.0 | 9 | yes |

Two failures, not one:
- **The threshold went negative**, i.e. below the minimum achievable hand
  quality, so `moonShotHandQuality()` became dead code and the seat committed
  blind.
- **The behaviour inverted**: it declined a quality-17 hand while level, then
  committed on a quality-5 hand once behind. Backwards -- you shoot when your
  CARDS justify it; standings should be a tiebreaker, not the dominant term.

It was also self-reinforcing (fall behind -> commit on hopeless hands ->
take 15-20 points -> fall further behind). That seat committed in 8 of 15
hands, went 2-for-8, and the six failures are most of how it reached 101.

**Fix:** `MAX_STANDING_DISCOUNT = 10` clamps the standings adjustment in BOTH
directions, and `MIN_THRESHOLD = 12` floors the result so no seat ever stops
reading its own cards. Replaying the same 15 hands against the real
qualities and standings: **8 commitments become 2**, and the two kept are
exactly the quality-21 hands -- including the successful moon shot. Of the
six dropped, five were failures; the one dropped success was a lucky
quality-8 hand.

Effective ranges now: NORMAL/AGGRESSIVE 26 down to 16; MOON_SHOOTER 14 down
to 12 (still far more eager than NORMAL, as intended); a comfortable leader
tops out at 36, correctly meaning it declines even a monster hand rather than
risking a lead it does not need to risk.

Verified at the time by `MoonThresholdCheck` (8 scenarios, not committed per
the old convention).

---

## 2026-08-09 -- REJECTED (2026-08-05 spec), CORRECTED + IMPLEMENTED: standings-aware defensive passing

Original idea from 2026-08-05 (found via live playthrough, the user shot the
moon twice in a handful of test hands) proposed that a passer should
**construct the recipient's hypothetical resulting hand** (their current 10
cards + the 3 about to arrive) and run it through
`HandEvaluator.moonShotHandQuality()` before finalizing a pass.

**That premise is wrong and was rejected by the user on 2026-08-09: a passer
can only ever see their own hand and the recipient's PUBLIC standing (running
point total) -- never the recipient's other cards.** `Deal_Hand.java`
already had this principle stated in a comment on `withholdMoonFuel()` ("this
reasons ONLY about what is being given away, never about the recipient's
hand, which a passer cannot see") -- the 2026-08-05 spec simply predates and
contradicts it. Recorded here so a future session doesn't propose the
hand-visibility version again.

**What was actually built instead**, per the user's corrected framing
("passing should focus on the recipient's standing, and if I'm leading I want
to slow down whoever is closest to me"): `dangerScore()`'s Spade-control
branch (Ace/King of Spades, unprotected) is now standings-aware via two new
`Deal_Hand.java` helpers, `isDefensiveCautionRecipient()`/
`isRelaxedRecipient()` (plus `seatInSecondPlace()`/`closestRivalTo()`),
generalizing `jackOfDiamondsPassScore()`'s already-proven leader/trailer
pattern:
- Recipient is the leader, the runner-up, OR (when the PASSER is leading) the
  passer's own closest rival -> reduced eagerness to pass Spade control (12
  -> 6) -- still passable if nothing better exists, just no longer preferred.
- Recipient is already in last place -> nudged up (12 -> 14) -- strengthening
  a hopeless seat costs nothing.
- Deliberately NOT applied to the Queen of Spades or high Hearts -- those are
  mandatory-to-shed regardless of recipient, so recipient identity should
  nudge discretionary cards, not override cards that must go either way.

**Explicitly deferred, not built:** a positive incentive to arm a contender
(e.g. the runner-up) specifically to help them fight the leader. Speculative
multiplayer-alliance heuristic with no proven value yet.

---

## 2026-08-07 -- IMPLEMENTED: per-type tactic gating and weighting (skill tiers)

`TacticWeights.java` holds the weight matrix exactly as designed; each scorer
in `AICardScorer.java` declares its `Tactic` via a `tactic()` override,
`pickLead()`/`pickFollow()` multiply by the acting player's weight,
`HandEvaluator.shouldAttemptMoonShot()` gates the attempt by `MOON_OFFENSE`
weight, and `Deal_Hand.chooseCardsToPass()` sends Brain_Dead/Bad down naive
passing paths. Verified at the time by the `WeightCheck` scratch suite (10
scenarios where the same position produces different plays per type). The
finer passing nuances (Avoids_Queen up-weighting the Queen rules,
Moon_Shooter keeping its own kit) remain future refinements -- see
`TODO.md`.

Requested 2026-08-07 ("figure out what each type of AI player uses in the
various game dynamics -- all of these tactics will not be available to the
players that aren't that good; Aggressive and moon-shooter types will ignore
some of the tactics or not value them as much"). This was deferred earlier
("all AI players can use everything for now") and then designed; the
`Counts_cards` trait was always the intended hook for the counted tactics
(see `TODO.md` for its current orphan status).

**Mechanism (fits the scored-candidate architecture directly):** give every
scorer a `Tactic` tag (enum in `AICardScorer.java`), and multiply each
scorer's contribution by `weight(player, tactic)` -- a per-type multiplier
table. `0` = the player doesn't know the tactic exists, `0.5` = knows it but
undervalues it, `1` = full use, `1.5` = overvalues it (personality flavor).
Because everything is additive scoring, a weight is one multiplication -- no
branch surgery, and it is EXACTLY the ranked-trait-mixture foundation the
roster system in `TODO.md` needs (a personality = its own weight row). The
passing phase gets the same treatment in `Deal_Hand.java` (gate
`withholdMoonFuel`, the Queen-right rule, the Jack pass rules, the spade
floor by the passer's weights).

**The matrix (v1):**

| Tactic family | Brain_Dead | Bad | Cautious | Normal | Aggressive | Avoids_Queen | Moon_Shooter |
|---|---|---|---|---|---|---|---|
| Basic safety (duck, graded lead dangers) | bypass | 1* | 1 | 1 | 0.5 (ducks less) | 1 | 1 |
| Void awareness (avoid/exploit known voids) | bypass | 0 | 1 | 1 | 1 | 1 | 1 |
| Jack guard (never gift the J-diamonds) | bypass | 0 | 1 | 1 | 1 | 1 | 1 |
| Jack hunt (pull leads, capture, counted lead) | bypass | 0 | 0 | 1 | 1.5 | 0.5 | 0.5 |
| Counted certainties (Q lead, safe unload, boss-heart grading) | bypass | 0 | 0.5 (safe unload only) | 1 | 1 | 1 | 1 |
| Queen flush (draw her out via a void) | bypass | 0 | 0 | 1 | 1 | 1.5 | 0 |
| Moon offense (attempt + moon-mode play) | bypass | 0 | 0 (never shoots) | 0.5 (rare) | 0.5 | 0 | 1.5 (eager) |
| Moon defense (redirect, withhold, take) | bypass | 0 | 1 | 1 | 1 | 1 | 1 |
| Passing craft (Q-right, spade floor, kit withholding, Jack rules) | bypass | 0 (passes naively) | 1 | 1 | 1 | 1 (Q rules up-weighted) | 1 (keeps own kit) |

\* Bad keeps basic safety but retains its existing 1-in-3 random blunder.
Brain_Dead keeps its existing full bypass (first legal card). The combo type
AGGRESSIVE_AVOIDS_QUEEN takes the max of its two parent columns per row.
Moon offense weight also scales the ATTEMPT threshold in
`HandEvaluator`/`Deal_Hand` (an eager shooter tries on a marginal hand, a
0-weight type never tries), not just in-hand play.

For the "future tactic ideas surfaced during this same review" list (mid-hand
moon adoption, exit-card preservation, etc.), see `TODO.md` -- those were
never built.

---

## 2026-08-07 -- IMPLEMENTED: card counting and the three counted-out tactics

`CardCounter.java` provides the deduction `VoidTracker` deliberately doesn't:
which individual ranks of a suit are still unaccounted for. It counts only
what a player could legitimately know -- cards in completed tricks (the
union of every seat's `won_cards`), cards face up in the trick in progress,
and the player's own cards. Everything else is, by elimination, in an
opponent's hand.

**Note on "own cards":** callers pass their legal-options list, since that is
all `AI.java` ever receives. That is sound for the suits these tactics reason
about -- `legalLeads()` only filters out Hearts (or forces the 2 of Clubs on
trick one), so a lead's options contain every Spade and Diamond held; and
when following, the options are exactly the player's cards of the led suit.

Three tactics built on it, all available to **every** AI player (gated by
`Player_Type` via `TacticWeights.COUNTED_CERTAINTIES`, not by the unused
`Counts_cards` trait -- see `TODO.md`):

1. **Counted-out Queen of Spades lead.** Fires only when every Spade below
   the Queen is accounted for AND at least one of the King/Ace is still out
   -- if both are already gone, nobody can beat the Queen and leading it
   wins your own trick for +13, the exact disaster the tactic exists to
   avoid. The underlying insight: once every non-Queen/Ace/King Spade is
   accounted for, whoever holds the Ace/King has no lower Spade left to duck
   with, and `Rules` forces following suit -- so the Queen lands on THEM
   instead of staying stuck waiting for a safe moment that may never come.
2. **Counted-out Jack of Diamonds capture**, on both lead and follow. Plays
   the Jack once the trick is provably won -- nothing outranks it, or nobody
   left can follow. On the follow side it also checks the table, since a
   higher Diamond already face up means the -10 goes to somebody else. Two
   sufficient conditions: all of Ace/King/Queen of Diamonds already played,
   or every other player already known-void in Diamonds (via `VoidTracker`).
3. **Safe high-Spade unload.** Sheds the King/Ace of Spades once the Queen
   provably cannot land on the trick: it has already been played, or it's in
   my own hand, or (lead side only) every opponent is void in Spades.
   Deliberate asymmetry: the all-opponents-void route is accepted when
   leading but refused when following, since void seats discard onto a
   trick the King/Ace is about to win anyway (Hearts included) -- not worth
   volunteering to collect them.

Verified at the time by 7 scenarios, weighted toward the cases where firing
would be a blunder.

---

## 2026-08-06 -- IMPLEMENTED: standings-aware moon-shot-stopping

Built in `MoonThreat.java` plus three new scorers in `AICardScorer.java`.
Prompted by a real lost hand: North shot the moon (-36, including the Jack of
Diamonds) and it looked unstoppable, but replaying the log showed it was not.
East, void, discarded the Queen of Spades on trick 2 and the **Ace of
Hearts** on trick 4 -- while holding five harmless alternatives. The Ace of
Hearts was the one card that guaranteed the shot could be broken. The old
void-discard rule sheds the HIGHEST Heart first -- correct ordinary Hearts
play, exactly wrong here.

What was built (priority order, cheapest/safest first):
1. **Don't feed the shooter.** While void with a visible, threatening
   shooter who can still win the trick, any safe card outscores every point
   card. The Queen is penalised harder than Hearts -- a shooter needs the
   Queen AND all thirteen Hearts, so the Queen is a single card the defender
   controls outright. Among Hearts the ranking inverts to lowest-first (keep
   the Ace, don't throw it).
2. **Take a trick to break the shot**, available to every seat, not just
   `Aggressive`. Only fires on tricks that actually carry points.
3. **Redirect by leading a suit the shooter is void in** (via `VoidTracker`),
   the cheapest tactic since it costs no points at all.

Taking the Queen deliberately stays the last resort and defers to
`MoonThreat.queenDenialWorthIt()`, which checks how +13 lands across all four
players -- stopping the moon is no victory if it hands a third player the
lead. Threat itself is a standings question, not a personality one (reuses
`AI.safelyAheadOfShooter`): a seat comfortably ahead of the shooter spends
nothing stopping them.

This was originally scoped narrower ("only fires for `Aggressive`, purely
reactive") -- the corrected requirements (any trait should engage when
threatened; priority order redirect > take a Heart > take the Queen, with
real cost/benefit analysis on the last one across all four players' current
standings, not just the me-vs-shooter pair) are what's described above.
`Need_to_Stop_Shooter` (`Player.java`) was considered as the tuning hook but
the standings-based gate above was built instead, since threat is
standings-driven, not trait-driven.

Verified at the time with a scratch harness replaying the exact trick-4
position (East now discards the King of Spades and keeps the Ace of Hearts)
plus three regression scenarios.

---

## 2026-08-06 -- RESOLVED: computer seats were hardcoded, hiding all AI work

Worth recording because it invalidated several rounds of play-testing. North
was permanently `Brain_Dead` and East permanently `Bad`
(`Set_Player(1)`/`(2)` in `Hearts_JFrame`'s constructor, never changed
after). `Brain_Dead` returns `legalOptions.get(0)` as the very first line of
`AI.chooseLead()`/`chooseFollow()`, so **North never reached a single line of
strategy code** -- no rules-aware lead choice, no moon-shot logic, no
Jack-of-Diamonds handling, nothing. Hands sort Hearts->Spades->Diamonds->
Clubs ascending, so before hearts are broken North's first legal card is its
lowest Spade: if its Spades started at the Queen, it led the Queen of Spades,
every single time. Hearts piling up in its won pile looked like a failed
moon-shot attempt but was simply the absence of any avoidance logic at all.

Two genuine AI bugs were found and fixed while chasing this (the moon-shot
abort missing on the lead path, and the baseline lead volunteering
Hearts / force-leading a self-held Queen) -- both real, both worth keeping,
but neither was what was actually being observed on North, because North
never executed either code path. The lesson: check which traits a seat
actually has before diagnosing its behaviour as a strategy bug.

Fixed by making each computer seat's type selectable from a dropdown
(`Player.Player_Type`, read at the start of every deal via
`applySelectedPlayerTypes()`), defaulting all three seats to `Normal`.

---

## 2026-07-31 -- IMPLEMENTED: deciding when an AI attempts to shoot the moon

`HandEvaluator.shouldAttemptMoonShot()` implements this:
`moonShotHandQuality()` scores Spade control, a long high suit, Heart
strength and an isolated-short-suit penalty; the threshold is lowered by the
`Shooting_the_Moon` trait, raised by a sub-1 `MOON_OFFENSE` weight (0 never
commits), and shifted by `Scoring.standingDelta()` so a trailing player
gambles more readily. It is a one-time commitment recorded in
`Player.attempting_moon_shot` during `beginHand()`, with the abort condition
handled by `AI.isMoonShotAlive()`.

Design, as originally specified 2026-07-31:
- **One-time commitment at hand-start, not a running re-evaluation.**
  Evaluate the dealt hand once (after passing), decide whether it's worth
  attempting, and if so, stay committed until either it succeeds or gets
  broken by someone else. Don't re-roll the decision every trick.
- **Hand-quality signals**, roughly in order of importance: (1) control of
  Spades (Ace/King, so the player is never forced into taking the Queen
  before ready), (2) a long, runnable suit (5+ cards topped by high cards),
  (3) strength in Hearts itself (need to actually WIN Heart tricks
  eventually), (4) few isolated low cards in short suits (force early,
  awkward discards).
- **Per-player tolerance dial** -- combine the above into a numeric "hand
  quality" score, compare against a per-player threshold.
- **Critical abort condition:** the instant any OTHER player captures a
  Heart or the Queen of Spades, the attempt is dead -- reuses (inverted) the
  same `Scoring.inProgressMoonShooter()` check built for the stopping side.

**Amended 2026-08-09/10 by three live-play fixes** -- see those entries
above for the evidence. The standings term is now clamped
(`MAX_STANDING_DISCOUNT`, `MIN_THRESHOLD`); `hasMoonHeartControl()` is a hard
precondition checked BEFORE the score and before any threshold arithmetic;
and `HEART_LENGTH_CREDIT` now scores Heart length, which the original four
signals did not distinguish at all.

---

## 2026-08-15 -- tooling note: scratch suites retired in favor of committed tests

Every fix above from 2026-08-06 through 2026-08-10 was verified at the time
by a scratch test harness (`KeepHandCheck`, `GameOverCheck`,
`MoonThresholdCheck`, `MoonGateCheck`, `MoonGatePositive`, `WeightCheck`, and
a 400,000-decision equivalence harness for the 2026-08-06 scored-candidate
refactor) that was deliberately **not committed**, per the project's
convention at the time. Each one proved its fix and then stopped existing,
which meant later changes to the same logic (the moon threshold alone was
fixed three times in five days) had nothing cheap to re-run against.

As of 2026-08-15 this convention is retired. Regression coverage for the
scenarios above now lives in committed test classes under `test/hearts_1/`
(see `AllTests.java` for the full list), and a committed `LogReplayer` tool
(`src/hearts_1/tools/LogReplayer.java`) formalizes the 2026-08-09 rules-audit
method -- reconstruct hands from a session log, replay every trick against
`Rules`/`Scoring`, report violations -- so any future live-play session log
can be fed through it as a free regression check, the same way the
2026-08-09 audit was done by hand.
