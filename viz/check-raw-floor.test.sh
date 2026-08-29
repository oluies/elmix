#!/usr/bin/env bash
# Test av viz/check-raw-floor.sh. Bygger syntetiska parquetfiler med duckdb i en
# temp-katalog och kör kontrollen mot varje felläge den ska fånga. Kräver duckdb.
#
#   ./viz/check-raw-floor.test.sh
set -uo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)/check-raw-floor.sh"
Y=2026
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/viz" && cp "$SRC" "$T/viz/" && cd "$T"
for s in generation prices flows imbalance; do mkdir -p "data/raw/$s"; done

fails=0
pass() { echo "  PASS  $1"; }
fail() { fails=$((fails + 1)); echo "  FAIL  $1  $2"; }

# En fil med N rader vars senaste ts ligger DAGAR dygn tillbaka.
mk() { # slag zon rader dagar
  duckdb -c "COPY (SELECT now() - INTERVAL ($4 + i) DAY AS ts, '$2' AS zone, 1.0 AS v
    FROM range(0, $3) t(i)) TO 'data/raw/$1/$2_${Y}.parquet' (FORMAT parquet)" >/dev/null
}
komplett() {
  rm -f data/raw/*/*.parquet
  for s in generation prices flows imbalance; do
    for z in SE_1 SE_2 SE_3 SE_4; do mk "$s" "$z" 50 0; done
  done
}
# Kör kontrollen tyst och svara OK/FEL.
utfall() { env "$@" bash viz/check-raw-floor.sh $Y >/dev/null 2>&1 && echo OK || echo FEL; }
vantar() { # namn forvantat [env...]
  local namn="$1" vantat="$2"; shift 2
  local fick; fick="$(utfall "$@")"
  [ "$fick" = "$vantat" ] && pass "$namn" || fail "$namn" "fick $fick, väntade $vantat"
}

komplett;                                        vantar "komplett och färskt" OK
komplett; rm data/raw/generation/SE_2_$Y.parquet; vantar "en generation-fil borta" FEL
komplett; rm data/raw/generation/*.parquet;      vantar "all generation borta (2026-08-29)" FEL
komplett; mk prices SE_3 0 0;                    vantar "fil med 0 rader" FEL
komplett; mk generation SE_1 50 30;              vantar "generation 30 dygn gammal" FEL
komplett; mk flows SE_4 50 30;                   vantar "flows 30 dygn gammal -> bara varning" OK
komplett; rm data/raw/imbalance/*.parquet;       vantar "årsskifte: imbalance får saknas" OK GOLV_NYAR_DAGAR=400
komplett; rm data/raw/generation/SE_1_$Y.parquet; vantar "årsskifte: generation krävs ändå" FEL GOLV_NYAR_DAGAR=400
komplett; rm data/raw/imbalance/*.parquet;       vantar "utanför grace: imbalance krävs" FEL GOLV_NYAR_DAGAR=0

if [ "$fails" = 0 ]; then echo "Alla golvkontrolltester gröna."
else echo "$fails test misslyckades." >&2; exit 1; fi
