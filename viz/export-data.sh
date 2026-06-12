#!/usr/bin/env bash
# Exporterar diagramdata fran elmix.duckdb till viz/data/elmix-data.js.
# Kor fran projektroten efter `./mill Elmix.scala transform`:
#   ./viz/export-data.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
COPY (
  SELECT zone, yr, kraftslag, round(capture_rate, 4) AS capture_rate
  FROM mart_capture ORDER BY zone, kraftslag, yr
) TO 'viz/data/capture.json' (FORMAT json, ARRAY true);
COPY (
  SELECT epoch_ms(p.ts_h) AS t, p.zone,
         round(p.eur_mwh, 2) AS eur_mwh,
         round(coalesce(g.mwh, 0), 1) AS wind_mwh
  FROM fct_price_h p
  LEFT JOIN (
    SELECT date_trunc('hour', ts) AS ts_h, zone, sum(mwh) AS mwh
    FROM fct_gen WHERE kraftslag = 'Vind' GROUP BY 1, 2
  ) g USING (ts_h, zone)
  ORDER BY p.zone, p.ts_h
) TO 'viz/data/pricewind.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixCapture = '
  cat viz/data/capture.json
  printf ';\nwindow.elmixPriceWind = '
  cat viz/data/pricewind.json
  printf ';\n'
} > viz/data/elmix-data.js

echo "skrev viz/data/elmix-data.js ($(wc -c < viz/data/elmix-data.js) byte)"
