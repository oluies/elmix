-- transform.sql
-- DuckDB-modell som bygger alla fem akterna fran data/raw/*.parquet.
-- Kor:  duckdb elmix.duckdb < transform.sql
-- Output: tabeller + data/marts/*.parquet (redo for duckdb-wasm-widgeten)

LOAD icu;  -- bundlad i de flesta distributioner, annars kor INSTALL icu forst
SET TimeZone = 'Europe/Stockholm';

------------------------------------------------------------------
-- 0. Rådata + klassificering planerbar / väderberoende
------------------------------------------------------------------
CREATE OR REPLACE TABLE raw_generation AS
SELECT ts, zone, psr_type, mw
FROM read_parquet('data/raw/generation/*.parquet');

CREATE OR REPLACE TABLE raw_prices AS
SELECT ts, zone, eur_mwh
FROM read_parquet('data/raw/prices/*.parquet');

CREATE OR REPLACE TABLE raw_imbalance AS
SELECT * FROM read_parquet('data/raw/imbalance/*.parquet', union_by_name=true);

CREATE OR REPLACE TABLE raw_flows AS
SELECT ts, zone, border, mw
FROM read_parquet('data/raw/flows/*.parquet');

CREATE OR REPLACE TABLE dim_psr AS
SELECT * FROM (VALUES
    ('Nuclear',                       'Kärnkraft',     'planerbar'),
    ('Hydro Water Reservoir',         'Vattenkraft',   'planerbar'),
    ('Hydro Run-of-river and poundage','Vattenkraft',  'planerbar'),
    ('Hydro Pumped Storage',          'Vattenkraft',   'planerbar'),
    ('Fossil Gas',                    'Kraftvärme/övr','planerbar'),
    ('Fossil Hard coal',              'Kraftvärme/övr','planerbar'),
    ('Fossil Oil',                    'Kraftvärme/övr','planerbar'),
    ('Fossil Peat',                   'Kraftvärme/övr','planerbar'),
    ('Biomass',                       'Kraftvärme/övr','planerbar'),
    ('Waste',                         'Kraftvärme/övr','planerbar'),
    ('Other',                         'Kraftvärme/övr','planerbar'),
    ('Other renewable',               'Kraftvärme/övr','planerbar'),
    ('Wind Onshore',                  'Vind',          'väderberoende'),
    ('Wind Offshore',                 'Vind',          'väderberoende'),
    ('Solar',                         'Sol',           'väderberoende')
) AS t(psr_type, kraftslag, kategori);

-- Timserie: produktion per kraftslag, pris, energi per period.
-- Generation kan vara 15/30/60 min beroende på period. Vi väger med
-- periodlängden så MWh blir rätt över MTU-bytet.
CREATE OR REPLACE TABLE fct_gen AS
WITH per_psr AS (
    SELECT ts, zone, psr_type, SUM(mw) AS mw
    FROM raw_generation
    GROUP BY ALL
),
with_len AS (
    SELECT *,
        EPOCH(LEAD(ts) OVER (PARTITION BY zone, psr_type ORDER BY ts) - ts) / 3600.0 AS h_len
    FROM per_psr
)
SELECT
    w.ts, w.zone, d.kraftslag, d.kategori,
    SUM(w.mw) AS mw,
    COALESCE(LEAST(ANY_VALUE(w.h_len), 1.0), 1.0) AS h_len,
    SUM(w.mw * COALESCE(LEAST(w.h_len, 1.0), 1.0)) AS mwh
FROM with_len w
JOIN dim_psr d USING (psr_type)
GROUP BY w.ts, w.zone, d.kraftslag, d.kategori;

-- Pris per timme (snitt av kvartar efter okt 2025) - gör join-nyckeln enkel.
CREATE OR REPLACE TABLE fct_price_h AS
SELECT date_trunc('hour', ts) AS ts_h, zone, AVG(eur_mwh) AS eur_mwh
FROM raw_prices GROUP BY ALL;

------------------------------------------------------------------
-- Akt 1: Mixen per månad och elområde
------------------------------------------------------------------
CREATE OR REPLACE TABLE mart_mix_monthly AS
WITH m AS (
    SELECT date_trunc('month', ts) AS month, zone, kraftslag, kategori,
           SUM(mwh) / 1000.0 AS gwh
    FROM fct_gen
    GROUP BY ALL
)
SELECT *,
    SUM(gwh) FILTER (WHERE kategori = 'väderberoende')
        OVER (PARTITION BY month, zone)
      / NULLIF(SUM(gwh) OVER (PARTITION BY month, zone), 0)
      AS andel_väderberoende
FROM m;

------------------------------------------------------------------
-- Akt 2: Pris vs vind (binned, per år och zon)
------------------------------------------------------------------
CREATE OR REPLACE TABLE mart_pris_vs_vind AS
WITH vind_h AS (
    SELECT date_trunc('hour', ts) AS ts_h, zone, SUM(mwh) AS vind_mwh
    FROM fct_gen WHERE kraftslag = 'Vind' GROUP BY ALL
),
j AS (
    SELECT v.ts_h, v.zone, v.vind_mwh, p.eur_mwh,
           YEAR(v.ts_h) AS yr,
           NTILE(20) OVER (PARTITION BY v.zone, YEAR(v.ts_h) ORDER BY v.vind_mwh) AS vind_bin
    FROM vind_h v JOIN fct_price_h p USING (ts_h, zone)
)
SELECT zone, yr, vind_bin,
       AVG(vind_mwh) AS vind_mwh_avg,
       AVG(eur_mwh)  AS pris_avg,
       MEDIAN(eur_mwh) AS pris_median,
       COUNT(*) AS n
FROM j GROUP BY ALL;

------------------------------------------------------------------
-- Akt 3: Capture rate och schablonintäkt per kraftslag/år/zon
------------------------------------------------------------------
CREATE OR REPLACE TABLE mart_capture AS
WITH gen_h AS (
    SELECT date_trunc('hour', ts) AS ts_h, zone, kraftslag, kategori, SUM(mwh) AS mwh
    FROM fct_gen GROUP BY ALL
),
j AS (SELECT g.*, p.eur_mwh FROM gen_h g JOIN fct_price_h p USING (ts_h, zone)),
agg AS (
    SELECT zone, YEAR(ts_h) AS yr, kraftslag, kategori,
           SUM(mwh) / 1e6                            AS twh,
           SUM(mwh * eur_mwh) / 1e6                  AS intäkt_meur,   -- schablonintäkt
           SUM(mwh * eur_mwh) / NULLIF(SUM(mwh), 0)  AS fångat_pris
    FROM j GROUP BY ALL
),
base AS (SELECT zone, YEAR(ts_h) AS yr, AVG(eur_mwh) AS baspris FROM fct_price_h GROUP BY ALL)
SELECT a.*, b.baspris,
       a.fångat_pris / NULLIF(b.baspris, 0) AS capture_rate
FROM agg a JOIN base b USING (zone, yr);

------------------------------------------------------------------
-- Akt 4: Dunkelflaute-zoom + changepoints
------------------------------------------------------------------
-- Nettoimport per timme
CREATE OR REPLACE TABLE mart_balans_h AS
WITH imp AS (
    SELECT date_trunc('hour', ts) AS ts_h, zone, SUM(mw) / 4.0 AS nettoimport_mwh_approx
    FROM raw_flows GROUP BY ALL
),
gen AS (
    SELECT date_trunc('hour', ts) AS ts_h, zone,
           SUM(mwh) FILTER (WHERE kategori = 'väderberoende') AS väder_mwh,
           SUM(mwh) FILTER (WHERE kategori = 'planerbar')     AS planerbar_mwh
    FROM fct_gen GROUP BY ALL
)
SELECT g.*, p.eur_mwh, i.nettoimport_mwh_approx
FROM gen g
LEFT JOIN fct_price_h p USING (ts_h, zone)
LEFT JOIN imp i USING (ts_h, zone);

-- Changepoints: timmar där priset bryter kraftigt mot 30-dagarsnivån,
-- eller överstiger absolut "reservnivå". Markeras som band i widgeten.
CREATE OR REPLACE TABLE mart_changepoints AS
WITH w AS (
    SELECT ts_h, zone, eur_mwh,
           AVG(eur_mwh) OVER win AS p_avg30,
           STDDEV(eur_mwh) OVER win AS p_std30
    FROM fct_price_h
    WINDOW win AS (PARTITION BY zone ORDER BY ts_h ROWS BETWEEN 720 PRECEDING AND 1 PRECEDING)
)
SELECT ts_h, zone, eur_mwh, p_avg30,
       (eur_mwh - p_avg30) / NULLIF(p_std30, 0) AS z,
       eur_mwh >= 500                            AS extrem_niva,
       (eur_mwh - p_avg30) / NULLIF(p_std30, 0) >= 5 AS regimbrott
FROM w
WHERE eur_mwh >= 500 OR (eur_mwh - p_avg30) / NULLIF(p_std30, 0) >= 5;

------------------------------------------------------------------
-- Akt 5: Kvartsvolatilitet inom timmen (fr.o.m. okt 2025)
------------------------------------------------------------------
CREATE OR REPLACE TABLE mart_kvartsvol AS
SELECT
    date_trunc('hour', ts) AS ts_h, zone,
    COUNT(*) AS n_kvart,
    MAX(eur_mwh) - MIN(eur_mwh) AS spann_eur,
    STDDEV(eur_mwh) AS std_eur,
    AVG(eur_mwh) AS snitt_eur
FROM raw_prices
WHERE ts >= TIMESTAMPTZ '2025-10-01 00:00:00+02'
GROUP BY ALL
HAVING COUNT(*) >= 4;

------------------------------------------------------------------
-- Exportera marts som Parquet för widgeten
------------------------------------------------------------------
COPY mart_mix_monthly   TO 'data/marts/mix_monthly.parquet'   (FORMAT parquet);
COPY mart_pris_vs_vind  TO 'data/marts/pris_vs_vind.parquet'  (FORMAT parquet);
COPY mart_capture       TO 'data/marts/capture.parquet'       (FORMAT parquet);
COPY mart_balans_h      TO 'data/marts/balans_h.parquet'      (FORMAT parquet);
COPY mart_changepoints  TO 'data/marts/changepoints.parquet'  (FORMAT parquet);
COPY mart_kvartsvol     TO 'data/marts/kvartsvol.parquet'     (FORMAT parquet);
