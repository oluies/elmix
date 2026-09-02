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

. "$(dirname "$0")/retry.sh"

# Vilka SE-filer fanns för året innan vi rensade? apiGet returnerar tomt både vid
# "ingen data" och vid HTTP-fel - ENTSO-E svarar 400 + Acknowledgement, eller 503
# + en HTML-underhållssida - och writeParquet hoppar då över filen UTAN att fetch
# felar. Ett dataset kan alltså försvinna tyst, och bygget skulle annars gå
# vidare på ofullständig data. Listan jämförs efter hämtningen; se kontrollen
# nedan.
# `|| true`: vid arsskiftet matchar globben inget, ls exitar 2, pipefail
# propagerar det och errexit dodar steget INNAN mill fetch hinner kora -
# och eftersom inget hamtas ser nasta dygn likadant ut. Tomt varde ar
# redan korrekt hanterat av `if [ -n "$fanns_innan" ]` nedan.
fanns_innan="$(ls data/raw/*/SE_*_"$YEAR".parquet 2>/dev/null | sort || true)"

# Flödena är den enda hämtningen som får misslyckas utan att fälla steget. Bara
# förbrukningsmixen läser raw_flows, medan generation, pris och obalans bär
# resten av sajten - en ändpunkt som inte svarar ska inte blockera en ombyggnad
# av sidor som inte behöver den. Den 29-30 augusti 2026 låg datarefreshen två
# och en halv timme på just flödena och docs/ byggdes inte om alls. Per-slag-
# budgeten i apiGet kortar numera ned den väntan; det här gör att den inte
# heller fäller resten. Spara undan förra körningens flöden före rensningen, så
# att ett misslyckande lämnar gammal data i stället för ett hål.
flowdir="data/raw/flows"
flowbackup="$(mktemp -d)"
cp "$flowdir"/SE_*_"$YEAR".parquet "$flowbackup"/ 2>/dev/null || true

rm -f data/raw/*/SE_*_"$YEAR".parquet
rm -f data/raw/eu/*/*_"$YEAR".parquet

# De tre datamängder bygget faktiskt står på, var för sig så felet pekar ut
# vilken som brast i stället för att bara säga "fetch".
for kind in generation prices imbalance; do
  retry ./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data "$kind" \
    || { echo "FEL: fetch $kind $YEAR" >&2; exit 1; }
done

flows_ok=1
retry ./mill Elmix.scala fetch --start "$YEAR" --end "$YEAR" --data flows || flows_ok=0
if [ "$flows_ok" -eq 0 ]; then
  mkdir -p "$flowdir"
  if cp "$flowbackup"/*.parquet "$flowdir"/ 2>/dev/null; then
    echo "VARNING: flödeshämtningen för $YEAR misslyckades - förra körningens flöden återställda." >&2
  else
    echo "VARNING: flödeshämtningen för $YEAR misslyckades och det fanns inget att återställa." >&2
  fi
  echo "Bara förbrukningsmixen läser flöden; resten av bygget går vidare på färsk data." >&2
  # Kravet nedan får då inte gälla flödena, annars faller steget ändå på det vi
  # nyss bestämde oss för att överleva. grep lämnar inga tomma rader efter sig:
  # filtreras allt bort blir värdet tomt, vilket [ -n ] nedan redan hanterar.
  if [ -n "$fanns_innan" ]; then
    fanns_innan="$(printf '%s\n' "$fanns_innan" | grep -v "^$flowdir/" || true)"
  fi
fi
rm -rf "$flowbackup"

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
  echo "Ligger felet hos ENTSO-E? Det finns ingen live-statustavla, men driftnotiser och" >&2
  echo "supportärenden går via https://transparencyplatform.zendesk.com/hc/en-us" >&2
  exit 1
fi

# DE/FR (icke-fatal – bryt inte hela hämtningen om kontinentala strular)
retry ./mill Elmix.scala fetcheu --start "$YEAR" --end "$YEAR" || echo "VARNING: fetcheu $YEAR misslyckades – DE/FR ej uppdaterat" >&2

# Regressionsvakten ovan ser bara vad som FORSVANN. Ar baslinjen redan
# trasig sager den inget, och en steg=hamta-korning skulle da rapportera
# "klart" trots att bygget kommer falla. Golvkontrollen ar absolut och
# svarar pa den fragan - radgivande har, avgorande i build-reports.sh.
./viz/check-raw-floor.sh "$YEAR" ||
  echo "VARNING: golvkontrollen faller - bygget kommer inte ga igenom" >&2

echo "Hämtning klar för $YEAR. Bygg med: ./viz/build-reports.sh $YEAR"
