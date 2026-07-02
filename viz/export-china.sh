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
import csv, json, sys
rows = list(csv.DictReader(open(sys.argv[1])))
# OWID-kolumn -> projektnyckel
M = [('Coal','kol'),('Gas','gas'),('Oil','olja'),('Nuclear','k'),
     ('Hydropower','va'),('Wind','v'),('Solar','s'),('Bioenergy','bio'),('Other renewables','ov')]
countries = [('China','Kina','China'),('India','Indien','India'),
             ('United States','USA','United States'),('European Union (27)','EU','EU'),
             ('Germany','Tyskland','Germany'),('France','Frankrike','France'),
             ('Sweden','Sverige','Sweden'),('World','Världen','World')]
years = [2015, 2020, 2025]
idx = {(r['Entity'], r['Year']): r for r in rows}
data = []
for ename, sv, en in countries:
    for y in years:
        r = idx.get((ename, str(y)))
        if not r:
            continue
        rec = {'name': sv, 'nameEn': en, 'y': y}
        for owid, key in M:
            rec[key] = round(float(r[owid])) if r[owid] else 0
        data.append(rec)
lines = ',\n  '.join(json.dumps(d, ensure_ascii=False, separators=(',', ':')) for d in data)
out = 'window.emberMix = {\n  years: [2015, 2020, 2025],\n  data: [\n  ' + lines + '\n]};\n'
open('viz/data/ember-data.js', 'w').write(out)
print(f'skrev viz/data/ember-data.js ({len(out)} byte, {len(data)} poster)')
PY
