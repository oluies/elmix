#!/usr/bin/env bash
# Bygger och paketerar GitHub Pages-sajten i docs/ (interaktiv app +
# prerenderad rapport). Kor fran projektroten: ./viz/publish-pages.sh
set -euo pipefail
cd "$(dirname "$0")/.."

./viz/export-data.sh
./viz/export-round.sh
./viz/export-consumption.sh
# DE/FR-förbrukningsmix (fetcheu-data) – bygg bara om rådatan finns.
if ls data/raw/eu/generation/*.parquet >/dev/null 2>&1; then ./viz/export-consumption-eu.sh; fi
# Kina vs världen (statisk OWID-årsdata) – icke-fatal om OWID inte svarar.
# Fallback: återanvänd redan publicerad data så sidan inte raderas ur docs/
# (viz/data är gitignored och cachas inte i CI -> varje körning hämtar färskt).
./viz/export-china.sh || echo "VARNING: export-china misslyckades – Kina-sidan ej uppdaterad" >&2
# Fallbacken får inte nöja sig med att filen finns. En tidigare körning skrev
# en giltig men tom payload som passerade -f och skrev över den fungerande
# datan i docs/; sidan såg publicerad ut men visade tre tomma diagram.
china_ok() { [ -f "$1" ] && grep -q '"nameEn":"China"' "$1"; }
china_ok viz/data/ember-data.js || cp docs/data/ember-data.js viz/data/ 2>/dev/null || true
china_ok viz/data/ember-data.js || echo "VARNING: ingen användbar ember-data.js – Kina-sidan blir tom" >&2
# Elpris per elområde EU+Norge (Energy-Charts, ingen nyckel) – icke-fatal.
# Fallback: återanvänd redan publicerad data så sidan inte raderas ur docs/.
./viz/export-euprices.sh || echo "VARNING: export-euprices misslyckades – elpris-sidan ej uppdaterad" >&2
[ -f viz/data/euprices-data.js ] || cp docs/data/euprices-data.js viz/data/ 2>/dev/null || true
(cd viz && ../mill app.fullLinkJS)   # bootstrap-mill (funkar även i CI utan global mill)
(cd viz/ssr && node render.mjs)

rm -rf docs
mkdir -p docs/vendor docs/data
cp viz/out/app/fullLinkJS.dest/main.js docs/
cp viz/vendor/echarts.min.js docs/vendor/
cp viz/data/elmix-data.js docs/data/
cp viz/prerendered.html docs/
sed 's|out/app/fastLinkJS.dest/main.js|main.js|' viz/index.html > docs/index.html
# Runda experimentsidor (fristående, delar round.js, läser *-data.js direkt).
cp viz/round.html viz/consumption.html viz/round.js docs/
cp viz/data/round-data.js viz/data/consumption-data.js docs/data/
# Multi-klock-sidor (delar multiclock.js + round.js). SE-alla-zoner alltid;
# DE/FR bara om fetcheu-datan byggts.
cp viz/multiclock.js viz/consumption-se.html viz/energi.html viz/energi.js docs/
# Investeringssignal (LCOE vs capture) – läser consumption-data.js, alltid byggd.
cp viz/investering.html viz/investering.js docs/
# Lagring & varaktighet – statisk referensdata i lagring.js, ingen hämtning.
cp viz/lagring.html viz/lagring.js docs/
# Modellsektionen – fristående sidor, ingen data och inga byggberoenden.
mkdir -p docs/modell
cp viz/modell/*.html docs/modell/
if [ -f viz/data/consumption-eu-data.js ]; then
  cp viz/consumption-eu.html docs/
  cp viz/data/consumption-eu-data.js docs/data/
fi
# Kina vs världen – bara om OWID-datan byggts.
if [ -f viz/data/ember-data.js ]; then
  cp viz/china.html viz/china.js docs/
  cp viz/data/ember-data.js docs/data/
fi
# Elpris EU+Norge – bara om Energy-Charts-datan byggts.
if [ -f viz/data/euprices-data.js ]; then
  cp viz/euprices.html viz/euprices.js docs/
  cp viz/data/euprices-data.js docs/data/
fi

echo "docs/ klar:"
ls docs/
