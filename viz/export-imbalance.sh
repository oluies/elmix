#!/usr/bin/env bash
# Hämtar nordiska obalanspriser per svenskt elområde från eSett open data
# (EXP14 Single Balance Prices per MBA, ingen API-nyckel) och skriver
#   viz/data/imbalance.parquet   – rådata per avräkningsperiod, för DuckDB-WASM
#   viz/data/imbalance-data.js   – föraggregerade histogram + statistik per år
#
# Fältet imblSpotDifferencePrice ÄR obalanspris minus spotpris, så hela serien
# kommer från ett anrop per zon och år. Ingen join mot day-ahead behövs.
#
# Enprismodellen gäller från nov 2021, så 2021 är ofullständigt och utelämnas
# ur aggregaten. Avräkningsperioden gick från timme till kvart 22 maj 2023.
#
# Körs från projektroten: ./viz/export-imbalance.sh
set -uo pipefail
cd "$(dirname "$0")/.."

START_YEAR="${IMBALANCE_START_YEAR:-2022}"
END_YEAR="$(date -u +%Y)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p viz/data

declare -a EIC_KEYS=(SE1 SE2 SE3 SE4)
eic_of() { case "$1" in
  SE1) echo 10Y1001A1001A44P ;; SE2) echo 10Y1001A1001A45N ;;
  SE3) echo 10Y1001A1001A46L ;; SE4) echo 10Y1001A1001A47J ;; esac; }

fail=0
for z in "${EIC_KEYS[@]}"; do
  for (( y=START_YEAR; y<=END_YEAR; y++ )); do
    end="$(( y + 1 ))-01-01T00:00:00.000Z"
    [ "$y" -eq "$END_YEAR" ] && end="$(date -u +%Y-%m-%d)T00:00:00.000Z"
    url="https://api.opendata.esett.com/EXP14/Prices?start=${y}-01-01T00:00:00.000Z&end=${end}&mba=$(eic_of "$z")"
    ok=0
    for attempt in 1 2 3; do
      if curl -sfL --max-time 300 "$url" -o "$TMP/${z}-${y}.json"; then ok=1; break; fi
      sleep $(( attempt * 5 ))
    done
    if [ "$ok" -ne 1 ]; then
      echo "VARNING: eSett svarade inte för $z $y – hoppas över" >&2
      rm -f "$TMP/${z}-${y}.json"; fail=1
    fi
  done
done

if ! ls "$TMP"/*.json >/dev/null 2>&1; then
  echo "FEL: ingen obalansdata hämtad – behåller befintlig export" >&2
  exit 1
fi

duckdb <<SQL
CREATE OR REPLACE TABLE imb AS
SELECT mba AS zone,
       CAST(timestampUTC AS TIMESTAMP) AS ts,
       CAST(imblSpotDifferencePrice AS DOUBLE) AS diff
FROM read_json_auto('$TMP/*.json', union_by_name = true)
WHERE imblSpotDifferencePrice IS NOT NULL;

COPY (SELECT zone, ts, round(diff, 2) AS diff FROM imb ORDER BY zone, ts)
  TO 'viz/data/imbalance.parquet' (FORMAT parquet, COMPRESSION zstd);

-- Aggregaten som den statiska bilden ritas ur. Histogrammet är 2 EUR-binnar
-- mellan -150 och +150; exakta nollor räknas separat och ritas inte, och
-- svansarna utanför +/-150 saknas hellre än skalas om.
COPY (
  WITH base AS (SELECT zone, year(ts) AS y, diff FROM imb),
  stat AS (
    SELECT zone, y, count(*) AS n,
      round(100.0 * count(*) FILTER (WHERE diff = 0) / count(*), 1) AS pct_zero,
      round(100.0 * count(*) FILTER (WHERE abs(diff) < 10) / count(*), 1) AS pct_near,
      round(100.0 * count(*) FILTER (WHERE abs(diff) > 150) / count(*), 2) AS pct_out,
      round(quantile_cont(diff, 0.05), 1) AS p05,
      round(quantile_cont(diff, 0.25), 1) AS p25,
      round(quantile_cont(diff, 0.50), 1) AS p50,
      round(quantile_cont(diff, 0.75), 1) AS p75,
      round(quantile_cont(diff, 0.95), 1) AS p95,
      round(min(diff), 0) AS lo, round(max(diff), 0) AS hi
    FROM base GROUP BY zone, y
  ),
  bins AS (
    SELECT zone, y, CAST(floor(diff / 2) AS INTEGER) AS b, count(*) AS c
    FROM base WHERE diff <> 0 AND diff >= -150 AND diff < 150
    GROUP BY zone, y, b
  ),
  hist AS (
    SELECT zone, y, list(b ORDER BY b) AS b, list(c ORDER BY b) AS c
    FROM bins GROUP BY zone, y
  )
  SELECT s.zone, s.y, s.n, s.pct_zero, s.pct_near, s.pct_out,
         s.p05, s.p25, s.p50, s.p75, s.p95, s.lo, s.hi, h.b, h.c
  FROM stat s JOIN hist h ON h.zone = s.zone AND h.y = s.y
  WHERE s.y >= $START_YEAR
  ORDER BY s.zone, s.y
) TO '$TMP/agg.json' (FORMAT json, ARRAY true);
SQL
rc=$?
if [ "$rc" -ne 0 ] || [ ! -s "$TMP/agg.json" ]; then
  echo "FEL: DuckDB-steget misslyckades" >&2; exit 1
fi

{ printf 'window.imbalanceAgg = '; cat "$TMP/agg.json"; printf ';\n'; } > viz/data/imbalance-data.js

rows=$(duckdb -noheader -list -c "SELECT count(*) FROM read_parquet('viz/data/imbalance.parquet')")
echo "export-imbalance: $rows perioder -> viz/data/imbalance.parquet ($(du -h viz/data/imbalance.parquet | cut -f1)), aggregat -> viz/data/imbalance-data.js"
[ "$fail" -eq 0 ] || echo "VARNING: minst en zon/år saknades i hämtningen" >&2
exit 0
