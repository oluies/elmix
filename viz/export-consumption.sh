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
  WITH gh AS (
    SELECT zone, date_trunc('hour', ts) AS ts_h, kraftslag, sum(mwh) AS mwh
    FROM fct_gen WHERE year(ts) IN (2025, 2026) GROUP BY ALL
  ),
  piv AS (
    SELECT zone, ts_h,
      sum(mwh) FILTER (WHERE kraftslag='Vind')           AS v,
      sum(mwh) FILTER (WHERE kraftslag='Sol')            AS s,
      sum(mwh) FILTER (WHERE kraftslag='Vattenkraft')    AS va,
      sum(mwh) FILTER (WHERE kraftslag='Kärnkraft')      AS k,
      sum(mwh) FILTER (WHERE kraftslag='Kraftvärme/övr') AS kv
    FROM gh GROUP BY zone, ts_h
  ),
  net AS (
    SELECT zone, ts, SUM(mw) AS net_mw
    FROM raw_flows WHERE year(ts) IN (2025, 2026) GROUP BY zone, ts
  ),
  neth AS (
    SELECT zone, date_trunc('hour', ts) AS ts_h, AVG(net_mw) AS netimp
    FROM net GROUP BY zone, ts_h
  ),
  j AS (
    SELECT p.zone AS z, year(p.ts_h) AS y,
           dayofyear(p.ts_h) AS doy, hour(p.ts_h) AS hh,
           CAST(round(COALESCE(p.v, 0))  AS INTEGER) AS v,
           CAST(round(COALESCE(p.s, 0))  AS INTEGER) AS s,
           CAST(round(COALESCE(p.va, 0)) AS INTEGER) AS va,
           CAST(round(COALESCE(p.k, 0))  AS INTEGER) AS k,
           CAST(round(COALESCE(p.kv, 0)) AS INTEGER) AS kv,
           CAST(round(GREATEST(0, COALESCE(n.netimp, 0))) AS INTEGER) AS imp,
           round(pr.eur_mwh, 1) AS p
    FROM piv p
    LEFT JOIN neth n ON n.zone = p.zone AND n.ts_h = p.ts_h
    LEFT JOIN fct_price_h pr ON pr.zone = p.zone AND pr.ts_h = p.ts_h
  )
  SELECT z, y,
    list(doy ORDER BY doy, hh) AS doy,
    list(hh  ORDER BY doy, hh) AS h,
    list(v   ORDER BY doy, hh) AS v,
    list(s   ORDER BY doy, hh) AS s,
    list(va  ORDER BY doy, hh) AS va,
    list(k   ORDER BY doy, hh) AS k,
    list(kv  ORDER BY doy, hh) AS kv,
    list(imp ORDER BY doy, hh) AS imp,
    list(p   ORDER BY doy, hh) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/consumption.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixConsumption = { years: [2025, 2026], zones: ["SE_1","SE_2","SE_3","SE_4"], data: '
  cat viz/data/consumption.json
  printf ' };\n'
} > viz/data/consumption-data.js

echo "skrev viz/data/consumption-data.js ($(wc -c < viz/data/consumption-data.js) byte)"
