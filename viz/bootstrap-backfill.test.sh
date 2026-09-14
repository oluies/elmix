#!/usr/bin/env bash
# Test av viz/bootstrap-backfill.sh. Stubbar mill i en temp-katalog så hela
# backfillen kan simuleras utan API-nyckel och utan att röra ENTSO-E.
#
#   ./viz/bootstrap-backfill.test.sh
#
# Backfillen är den enda vägen tillbaka från en kall cache, och den vägen är
# svår att prova skarpt: den utlöses bara när cachen faktiskt tappats, och
# kostar då timmar. Regressionstestas därför som golvkontrollen och
# hämtningsvakten, i stället för att bara finnas.
#
# Test 3 och 5 är de som skyddar rättelsen i #109: budgeten får inte överskridas
# (jobbet dör vid 6 h) och ett trasigt historiskt år får inte fälla dygnets
# hämtning och publicering.
set -uo pipefail
VIZ="$(cd "$(dirname "$0")" && pwd)"
IAR="$(date -u +%Y)"
FORRA=$((IAR - 1))
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

mkdir -p "$T/viz"
cp "$VIZ/bootstrap-backfill.sh" "$VIZ/retry.sh" "$T/viz/"
cd "$T" || exit 1

fails=0
pass() { echo "  PASS  $1"; }
fail() { fails=$((fails + 1)); echo "  FAIL  $1  $2"; }

# mill-stub: skriver årets alla filer, loggar anropet, och felar för de år som
# listas i STUB_FELAR. MILL_SOV simulerar att ett år tar tid (budgettestet).
cat > mill <<'STUB'
#!/usr/bin/env bash
ar=""
for a in "$@"; do case "$prev" in --start) ar="$a";; esac; prev="$a"; done
echo "$2 $ar" >> "$PWD/anrop.log"
case " ${STUB_FELAR:-} " in *" $ar "*) exit 1;; esac
[ -n "${MILL_SOV:-}" ] && sleep "$MILL_SOV"
if [ "$2" = fetcheu ]; then
  for s in generation prices load; do
    for z in DE_LU FR; do mkdir -p "data/raw/eu/$s"; echo x > "data/raw/eu/$s/${z}_$ar.parquet"; done
  done
else
  for s in generation prices imbalance flows; do
    for z in SE_1 SE_2 SE_3 SE_4; do mkdir -p "data/raw/$s"; echo x > "data/raw/$s/${z}_$ar.parquet"; done
  done
fi
exit 0
STUB
chmod +x mill

# Lägg upp ett komplett år på disk utan att gå via stubben.
lagg_upp_ar() {
  local ar="$1" s z
  for s in generation prices imbalance flows; do
    for z in SE_1 SE_2 SE_3 SE_4; do
      mkdir -p "data/raw/$s"; echo x > "data/raw/$s/${z}_$ar.parquet"
    done
  done
  for s in generation prices load; do
    for z in DE_LU FR; do
      mkdir -p "data/raw/eu/$s"; echo x > "data/raw/eu/$s/${z}_$ar.parquet"
    done
  done
}

kor() { # miljö som "VAR=x VAR=y" -> exitkod i $EXIT, utdata i $UT
  rm -f anrop.log
  UT="$(env "$@" PATH="$T:$PATH" RETRY_FORSOK=1 RETRY_PAUS=0 \
        bash viz/bootstrap-backfill.sh 2>&1)"
  EXIT=$?
}

# 1. Komplett historik -> rör inte mill alls.
rm -rf data
for a in $(seq 2016 "$FORRA"); do lagg_upp_ar "$a"; done
kor BACKFILL_FORSTA_AR=2016
if [ "$EXIT" = 0 ] && [ ! -f anrop.log ] && echo "$UT" | grep -q "är komplett"; then
  pass "komplett historik -> inga anrop"
else
  fail "komplett historik -> inga anrop" "exit $EXIT: $UT"
fi

# 2. Ett hål mitt i historiken fylls, och bara det.
rm -rf data
for a in $(seq 2016 "$FORRA"); do lagg_upp_ar "$a"; done
rm -f data/raw/flows/SE_3_2019.parquet
kor BACKFILL_FORSTA_AR=2016
if [ "$EXIT" = 0 ] && grep -q "^fetch 2019$" anrop.log &&
   [ "$(grep -c '^fetch ' anrop.log)" = 1 ] && [ -f data/raw/flows/SE_3_2019.parquet ]; then
  pass "ett hål fylls, övriga år rörs inte"
else
  fail "ett hål fylls, övriga år rörs inte" "exit $EXIT: $(cat anrop.log 2>/dev/null): $UT"
fi

# 3. Budgeten hålls. Fyra tomma år, budget 0 min -> inget år får påbörjas, och
#    skriptet ska säga vad som återstår i stället för att köra på.
rm -rf data
kor BACKFILL_FORSTA_AR=$((FORRA - 3)) BACKFILL_BUDGET_MIN=0
if [ "$EXIT" = 0 ] && [ ! -f anrop.log ] && echo "$UT" | grep -q "Budgeten slut" &&
   echo "$UT" | grep -q "kvar:.*$FORRA"; then
  pass "budget 0 -> inget påbörjas, återstoden rapporteras"
else
  fail "budget 0 -> inget påbörjas, återstoden rapporteras" "exit $EXIT: $UT"
fi

# 4. Nyast först: det år som publiceras närmast ska tas före 2016.
rm -rf data
kor BACKFILL_FORSTA_AR=$((FORRA - 2))
if [ "$EXIT" = 0 ] && [ "$(grep '^fetch ' anrop.log | head -1)" = "fetch $FORRA" ]; then
  pass "nyast först"
else
  fail "nyast först" "exit $EXIT: $(cat anrop.log 2>/dev/null)"
fi

# 5. Ett trasigt år får inte fälla körningen - dygnets hämtning och publicering
#    ligger efter det här steget i workflowen.
rm -rf data
for a in $(seq 2016 "$FORRA"); do lagg_upp_ar "$a"; done
rm -rf data/raw/generation/SE_1_2018.parquet
kor BACKFILL_FORSTA_AR=2016 STUB_FELAR=2018
if [ "$EXIT" = 0 ] && echo "$UT" | grep -q "VARNING: backfill av 2018" &&
   echo "$UT" | grep -q "kvar:.*2018"; then
  pass "trasigt år -> varning, exit 0"
else
  fail "trasigt år -> varning, exit 0" "exit $EXIT: $UT"
fi

# 6. Två raka nitar = systemfel (ENTSO-E nere, nyckel utgången). Sluta mala i
#    stället för att bränna hela budgeten varje dygn.
rm -rf data
kor BACKFILL_FORSTA_AR=$((FORRA - 4)) STUB_FELAR="$FORRA $((FORRA - 1)) $((FORRA - 2))"
if [ "$EXIT" = 0 ] && echo "$UT" | grep -q "två år i rad" &&
   [ "$(grep -c '^fetch ' anrop.log)" = 2 ]; then
  pass "två raka nitar -> avbryter"
else
  fail "två raka nitar -> avbryter" "exit $EXIT: $(grep -c '^fetch ' anrop.log 2>/dev/null) anrop: $UT"
fi

# 7. Innevarande år ägs av fetch-year.sh och får aldrig röras här - annars ser
#    backfillen årets halvfärdiga filer som ett klart år.
rm -rf data
kor BACKFILL_FORSTA_AR=$((FORRA - 1))
if [ "$EXIT" = 0 ] && ! grep -q " $IAR$" anrop.log; then
  pass "innevarande år rörs inte"
else
  fail "innevarande år rörs inte" "exit $EXIT: $(cat anrop.log 2>/dev/null)"
fi

# 8. DE/FR hämtas bara från sitt eget startår, och ett eu-fel fäller inte året.
rm -rf data
kor BACKFILL_FORSTA_AR=$((FORRA - 1)) BACKFILL_EU_FORSTA_AR="$FORRA"
if [ "$EXIT" = 0 ] && grep -q "^fetcheu $FORRA$" anrop.log &&
   ! grep -q "^fetcheu $((FORRA - 1))$" anrop.log; then
  pass "eu bara från sitt startår"
else
  fail "eu bara från sitt startår" "exit $EXIT: $(cat anrop.log 2>/dev/null)"
fi

if [ "$fails" = 0 ]; then echo "Alla backfill-tester gröna."
else echo "$fails test misslyckades." >&2; exit 1; fi
