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

# DuckDB-native kan ge en flaky mid-run-krasch i CI (icke-deterministiskt,
# utan stacktrace). Fetch är inkrementell (skipIfExists) så en omkörning
# ÅTERUPPTAR automatiskt – retry tills den lyckas (max 4 försök).
for attempt in 1 2 3 4; do
  if ./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data all; then break; fi
  echo "  fetch $YEAR: försök $attempt gav exit ≠0 – återupptar inkrementellt..." >&2
  [ "$attempt" = 4 ] && { echo "FEL: fetch $YEAR misslyckades efter 4 försök" >&2; exit 1; }
  sleep 5
done

./mill Elmix.scala transform
# pca skriver sina marts + "Klart", men mill-subprocessen kan i CI exita non-zero
# vid JVM-avslut (flaky, utan stacktrace). Acceptera om marterna faktiskt skrevs.
if ! ./mill Elmix.scala pca; then
  if [ -f data/marts/pca_scores.parquet ] && [ data/marts/pca_scores.parquet -nt elmix.duckdb ]; then
    echo "OBS: 'mill pca' gav exitkod ≠0 men pca-marts skrevs nyss – fortsätter."
  else
    echo "FEL: pca misslyckades (pca-marts saknas eller föråldrade)" >&2; exit 1
  fi
fi
./viz/publish-pages.sh
echo "Klart – rapporterna ombyggda med $YEAR uppdaterat."
