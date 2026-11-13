"""Ticks Downloaded/Converted in card-art-checklist.xlsx from a convertSet report.

Usage: python docs/tick_checklist.py OP01
Reads %LOCALAPPDATA%/OPTCGSimCardArtChooser/converted/<SET>/report.json (or the macOS equivalent).
Only fills cells; never clears a "Yes" and never touches Verified in Sim or formulas.
Open the workbook in Excel afterwards (or let it recalculate) to refresh the progress counts.
"""
import json
import os
import platform
import sys
from pathlib import Path

from openpyxl import load_workbook

set_id = sys.argv[1].upper()
if platform.system() == "Windows":
    data = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData/Local")) / "OPTCGSimCardArtChooser"
elif platform.system() == "Darwin":
    data = Path.home() / "Library/Application Support/OPTCGSimCardArtChooser"
else:
    data = Path.home() / ".local/share/OPTCGSimCardArtChooser"

report = json.loads((data / "converted" / set_id / "report.json").read_text(encoding="utf-8"))
done = {r["variantId"] for r in report["results"] if r["status"] in ("Converted", "AlreadyConverted")}
failed = {r["variantId"]: r.get("message") for r in report["results"] if r["status"] == "Failed"}

book = Path(__file__).with_name("card-art-checklist.xlsx")
wb = load_workbook(book)
ws = wb[set_id]
header_row = 4
cols = {ws.cell(row=header_row, column=c).value: c for c in range(1, ws.max_column + 1)}
ticked = 0
for row in range(header_row + 1, ws.max_row + 1):
    variant = ws.cell(row=row, column=cols["Variant ID"]).value
    if variant in done:
        for name in ("Downloaded", "Converted"):
            cell = ws.cell(row=row, column=cols[name])
            if cell.value != "Yes":
                cell.value = "Yes"
        ticked += 1
    elif variant in failed:
        notes = ws.cell(row=row, column=cols["Notes"])
        notes.value = f"Conversion failed: {failed[variant]}"
wb.save(book)
print(f"{set_id}: ticked {ticked} of {len(report['results'])} variants; {len(failed)} failed.")
