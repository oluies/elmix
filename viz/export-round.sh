#!/usr/bin/env bash
# Exporterar timvis mix + pris per zon/år (2019/2022/2025) till
# viz/data/round-data.js för den runda experimentsidan (round.html).
# Kör från projektroten efter transform: ./viz/export-round.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
COPY (
  WITH gh AS (
    SELECT zone, date_trunc('hour', ts) AS ts_h, kraftslag, sum(mwh) AS mwh
    FROM fct_gen WHERE year(ts) IN (2019, 2022, 2025) GROUP BY ALL
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
  j AS (
    SELECT p.zone AS z, year(p.ts_h) AS y,
           dayofyear(p.ts_h) AS doy, hour(p.ts_h) AS hh,
           CAST(round(COALESCE(p.v, 0))  AS INTEGER) AS v,
           CAST(round(COALESCE(p.s, 0))  AS INTEGER) AS s,
           CAST(round(COALESCE(p.va, 0)) AS INTEGER) AS va,
           CAST(round(COALESCE(p.k, 0))  AS INTEGER) AS k,
           CAST(round(COALESCE(p.kv, 0)) AS INTEGER) AS kv,
           round(pr.eur_mwh, 1) AS p
    FROM piv p LEFT JOIN fct_price_h pr ON pr.zone = p.zone AND pr.ts_h = p.ts_h
  )
  SELECT z, y,
    list(doy ORDER BY doy, hh) AS doy,
    list(hh  ORDER BY doy, hh) AS h,
    list(v   ORDER BY doy, hh) AS v,
    list(s   ORDER BY doy, hh) AS s,
    list(va  ORDER BY doy, hh) AS va,
    list(k   ORDER BY doy, hh) AS k,
    list(kv  ORDER BY doy, hh) AS kv,
    list(p   ORDER BY doy, hh) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/round.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixRound = { years: [2019, 2022, 2025], zones: ["SE_1","SE_2","SE_3","SE_4"], data: '
  cat viz/data/round.json
  printf ' };\n'
} > viz/data/round-data.js

echo "skrev viz/data/round-data.js ($(wc -c < viz/data/round-data.js) byte)"
