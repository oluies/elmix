#!/usr/bin/env bash
# Exporterar diagramdata fran elmix.duckdb till viz/data/elmix-data.js.
# Kor fran projektroten efter `./mill Elmix.scala transform`:
#   ./viz/export-data.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
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
-- 15-min-grunddata för klientsidig PCA: mixandelar + day-ahead-pris per
-- zon/kvart (från 2 dec 2025). Webbläsaren räknar PCA på vald delperiod.
COPY (
  WITH gen AS (
    SELECT zone, ts,
      sum(mwh) FILTER (WHERE kraftslag='Vind')           AS vind,
      sum(mwh) FILTER (WHERE kraftslag='Sol')            AS sol,
      sum(mwh) FILTER (WHERE kraftslag='Vattenkraft')    AS vatten,
      sum(mwh) FILTER (WHERE kraftslag='Kärnkraft')      AS karn,
      sum(mwh) FILTER (WHERE kraftslag='Kraftvärme/övr') AS kraftv,
      sum(mwh) AS tot
    FROM fct_gen WHERE ts >= TIMESTAMPTZ '2025-12-02 00:00:00+01'
    GROUP BY zone, ts
  ),
  pr AS (
    SELECT zone, ts, avg(eur_mwh) AS eur
    FROM read_parquet('data/raw/prices/SE_*.parquet')
    WHERE ts >= TIMESTAMPTZ '2025-12-02 00:00:00+01' GROUP BY zone, ts
  )
  SELECT gen.zone AS z, epoch_ms(gen.ts) AS t,
         round(COALESCE(vind, 0) / tot, 4)   AS v,
         round(COALESCE(sol, 0) / tot, 4)    AS s,
         round(COALESCE(vatten, 0) / tot, 4) AS va,
         round(COALESCE(karn, 0) / tot, 4)   AS k,
         round(COALESCE(kraftv, 0) / tot, 4) AS kv,
         round(pr.eur, 1) AS p
  FROM gen LEFT JOIN pr USING (zone, ts)
  WHERE tot > 0 ORDER BY gen.zone, gen.ts
) TO 'viz/data/elmix15.json' (FORMAT json, ARRAY true);
-- Kannibalisering: capture rate per kraftslag/år/zon (mart_capture). Vindens
-- capture_rate faller mot baspriset när penetrationen ökar; kärnkraft ~1.
COPY (
  SELECT zone, yr, kraftslag,
         round(capture_rate, 3) AS capture_rate,
         round(fångat_pris, 1)  AS fangat_pris,
         round(baspris, 1)      AS baspris,
         round(twh, 2)          AS twh
  FROM read_parquet('data/marts/capture.parquet')
  WHERE capture_rate IS NOT NULL
  ORDER BY zone, kraftslag, yr
) TO 'viz/data/capture.json' (FORMAT json, ARRAY true);
-- Pris vs vind, binnat per år/zon (mart_pris_vs_vind): nedåtlutande kurva =
-- kannibaliseringsmekaniken (priset faller i timmar med mycket vind).
COPY (
  SELECT zone, yr, vind_bin,
         round(vind_mwh_avg, 0) AS vind_mwh_avg,
         round(pris_median, 1)  AS pris_median,
         round(pris_avg, 1)     AS pris_avg
  FROM read_parquet('data/marts/pris_vs_vind.parquet')
  ORDER BY zone, yr, vind_bin
) TO 'viz/data/pris_vs_vind.json' (FORMAT json, ARRAY true);
SQL

# Interaktiva appen läser elmix15 (räknar PCA klientsidan) samt de
# förberäknade kannibaliseringsmarterna (capture/pris-vs-vind, statiska per
# zon). De prerenderade pca_*.json läses som filer av render.mjs, inte globaler.
{
  printf 'window.elmix15 = '
  cat viz/data/elmix15.json
  printf ';\nwindow.elmixCapture = '
  cat viz/data/capture.json
  printf ';\nwindow.elmixPrisVind = '
  cat viz/data/pris_vs_vind.json
  printf ';\n'
} > viz/data/elmix-data.js

echo "skrev viz/data/elmix-data.js ($(wc -c < viz/data/elmix-data.js) byte)"
