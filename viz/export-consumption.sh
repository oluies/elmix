#!/usr/bin/env bash
# Exporterar timvis FÖRBRUKNINGSmix per zon/år (2019/2022/2025) till
# viz/data/consumption-data.js: produktion per kraftslag + nettoimport (imp).
# Nettoimport = AVG(net_mw) per timme (upplösningsokänsligt; mart_balans_h:s
# /4.0 antar 15-min). Flöden är FYSISKA (A11); import positiv, export negativ.
# Kör från projektroten efter transform: ./viz/export-consumption.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
COPY (
  -- Bucketa på generationens NATIVA upplösning (15-min 2026 + från 2 dec 2025).
  -- q = kvart (0..3). hlen = bucketens längd i timmar (0.25/1) -> ger import i
  -- MWh per bucket. Pris ur rå-priserna (kvart från okt 2025). Nettoimport från
  -- fysiska flöden (A11) per timme, fördelat per bucket via hlen.
  WITH piv AS (
    SELECT zone, ts, max(h_len) AS hlen,
      sum(mwh) FILTER (WHERE kraftslag='Vind')           AS v,
      sum(mwh) FILTER (WHERE kraftslag='Sol')            AS s,
      sum(mwh) FILTER (WHERE kraftslag='Vattenkraft')    AS va,
      sum(mwh) FILTER (WHERE kraftslag='Kärnkraft')      AS k,
      sum(mwh) FILTER (WHERE kraftslag='Kraftvärme/övr') AS kv
    FROM fct_gen WHERE year(ts) IN (2025, 2026) GROUP BY zone, ts
  ),
  neth AS (
    SELECT zone, date_trunc('hour', ts) AS ts_h, AVG(net_mw) AS net_mw FROM (
      SELECT zone, ts, SUM(mw) AS net_mw
      FROM raw_flows WHERE year(ts) IN (2025, 2026) GROUP BY zone, ts
    ) GROUP BY zone, ts_h
  ),
  pr AS (
    SELECT zone, ts, avg(eur_mwh) AS eur
    FROM read_parquet('data/raw/prices/SE_*.parquet')
    WHERE year(ts) IN (2025, 2026) GROUP BY zone, ts
  ),
  j AS (
    SELECT p.zone AS z, year(p.ts) AS y, dayofyear(p.ts) AS doy,
           hour(p.ts) AS hh, CAST(minute(p.ts) / 15 AS INTEGER) AS q,
           CAST(round(COALESCE(v, 0))  AS INTEGER) AS v,
           CAST(round(COALESCE(s, 0))  AS INTEGER) AS s,
           CAST(round(COALESCE(va, 0)) AS INTEGER) AS va,
           CAST(round(COALESCE(k, 0))  AS INTEGER) AS k,
           CAST(round(COALESCE(kv, 0)) AS INTEGER) AS kv,
           CAST(round(GREATEST(0, COALESCE(n.net_mw, 0) * p.hlen)) AS INTEGER) AS imp,
           round(pr.eur, 1) AS p
    FROM piv p
    LEFT JOIN neth n ON n.zone = p.zone AND n.ts_h = date_trunc('hour', p.ts)
    LEFT JOIN pr ON pr.zone = p.zone AND pr.ts = p.ts
  )
  SELECT z, y,
    list(doy ORDER BY doy, hh, q) AS doy,
    list(hh  ORDER BY doy, hh, q) AS h,
    list(q   ORDER BY doy, hh, q) AS q,
    list(v   ORDER BY doy, hh, q) AS v,
    list(s   ORDER BY doy, hh, q) AS s,
    list(va  ORDER BY doy, hh, q) AS va,
    list(k   ORDER BY doy, hh, q) AS k,
    list(kv  ORDER BY doy, hh, q) AS kv,
    list(imp ORDER BY doy, hh, q) AS imp,
    list(p   ORDER BY doy, hh, q) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/consumption.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixConsumption = { years: [2025, 2026], zones: ["SE_1","SE_2","SE_3","SE_4"], data: '
  cat viz/data/consumption.json
  printf ' };\n'
} > viz/data/consumption-data.js

echo "skrev viz/data/consumption-data.js ($(wc -c < viz/data/consumption-data.js) byte)"
