#!/usr/bin/env bash
# Exporterar årlig elproduktion per kraftslag (TWh) för Kina + jämförelseländer
# till viz/data/ember-data.js. Källa: Our World in Data (Ember + Energy Institute),
# CC-BY. Statisk årsdata – körs sällan (via publish-pages.sh), ingen API-nyckel.
# Kör från projektroten: ./viz/export-china.sh
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p viz/data

CSV="$(mktemp)"
trap 'rm -f "$CSV"' EXIT
URL="https://ourworldindata.org/grapher/electricity-prod-source-stacked.csv?csvType=full"
ok=""
for a in 1 2 3; do
  if curl -sSL -m 60 "$URL" -o "$CSV" && [ -s "$CSV" ]; then ok=1; break; fi
  echo "  OWID-hämtning försök $a/3 gav fel – försöker igen..." >&2
  sleep 3
done
[ -n "$ok" ] || { echo "FEL: kunde inte hämta OWID-data" >&2; exit 1; }

python3 - "$CSV" <<'PY'
import csv, json, os, sys
rows = list(csv.DictReader(open(sys.argv[1])))
# OWID-kolumn -> projektnyckel
M = [('Coal','kol'),('Gas','gas'),('Oil','olja'),('Nuclear','k'),
     ('Hydropower','va'),('Wind','v'),('Solar','s'),('Bioenergy','bio'),('Other renewables','ov')]
countries = [('China','Kina','China'),('India','Indien','India'),
             ('United States','USA','United States'),('European Union (27)','EU','EU'),
             ('Germany','Tyskland','Germany'),('France','Frankrike','France'),
             ('Sweden','Sverige','Sweden'),('World','Världen','World')]
missing = [c for c in M if c[0] not in (rows[0].keys() if rows else [])]
if missing:
    sys.exit('FEL: OWID-kolumner saknas: %s. Kolumnnamnen har troligen ändrats.'
             % ', '.join(c[0] for c in missing))
idx = {(r['Entity'], r['Year']): r for r in rows}

# Åren härleds ur datan i stället för att stå hårdkodade. Basåren är fasta
# femårssteg från 2015 så historiken förblir jämförbar mellan körningar;
# därtill läggs det senaste kompletta året om det inte redan är ett femårssteg.
# 2025 ger [2015, 2020, 2025], 2026 ger [2015, 2020, 2025, 2026].
BASE, STEP, MIN_LATEST = 2015, 5, 2025

def complete(y):
    """Året duger bara om varje land har en rad med positiv totalproduktion.
    OWID publicerar ofta ett halvfärdigt senaste år; utan det här testet
    skulle sidan visa ett år där halva världen saknas."""
    for ename, _, _ in countries:
        r = idx.get((ename, str(y)))
        if not r or sum(float(r[o]) if r[o] else 0 for o, _ in M) <= 0:
            return False
    return True

seen = sorted({int(r['Year']) for r in rows if r['Year'].isdigit()})
full = [y for y in seen if y >= BASE and complete(y)]
if not full:
    sys.exit('FEL: inget år från %d har fullständig data för alla %d länder'
             % (BASE, len(countries)))
latest = max(full)
# Golv mot att ett trasigt flöde tyst rullar sidan bakåt i tiden.
if latest < MIN_LATEST:
    sys.exit('FEL: senaste kompletta år är %d, lägre än golvet %d – '
             'flödet ser degraderat ut, behåller befintlig data' % (latest, MIN_LATEST))
years = [y for y in range(BASE, latest + 1, STEP) if y in full]
if latest not in years:
    years.append(latest)
print('år: %s (senaste kompletta: %d)' % (', '.join(map(str, years)), latest))
data, gaps = [], []
for ename, sv, en in countries:
    for y in years:
        r = idx.get((ename, str(y)))
        if not r:
            gaps.append('%s %d' % (ename, y))
            continue
        rec = {'name': sv, 'nameEn': en, 'y': y}
        for owid, key in M:
            rec[key] = round(float(r[owid])) if r[owid] else 0
        data.append(rec)

# En tom eller halvtom fil får aldrig skrivas. Sidan renderar tre tomma
# diagram utan att klaga, så ett misslyckat uttag såg tidigare ut som en
# lyckad publicering. Skriv till temp och flytta först när datan håller.
need = len(countries) * len(years)
if len(data) < need:
    sys.exit('FEL: %d av %d poster – saknas: %s' % (len(data), need, ', '.join(gaps)))
for rec in data:
    if sum(rec[k] for _, k in M) <= 0:
        sys.exit('FEL: %s %d har noll total produktion' % (rec['nameEn'], rec['y']))

lines = ',\n  '.join(json.dumps(d, ensure_ascii=False, separators=(',', ':')) for d in data)
out = ('window.emberMix = {\n  years: [' + ', '.join(map(str, years)) + '],\n'
       '  data: [\n  ' + lines + '\n]};\n')
tmp = 'viz/data/ember-data.js.tmp'
open(tmp, 'w').write(out)
os.replace(tmp, 'viz/data/ember-data.js')
print(f'skrev viz/data/ember-data.js ({len(out)} byte, {len(data)} poster)')
PY
