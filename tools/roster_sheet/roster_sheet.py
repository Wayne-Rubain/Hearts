"""Builds the Hearts_2 AI player roster worksheet (draft for Wayne to fill in)."""
import sys
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

PLAYERS = ["AI-%02d" % i for i in range(1, 11)]

TEMPLATES = ["NORMAL", "CAUTIOUS", "AGGRESSIVE", "AVOIDS_QUEEN", "AGGRESSIVE_AVOIDS_QUEEN",
             "MOON_SHOOTER", "BAD", "BRAIN_DEAD"]
OPTIONS = {
    "KIT_PASS": ["BLOCKER", "FREE", "HOLD_BACK"],
    "LOW_HEARTS": ["KEEP_TWO", "BY_HEIGHT", "KEEP_LOW", "PASS_FREELY"],
    "SIDE_SUITS": ["BY_HEIGHT", "FLAT"],
    "VOID_HUNT": ["WHEN_SHORT_ON_SPADES", "NEVER"],
    "SPADE_CONTROL": ["TO_LEADER", "ANYONE", "SHIELD_LEADER"],
    "QUEEN_KEEP": ["FOUR_LOW_SPADES", "FIVE_LOW_SPADES", "NEVER"],
    "HEART_INSURANCE": ["OFF", "ON"],
    "MOON_ALARM": ["FIRST_HEART", "FOUR_HEARTS", "QUEEN_AND_HEART", "FOUR_OR_QUEEN",
                   "SURE_WINNER_SAME", "SURE_WINNER_ANY", "KEPT_LEAD", "SHOOTER_READ"],
    "QUEEN_PHOBIA": ["OFF", "PASS_HIGH_SPADES", "FISH", "BOTH"],
    "JACK_CHASE": ["OFF", "KEEP_DIAMONDS", "PRESSURE", "BOTH"],
}

# Each row: (label, roster key or None, kind, list of 10 values, guide text, default)
# kind: "text", "pct", "list:<name>", "num", "section"
D = None  # default marker in the proposal

ROWS = [
    ("IDENTITY", None, "section", None, None, None),
    ("Player name", None, "text", [""] * 10,
     "The player's real name, to be chosen. Blank for now.", ""),
    ("Character", None, "text",
     ["Beginner", "Bonehead", "Steady", "Jack Hunter", "Moon Chaser", "Queen-phobic",
      "Wildcard", "Late Bloomer", "Careful Counter", "Shark"],
     "One or two words: who this player is at the table.", ""),
    ("Skill tier", None, "list:tier",
     ["Low", "Low", "Middle", "Middle", "Middle", "Middle", "Middle", "Middle (grows)",
      "Expert", "Expert"],
     "Low / Middle / Expert. Several experts, each a DIFFERENT mixture (user, 10/11).", ""),
    ("How they play", None, "text",
     ["Knows the rules, not the craft. Passes Hearts, keeps high cards, misses moons.",
      "Plays almost at random. Fun to beat, dangerous by accident.",
      "By the book. Solid, predictable, rarely wild.",
      "Lives for the Jack of Diamonds; plans the whole hand around it.",
      "Shoots the moon whenever it looks possible; passes kits freely.",
      "Never holds the Queen; dumps Spade control early.",
      "Different every hand. Moody, sometimes brilliant, sometimes reckless.",
      "Starts middling and improves the most with experience.",
      "Counts cards, reads the table, reads moons early.",
      "Strong, aggressive at times, holds grudges, watches who shoots."],
     "Short description for the stats screen and for us.", ""),

    ("BASE", None, "section", None, None, None),
    ("Template", "template", "list:template",
     ["NORMAL", "BAD", "NORMAL", "NORMAL", "MOON_SHOOTER", "AVOIDS_QUEEN", "NORMAL", "NORMAL",
      "CAUTIOUS", "AGGRESSIVE"],
     "Starting preset. Everything below overrides it. NORMAL / CAUTIOUS / AGGRESSIVE / "
     "AVOIDS_QUEEN / AGGRESSIVE_AVOIDS_QUEEN / MOON_SHOOTER / BAD (random 1 card in 3) / "
     "BRAIN_DEAD (no strategy).", "NORMAL"),
    ("Level (1-5)", "level", "pct",
     [1, 1, 4, 3, 3, 3, 2, 2, 5, 5],
     "How much reasoning the player does: 1 = beginner (panics, e.g. grabs a point trick the "
     "moment it fears a moon) .. 5 = expert (checks whether a play is needed at all). "
     "Seeded from the measured strength order.", "3"),
    ("Aggressive %", "style.Aggressive", "pct",
     [0, 0, 0, 20, 30, 0, 40, 10, 0, 30],
     "Hands in 100 the player plays aggressively (takes tricks to control play). "
     "Measured: Aggressive 50% mood = 16.5% won vs 23.9% (costly).", "by template"),
    ("Avoids Queen %", "style.Avoids_Queen", "pct",
     [0, 0, 0, 0, 0, 100, 0, 0, 0, 0],
     "Hands in 100 the player won't trust Spade protection with the Queen.", "by template"),
    ("Moon shooter %", "style.Shooting_the_Moon", "pct",
     [0, 0, 0, 0, 100, 0, 20, 0, 0, 10],
     "Hands in 100 the player has the lowered bar for trying a moon.", "by template"),
    ("Counts cards %", "countCardsPercent", "pct",
     [0, 0, 40, 50, 30, 30, 30, 20, 90, 80],
     "Hands in 100 the player counts cards (unlocks the endgame solver and certainty plays).", "0"),
    ("Moon gamble %", "moonGamblePercent", "pct",
     [0, 10, 5, 5, 40, 0, 25, 10, 5, 15],
     "Hands in 100 it will pull the Ace of Hearts out to try a moon; rises when trailing."
     "  [Audit 11/05: NO EFFECT: changes play in 0-2 of 30 games.]", "0"),
    ("Moon interest %", "moonInterestPercent", "pct",
     [""] * 10,
     "Hands in 100 it gets interested in a moon shot even when the hand would not normally "
     "qualify; doubles as it trails the leader by up to 30 points. Blank = the level default: "
     "22 / 8 / 3 / 1 / 1 for levels 1 to 5.", "by level"),
    ("Moon look %", "moonLook", "pct",
     [""] * 10,
     "Hands in 100 the player reads its hand like an optimistic real player (2026-11-08, from "
     "Wayne's own tries): passes its low Hearts, keeps the top ones, runs a long side suit "
     "headed by the Ace, and goes for the moon when the cards that come back make it. "
     "Blank = 0 = never. Measured 11/08: 50 on every player = about 15 tries per 100 hands "
     "(today 2.5 to 3.5 for most), made about 1 time in 7.", "0"),
    ("Moon optimism", "moonOptimism", "pct",
     [""] * 10,
     "With Moon look on, how remote a chance still gets a try, 0 to 100: 0 = only strong "
     "hands, 50 = about Wayne's own bar, 100 = very optimistic. Does nothing while Moon look "
     "is blank or 0.", "50"),
    ("Moon heart last", "moonHeartLast", "pct",
     [""] * 10,
     "With Moon look on and the A K of Hearts plus one more Heart: hands in 100 the player "
     "plays the extra Heart LAST, Wayne's way (side-suit winners, then the A K late, then the "
     "extra Heart). Otherwise it leads the extra Heart early to test it. Blank = 100. "
     "Measured 11/08 on Wayne's A K 10 deal: testing early made 44 moons in 120, last 28.", "100"),
    ("Blunder %", "blunder", "pct",
     [15, 0, 3, 5, 6, 5, 8, 8, 1, 2],
     "Chance per card of a near-miss (2nd or 3rd best card). Bonehead's BAD template already "
     "plays randomly 1 card in 3. Measured: 10% = 17.9% won, 33% = 5.6% vs 23.9%."
     "  [Audit 11/05: levels 4 and 5 are capped at 2% and 1%, so higher values act as the cap.]", "0"),
    ("Queen payback", "payback", "text",
     ["off", "off", "off", "on", "off", "off", "on", "off", "off", "100,80,50"],
     "Grudge against whoever stuck them with the Queen: off / on (taper by type) / three "
     "numbers for the next three hands. Measured neutral."
     "  [Audit 11/05: NO EFFECT: on/off changes play in 0-2 of 30 games.]", "off"),

    ("SKILL RELIABILITY  (how often the skill is used, 0-100, rolled each hand)", None, "section",
     None, None, None),
    ("SAFETY", "reliable.SAFETY", "pct",
     [70, 50, 95, 90, 85, 95, 80, 80, 100, 100],
     "Basic safety and void awareness. Measured 10/11: off = 19.2% won vs 25.3% (vital).", "100"),
    ("JACK", "reliable.JACK", "pct",
     [40, 30, 90, 100, 80, 85, 70, 70, 100, 100],
     "Guarding and hunting the Jack of Diamonds. Measured 10/11: off = 11.7% (the most vital).", "100"),
    ("QUEEN", "reliable.QUEEN", "pct",
     [50, 30, 90, 85, 80, 100, 70, 75, 100, 100],
     "Flushing the Queen of Spades out. Measured 10/11: off = 25.8% (no measurable effect).", "100"),
    ("MOON", "reliable.MOON", "pct",
     [30, 20, 85, 80, 100, 80, 70, 70, 100, 100],
     "Moon offense and defense. Measured 10/11: off = 24.0% (small help; was a cost before the alarm work).", "100"),
    ("PASSING", "reliable.PASSING", "pct",
     [40, 20, 95, 90, 90, 95, 75, 75, 100, 100],
     "Passing craft. 0 = 'pass your three highest cards'. Measured 10/11: off = 21.5% (now helps; was 43.7% before the passing rework).", "100"),
    ("STANDINGS", "reliable.STANDINGS", "pct",
     [20, 10, 80, 70, 60, 75, 60, 60, 100, 100],
     "End-game and social (attack the leader) play. Measured 10/11: off = 25.3% (no measurable effect)."
     "  [Audit 11/05: NO EFFECT: 50 changes play in 0-2 of 30 games.]",
     "100"),
    ("Weight overrides", "weight.<FAMILY>", "text",
     ["", "MOON_OFFENSE 0.5", "", "JACK_HUNT 1.5", "MOON_OFFENSE 1.5", "QUEEN_FLUSH 1.5, MOON_OFFENSE 0.4", "", "", "MOON_OFFENSE 0.5", ""],
     "Optional: how much a skill is VALUED, 0-1.5 (1 = normal). Families: BASIC_SAFETY "
     "VOID_AWARENESS JACK_GUARD JACK_HUNT QUEEN_FLUSH MOON_OFFENSE MOON_DEFENSE PASSING_CRAFT "
     "END_GAME SOCIAL COUNTED_CERTAINTIES."
     "  [Audit 11/05: NO EFFECT between 0.5 and 1.5 for JACK_HUNT, MOON_DEFENSE, PASSING_CRAFT, END_GAME, "
     "SOCIAL, COUNTED_CERTAINTIES, and QUEEN_FLUSH 1.5. 0 still switches a family off.]", "1"),

    ("STRATEGY  (which way the player makes each decision)", None, "section", None, None, None),
    ("KIT_PASS", "strategy.KIT_PASS", "list:KIT_PASS",
     ["HOLD_BACK", "FREE", "HOLD_BACK", "BLOCKER", "FREE", "BLOCKER", "FREE", "BLOCKER",
      "HOLD_BACK", "FREE"],
     "Passing a moon kit. HOLD_BACK (default since 10/11): keep the top Heart 30.4%. BLOCKER: "
     "always with a low Heart (your 10/10 rule) 26.1%. FREE: as chosen 32.1%.", "HOLD_BACK"),
    ("LOW_HEARTS", "strategy.LOW_HEARTS", "list:LOW_HEARTS",
     ["PASS_FREELY", "PASS_FREELY", "KEEP_TWO", "KEEP_TWO", "BY_HEIGHT", "KEEP_TWO", "KEEP_LOW",
      "BY_HEIGHT", "KEEP_TWO", "KEEP_TWO"],
     "Low Hearts in the pass. KEEP_TWO (your rule) 26.1% / BY_HEIGHT 25.1% / KEEP_LOW 23.8% / "
     "PASS_FREELY (old leak) 18.2%.", "KEEP_TWO"),
    ("SIDE_SUITS", "strategy.SIDE_SUITS", "list:SIDE_SUITS",
     ["FLAT", "FLAT", "BY_HEIGHT", "BY_HEIGHT", "BY_HEIGHT", "BY_HEIGHT", "BY_HEIGHT", "FLAT",
      "BY_HEIGHT", "BY_HEIGHT"],
     "Clubs/Diamonds in the pass. BY_HEIGHT 26.1% / FLAT 22.4%.", "BY_HEIGHT"),
    ("VOID_HUNT", "strategy.VOID_HUNT", "list:VOID_HUNT",
     ["NEVER", "NEVER", "WHEN_SHORT_ON_SPADES", "WHEN_SHORT_ON_SPADES", "WHEN_SHORT_ON_SPADES",
      "WHEN_SHORT_ON_SPADES", "WHEN_SHORT_ON_SPADES", "NEVER", "WHEN_SHORT_ON_SPADES",
      "WHEN_SHORT_ON_SPADES"],
     "Emptying a suit when short on Spades (your rule). WHEN_SHORT 26.1% / NEVER 22.5%.",
     "WHEN_SHORT_ON_SPADES"),
    ("SPADE_CONTROL", "strategy.SPADE_CONTROL", "list:SPADE_CONTROL",
     ["SHIELD_LEADER", "ANYONE", "TO_LEADER", "TO_LEADER", "ANYONE", "ANYONE", "ANYONE",
      "SHIELD_LEADER", "TO_LEADER", "TO_LEADER"],
     "Who gets an unprotected Ace/King of Spades. All within noise (flavor).", "TO_LEADER"),
    ("QUEEN_KEEP", "strategy.QUEEN_KEEP", "list:QUEEN_KEEP",
     ["FOUR_LOW_SPADES", "FOUR_LOW_SPADES", "FOUR_LOW_SPADES", "FIVE_LOW_SPADES",
      "FOUR_LOW_SPADES", "NEVER", "FOUR_LOW_SPADES", "FIVE_LOW_SPADES", "FOUR_LOW_SPADES",
      "FOUR_LOW_SPADES"],
     "Low Spades needed to keep the Queen. All within noise (flavor).", "FOUR_LOW_SPADES"),
    ("HEART_INSURANCE", "strategy.HEART_INSURANCE", "list:HEART_INSURANCE",
     ["ON", "OFF", "OFF", "OFF", "OFF", "ON", "OFF", "ON", "OFF", "OFF"],
     "Hoard top Hearts before anyone takes a point (the paranoid habit). OFF 26.1% / ON 24.4%.",
     "OFF"),
    ("MOON_ALARM", "strategy.MOON_ALARM", "list:MOON_ALARM",
     ["FIRST_HEART", "FIRST_HEART", "SHOOTER_READ", "FOUR_HEARTS", "KEPT_LEAD", "QUEEN_AND_HEART",
      "SURE_WINNER_ANY", "FIRST_HEART", "SURE_WINNER_SAME", "SHOOTER_READ"],
     "When to defend against a moon. SHOOTER_READ is the default since 10/11. FIRST_HEART 26.1% / FOUR_HEARTS 29.5% / FOUR_OR_QUEEN "
     "28.9% / QUEEN_AND_HEART 28.8% / SHOOTER_READ 28.8% / SURE_WINNER_SAME 28.7% (your read) / "
     "SURE_WINNER_ANY 28.5% / KEPT_LEAD 28.2%."
     "  [Audit 11/05: plays as two behaviors: FIRST_HEART, and the other seven, which barely differ.]", "SHOOTER_READ"),

    ("QUEEN_PHOBIA", "strategy.QUEEN_PHOBIA", "list:QUEEN_PHOBIA",
     [""] * 10,
     "Signature (never strayed by wildness). PASS_HIGH_SPADES: passes the Ace/King of Spades like a "
     "high Heart. FISH: leads low Spades to force the Queen out when it holds no Ace, King or Queen "
     "of Spades. BOTH. Blank = OFF.", "OFF"),
    ("JACK_CHASE", "strategy.JACK_CHASE", "list:JACK_CHASE",
     [""] * 10,
     "Signature (never strayed by wildness). KEEP_DIAMONDS: never passes the Ace/King/Queen of "
     "Diamonds. PRESSURE: leads low Diamonds to strip the Jack's cover when it holds a Diamond above "
     "the Jack. BOTH. Blank = OFF.", "OFF"),

    ("WILDNESS  (straying from the usual strategy, hand by hand)", None, "section", None, None, None),
    ("Wildness %", "wildness", "pct",
     [40, 60, 10, 15, 20, 10, 60, 30, 5, 5],
     "Chance per strategy decision per hand of playing it some other way. Measured: "
     "15 = 25.8%, 30 = 23.9%, 60 = 21.9% vs 26.1%.", "0"),
    ("Opening wildness %", "wildness.opening", "pct",
     [0, 0, 10, 0, 0, 0, 0, 10, 25, 20],
     "Added in the first hands of each game (top players feel out the table).", "0"),
    ("Opening hands", "wildness.openingHands", "num",
     [3, 3, 3, 3, 3, 3, 3, 3, 2, 3],
     "How many first hands of each game get the opening wildness (0-20).", "3"),
    ("Wildness when leading %", "wildness.leading", "pct300",
     [50, 100, 50, 50, 75, 50, 100, 50, 25, 50],
     "Percent of wildness while leading the game (50 = half as wild; 0-300).", "50"),
    ("Wildness when last %", "wildness.losing", "pct300",
     [150, 100, 150, 150, 200, 125, 200, 150, 250, 200],
     "Percent of wildness while in last place (150 = half again; 0-300).", "150"),
    ("Stray toward", "stray.<COMPONENT>", "text",
     ["", "", "", "", "KIT_PASS: FREE 3, HOLD_BACK 1", "", "", "", "", "MOON_ALARM: FIRST_HEART 1"],
     "Optional: where it tends to go when it strays, e.g. KIT_PASS: FREE 2, HOLD_BACK 1. "
     "Blank = evenly among the other options.", "blank"),

    ("MOON MEMORY & TABLE TALK  (design only, not built yet)", None, "section", None, None, None),
    ("Observant %", None, "pct",
     [20, 5, 60, 50, 70, 50, 40, 50, 95, 100],
     "How much of what happens at the table the player notices and remembers.", ""),
    ("Memory (games)", None, "num",
     [2, 1, 6, 5, 8, 5, 3, 6, 12, 20],
     "How many games an impression lasts before fading (counted in games, not time).", ""),
    ("Overreacts", None, "list:level",
     ["High", "High", "Low", "Medium", "Medium", "High", "High", "Medium", "Low", "Medium"],
     "How much one memorable moment shifts it (High = one moon and it's wary all night).", ""),
    ("Chattiness %", None, "pct",
     [50, 70, 30, 40, 45, 55, 80, 35, 15, 25],
     "How much it talks at the table in general. Everyone talks, some more than others "
     "(never 0). Applies AI to AI too.", ""),
    ("Moon warning %", None, "pct",
     [10, 30, 5, 10, 20, 5, 40, 10, 0, 15],
     "How often it calls out that it suspects someone of shooting the moon (0 = never warns, "
     "just adjusts its play). Applies AI to AI too.", ""),

    ("NOTES", None, "section", None, None, None),
    ("Notes", None, "text", [""] * 10, "Anything else about this player.", ""),
]

def build(out):
    HEADER_FILL = PatternFill("solid", fgColor="1F3864")
    SECTION_FILL = PatternFill("solid", fgColor="D9E2F3")
    NAME_FILL = PatternFill("solid", fgColor="FFF2CC")
    DESIGN_FILL = PatternFill("solid", fgColor="EDEDED")
    WHITE = Font(color="FFFFFF", bold=True)
    BOLD = Font(bold=True)
    thin = Side(style="thin", color="BFBFBF")
    BORDER = Border(left=thin, right=thin, top=thin, bottom=thin)
    WRAP_TOP = Alignment(wrap_text=True, vertical="top")
    CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)

    wb = Workbook()
    ws = wb.active
    ws.title = "Players"

    # Header row
    ws.cell(row=1, column=1, value="Attribute").font = WHITE
    ws.cell(row=1, column=2, value="roster.txt key").font = WHITE
    for j, p in enumerate(PLAYERS):
        ws.cell(row=1, column=3 + j, value=p).font = WHITE
    ws.cell(row=1, column=13, value="Default").font = WHITE
    for c in range(1, 14):
        cell = ws.cell(row=1, column=c)
        cell.fill = HEADER_FILL
        cell.alignment = CENTER
        cell.border = BORDER

    lists = {"template": TEMPLATES, "tier": ["Low", "Middle", "Middle (grows)", "Expert"],
             "level": ["Low", "Medium", "High"]}
    lists.update(OPTIONS)

    # Hidden sheet holding dropdown lists
    lsheet = wb.create_sheet("Lists")
    list_ranges = {}
    for col, (name, values) in enumerate(lists.items(), start=1):
        lsheet.cell(row=1, column=col, value=name)
        for i, v in enumerate(values, start=2):
            lsheet.cell(row=i, column=col, value=v)
        letter = get_column_letter(col)
        list_ranges[name] = "Lists!$%s$2:$%s$%d" % (letter, letter, len(values) + 1)
    lsheet.sheet_state = "hidden"

    validations = {}
    pct_dv = DataValidation(type="whole", operator="between", formula1="0", formula2="100",
                            showErrorMessage=True, errorTitle="0-100", error="Enter a whole number 0-100.")
    pct300_dv = DataValidation(type="whole", operator="between", formula1="0", formula2="300",
                               showErrorMessage=True, errorTitle="0-300", error="Enter a whole number 0-300.")
    num_dv = DataValidation(type="whole", operator="between", formula1="0", formula2="99",
                            showErrorMessage=True, error="Enter a whole number.")
    for dv in (pct_dv, pct300_dv, num_dv):
        ws.add_data_validation(dv)

    r = 2
    in_design = False
    for label, key, kind, values, guide, default in ROWS:
        if kind == "section":
            in_design = "design only" in label
            ws.cell(row=r, column=1, value=label).font = BOLD
            for c in range(1, 14):
                ws.cell(row=r, column=c).fill = SECTION_FILL
                ws.cell(row=r, column=c).border = BORDER
            ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=13)
            r += 1
            continue
        a = ws.cell(row=r, column=1, value=label)
        a.font = BOLD
        a.alignment = WRAP_TOP
        ws.cell(row=r, column=2, value=key or "").alignment = WRAP_TOP
        for j, v in enumerate(values):
            cell = ws.cell(row=r, column=3 + j, value=v)
            cell.alignment = WRAP_TOP if kind == "text" else CENTER
            if label == "Player name":
                cell.fill = NAME_FILL
            elif in_design:
                cell.fill = DESIGN_FILL
        ws.cell(row=r, column=13, value=default).alignment = CENTER
        rng = "C%d:L%d" % (r, r)
        if kind.startswith("list:"):
            name = kind[5:]
            dv = validations.get(name)
            if dv is None:
                dv = DataValidation(type="list", formula1=list_ranges[name], allow_blank=True,
                                    showErrorMessage=True, error="Pick from the list.")
                ws.add_data_validation(dv)
                validations[name] = dv
            dv.add(rng)
        elif kind == "pct":
            pct_dv.add(rng)
        elif kind == "pct300":
            pct300_dv.add(rng)
        elif kind == "num":
            num_dv.add(rng)
        for c in range(1, 14):
            ws.cell(row=r, column=c).border = BORDER
        if label == "How they play":
            ws.row_dimensions[r].height = 75
        elif kind == "text" and label in ("Stray toward", "Weight overrides", "Notes"):
            ws.row_dimensions[r].height = 32
        r += 1

    ws.column_dimensions["A"].width = 24
    ws.column_dimensions["B"].width = 24
    for j in range(10):
        ws.column_dimensions[get_column_letter(3 + j)].width = 25
    ws.column_dimensions["M"].width = 25
    ws.freeze_panes = "C2"
    ws.page_setup.orientation = "landscape"
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.print_title_rows = "1:1"
    ws.print_title_cols = "A:A"

    # Field guide
    g = wb.create_sheet("Field guide", 1)
    g.append(["Attribute", "roster.txt key", "What it means (and measured effect)", "Default"])
    for c in range(1, 5):
        cell = g.cell(row=1, column=c)
        cell.font = WHITE
        cell.fill = HEADER_FILL
        cell.alignment = CENTER
        cell.border = BORDER
    gr = 2
    for label, key, kind, values, guide, default in ROWS:
        if kind == "section":
            g.cell(row=gr, column=1, value=label).font = BOLD
            for c in range(1, 5):
                g.cell(row=gr, column=c).fill = SECTION_FILL
            g.merge_cells(start_row=gr, start_column=1, end_row=gr, end_column=4)
        else:
            g.cell(row=gr, column=1, value=label).font = BOLD
            g.cell(row=gr, column=2, value=key or "")
            g.cell(row=gr, column=3, value=guide)
            g.cell(row=gr, column=4, value=default)
            for c in range(1, 5):
                g.cell(row=gr, column=c).alignment = WRAP_TOP
                g.cell(row=gr, column=c).border = BORDER
        gr += 1
    gr += 1
    notes = [
        "Measured figures: one AI using the setting vs three Normals, 2,400 seeded whole games, "
        "games won (margin about +/-1 point). Different rounds used different baselines; compare "
        "within a row, not across rows.",
        "Proposed values in the Players sheet are a first draft to edit -- yellow = to fill in, "
        "grey = design only (not built yet).",
        "Wildness, skills and blunders make a player weaker or more varied; strategy options make "
        "players DIFFERENT. Several experts should use different mixtures.",
    ]
    for n in notes:
        g.cell(row=gr, column=1, value=n).alignment = WRAP_TOP
        g.merge_cells(start_row=gr, start_column=1, end_row=gr, end_column=4)
        g.row_dimensions[gr].height = 32
        gr += 1
    g.column_dimensions["A"].width = 26
    g.column_dimensions["B"].width = 26
    g.column_dimensions["C"].width = 90
    g.column_dimensions["D"].width = 24
    g.freeze_panes = "A2"
    g.page_setup.orientation = "landscape"
    g.page_setup.fitToWidth = 1
    g.page_setup.fitToHeight = 0
    g.sheet_properties.pageSetUpPr.fitToPage = True

    wb.save(out)
    print("saved", out, "rows", r - 1)


if __name__ == "__main__":
    build(sys.argv[1])
