"""Checks that roster_sheet.py covers draft_roster.txt.

Fails if a key in draft_roster.txt has no row in the sheet, or if a draft value
differs from what the sheet would write through sheet_to_roster.py.
Usage: python check_roster_sheet.py [draft_roster.txt]"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from roster_sheet import ROWS  # noqa: E402
from sheet_to_roster import roster_lines  # noqa: E402


def parse(lines):
    """roster.txt lines -> {id: {key: value}} (values with spaces normalized)."""
    players, cur = {}, None
    for line in lines:
        t = line.split("#", 1)[0].strip()
        if not t or "=" not in t:
            continue
        k, v = (x.strip() for x in t.split("=", 1))
        if k == "id":
            cur = players.setdefault(v, {})
        elif cur is not None:
            cur[k] = " ".join(v.split())
    return players


def normalize(key, v):
    try:
        return repr(float(v))
    except ValueError:
        return " ".join(v.replace(" ,", ",").split())


def main():
    draft_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "draft_roster.txt")
    with open(draft_path, encoding="utf-8") as f:
        draft = parse(f.read().splitlines())
    rows = {label: (key, values) for label, key, kind, values, guide, default in ROWS
            if kind != "section"}
    sheet = parse(roster_lines(rows))
    sheet_keys = {key for key, _ in rows.values() if key}
    problems = []
    for pid in sorted(set(draft) | set(sheet)):
        d, s = draft.get(pid, {}), sheet.get(pid, {})
        for k in sorted(set(d) | set(s)):
            covered = (k in sheet_keys or k == "name"
                       or k.split(".", 1)[0] + ".<FAMILY>" in sheet_keys
                       or k.split(".", 1)[0] + ".<COMPONENT>" in sheet_keys)
            if not covered:
                problems.append("%s: key %s has no row in roster_sheet.py" % (pid, k))
            elif k not in s:
                problems.append("%s: %s = %s in the draft, blank in the sheet" % (pid, k, d[k]))
            elif k not in d:
                problems.append("%s: %s = %s in the sheet, missing from the draft" % (pid, k, s[k]))
            elif normalize(k, d[k]) != normalize(k, s[k]):
                problems.append("%s: %s is %s in the draft, %s in the sheet" % (pid, k, d[k], s[k]))
    for p in problems:
        print(p)
    print("%d problem(s)" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
