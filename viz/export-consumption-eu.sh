#!/usr/bin/env bash
# Exporterar FÖRBRUKNINGSmix för DE/FR (2025/2026) till consumption-eu-data.js.
# Rikare kategorier (Kärnkraft/Kol/Gas/Vind/Sol/Vatten/Övrigt) + nettoimport =
# total last (A65) − total produktion. Läser rådata ur data/raw/eu/ (fetcheu).
# Allt i MW (klockan visar normaliserade andelar, så MW ≈ energi per bucket).
# Kör: ./viz/export-consumption-eu.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb <<'SQL'
COPY (
  WITH gen AS (
    SELECT zone, ts,
      sum(mw) FILTER (WHERE psr_type='Nuclear')                                          AS k,
      sum(mw) FILTER (WHERE psr_type IN ('Fossil Brown coal/Lignite','Fossil Hard coal')) AS kol,
      sum(mw) FILTER (WHERE psr_type IN ('Fossil Gas','Fossil Coal-derived gas'))          AS gas,
      sum(mw) FILTER (WHERE psr_type IN ('Wind Onshore','Wind Offshore'))                 AS v,
      sum(mw) FILTER (WHERE psr_type='Solar')                                             AS s,
      sum(mw) FILTER (WHERE psr_type IN ('Hydro Run-of-river and poundage',
                                         'Hydro Water Reservoir','Hydro Pumped Storage')) AS va,
      sum(mw) FILTER (WHERE psr_type IN ('Biomass','Fossil Oil','Fossil Oil shale',
                     'Fossil Peat','Geothermal','Marine','Other renewable','Waste','Other')) AS ov
    FROM read_parquet('data/raw/eu/generation/*.parquet') GROUP BY zone, ts
  ),
  ld AS (
    SELECT zone, ts, avg(mw) AS load FROM read_parquet('data/raw/eu/load/*.parquet') GROUP BY zone, ts
  ),
  pr AS (
    SELECT zone, ts, avg(eur_mwh) AS eur FROM read_parquet('data/raw/eu/prices/*.parquet') GROUP BY zone, ts
  ),
  j AS (
    SELECT g.zone AS z, year(g.ts) AS y, dayofyear(g.ts) AS doy,
           hour(g.ts) AS hh, CAST(minute(g.ts) / 15 AS INTEGER) AS q,
           CAST(round(COALESCE(k, 0))   AS INTEGER) AS k,
           CAST(round(COALESCE(kol, 0)) AS INTEGER) AS kol,
           CAST(round(COALESCE(gas, 0)) AS INTEGER) AS gas,
           CAST(round(COALESCE(v, 0))   AS INTEGER) AS v,
           CAST(round(COALESCE(s, 0))   AS INTEGER) AS s,
           CAST(round(COALESCE(va, 0))  AS INTEGER) AS va,
           CAST(round(COALESCE(ov, 0))  AS INTEGER) AS ov,
           CAST(round(GREATEST(0, COALESCE(ld.load, 0)
             - (COALESCE(k,0)+COALESCE(kol,0)+COALESCE(gas,0)+COALESCE(v,0)
                +COALESCE(s,0)+COALESCE(va,0)+COALESCE(ov,0)))) AS INTEGER) AS imp,
           round(pr.eur, 1) AS p
    FROM gen g
    LEFT JOIN ld ON ld.zone = g.zone AND ld.ts = g.ts
    LEFT JOIN pr ON pr.zone = g.zone AND pr.ts = g.ts
  )
  SELECT z, y,
    list(doy ORDER BY doy, hh, q) AS doy,
    list(hh  ORDER BY doy, hh, q) AS h,
    list(q   ORDER BY doy, hh, q) AS q,
    list(k   ORDER BY doy, hh, q) AS k,
    list(kol ORDER BY doy, hh, q) AS kol,
    list(gas ORDER BY doy, hh, q) AS gas,
    list(v   ORDER BY doy, hh, q) AS v,
    list(s   ORDER BY doy, hh, q) AS s,
    list(va  ORDER BY doy, hh, q) AS va,
    list(ov  ORDER BY doy, hh, q) AS ov,
    list(imp ORDER BY doy, hh, q) AS imp,
    list(p   ORDER BY doy, hh, q) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/consumption-eu.json' (FORMAT json, ARRAY true);
SQL

{
  printf 'window.elmixEu = { years: [2025, 2026], zones: ["DE_LU","FR"], data: '
  cat viz/data/consumption-eu.json
  printf ' };\n'
} > viz/data/consumption-eu-data.js

echo "skrev viz/data/consumption-eu-data.js ($(wc -c < viz/data/consumption-eu-data.js) byte)"
