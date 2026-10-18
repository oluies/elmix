#!/usr/bin/env bash
# Exporterar DYGNSPROFIL - medeleffekt per timme på dygnet (0..23), per
# kraftslag, zon och år - till viz/data/diurnal-data.js för dygnsklockorna i
# round.html. Riket (SE) = summan av SE1–SE4. Liten payload (24 rader per zon
# och år) så den tar hela historien utan årsfilter.
#
# ENHET: MW (medeleffekt), inte MWh. Medeleffekt = Σ mwh / Σ h_len över årets
# alla dygn för den timmen, alltså UPPLÖSNINGSOKÄNSLIGT: 15-min-eran (från
# 2 dec 2025) väger lika mycket per klocktimme som tim-eran före den. Hade vi
# summerat mwh rakt per bucket skulle kvartsperioderna fått en fjärdedels vikt.
# Sidan dividerar med 1000 och visar GW.
#
# Sommartid: timme 2 inträffar två gånger i oktober och hoppas över i mars.
# Σ h_len i nämnaren väger med faktisk tid, så profilen blir rätt ändå.
#
# Kör från projektroten efter transform: ./viz/export-diurnal.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
-- Tidszonen är avgörande här och inte kosmetisk: en dygnsprofil i UTC lägger
-- solmaxet en-två timmar fel. CI kör UTC, så den måste sättas explicit.
LOAD icu;
SET TimeZone = 'Europe/Stockholm';

COPY (
  -- En rad per zon och tidsstämpel först: h_len är bucketens längd och ligger
  -- på varje (ts, zone, kraftslag)-rad, så den måste plockas med max() INNAN
  -- kraftslagen summeras - annars räknas den fem gånger.
  WITH b AS (
    SELECT zone, ts, max(h_len) AS hlen,
      sum(mwh) FILTER (WHERE kraftslag = 'Vind')           AS v,
      sum(mwh) FILTER (WHERE kraftslag = 'Sol')            AS s,
      sum(mwh) FILTER (WHERE kraftslag = 'Vattenkraft')    AS va,
      sum(mwh) FILTER (WHERE kraftslag = 'Kärnkraft')      AS k,
      sum(mwh) FILTER (WHERE kraftslag = 'Kraftvärme/övr') AS kv
    FROM fct_gen GROUP BY zone, ts
  ),
  prof AS (
    SELECT zone AS z, year(ts) AS y, hour(ts) AS h,
           sum(hlen) AS hsum,
           count(DISTINCT CAST(ts AS DATE)) AS dagar,
           sum(COALESCE(v,  0)) AS v, sum(COALESCE(s,  0)) AS s,
           sum(COALESCE(va, 0)) AS va, sum(COALESCE(k,  0)) AS k,
           sum(COALESCE(kv, 0)) AS kv
    FROM b GROUP BY 1, 2, 3
  ),
  mw AS (
    SELECT z, y, h, dagar,
           v / hsum AS v, s / hsum AS s, va / hsum AS va,
           k / hsum AS k, kv / hsum AS kv
    FROM prof WHERE hsum > 0
  ),
  riket AS (
    SELECT 'SE' AS z, y, h, max(dagar) AS dagar,
           sum(v) AS v, sum(s) AS s, sum(va) AS va, sum(k) AS k, sum(kv) AS kv
    FROM mw GROUP BY y, h
  ),
  -- Snittspot per timme på dygnet. Rikspriset är ovägt medel av de fyra
  -- zonpriserna (inte volymvägt) - det ska bara visa dygnsformen, inte
  -- användas som intäktsmått. fct_price_h är redan timsnitt av kvartarna.
  pr AS (
    SELECT zone AS z, year(ts_h) AS y, hour(ts_h) AS h, avg(eur_mwh) AS p
    FROM fct_price_h GROUP BY 1, 2, 3
  ),
  prall AS (
    SELECT * FROM pr UNION ALL
    SELECT 'SE' AS z, y, h, avg(p) AS p FROM pr GROUP BY y, h
  ),
  j AS (
    SELECT m.z, m.y, m.h, m.dagar,
           CAST(round(m.v)  AS INTEGER) AS v,
           CAST(round(m.s)  AS INTEGER) AS s,
           CAST(round(m.va) AS INTEGER) AS va,
           CAST(round(m.k)  AS INTEGER) AS k,
           CAST(round(m.kv) AS INTEGER) AS kv,
           round(prall.p, 1) AS p
    FROM (SELECT * FROM mw UNION ALL SELECT * FROM riket) m
    LEFT JOIN prall ON prall.z = m.z AND prall.y = m.y AND prall.h = m.h
  )
  SELECT z, y, max(dagar) AS dagar,
    list(h  ORDER BY h) AS h,
    list(v  ORDER BY h) AS v,
    list(s  ORDER BY h) AS s,
    list(va ORDER BY h) AS va,
    list(k  ORDER BY h) AS k,
    list(kv ORDER BY h) AS kv,
    list(p  ORDER BY h) AS p
  FROM j GROUP BY z, y ORDER BY z, y
) TO 'viz/data/diurnal.json' (FORMAT json, ARRAY true);
SQL

YEARS=$(duckdb -readonly -noheader -list elmix.duckdb \
  "SELECT '[' || string_agg(DISTINCT y, ', ' ORDER BY y) || ']'
   FROM (SELECT year(ts) AS y FROM fct_gen)")

{
  printf 'window.elmixDiurnal = { years: %s, zones: ["SE","SE_1","SE_2","SE_3","SE_4"], data: ' "$YEARS"
  cat viz/data/diurnal.json
  printf ' };\n'
} > viz/data/diurnal-data.js

echo "skrev viz/data/diurnal-data.js ($(wc -c < viz/data/diurnal-data.js) byte, år $YEARS)"
