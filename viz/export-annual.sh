#!/usr/bin/env bash
# Exporterar ÅRSVOLYM per kraftslag och zon för HELA historiken (2016->) till
# viz/data/annual-data.js för tioårsvyn i energi.html. Liten payload (en rad
# per zon och år), så den tar alla år fct_gen känner till - ingen årsfilter.
#
# Produktion, inte förbrukning: import/export ingår INTE (fct_gen är inhemsk
# generation). Det är avsiktligt - tioårsvyn ska visa vad zonen själv har
# byggt ut, och nettoimporten hoppar för mycket mellan år för att höra hit.
# Jämför energi.html:s FBMC-vy som räknar in import.
#
# dagar = antal dygn med data. Innevarande år är ofullständigt (hämtningen går
# till idag) och sidan märker ut staplar med dagar < 360 som "delår" - annars
# läses den sista korta stapeln som ett ras i produktionen.
#
# Kör från projektroten efter transform: ./viz/export-annual.sh
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p viz/data
duckdb -readonly elmix.duckdb <<'SQL'
-- Tidszon explicit: år- och dygnsgränser ska följa Europe/Stockholm, inte den
-- TZ som råkar gälla i skalet (CI kör UTC, utvecklarmaskinen lokal tid).
LOAD icu;
SET TimeZone = 'Europe/Stockholm';

COPY (
  -- mwh i fct_gen är redan periodviktad (h_len), så summan är korrekt över
  -- MTU-bytet okt 2025. TWh med tre decimaler räcker: minsta posten (Sol i
  -- SE1) är ~0,001 TWh och sidan visar en decimal.
  WITH per_zone AS (
    SELECT year(ts) AS y, zone AS z,
      count(DISTINCT CAST(ts AS DATE))                        AS dagar,
      sum(mwh) FILTER (WHERE kraftslag = 'Vind')              AS v,
      sum(mwh) FILTER (WHERE kraftslag = 'Sol')               AS s,
      sum(mwh) FILTER (WHERE kraftslag = 'Vattenkraft')       AS va,
      sum(mwh) FILTER (WHERE kraftslag = 'Kärnkraft')         AS k,
      sum(mwh) FILTER (WHERE kraftslag = 'Kraftvärme/övr')    AS kv
    FROM fct_gen GROUP BY 1, 2
  ),
  -- Riket = summan av de fyra elområdena. Interna flöden nettar ut per
  -- definition eftersom det är produktion som summeras, inte förbrukning.
  riket AS (
    SELECT y, 'SE' AS z, max(dagar) AS dagar,
           sum(v) AS v, sum(s) AS s, sum(va) AS va, sum(k) AS k, sum(kv) AS kv
    FROM per_zone GROUP BY y
  )
  SELECT z, y, dagar,
         round(COALESCE(v,  0) / 1e6, 3) AS v,
         round(COALESCE(s,  0) / 1e6, 3) AS s,
         round(COALESCE(va, 0) / 1e6, 3) AS va,
         round(COALESCE(k,  0) / 1e6, 3) AS k,
         round(COALESCE(kv, 0) / 1e6, 3) AS kv
  FROM (SELECT * FROM per_zone UNION ALL SELECT * FROM riket)
  ORDER BY z, y
) TO 'viz/data/annual.json' (FORMAT json, ARRAY true);
SQL

# years och zones läses ur datan i stället för att hårdkodas: historiken växer
# med ett år per årsskifte och den här sidan ska inte behöva röras då.
YEARS=$(duckdb -readonly -noheader -list elmix.duckdb \
  "SELECT '[' || string_agg(DISTINCT y, ', ' ORDER BY y) || ']'
   FROM (SELECT year(ts) AS y FROM fct_gen)")

{
  printf 'window.elmixAnnual = { years: %s, zones: ["SE","SE_1","SE_2","SE_3","SE_4"], data: ' "$YEARS"
  cat viz/data/annual.json
  printf ' };\n'
} > viz/data/annual-data.js

echo "skrev viz/data/annual-data.js ($(wc -c < viz/data/annual-data.js) byte, år $YEARS)"
