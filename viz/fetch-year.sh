#!/usr/bin/env bash
# Hämtningssteget: tvingar om-hämtning av ETT år från ENTSO-E och verifierar att
# inget dataset tappades på vägen. Skriver bara data/raw - rör varken marts,
# elmix.duckdb eller docs/. Valfritt år som argument (default i år).
#
#   ./viz/fetch-year.sh           # hämta om innevarande år
#   ./viz/fetch-year.sh 2025      # tvinga om-hämtning av ett specifikt år
#
# Separerat från bygget (viz/build-reports.sh) för att stegen felar av helt
# olika skäl: hämtningen faller på ENTSO-E:s uppetid, bygget på DuckDB. Går de
# i samma steg tvingar ett trasigt bygge fram en ny 17-minuters hämtning, och
# varje sådan hämtning är ett nytt lotteri mot samma 503:or som fällde den
# förra. Kör om det steg som faktiskt gick sönder i stället.
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
echo "Hämtning: tvingar om-hämtning av $YEAR (tidigare år hoppas över inkrementellt)"

# DuckDB-native flaky-kraschar slumpvis i CI (icke-deterministiskt, utan
# stacktrace, drabbar fetch/transform/pca oberoende – oftast vid teardown efter
# klart arbete). fetch är inkrementell och därmed idempotent, så en omkörning är
# säker.
retry() {
  local a
  for a in 1 2 3 4; do
    "$@" && return 0
    echo "  retry $a/4 (exit ≠0, flaky DuckDB-native): $*" >&2
    sleep 5
  done
  return 1
}

# Vilka SE-filer fanns för året innan vi rensade? apiGet returnerar tomt både vid
# "ingen data" och vid HTTP-fel - ENTSO-E svarar 400 + Acknowledgement, eller 503
# + en HTML-underhållssida - och writeParquet hoppar då över filen UTAN att fetch
# felar. Ett dataset kan alltså försvinna tyst, och bygget skulle annars gå
# vidare på ofullständig data. Listan jämförs efter hämtningen; se kontrollen
# nedan.
fanns_innan="$(ls data/raw/*/SE_*_"$YEAR".parquet 2>/dev/null | sort)"
rm -f data/raw/*/SE_*_"$YEAR".parquet
rm -f data/raw/eu/*/*_"$YEAR".parquet

retry ./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data all || { echo "FEL: fetch $YEAR" >&2; exit 1; }

# Fetch kan lyckas (exit 0) och ändå ha tappat ett helt dataset, se kommentaren
# vid fanns_innan. Kräv att allt som fanns före rensningen finns igen.
saknas=""
if [ -n "$fanns_innan" ]; then
  while IFS= read -r f; do
    [ -f "$f" ] || saknas="$saknas  $f
"
  done <<< "$fanns_innan"
fi
if [ -n "$saknas" ]; then
  echo "FEL: hämtningen av $YEAR tappade filer som fanns före omhämtningen:" >&2
  printf '%s' "$saknas" >&2
  echo "ENTSO-E svarade sannolikt 400/Acknowledgement eller 503 för dessa (se HTTP-raderna ovan)." >&2
  echo "Kör om hämtningssteget - bygget rörs inte och behöver inte göras om." >&2
  exit 1
fi

# DE/FR (icke-fatal – bryt inte hela hämtningen om kontinentala strular)
retry ./mill Elmix.scala fetcheu --start "$YEAR" --end "$YEAR" || echo "VARNING: fetcheu $YEAR misslyckades – DE/FR ej uppdaterat" >&2

echo "Hämtning klar för $YEAR. Bygg med: ./viz/build-reports.sh $YEAR"
