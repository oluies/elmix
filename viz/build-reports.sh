#!/usr/bin/env bash
# Byggsteget: rådata -> marts -> rapporter i docs/. Rör aldrig ENTSO-E och
# behöver därför ingen API-nyckel. Valfritt år som argument (default i år) -
# året styr bara vilken årgång golvkontrollen granskar.
#
#   ./viz/build-reports.sh        # bygg om rapporterna på rådatan som finns
#   ./viz/build-reports.sh 2025
#
# Separerat från hämtningen (viz/fetch-year.sh) så att ett trasigt bygge kan
# köras om utan att först riskera en ny hämtning mot ett ENTSO-E som svarar 503.
#
# Golvkontrollen ligger först och är hela skyddet för det här steget: den är
# absolut, inte relativ, så den fäller även när rådatan var ofullständig redan
# innan. Det gör steget säkert att köra ensamt - det kan inte publicera på
# halvfärdig data oavsett vad som hänt tidigare i sekvensen.
set -euo pipefail
cd "$(dirname "$0")/.."

YEAR="${1:-$(date +%Y)}"

./viz/check-raw-floor.sh "$YEAR" || {
  echo "FEL: golvkontroll $YEAR - rådatan duger inte att bygga på." >&2
  echo "Kör hämtningssteget först: ./viz/fetch-year.sh $YEAR" >&2
  exit 1
}

# DuckDB-native flaky-kraschar slumpvis i CI (icke-deterministiskt, utan
# stacktrace, drabbar transform/pca oberoende – oftast vid teardown efter klart
# arbete). Båda skriver om sina marts och är därmed idempotenta, så en omkörning
# är säker.
retry() {
  local a
  for a in 1 2 3 4; do
    "$@" && return 0
    echo "  retry $a/4 (exit ≠0, flaky DuckDB-native): $*" >&2
    sleep 5
  done
  return 1
}

retry ./mill Elmix.scala transform || { echo "FEL: transform" >&2; exit 1; }
retry ./mill Elmix.scala pca || { echo "FEL: pca" >&2; exit 1; }
./viz/publish-pages.sh
echo "Klart – rapporterna ombyggda ($YEAR granskat av golvkontrollen)."
