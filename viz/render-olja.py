#!/usr/bin/env python3
"""Ritar diagrammen på modell/olja.html och skriver in dem mellan markörerna.
Data är årlig och ligger som konstanter här med källa i sidans fot; kör om
skriptet när Eurostat eller Statistical Review släpper ett nytt år."""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PAGE = os.path.join(HERE, "modell", "olja.html")

# Eurostat nrg_ti_oil, råolja per partnerland, Sverige 2024, tusen ton.
TOT = 18165.0
IMP = [("Norge", "Norway", 10331, 3), ("USA", "United States", 2311, 2),
       ("Storbritannien", "United Kingdom", 1966, 2), ("Guyana", "Guyana", 1942, 2),
       ("Nigeria", "Nigeria", 810, 2), ("Libyen", "Libya", 652, 0),
       ("Elfenbenskusten", "Côte d'Ivoire", 67, 1), ("Danmark", "Denmark", 46, 3),
       ("Ej specificerat", "Not specified", 39, None)]
# Energy Institute Statistical Review 2026 (2025) mot V-Dem RoW 2025, andel av världen.
WORLD = {0: 27.6, 1: 31.9, 2: 37.8, 3: 2.6, None: 0.1}
EU_PCT, EES_PCT = 0.25, 57.1

# Divergerande, ordnad skala. Validerad mot ljus yta: alla sex kontroller passerar.
# Mot mörk yta passerar färgseende-, kroma- och normalseendekontrollerna; indigon
# ligger under 3:1 i kontrast, vilket är tillåtet just för att varje stapel bär en
# egen etikett och det finns en teckenförklaring.
C = {0: "var(--r0)", 1: "var(--r1)", 2: "var(--r2)", 3: "var(--r3)", None: "var(--rna)"}
L = {0: "sluten autokrati", 1: "valautokrati", 2: "valdemokrati",
     3: "liberal demokrati", None: "ej specificerat"}
LEN = {0: "closed autocracy", 1: "electoral autocracy", 2: "electoral democracy",
       3: "liberal democracy", None: "not specified"}

def esc(t): return t.replace("&", "&amp;").replace("<", "&lt;")
def sv(x, d=1): return f"{x:.{d}f}".replace(".", ",")
def en(x, d=1): return f"{x:.{d}f}"

def bi(x, y, cls, anchor, s_sv, s_en, extra=""):
    """Sidan byter sprak med html[data-lang] och CSS traffar aven SVG-text, sa
    bada spraken ligger pa samma koordinat. Galler ocksa talen: svenskt
    decimalkomma mot engelsk punkt."""
    a = f' text-anchor="{anchor}"' if anchor else ""
    return (f'<text x="{x}" y="{y}" class="{cls}"{a}{extra} lang="sv">{esc(s_sv)}</text>'
            f'<text x="{x}" y="{y}" class="{cls}"{a}{extra} lang="en">{esc(s_en)}</text>')

# ---------------------------------------------------------------- importstaplar
W, X0, X1, TOP, ROW = 1180, 250, 1010, 40, 44
H = TOP + ROW * len(IMP) + 46
sx = lambda p: X0 + p / 60.0 * (X1 - X0)
s = [f'<svg viewBox="0 0 {W} {H}" width="100%" xmlns="http://www.w3.org/2000/svg" role="img" '
     f'aria-label="Sveriges raoljeimport per ursprungsland 2024, fargad efter regimklass">']
for i, (n_sv, n_en, v, g) in enumerate(IMP):
    pct = 100 * v / TOT
    y = TOP + ROW * i
    s.append(f'<rect x="{X0}" y="{y:.0f}" width="{sx(pct)-X0:.1f}" height="26" fill="{C[g]}" rx="2"/>')
    s.append(bi(f"{X0-14}", f"{y+18:.0f}", "obl", "end", n_sv, n_en))
    s.append(bi(f"{sx(pct)+10:.1f}", f"{y+18:.0f}", "oval", None,
                f"{sv(pct)} %", f"{en(pct)}%"))
s.append(bi(f"{X0}", f"{H-14}", "osub", "start",
            "andel av Sveriges råoljeimport, 18,2 Mt totalt",
            "share of Sweden's crude oil imports, 18.2 Mt in total"))
s.append("</svg>")
bars = "\n".join(s)

# ---------------------------------------------------------------- jamforelse
se = {}
for _, _, v, g in IMP: se[g] = se.get(g, 0) + 100 * v / TOT
W2, H2, PX0, PX1 = 1180, 150, 250, 1120
t = [f'<svg viewBox="0 0 {W2} {H2}" width="100%" xmlns="http://www.w3.org/2000/svg" role="img" '
     f'aria-label="Regimfordelning: Sveriges import mot varldens produktion">']
for row, (lab, lab_en, data) in enumerate([("Sveriges import", "Sweden's imports", se),
                                           ("Världens produktion", "World production", WORLD)]):
    y = 26 + row * 52
    left = PX0
    for g in (0, 1, 2, 3, None):
        w = data.get(g, 0)
        if w <= 0: continue
        px = w / 100.0 * (PX1 - PX0)
        t.append(f'<rect x="{left:.1f}" y="{y}" width="{px:.1f}" height="30" fill="{C[g]}"/>')
        if w > 6:
            t.append(f'<text x="{left+px/2:.1f}" y="{y+20}" class="oin" text-anchor="middle">{w:.0f} %</text>')
        left += px
    t.append(bi(f"{PX0-14}", f"{y+20}", "obl", "end", lab, lab_en))
t.append("</svg>")
comp = "\n".join(t)

leg = " ".join(
    f'<span class="lgi"><span class="sw" style="background:{C[g]}"></span>'
    f'<span lang="sv">{L[g]}</span><span lang="en">{LEN[g]}</span></span>'
    for g in (0, 1, 2, 3, None))

aut = se.get(0, 0) + se.get(1, 0)
H1 = ('<span lang="sv">Sveriges råoljeimport 2024 &#183; per ursprungsland</span>'
      '<span lang="en">Sweden&#8217;s crude oil imports 2024 &#183; by country of origin</span>')
H2 = ('<span lang="sv">Samma fyra klasser &#183; import mot världsproduktion</span>'
      '<span lang="en">The same four classes &#183; imports against world production</span>')
FOOT = (f'<span lang="sv">Autokratier står för {sv(aut)} % av importen och '
        f'{WORLD[0]+WORLD[1]:.0f} % av världsproduktionen.</span>'
        f'<span lang="en">Autocracies account for {en(aut)}% of the imports and '
        f'{WORLD[0]+WORLD[1]:.0f}% of world production.</span>')
block = (f'<div class="fig"><p class="figh">{H1}</p>{bars}'
         f'<p class="leg">{leg}</p></div>\n'
         f'<div class="fig"><p class="figh">{H2}</p>{comp}'
         f'<p class="leg">{FOOT}</p></div>')

page = open(PAGE, encoding="utf-8").read()
new = re.sub(r"(<!-- OLJA-CHART:BEGIN -->).*?(<!-- OLJA-CHART:END -->)",
             lambda m: m.group(1) + "\n" + block + "\n" + m.group(2), page, flags=re.S)
if new == page and "OLJA-CHART:BEGIN" not in page:
    sys.exit("FEL: markorerna OLJA-CHART saknas i " + PAGE)
open(PAGE, "w", encoding="utf-8").write(new)
print(f"render-olja: {len(IMP)} lander (sv+en), EU {sv(EU_PCT,2)} %, EES {sv(EES_PCT)} % -> modell/olja.html")
