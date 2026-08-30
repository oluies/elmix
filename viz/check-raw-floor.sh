#!/usr/bin/env bash
# Golvkontroll för innevarande års rådata.
#
# Regressionsvakten i viz/fetch-year.sh kräver bara att inget som fanns FÖRE rensningen
# försvann. Den duger inte när baslinjen redan är trasig: korningen 2026-08-29
# (run 33271325417) publicerade ett 2026 utan generation/flows/imbalance, och
# cachen som sparades saknar dem nu. Nästa körning jämför då mot ett redan
# tomt läge och ser inget fel.
#
# Den här kontrollen är absolut i stället för relativ: varje zon och dataset
# MÅSTE finnas, ha rader, och vara färsk. Körs först i viz/build-reports.sh
# och är därmed byggstegets ingångsvillkor - den kan alltså köras utan att
# någon hämtning föregått den i samma anrop. fetch-year.sh kör den också,
# men bara rådgivande.
#
#   ./viz/check-raw-floor.sh [ÅR]
#
# Trösklar (dagar) kan justeras via miljön:
#   GOLV_MAX_ALDER      generation/prices - hårt fel (default 7)
#   GOLV_MAX_ALDER_OVR  flows/imbalance   - bara varning (default 14)
set -euo pipefail
cd "$(dirname "$0")/.."

YEAR="${1:-$(date +%Y)}"
MAX_ALDER="${GOLV_MAX_ALDER:-7}"
MAX_ALDER_OVR="${GOLV_MAX_ALDER_OVR:-14}"
ZONER="SE_1 SE_2 SE_3 SE_4"
# generation och prices driver den publicerade rullande vyn direkt - blir de
# gamla syns det pa sajten, darfor hart fel. flows/imbalance slapar mer hos
# ENTSO-E och far bara varna.
HARDA="generation prices"
MJUKA="flows imbalance"

fel=0

# 1. Filerna måste finnas. Det här är exakt det som brast 2026-08-29: apiGet
#    returnerar tomt både vid "ingen data" och vid HTTP-fel, writeParquet
#    hoppar över filen, och fetch exitar ändå 0.
# Vid årsskiftet finns ännu inte allt: A85-obalans (och ibland flöden) släpar
# ett dygn eller mer hos ENTSO-E, så 1 januari 05:17 saknas de legitimt. Låt
# dem varna i stället för att fälla under de första dygnen - generation och
# prices krävs alltid, och det är generation som brast 2026-08-29.
nyar=0
if [ "$YEAR" = "$(date -u +%Y)" ] && [ "$(date -u +%j | sed 's/^0*//')" -le "${GOLV_NYAR_DAGAR:-3}" ]; then
  nyar=1
  echo "Golvkontroll: årets $(date -u +%j | sed 's/^0*//'):e dygn - flows/imbalance får saknas än" >&2
fi

saknade=""
for slag in $HARDA $MJUKA; do
  for zon in $ZONER; do
    f="data/raw/$slag/${zon}_${YEAR}.parquet"
    [ -f "$f" ] && continue
    case " $MJUKA " in
      *" $slag "*)
        if [ "$nyar" = 1 ]; then
          echo "VARNING: $f saknas (årsskifte, tolereras)" >&2
          continue
        fi
        ;;
    esac
    saknade="$saknade  $f
"
  done
done
if [ -n "$saknade" ]; then
  echo "FEL: golvkontroll $YEAR - rådatafiler saknas:" >&2
  printf '%s' "$saknade" >&2
  fel=1
fi

# 2. Filerna som finns måste ha rader, och vara färska. En trunkerad fil
#    (t.ex. bara januari) passerar en ren existenskontroll men publicerar
#    ändå fel data.
sql=""
for slag in $HARDA $MJUKA; do
  ls data/raw/"$slag"/SE_*_"$YEAR".parquet >/dev/null 2>&1 || continue
  [ -n "$sql" ] && sql="$sql UNION ALL "
  sql="$sql SELECT '$slag', zone, count(*), date_diff('day', max(ts), now())
       FROM read_parquet('data/raw/$slag/SE_*_${YEAR}.parquet') GROUP BY zone"
done

sedda=""
if [ -n "$sql" ]; then
  while IFS=, read -r slag zon n alder; do
    [ -n "${slag:-}" ] || continue
    sedda="$sedda $slag/$zon"
    case " $HARDA " in
      *" $slag "*)
        if [ "$alder" -gt "$MAX_ALDER" ]; then
          echo "FEL: golvkontroll $YEAR - $slag $zon är $alder dagar gammal (max $MAX_ALDER)" >&2
          fel=1
        fi
        ;;
      *)
        [ "$alder" -gt "$MAX_ALDER_OVR" ] &&
          echo "VARNING: $slag $zon är $alder dagar gammal (max $MAX_ALDER_OVR)" >&2
        ;;
    esac
  done < <(duckdb -csv -noheader -c "$sql ORDER BY 1, 2")
fi

# En tom parquet bidrar med noll grupper och syns darfor inte alls i frågan
# ovan - den passerar bade existenskontrollen och radloopen. Kraev i stallet
# att varje fil som FINNS ocksa dok upp i resultatet.
for slag in $HARDA $MJUKA; do
  for zon in $ZONER; do
    [ -f "data/raw/$slag/${zon}_${YEAR}.parquet" ] || continue
    case " $sedda " in
      *" $slag/$zon "*) ;;
      *)
        echo "FEL: golvkontroll $YEAR - $slag $zon har 0 rader" >&2
        fel=1
        ;;
    esac
  done
done

if [ "$fel" -ne 0 ]; then
  echo "Avbryter hellre än bygger marts och publicerar på ofullständig data." >&2
  exit 1
fi
echo "Golvkontroll $YEAR: alla $(echo $HARDA $MJUKA | wc -w | tr -d ' ') dataset × $(echo $ZONER | wc -w | tr -d ' ') zoner finns, har rader och är färska."
