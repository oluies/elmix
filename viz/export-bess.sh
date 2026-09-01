#!/usr/bin/env bash
# Hämtar day-ahead-priser för SE1-SE4 från Energy-Charts (ENTSO-E/SMARD-härledd,
# CC BY 4.0, ingen API-nyckel) och räknar dygnsvisa prisspreadar per år och
# varaktighet -> viz/data/bess-data.js.
#
# Ett anrop per zon för hela perioden; Energy-Charts klarar fleråriga intervall
# men rate-limitar hårt (HTTP 429), så generös paus mellan zoner och
# exponentiell backoff. Samma mönster som export-euprices.sh.
#
# Icke-fatal: faller tillbaka på redan publicerad payload i docs/ om
# hämtningen inte går igenom. Fallbacken kontrollerar att filen är ANVÄNDBAR,
# inte bara att den finns - en tidigare bugg i det här repot skrev en giltig
# men tom fil som passerade `-f` och blankade tre diagram.
#
# Körs från projektroten: ./viz/export-bess.sh
set -uo pipefail
cd "$(dirname "$0")/.."

START="${BESS_START:-2020-01-01}"
END="${BESS_END:-$(date -u +%Y-%m-%d)}"
OUT="viz/data/bess-data.js"
GAP="${BESS_GAP:-8}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p viz/data

fetch_zone() {  # $1=zon -> $TMP/$1.json
  local z="$1" try wait=6 code
  for try in 1 2 3 4; do
    code=$(curl -s -o "$TMP/$z.json" -w '%{http_code}' \
      "https://api.energy-charts.info/price?bzn=$z&start=$START&end=$END")
    if [ "$code" = "200" ] && grep -q unix_seconds "$TMP/$z.json"; then return 0; fi
    echo "  $z: http $code (försök $try) - väntar ${wait}s" >&2
    perl -e "select(undef,undef,undef,$wait)"; wait=$((wait * 2))
  done
  rm -f "$TMP/$z.json"
  return 1
}

echo "Hämtar SE1-SE4 $START..$END (Energy-Charts)..." >&2
for z in SE1 SE2 SE3 SE4; do
  if fetch_zone "$z"; then echo "  $z ok" >&2; else echo "  $z: ingen data (hoppas)" >&2; fi
  perl -e "select(undef,undef,undef,$GAP)"
done

if UPDATED="$(date -u +%Y-%m-%d)" python3 viz/bess_agg.py "$TMP" "$OUT"; then
  exit 0
fi

echo "VARNING: bess-uttaget misslyckades - försöker återanvända publicerad payload" >&2
# Inte bara -f: filen måste innehålla en spread för minst en zon. Annars är den
# lika värdelös som ingen fil alls, och sidan skulle se publicerad ut men tom.
# Kräver inte bara att nycklarna finns utan att det ligger en faktisk spread
# under dem: "hi" skrivs bara av bess_agg.py när ett dygn gett både ett köp- och
# ett säljfönster. En payload med tomma zoner passerar de två första villkoren.
if [ -f "docs/data/bess-data.js" ] && grep -q '"spread"' "docs/data/bess-data.js" &&
   grep -qE '"SE[1-4]"' "docs/data/bess-data.js" &&
   grep -q '"hi"' "docs/data/bess-data.js"; then
  cp "docs/data/bess-data.js" "$OUT"
  echo "  återanvände docs/data/bess-data.js" >&2
  exit 0
fi
echo "FEL: ingen användbar bess-payload - sidan kommer säga att datan saknas" >&2
exit 1
