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
cp viz/multiclock.js viz/co2.js viz/consumption-se.html docs/
if [ -f viz/data/consumption-eu-data.js ]; then
  cp viz/consumption-eu.html viz/co2.html docs/
  cp viz/data/consumption-eu-data.js docs/data/
fi

echo "docs/ klar:"
ls docs/
