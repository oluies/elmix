#!/usr/bin/env bash
# Inkrementell data-refresh: tvingar om-hämtning av INNEVARANDE år (vars Parquet
# växer dag för dag), medan tidigare år hoppas över automatiskt (skipIfExists).
# Sedan transform -> pca -> publish. Valfritt år som argument (default i år).
#
#   ./viz/refresh.sh           # uppdatera innevarande år
#   ./viz/refresh.sh 2025      # tvinga om-hämtning av ett specifikt år
#
# Kräver ENTSOE_API_KEY i miljön; faller tillbaka till macOS-nyckelringen
# (service ENTSOE_API_KEY) om den finns.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${ENTSOE_API_KEY:-}" ] && command -v security >/dev/null 2>&1; then
  ENTSOE_API_KEY="$(security find-generic-password -s ENTSOE_API_KEY -w 2>/dev/null || true)"
  export ENTSOE_API_KEY
fi
: "${ENTSOE_API_KEY:?Sätt ENTSOE_API_KEY (eller lägg den i macOS-nyckelringen som service ENTSOE_API_KEY)}"

YEAR="${1:-$(date +%Y)}"
echo "Refresh: tvingar om-hämtning av $YEAR (tidigare år hoppas över inkrementellt)"
rm -f data/raw/*/SE_*_"$YEAR".parquet
./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data all
./mill Elmix.scala transform
./mill Elmix.scala pca
./viz/publish-pages.sh
echo "Klart – rapporterna ombyggda med $YEAR uppdaterat."
