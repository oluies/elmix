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
-- PCA-marts (skrivna av `mill Elmix.scala pca`, lästa som parquet).
COPY (
  SELECT zone, pc, round(explained, 4) AS explained
  FROM read_parquet('data/marts/pca_explained.parquet') ORDER BY zone, pc
) TO 'viz/data/pca_explained.json' (FORMAT json, ARRAY true);
COPY (
  SELECT zone, pc, kraftslag, round(loading, 4) AS loading
  FROM read_parquet('data/marts/pca_loadings.parquet') ORDER BY zone, pc, kraftslag
) TO 'viz/data/pca_loadings.json' (FORMAT json, ARRAY true);
-- Scores: dagsmedel av PC1/PC2 per zon (biplot), år för färgläggning.
COPY (
  SELECT zone, year(make_timestamp(t * 1000)) AS yr,
         round(avg(pc1), 4) AS pc1, round(avg(pc2), 4) AS pc2
  FROM read_parquet('data/marts/pca_scores.parquet')
  GROUP BY zone, date_trunc('day', make_timestamp(t * 1000)),
           year(make_timestamp(t * 1000))
  ORDER BY zone, date_trunc('day', make_timestamp(t * 1000))
) TO 'viz/data/pca_scores.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixCapture = '
  cat viz/data/capture.json
  printf ';\nwindow.elmixPriceWind = '
  cat viz/data/pricewind.json
  printf ';\nwindow.elmixPcaExplained = '
  cat viz/data/pca_explained.json
  printf ';\nwindow.elmixPcaLoadings = '
  cat viz/data/pca_loadings.json
  printf ';\nwindow.elmixPcaScores = '
  cat viz/data/pca_scores.json
  printf ';\n'
} > viz/data/elmix-data.js

echo "skrev viz/data/elmix-data.js ($(wc -c < viz/data/elmix-data.js) byte)"
