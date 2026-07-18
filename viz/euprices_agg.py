#!/usr/bin/env python3
"""Aggregerar en zons Energy-Charts-prissvar till månadsmedel (UTC-månad) samt
andel tid under ett antal priströsklar per år.
Läser sökväg + metadata ur env, skriver en JSON-rad till stdout."""
import json, os, datetime as dt
from collections import defaultdict

d = json.load(open(os.environ["JSON"]))
secs = d.get("unix_seconds", []) or []
price = d.get("price", []) or []

# Släng punkter före begärd startdag (Energy-Charts kan börja 23:00 UTC dagen
# innan -> annars uppstår en falsk 1-timmes "månad" i föregående december).
start_ep = dt.datetime.strptime(os.environ.get("START", "1970-01-01"), "%Y-%m-%d") \
    .replace(tzinfo=dt.timezone.utc).timestamp()

# Trösklar i EUR/MWh. 0 = negativpris (B1-indikator), 54 = Hannulas breakeven
# för elpanna mot gaseldad ånga. Andelarna är tidsviktade, inte punkträknade:
# Energy-Charts byter upplösning (60 -> 15 min vid MTU-bytet okt 2025) och en
# ren punkträkning skulle då överväga de finupplösta perioderna.
THRESHOLDS = [0, 20, 40, 54, 70]

sums = defaultdict(float)
cnts = defaultdict(int)
year_tot = defaultdict(float)                       # år -> sekunder med pris
year_below = defaultdict(lambda: defaultdict(float))  # år -> tröskel -> sekunder

n = len(secs)
for i, (t, p) in enumerate(zip(secs, price)):
    if p is None or t < start_ep:
        continue
    # Punktens varaktighet = avståndet till nästa punkt (sista ärver föregående).
    if i + 1 < n:
        dur = secs[i + 1] - t
    elif i > 0:
        dur = t - secs[i - 1]
    else:
        dur = 3600
    if dur <= 0 or dur > 6 * 3600:   # hoppa över luckor/skräp
        dur = 3600
    ts = dt.datetime.fromtimestamp(t, dt.timezone.utc)
    sums[ts.strftime("%Y-%m")] += p
    cnts[ts.strftime("%Y-%m")] += 1
    y = ts.strftime("%Y")
    year_tot[y] += dur
    for thr in THRESHOLDS:
        if p < thr:
            year_below[y][thr] += dur

# Kräv minst tre dygns punkter så partiella randmånader inte förvränger snittet.
months = {ym: round(sums[ym] / cnts[ym], 2) for ym in sums if cnts[ym] >= 72}

# Kräv minst 300 dygn täckning innan ett år får en andel - annars blir
# randåren (och år med källgap) missvisande låga/höga.
MIN_YEAR_SEC = 300 * 24 * 3600
hours = {}
for y, tot in year_tot.items():
    if tot < MIN_YEAR_SEC:
        continue
    hours[y] = {
        "h": round(tot / 3600),
        "b": {str(thr): round(year_below[y][thr] / tot * 100, 2) for thr in THRESHOLDS},
    }

print(json.dumps({
    "code":  os.environ["ZCODE"],
    "label": os.environ["ZLABEL"],
    "land":  os.environ["ZLAND"],
    "cc":    os.environ["ZCC"],
    "se":    os.environ["ZSE"] == "1",
    "months": months,
    "hours": hours,
}, ensure_ascii=False))
