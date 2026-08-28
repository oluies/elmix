#!/usr/bin/env python3
"""Ritar de statiska diagrammen på modell/obalans.html ur viz/data/imbalance-data.js
och skriver in dem mellan markörerna i sidan. Körs av viz/export-imbalance.sh, så
den statiska bilden följer med varje datarefresh. Ingen JS behövs för att läsa den."""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data", "imbalance-data.js")
PAGE = os.path.join(HERE, "modell", "obalans.html")
LIM, BIN, ZONE = 150.0, 2.0, "SE3"
ZC = {"SE1": "var(--ind)", "SE2": "var(--flex)", "SE3": "var(--pol)", "SE4": "var(--eco)"}
RES = {2022: ("timme", "hourly"),
       2023: ("timme, sedan kvart från 22 maj", "hourly, then quarter-hourly from 22 May"),
       2024: ("kvart", "quarter-hourly"), 2025: ("kvart", "quarter-hourly"),
       2026: ("kvart, till och med augusti", "quarter-hourly, through August")}

raw = open(DATA, encoding="utf-8").read()
agg = json.loads(raw[raw.index("=") + 1: raw.rstrip().rfind(";")])
by = {(r["zone"], r["y"]): r for r in agg}
years = sorted({r["y"] for r in agg})

def density(r):
    """Andel av ALLA perioder per EUR. Exakta nollor och svansar utanför +/-150
    är med i nämnaren men inte i kurvan, så de saknas i stället för att skalas om."""
    d = {b: c for b, c in zip(r["b"], r["c"])}
    xs = [int(-LIM // BIN) + i for i in range(int(2 * LIM // BIN))]
    v = [d.get(b, 0) / r["n"] / BIN for b in xs]
    k, out = 2, []
    for i in range(len(v)):
        w = v[max(0, i - k): i + k + 1]
        out.append(sum(w) / len(w))
    return [(b * BIN + BIN / 2, y) for b, y in zip(xs, out)]

def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;")

def bi(x, y, cls, anchor, sv, en, extra=""):
    """Sidan byter språk med html[data-lang]; CSS döljer den variant som inte gäller.
    Samma trick fungerar på SVG-text, så båda språken ligger på samma koordinat."""
    a = f' text-anchor="{anchor}"' if anchor else ""
    return (f'<text x="{x}" y="{y}" class="{cls}"{a}{extra} lang="sv">{esc(sv)}</text>'
            f'<text x="{x}" y="{y}" class="{cls}"{a}{extra} lang="en">{esc(en)}</text>')

# ---------------------------------------------------------------- ridgeline
W, X0, X1 = 1180, 250, 1120
ROW, TOP = 78, 54
curves = {y: density(by[(ZONE, y)]) for y in years}
gmax = max(v for c in curves.values() for _, v in c) or 1.0
sx = lambda x: X0 + (x + LIM) / (2 * LIM) * (X1 - X0)
H = TOP + ROW * len(years) + 66

s = [f'<svg viewBox="0 0 {W} {H}" width="100%" xmlns="http://www.w3.org/2000/svg" role="img" '
     f'aria-label="Fördelning av obalanspris minus day-ahead-pris i SE3 per år">']
zero = sx(0)
s.append(f'<line x1="{zero:.1f}" y1="{TOP-16:.0f}" x2="{zero:.1f}" y2="{TOP+ROW*len(years)+4:.0f}" '
         f'stroke="var(--ink3)" stroke-width="1" stroke-dasharray="4 3"/>')
for i, y in enumerate(years):
    base = TOP + ROW * i + ROW - 10
    r, pts = by[(ZONE, y)], curves[y]
    path = " ".join(f"{sx(x):.1f},{base - v / gmax * (ROW - 16):.1f}" for x, v in pts)
    s.append(f'<line x1="{X0}" y1="{base:.0f}" x2="{X1}" y2="{base:.0f}" stroke="var(--rule)"/>')
    s.append(f'<polygon points="{sx(-LIM):.1f},{base:.1f} {path} {sx(LIM):.1f},{base:.1f}" '
             f'fill="{ZC[ZONE]}" fill-opacity="0.15"/>')
    s.append(f'<polyline points="{path}" fill="none" stroke="{ZC[ZONE]}" stroke-width="2"/>')
    s.append(f'<text x="{X0-16}" y="{base-24:.0f}" class="ryear" text-anchor="end">{y}</text>')
    s.append(bi(f"{X0-16}", f"{base-8:.0f}", "rsub", "end", RES[y][0], RES[y][1]))
    s.append(bi(f"{X1}", f"{base-40:.0f}", "rstat", "end",
                f'{r["pct_near"]:.0f}% inom ±10 EUR/MWh',
                f'{r["pct_near"]:.0f}% within ±10 EUR/MWh',
                extra=f' fill="{ZC[ZONE]}"'))
    s.append(bi(f"{X1}", f"{base-24:.0f}", "rsub", "end",
                f'{r["pct_zero"]:.0f}% exakt noll, ej ritad · {r["pct_out"]:.1f}% utanför skalan',
                f'{r["pct_zero"]:.0f}% exactly zero, not drawn · {r["pct_out"]:.1f}% off the scale'))
ay = TOP + ROW * len(years) + 6
s.append(f'<line x1="{X0}" y1="{ay:.0f}" x2="{X1}" y2="{ay:.0f}" stroke="var(--rule)"/>')
for t in (-150, -100, -50, 0, 50, 100, 150):
    s.append(f'<line x1="{sx(t):.1f}" y1="{ay:.0f}" x2="{sx(t):.1f}" y2="{ay+5:.0f}" stroke="var(--rule)"/>')
    s.append(f'<text x="{sx(t):.1f}" y="{ay+21:.0f}" class="ax" text-anchor="middle">{t}</text>')
s.append(bi(f"{X0}", f"{ay+44:.0f}", "rsub", "start",
            "obalanspris minus day-ahead-pris, EUR/MWh",
            "imbalance price minus day-ahead price, EUR/MWh"))
s.append("</svg>")
ridge = "\n".join(s)

# ---------------------------------------------------------------- four zones
W2, H2, PX0, PX1, PY0, PY1 = 1180, 300, 150, 980, 40, 220
vals = {z: [by[(z, y)]["pct_near"] for y in years] for z in ZC}
lo, hi = 25, 70
px = lambda i: PX0 + i * (PX1 - PX0) / (len(years) - 1)
py = lambda v: PY1 - (v - lo) / (hi - lo) * (PY1 - PY0)
t = [f'<svg viewBox="0 0 {W2} {H2}" width="100%" xmlns="http://www.w3.org/2000/svg" role="img" '
     f'aria-label="Andel perioder inom plus/minus 10 EUR per MWh, fyra elområden">']
for g in (30, 40, 50, 60, 70):
    t.append(f'<line x1="{PX0}" y1="{py(g):.1f}" x2="{PX1}" y2="{py(g):.1f}" stroke="var(--rule)"/>')
    t.append(f'<text x="{PX0-12}" y="{py(g)+4:.1f}" class="ax" text-anchor="end">{g}%</text>')
for z, col in ZC.items():
    pts = " ".join(f"{px(i):.1f},{py(v):.1f}" for i, v in enumerate(vals[z]))
    t.append(f'<polyline points="{pts}" fill="none" stroke="{col}" stroke-width="2.6"/>')
    for i, v in enumerate(vals[z]):
        t.append(f'<circle cx="{px(i):.1f}" cy="{py(v):.1f}" r="4" fill="{col}"/>')
    # SE1 och SE2 ligger nastan pa varandra sista aret, sa etiketterna sarras
    OFF = {"SE1": -8.0, "SE2": 6.0, "SE3": 6.0, "SE4": -4.0}
    t.append(f'<text x="{PX1+10}" y="{py(vals[z][-1])+4+OFF[z]:.1f}" class="rstat" fill="{col}">{z}</text>')
for i, y in enumerate(years):
    t.append(f'<text x="{px(i):.1f}" y="{PY1+24:.0f}" class="ax" text-anchor="middle">{y}</text>')
t.append(bi(f"{PX0}", "24", "rsub", "start",
            "andel perioder inom ±10 EUR/MWh",
            "share of periods within ±10 EUR/MWh"))
t.append("</svg>")
zones = "\n".join(t)

SVH = ('<span lang="sv">SE3 &#183; fördelning per år</span>'
       '<span lang="en">SE3 &#183; distribution by year</span>')
ZOH = ('<span lang="sv">Alla fyra elområden &#183; andel nära noll</span>'
       '<span lang="en">All four bidding zones &#183; share close to zero</span>')
block = (f'<div class="fig"><p class="figh">{SVH}</p>{ridge}</div>\n'
         f'<div class="fig"><p class="figh">{ZOH}</p>{zones}</div>')

page = open(PAGE, encoding="utf-8").read()
new = re.sub(r"(<!-- STATIC-CHART:BEGIN -->).*?(<!-- STATIC-CHART:END -->)",
             lambda m: m.group(1) + "\n" + block + "\n" + m.group(2), page, flags=re.S)
if new == page and "STATIC-CHART:BEGIN" not in page:
    sys.exit("FEL: markörerna STATIC-CHART saknas i " + PAGE)
open(PAGE, "w", encoding="utf-8").write(new)
print(f"render-obalans: {len(years)} år, {len(block)} tecken statisk SVG -> modell/obalans.html")
