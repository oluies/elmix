#!/usr/bin/env python3
"""Aggregerar en zons Energy-Charts-prissvar till månadsmedel (UTC-månad).
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

sums = defaultdict(float)
cnts = defaultdict(int)
for t, p in zip(secs, price):
    if p is None or t < start_ep:
        continue
    ym = dt.datetime.fromtimestamp(t, dt.timezone.utc).strftime("%Y-%m")
    sums[ym] += p
    cnts[ym] += 1

# Kräv minst tre dygns punkter så partiella randmånader inte förvränger snittet.
months = {ym: round(sums[ym] / cnts[ym], 2) for ym in sums if cnts[ym] >= 72}
print(json.dumps({
    "code":  os.environ["ZCODE"],
    "label": os.environ["ZLABEL"],
    "land":  os.environ["ZLAND"],
    "cc":    os.environ["ZCC"],
    "se":    os.environ["ZSE"] == "1",
    "months": months,
}, ensure_ascii=False))
