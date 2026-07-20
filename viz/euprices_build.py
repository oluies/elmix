#!/usr/bin/env python3
"""Slår ihop per-zon-rader (JSONL) till viz/data/euprices-data.js.
Månadsaxeln = unionen av alla zoners månader (sorterad). Zonens v[] är
månadsmedel alignade mot den axeln (null där data saknas). Zonerna sorteras
på totalmedel fallande. window.euPrices konsumeras direkt av euprices.js."""
import json, os, sys

infile, outfile = sys.argv[1], sys.argv[2]
zones = [json.loads(l) for l in open(infile) if l.strip()]

all_months = sorted({m for z in zones for m in z["months"]})
all_years = sorted({y for z in zones for y in z.get("hours", {})})
thresholds = sorted({int(t) for z in zones for y in z.get("hours", {}).values()
                     for t in y["b"]})

# Bästa täckning per år över alla zoner. Ett år där ingen zon når 300 dygn är
# partiellt för alla (innevarande år); då ska zonerna ändå jämföras med
# varandra, för de täcker samma fönster.
best = {y: max((z["hours"][y]["h"] for z in zones if y in z.get("hours", {})), default=0)
        for y in all_years}
partial = [not any(z.get("hours", {}).get(y, {}).get("full") for z in zones)
           for y in all_years]

# En zon får vara med ett givet år bara om den täcker minst 80 % av den bästa
# täckningen det året. Annars jämförs fyra månaders IT-data med tolv månaders
# svensk - andelen är då en säsongsartefakt, inte en zonskillnad.
def keeps(z, y):
    h = z.get("hours", {}).get(y)
    return h is not None and best[y] > 0 and h["h"] >= 0.8 * best[y]

out_zones = []
for z in zones:
    v = [z["months"].get(m) for m in all_months]
    vals = [x for x in v if x is not None]
    mean = round(sum(vals) / len(vals), 2) if vals else None
    # b[tröskel] = lista med andel av tiden (%) under tröskeln, per år i all_years.
    hrs = z.get("hours", {})
    below = {str(t): [hrs[y]["b"].get(str(t)) if keeps(z, y) else None for y in all_years]
             for t in thresholds}
    # Faktiskt täckta timmar per år - tooltipen räknar om andel till timmar och
    # får inte anta 8760 för ett pågående år.
    zh = [hrs[y]["h"] if keeps(z, y) else None for y in all_years]
    out_zones.append({
        "code": z["code"], "label": z["label"], "land": z["land"],
        "cc": z["cc"], "se": z["se"], "mean": mean, "v": v, "b": below, "h": zh,
    })

# Sortera fallande på totalmedel (zoner utan data sist).
out_zones.sort(key=lambda z: (z["mean"] is None, -(z["mean"] or 0)))

payload = {
    "updated": os.environ.get("UPDATED", ""),
    "unit": "EUR/MWh",
    "months": all_months,
    "years": all_years,
    "partial": partial,
    "thresholds": thresholds,
    "zones": out_zones,
}
with open(outfile, "w") as f:
    f.write("window.euPrices = ")
    json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    f.write(";\n")
