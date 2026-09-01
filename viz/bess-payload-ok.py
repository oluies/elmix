#!/usr/bin/env python3
"""Duger den här bess-payloaden att publicera?

Strukturell kontroll i stället för greppar. Tre okarade greppar på `"spread"`,
`"SE[1-4]"` och `"hi"` fanns tidigare i export-bess.sh och en fjärde, svagare,
i publish-pages.sh - och `"SE[1-4]"` matchade dessutom `reserves.byZone`, så en
payload utan en enda spread kunde passera. En sanning på ett ställe i stället.

    python3 viz/bess-payload-ok.py <fil>   # exit 0 = duger

Kravet: minst en zon som har minst ett år som har minst en varaktighet med ett
`hi`-värde. Det är precis vad sidan behöver för att kunna rita något alls.
"""
import json
import sys


def duger(sokvag):
    try:
        with open(sokvag) as f:
            text = f.read()
        payload = json.loads(text[text.index("{"):].rstrip().rstrip(";"))
    except Exception:
        return False
    spread = payload.get("spread") or {}
    for zon in payload.get("zones") or []:
        for ar in (spread.get(zon) or {}).values():
            for varaktighet in ar.values():
                if isinstance(varaktighet, dict) and "hi" in varaktighet:
                    return True
    return False


if __name__ == "__main__":
    sys.exit(0 if len(sys.argv) > 1 and duger(sys.argv[1]) else 1)
