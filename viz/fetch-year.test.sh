#!/usr/bin/env bash
# Test av viz/fetch-year.sh:s regressionsvakt. Stubbar mill i en temp-katalog så
# hela hämtningen kan simuleras utan API-nyckel och utan att röra ENTSO-E.
#
#   ./viz/fetch-year.test.sh
#
# Vakten är hela skälet till att skriptet finns - den fångar att apiGet returnerar
# tomt både vid "ingen data" och vid HTTP-fel - men den hade ingen täckning, till
# skillnad från check-raw-floor.sh. Den tredje testet nedan är det som skulle ha
# fångat årsskiftesbuggen: tom glob under pipefail dödade steget innan mill kördes.
set -uo pipefail
VIZ="$(cd "$(dirname "$0")" && pwd)"
Y=2026
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

mkdir -p "$T/viz"
cp "$VIZ/fetch-year.sh" "$VIZ/retry.sh" "$T/viz/"
# Golvkontrollen körs rådgivande i slutet av fetch-year.sh och ska inte påverka
# utfallet här - stubba den som en no-op så testet mäter bara vakten.
printf '#!/usr/bin/env bash\nexit 0\n' > "$T/viz/check-raw-floor.sh"
chmod +x "$T/viz/check-raw-floor.sh"
cd "$T"
for s in generation prices flows imbalance; do mkdir -p "data/raw/$s"; done
mkdir -p data/raw/eu/generation

fails=0
pass() { echo "  PASS  $1"; }
fail() { fails=$((fails + 1)); echo "  FAIL  $1  $2"; }

# mill-stub: skriver de filer som listas i STUB_SKRIVER, ignorerar allt annat.
# Efterliknar writeParquet, som hoppar över filen utan att fetch felar.
cat > mill <<'STUB'
#!/usr/bin/env bash
case "${2:-}" in
  fetch)
    for f in $STUB_SKRIVER; do mkdir -p "$(dirname "$f")"; echo x > "$f"; done ;;
  fetcheu) : ;;
esac
exit 0
STUB
chmod +x mill

ALLA=""
for s in generation prices flows imbalance; do
  for z in SE_1 SE_2 SE_3 SE_4; do ALLA="$ALLA data/raw/$s/${z}_$Y.parquet"; done
done

# Utgångsläge: alla filer finns före körningen.
lagg_upp() { rm -f data/raw/*/*.parquet; for f in $ALLA; do echo x > "$f"; done; }

kor() { # STUB_SKRIVER-lista -> exitkod + stderr i $UT
  UT="$(STUB_SKRIVER="$1" ENTSOE_API_KEY=dummy PATH="$T:$PATH" \
        bash viz/fetch-year.sh "$Y" 2>&1)"
  echo "$?" > exitkod
}

# 1. Allt kommer tillbaka -> vakten ska släppa igenom.
lagg_upp
kor "$ALLA"
if [ "$(cat exitkod)" = 0 ]; then pass "alla filer återskapade -> exit 0"
else fail "alla filer återskapade -> exit 0" "exit $(cat exitkod): $UT"; fi

# 2. En fil kommer inte tillbaka -> vakten ska fälla OCH namnge filen. Det här
#    är precis det som hände 2026-08-30: ENTSO-E svarade 503 för prices SE_2.
lagg_upp
UTAN="$(echo "$ALLA" | tr ' ' '\n' | grep -v "prices/SE_2_$Y" | tr '\n' ' ')"
kor "$UTAN"
if [ "$(cat exitkod)" != 0 ] && echo "$UT" | grep -q "prices/SE_2_$Y.parquet"; then
  pass "tappad fil -> fäller och namnger filen"
else
  fail "tappad fil -> fäller och namnger filen" "exit $(cat exitkod): $UT"
fi

# 3. Årsskifte: inga filer för året finns innan. Globben matchar inget, och utan
#    `|| true` dödar pipefail+errexit steget innan mill ens körs. Kräv att mill
#    faktiskt anropades och att skriptet gick igenom.
rm -f data/raw/*/*.parquet
kor "$ALLA"
if [ "$(cat exitkod)" = 0 ] && [ -f "data/raw/prices/SE_1_$Y.parquet" ]; then
  pass "årsskifte: tom glob stoppar inte hämtningen"
else
  fail "årsskifte: tom glob stoppar inte hämtningen" "exit $(cat exitkod): $UT"
fi

# 4. Årsvalideraren ska avvisa skräp och släppa igenom tomt/giltigt.
ok_ar() { bash "$VIZ/kontrollera-ar.sh" "$1" >/dev/null 2>&1; }
if ok_ar "" && ok_ar 2026 && ! ok_ar "2026; echo pwned" && ! ok_ar "2026 2025" &&
   ! ok_ar "-rf" && ! ok_ar 1999; then
  pass "kontrollera-ar: släpper igenom tomt/2026, avvisar skräp"
else
  fail "kontrollera-ar: släpper igenom tomt/2026, avvisar skräp" "se ovan"
fi

if [ "$fails" = 0 ]; then echo "Alla hämtningstester gröna."
else echo "$fails test misslyckades." >&2; exit 1; fi
