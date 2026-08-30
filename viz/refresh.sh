#!/usr/bin/env bash
# Inkrementell data-refresh: hämtning + bygge i ett svep. Tunn omslagsfil kring
# de två stegen, som också går att köra var för sig när bara det ena gick fel:
#
#   ./viz/fetch-year.sh 2026      # bara hämtningen (ENTSO-E, kräver API-nyckel)
#   ./viz/build-reports.sh 2026   # bara marts + rapporter (ingen nyckel behövs)
#
# Valfritt år som argument (default i år).
#
#   ./viz/refresh.sh              # uppdatera innevarande år
#   ./viz/refresh.sh 2025         # tvinga om-hämtning av ett specifikt år
#
# Kräver ENTSOE_API_KEY i miljön; fetch-year.sh faller tillbaka till
# macOS-nyckelringen (service ENTSOE_API_KEY) om den finns.
set -euo pipefail
cd "$(dirname "$0")/.."

YEAR="${1:-$(date +%Y)}"

./viz/fetch-year.sh "$YEAR"
./viz/build-reports.sh "$YEAR"
