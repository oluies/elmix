#!/usr/bin/env python3
"""Slår ihop per-zon-rader (JSONL) till viz/data/euprices-data.js.
Månadsaxeln = unionen av alla zoners månader (sorterad). Zonens v[] är
månadsmedel alignade mot den axeln (null där data saknas). Zonerna sorteras
på totalmedel fallande. window.euPrices konsumeras direkt av euprices.js."""
import json, os, sys

infile, outfile = sys.argv[1], sys.argv[2]
zones = [json.loads(l) for l in open(infile) if l.strip()]

all_months = sorted({m for z in zones for m in z["months"]})

out_zones = []
for z in zones:
    v = [z["months"].get(m) for m in all_months]
    vals = [x for x in v if x is not None]
    mean = round(sum(vals) / len(vals), 2) if vals else None
    out_zones.append({
        "code": z["code"], "label": z["label"], "land": z["land"],
        "cc": z["cc"], "se": z["se"], "mean": mean, "v": v,
    })

# Sortera fallande på totalmedel (zoner utan data sist).
out_zones.sort(key=lambda z: (z["mean"] is None, -(z["mean"] or 0)))

payload = {
    "updated": os.environ.get("UPDATED", ""),
    "unit": "EUR/MWh",
    "months": all_months,
    "zones": out_zones,
}
with open(outfile, "w") as f:
    f.write("window.euPrices = ")
    json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    f.write(";\n")
