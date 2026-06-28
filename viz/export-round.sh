#!/usr/bin/env bash
# Exporterar timvis mix + pris per zon/år (2019/2022/2025) till
# viz/data/round-data.js för den runda experimentsidan (round.html).
# Kör från projektroten efter transform: ./viz/export-round.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
COPY (
  -- Bucketa på generationens NATIVA upplösning (15-min från 2 dec 2025, annars
  -- tim). q = kvart i timmen (0..3). Pris läses ur rå-priserna på exakt ts, så
  -- kvartspris (från 1 okt 2025) följer med; äldre = tim.
  WITH piv AS (
    SELECT zone, ts,
      sum(mwh) FILTER (WHERE kraftslag='Vind')           AS v,
      sum(mwh) FILTER (WHERE kraftslag='Sol')            AS s,
      sum(mwh) FILTER (WHERE kraftslag='Vattenkraft')    AS va,
      sum(mwh) FILTER (WHERE kraftslag='Kärnkraft')      AS k,
      sum(mwh) FILTER (WHERE kraftslag='Kraftvärme/övr') AS kv
    FROM fct_gen WHERE year(ts) IN (2019, 2022, 2025) GROUP BY zone, ts
  ),
  pr AS (
    SELECT zone, ts, avg(eur_mwh) AS eur
    FROM read_parquet('data/raw/prices/SE_*.parquet')
    WHERE year(ts) IN (2019, 2022, 2025) GROUP BY zone, ts
  ),
  j AS (
    SELECT p.zone AS z, year(p.ts) AS y, dayofyear(p.ts) AS doy,
           hour(p.ts) AS hh, CAST(minute(p.ts) / 15 AS INTEGER) AS q,
           CAST(round(COALESCE(v, 0))  AS INTEGER) AS v,
           CAST(round(COALESCE(s, 0))  AS INTEGER) AS s,
           CAST(round(COALESCE(va, 0)) AS INTEGER) AS va,
           CAST(round(COALESCE(k, 0))  AS INTEGER) AS k,
           CAST(round(COALESCE(kv, 0)) AS INTEGER) AS kv,
           round(pr.eur, 1) AS p
    FROM piv p LEFT JOIN pr ON pr.zone = p.zone AND pr.ts = p.ts
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
    list(p   ORDER BY doy, hh, q) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/round.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixRound = { years: [2019, 2022, 2025], zones: ["SE_1","SE_2","SE_3","SE_4"], data: '
  cat viz/data/round.json
  printf ' };\n'
} > viz/data/round-data.js

echo "skrev viz/data/round-data.js ($(wc -c < viz/data/round-data.js) byte)"
