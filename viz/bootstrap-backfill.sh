#!/usr/bin/env bash
# Backfill av historiska år, tidsbudgeterad.
#
#   ./viz/bootstrap-backfill.sh
#
# Ersätter bootstrap-steget som låg inline i .github/workflows/refresh.yml. Det
# hade två fel efter att ENTSO-E kapade A75/A11 till en månad per anrop (#109):
#
# 1. Det fick inte plats. En full hämtning 2016..idag gick från ~800 till
#    ~5 200 API-anrop. Flödena ensamma mäter 3,5 s/anrop (1 057 s för 306
#    anrop, uppmätt), så elva år flöden är ~4 h och generationen ~1 h till.
#    Ett GitHub-jobb dör vid 6 h, och dygnskörningen behöver 50 min av dem
#    till innevarande år och bygget.
# 2. Det återupptog inte. Grinden var `ls data/raw/generation/SE_*.parquet`,
#    alltså "finns NÅGON generationsfil?". En bootstrap som dog halvvägs
#    gjorde grinden falsk för all framtid, och hålen efter den fylldes aldrig.
#
# Här görs i stället ett år i taget tills budgeten är slut, och "kvar att göra"
# räknas ur vilka filer som faktiskt saknas. Det som inte hinns med tas av
# nästa körning: ett kallt spann fylls på ett par dygn i stället för att ligga
# i ett jobb som aldrig går i mål.
#
# Nyast först, för att historiken är olika mycket värd. De rullande vyerna och
# BESS-analysen står på de senaste åren; 2016 är trevligt att ha men ingen
# saknar det ett dygn extra.
#
# Innevarande år hämtas INTE här - det ägs av viz/fetch-year.sh, som tvingar om
# hämtning av det varje dygn. Skulle båda röra det vore mill:s skipIfExists
# (filnivå) det enda som skilde dem åt, och då skulle backfillen se årets
# halvfärdiga filer som "klara".
#
# Faller aldrig dygnskörningen: en trasig backfill av 2019 får inte hindra att
# dagens data hämtas, byggs och publiceras. Skriptet exitar därför alltid 0 och
# skriver vad som återstår i stället - raden "kvar:" i loggen är signalen.
#
# Budget och spann kan justeras via miljön:
#   BACKFILL_BUDGET_MIN   minuter att lägga per körning (default 150)
#   BACKFILL_FORSTA_AR    äldsta år att fylla på (default 2016)
#   BACKFILL_EU_FORSTA_AR äldsta år för DE/FR (default 2025)
set -uo pipefail
VIZ="$(cd "$(dirname "$0")" && pwd)"
# Skriptet kör utan `set -e` (se ovan), så ett misslyckat cd skulle annars gå
# vidare i fel katalog - och där saknas varenda parquet, alltså "hela
# historiken är borta, hämta om 2016..i fjol".
cd "$VIZ/.." || { echo "FEL: kan inte nå repot från $VIZ" >&2; exit 1; }

BUDGET_MIN="${BACKFILL_BUDGET_MIN:-150}"
FORSTA_AR="${BACKFILL_FORSTA_AR:-2016}"
EU_FORSTA_AR="${BACKFILL_EU_FORSTA_AR:-2025}"
ZONER="SE_1 SE_2 SE_3 SE_4"
SLAG="generation prices imbalance flows"
EU_ZONER="DE_LU FR"
EU_SLAG="generation prices load"

# shellcheck source=viz/retry.sh
. "$VIZ/retry.sh"

SISTA_AR=$(( $(date -u +%Y) - 1 ))

# Ett år återstår så länge någon av dess filer saknas. Grovt med flit: mill:s
# skipIfExists hoppar över det som redan ligger på disk, så ett halvfärdigt år
# kostar bara resten av det - inte en omhämtning.
ar_kvar() {
  local ar="$1" slag zon
  for slag in $SLAG; do
    for zon in $ZONER; do
      [ -f "data/raw/$slag/${zon}_${ar}.parquet" ] || return 0
    done
  done
  if [ "$ar" -ge "$EU_FORSTA_AR" ]; then
    for slag in $EU_SLAG; do
      for zon in $EU_ZONER; do
        [ -f "data/raw/eu/$slag/${zon}_${ar}.parquet" ] || return 0
      done
    done
  fi
  return 1
}

aterstaende() {
  local ar ut=""
  [ "$SISTA_AR" -lt "$FORSTA_AR" ] && { echo ""; return; }
  for ar in $(seq "$SISTA_AR" -1 "$FORSTA_AR"); do
    ar_kvar "$ar" && ut="$ut $ar"
  done
  echo "$ut"
}

kvar="$(aterstaende)"
if [ -z "$kvar" ]; then
  echo "Backfill: historiken $FORSTA_AR..$SISTA_AR är komplett - inget att göra."
  exit 0
fi

antal_kvar="$(echo "$kvar" | wc -w | tr -d ' ')"
echo "Backfill: $antal_kvar år att fylla på ($BUDGET_MIN min budget):$kvar"

deadline=$(( $(date +%s) + BUDGET_MIN * 60 ))
gjorda=0
misslyckade_i_rad=0
# Budgeten prövas bara MELLAN år - ett påbörjat år körs klart. Ett historiskt
# år mäter ~35 min (flöden ~24, generation ~7), så överdraget är bundet till
# ungefär det. Att avbryta mitt i ett år skulle inte spara något ändå: mill
# skriver en parquet per zon och år, så ett avbrutet år ger inga filer.
for ar in $kvar; do
  nu="$(date +%s)"
  if [ "$nu" -ge "$deadline" ]; then
    echo "Budgeten slut efter $gjorda år - resten tas av kommande körningar."
    break
  fi
  echo "Backfill $ar ($(( (deadline - nu) / 60 )) min kvar av budgeten)"

  ok=1
  retry ./mill Elmix.scala fetch --start "$ar" --end "$ar" --data all || ok=0
  if [ "$ar" -ge "$EU_FORSTA_AR" ]; then
    # DE/FR bär bara jämförelsevyerna - varna, fäll inte året för deras skull.
    retry ./mill Elmix.scala fetcheu --start "$ar" --end "$ar" ||
      echo "VARNING: eu-backfill av $ar misslyckades - DE/FR saknas för det året." >&2
  fi

  if [ "$ok" = 1 ]; then
    gjorda=$((gjorda + 1))
    misslyckade_i_rad=0
  else
    echo "VARNING: backfill av $ar misslyckades - nästa körning tar om det." >&2
    misslyckade_i_rad=$((misslyckade_i_rad + 1))
    # Två raka nitar är inte otur med ett enskilt år, det är ENTSO-E som ligger
    # nere eller en nyckel som slutat gälla. Sluta då mala - annars bränns hela
    # budgeten varje dygn på något som ändå inte kan lyckas.
    if [ "$misslyckade_i_rad" -ge 2 ]; then
      echo "VARNING: två år i rad misslyckades - avbryter backfillen." >&2
      break
    fi
  fi
done

efter="$(aterstaende)"
if [ -n "$efter" ]; then
  echo "Backfill: $gjorda år klara den här körningen, kvar:$efter"
else
  echo "Backfill: $gjorda år klara - historiken $FORSTA_AR..$SISTA_AR är nu komplett."
fi
exit 0
