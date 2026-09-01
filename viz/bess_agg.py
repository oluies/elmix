#!/usr/bin/env python3
"""Räknar dygnsvisa prisspreadar per elområde, år och varaktighet.

Läser Energy-Charts-svar (ett per zon, JSON med unix_seconds + price) och
skriver en kompakt payload som viz/bess.html konsumerar:

    window.bessData = {updated, unit, years, zones, spread: {...}, ...}

Allt som är dyrt att räkna görs här: per dygn plockas de H dyraste och H
billigaste kvartarna/timmarna och medelvärdesbildas, och först därefter
medelvärdesbildas över dygnen. Att i stället bilda ett medeldygn och läsa av
spreaden på det ger ett annat och smickrande svar, eftersom utjämningen över
dygn tar bort just den variation batteriet lever på.

Upplösningen växlar mitt i serien: den svenska day-ahead-marknaden gick över
till kvartar 2025-10-01. Därför tas "H timmar" som andel av dygnets punkter,
round(H/24 * n), inte som ett fast antal - annars blir hösten räknad fyra
gånger för liten.

Dygnen är Europe/Stockholm-dygn, inte UTC: det är den lokala natten och
eftermiddagen batteriet arbetar mot.

Körs av viz/export-bess.sh:
    python3 viz/bess_agg.py <katalog-med-zonfiler> <utfil>
"""
import json
import os
import statistics
import sys
from collections import defaultdict
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

STHLM = ZoneInfo("Europe/Stockholm")
ZONER = ["SE1", "SE2", "SE3", "SE4"]
VARAKTIGHETER = list(range(1, 9))
# Ett dygn räknas bara om det är hyfsat helt. Tröskeln måste vara RELATIV:
# efter kvartsbytet 2025-10-01 är ett helt dygn 96 punkter, så ett fast tal som
# 20 släppte igenom ett stympat dygn och lät dygnsspreaden räknas på ett
# femtimmarsfönster som om det vore hela dygnet. Andelen jämförs mot dygnets
# egen förväntade längd, härledd ur den upplösning dygnet faktiskt har.
MIN_ANDEL = 0.8


def las_zon(sokvag):
    """unix_seconds + price -> {lokalt datum: (priser, steg_i_sekunder)}.

    Steget bärs med: upplösningen går bara att veta ur tidsstämplarna, och den
    behövs för att avgöra om ett dygn är helt. Att gissa den ur punktantalet
    gör kontrollen cirkulär - ett stympat kvartsdygn ser då ut som ett helt
    timdygn.
    """
    with open(sokvag) as f:
        d = json.load(f)
    dagar = defaultdict(list)
    for t, p in zip(d["unix_seconds"], d["price"]):
        if p is None:
            continue
        dt = datetime.fromtimestamp(t, timezone.utc).astimezone(STHLM)
        dagar[dt.date()].append((t, p))
    ut = {}
    for datum, poster in dagar.items():
        poster.sort()
        steg = _typiskt_steg([t for t, _ in poster])
        ut[datum] = ([p for _, p in poster], steg)
    return ut


def _typiskt_steg(tider):
    """Vanligaste avståndet mellan tidsstämplar, i sekunder. 0 vid en enda punkt."""
    if len(tider) < 2:
        return 0
    diffar = [b - a for a, b in zip(tider, tider[1:]) if b > a]
    if not diffar:
        return 0
    return max(set(diffar), key=diffar.count)


def dygnsspread(priser, H):
    """Dygnets H dyraste och H billigaste, som medelvärden.

    Returnerar None när dygnet inte rymmer både ett köp- och ett säljfönster -
    ett batteri kan inte ladda och urladda samma kvart.
    """
    n = max(1, round(H / 24 * len(priser)))
    if len(priser) < 2 * n:
        return None
    s = sorted(priser)
    return sum(s[-n:]) / n, sum(s[:n]) / n


def helt_dygn(priser, steg):
    """Är dygnet tillräckligt komplett för att räknas?

    Förväntat antal punkter räknas ur dygnets FAKTISKA steg (900 s = kvartar,
    3600 s = timmar), inte ur punktantalet. DST-dygnen har 23 respektive 25
    timmar, vilket ryms inom marginalen.
    """
    if steg <= 0:
        return False
    return len(priser) >= MIN_ANDEL * (86400 / steg)


def spread_for_ar(dagar, ar, H):
    hi, lo = [], []
    for datum, (priser, steg) in dagar.items():
        if datum.year != ar or not helt_dygn(priser, steg):
            continue
        par = dygnsspread(priser, H)
        if par is None:
            continue
        hi.append(par[0])
        lo.append(par[1])
    if not hi:
        return None
    return {
        # Medeldygnet driver modellen; mediandygnet visas bredvid eftersom ett
        # fåtal extremdygn bär medelvärdet i de norra zonerna.
        "hi": round(sum(hi) / len(hi), 2),
        "lo": round(sum(lo) / len(lo), 2),
        "hiMed": round(statistics.median(hi), 2),
        "loMed": round(statistics.median(lo), 2),
        "days": len(hi),
    }


def main():
    katalog, utfil = sys.argv[1], sys.argv[2]
    spread = {}
    ar_sedda = set()
    for z in ZONER:
        p = os.path.join(katalog, f"{z}.json")
        if not os.path.exists(p):
            print(f"  {z}: ingen fil - hoppas", file=sys.stderr)
            continue
        dagar = las_zon(p)
        if not dagar:
            print(f"  {z}: tom serie - hoppas", file=sys.stderr)
            continue
        ar_i_zon = sorted({d.year for d in dagar})
        per_ar = {}
        for ar in ar_i_zon:
            per_H = {}
            for H in VARAKTIGHETER:
                r = spread_for_ar(dagar, ar, H)
                if r:
                    per_H[str(H)] = r
            if per_H:
                per_ar[str(ar)] = per_H
                ar_sedda.add(ar)
        if per_ar:
            spread[z] = per_ar
            print(f"  {z}: {len(per_ar)} år, {len(dagar)} dygn", file=sys.stderr)

    if not spread:
        sys.exit("FEL: ingen zon gav data - skriver inte payloaden")

    # `if not spread` ovanför har redan fångat fallet att ALLA zoner saknas, så
    # här återstår bara delvis bortfall - varna, men skriv payloaden.
    saknas = [z for z in ZONER if z not in spread]
    if saknas:
        print(f"  VARNING: saknar {', '.join(saknas)}", file=sys.stderr)

    payload = {
        "updated": os.environ.get("UPDATED", ""),
        "unit": "EUR/MWh",
        "source": "Energy-Charts (ENTSO-E/SMARD, CC BY 4.0)",
        "zones": [z for z in ZONER if z in spread],
        "years": sorted(ar_sedda),
        "durations": VARAKTIGHETER,
        "spread": spread,
        "reserves": las_reserver(),
    }
    tmp = utfil + ".tmp"
    with open(tmp, "w") as f:
        f.write("window.bessData = " + json.dumps(payload, ensure_ascii=False,
                                                  separators=(",", ":")) + ";\n")
    os.replace(tmp, utfil)
    print(f"skrev {utfil} ({os.path.getsize(utfil)} byte)", file=sys.stderr)


def las_reserver():
    """Reservpriser från viz/bess-reserves.json om filen finns.

    Svenska kraftnät publicerar inga öppna reservprisserier - Mimer har ett
    internt API bakom webbgränssnittet men inget dokumenterat publikt. Därför
    är de här siffrorna handmatade ur SvK:s månadsrapporter, och varje rad bär
    sin källa och sitt datum så sidan kan säga vilka tal som inte uppdateras
    automatiskt.

    Viktigt: FCR-D och FCR-N upphandlas nationellt, aFRR och mFRR per
    elområde. Varje post bär därför `basis`, så sidan aldrig visar ett
    nationellt pris som om det vore zonens.
    """
    # Ligger UTANFOR viz/data/, som ar gitignorerad. Det har ar handmatad
    # kalldata som maste folja med i repot, inte genererad payload.
    p = "viz/bess-reserves.json"
    if not os.path.exists(p):
        return {"products": [], "note": "saknas"}
    with open(p) as f:
        return json.load(f)


if __name__ == "__main__":
    main()
