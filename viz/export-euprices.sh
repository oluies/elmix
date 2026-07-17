#!/usr/bin/env bash
# Hämtar day-ahead-priser per elområde för EU + Norge från Energy-Charts
# (ENTSO-E/SMARD-härledd, CC BY 4.0, ingen API-nyckel) och aggregerar till
# månadsmedel per elområde -> viz/data/euprices-data.js.
#
# Ett anrop per zon för hela perioden (Energy-Charts klarar fleråriga intervall),
# sedan lokal aggregering. Energy-Charts rate-limitar hårt (HTTP 429) -> generös
# paus mellan zoner + exponentiell backoff. Icke-fatal per zon: saknas data
# hoppas zonen (null) utan att fälla bygget.
#
# Körs från projektroten: ./viz/export-euprices.sh
set -uo pipefail
cd "$(dirname "$0")/.."

START="${EUPRICES_START:-2020-01-01}"
END="${EUPRICES_END:-$(date -u +%Y-%m-%d)}"
OUT="viz/data/euprices-data.js"
TMP="$(mktemp -d)"
GAP="${EUPRICES_GAP:-4}"      # sekunder mellan zoner
mkdir -p viz/data

# bzn|label|land(sv)|cc|är_sverige   – ENTSO-E-elområden, EU + Norge
ZONES='
SE1|SE1|Sverige|SE|1
SE2|SE2|Sverige|SE|1
SE3|SE3|Sverige|SE|1
SE4|SE4|Sverige|SE|1
NO1|NO1|Norge|NO|0
NO2|NO2|Norge|NO|0
NO3|NO3|Norge|NO|0
NO4|NO4|Norge|NO|0
NO5|NO5|Norge|NO|0
DK1|DK1|Danmark|DK|0
DK2|DK2|Danmark|DK|0
FI|FI|Finland|FI|0
EE|EE|Estland|EE|0
LV|LV|Lettland|LV|0
LT|LT|Litauen|LT|0
DE-LU|DE-LU|Tyskland/Lux|DE|0
FR|FR|Frankrike|FR|0
NL|NL|Nederländerna|NL|0
BE|BE|Belgien|BE|0
AT|AT|Österrike|AT|0
CH|CH|Schweiz|CH|0
PL|PL|Polen|PL|0
CZ|CZ|Tjeckien|CZ|0
SK|SK|Slovakien|SK|0
HU|HU|Ungern|HU|0
SI|SI|Slovenien|SI|0
RO|RO|Rumänien|RO|0
RS|RS|Serbien|RS|0
GR|GR|Grekland|GR|0
ES|ES|Spanien|ES|0
PT|PT|Portugal|PT|0
IT-North|IT-North|Italien|IT|0
IT-Centre-North|IT-CNorth|Italien|IT|0
IT-Centre-South|IT-CSouth|Italien|IT|0
IT-South|IT-South|Italien|IT|0
IT-Sardinia|IT-Sard|Italien|IT|0
IT-Sicily|IT-Sicily|Italien|IT|0
IT-Calabria|IT-Cala|Italien|IT|0
'

fetch_zone() {  # $1=bzn -> skriver $TMP/$1.json, returnerar 0 om unix_seconds finns
  local bzn="$1" try wait=6
  for try in 1 2 3 4; do
    local code
    code=$(curl -s -o "$TMP/$bzn.json" -w '%{http_code}' \
      "https://api.energy-charts.info/price?bzn=$bzn&start=$START&end=$END")
    if [ "$code" = "200" ] && grep -q unix_seconds "$TMP/$bzn.json"; then return 0; fi
    echo "  $bzn: http $code (försök $try) – väntar ${wait}s" >&2
    perl -e "select(undef,undef,undef,$wait)"; wait=$((wait*2))
  done
  return 1
}

echo "Hämtar EU+Norge-priser $START..$END (Energy-Charts)..." >&2
: > "$TMP/zones.jsonl"
while IFS='|' read -r bzn label land cc se; do
  [ -z "$bzn" ] && continue
  if fetch_zone "$bzn"; then
    JSON="$TMP/$bzn.json" START="$START" ZLABEL="$label" ZLAND="$land" ZCC="$cc" ZSE="$se" ZCODE="$bzn" \
      python3 viz/euprices_agg.py >> "$TMP/zones.jsonl" \
      && echo "  $bzn ok" >&2 || echo "  $bzn: aggregering misslyckades" >&2
  else
    echo "  $bzn: ingen data (hoppas)" >&2
  fi
  perl -e "select(undef,undef,undef,$GAP)"
done <<< "$ZONES"

UPDATED="$(date -u +%Y-%m-%d)" python3 viz/euprices_build.py "$TMP/zones.jsonl" "$OUT" \
  && echo "Skrev $OUT ($(wc -c < "$OUT") byte)" >&2
rm -rf "$TMP"
