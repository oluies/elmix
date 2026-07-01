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
rm -f data/raw/eu/*/*_"$YEAR".parquet

# DuckDB-native flaky-kraschar slumpvis i CI (icke-deterministiskt, utan
# stacktrace, drabbar fetch/transform/pca oberoende – oftast vid teardown efter
# klart arbete). Alla tre är idempotenta (fetch inkrementell, transform/pca
# skriver om sina marts) så en omkörning är säker – retry runt var och en.
retry() {
  local a
  for a in 1 2 3 4; do
    "$@" && return 0
    echo "  retry $a/4 (exit ≠0, flaky DuckDB-native): $*" >&2
    sleep 5
  done
  return 1
}
retry ./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data all || { echo "FEL: fetch $YEAR" >&2; exit 1; }
# DE/FR (icke-fatal – bryt inte hela refreshen om kontinentala hämtningen strular)
retry ./mill Elmix.scala fetcheu --start "$YEAR" --end "$YEAR" || echo "VARNING: fetcheu $YEAR misslyckades – DE/FR ej uppdaterat" >&2
retry ./mill Elmix.scala transform || { echo "FEL: transform" >&2; exit 1; }
retry ./mill Elmix.scala pca || { echo "FEL: pca" >&2; exit 1; }
./viz/publish-pages.sh
echo "Klart – rapporterna ombyggda med $YEAR uppdaterat."
