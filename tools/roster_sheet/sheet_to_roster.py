"""Reads the Players sheet of the roster workbook and writes a roster.txt."""
import sys


def roster_lines(rows):
    """rows: {label: (roster key or None, ten values)}, as on the Players sheet.
    Returns the roster.txt lines. Blank cells are left out, so the game uses its default."""
    lines = ["version = 2", ""]
    for i in range(10):
        pid = "AI_%02d" % (i + 1)
        lines.append("[player]")
        lines.append("id = " + pid)
        lines.append("name = " + (rows["Character"][1][i] or pid))
        for label, (key, vals) in rows.items():
            v = vals[i]
            if key is None or v is None or v == "":
                continue
            if key == "weight.<FAMILY>":
                for part in str(v).split(","):
                    fam, w = part.split()
                    lines.append("weight.%s = %s" % (fam, w))
            elif key == "stray.<COMPONENT>":
                comp, mix = str(v).split(":", 1)
                lines.append("stray.%s = %s" % (comp.strip(), mix.strip()))
            else:
                lines.append("%s = %s" % (key, v))
        lines.append("")
    return lines


def read_sheet(src):
    import openpyxl
    ws = openpyxl.load_workbook(src, data_only=True)["Players"]
    rows = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        label, key = row[0], row[1]
        if label and key is not None:
            rows[label] = (key, row[2:12])
        elif label:
            rows[label] = (None, row[2:12])
    return rows


if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    lines = roster_lines(read_sheet(src))
    open(out, "w", encoding="utf-8").write("\n".join(lines))
    print("\n".join(lines[:40]))
