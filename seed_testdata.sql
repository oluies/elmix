-- seed_testdata.sql
-- Genererar syntetisk radata i data/raw/ sa att transform.sql och
-- rorflodena kan roktestas utan ENTSO-E-nyckel. Kor:
--   mkdir -p data/raw/generation data/raw/prices data/raw/flows data/raw/imbalance data/marts
--   duckdb < seed_testdata.sql
-- Egenskaper: pris antikorrelerat med vind, fem prisspikar per zon,
-- timdata jan-feb 2024 plus kvartsdata en vecka i okt 2025.

LOAD icu;
SET TimeZone = 'Europe/Stockholm';
SELECT setseed(0.42);

CREATE OR REPLACE TABLE ts_h AS
SELECT unnest(generate_series(
    TIMESTAMPTZ '2024-01-01 00:00:00+01',
    TIMESTAMPTZ '2024-02-29 23:00:00+01',
    INTERVAL 1 HOUR)) AS ts;

CREATE OR REPLACE TABLE ts_q AS
SELECT unnest(generate_series(
    TIMESTAMPTZ '2025-10-01 00:00:00+02',
    TIMESTAMPTZ '2025-10-07 23:45:00+02',
    INTERVAL 15 MINUTE)) AS ts;

CREATE OR REPLACE TABLE ts_all AS
SELECT ts FROM ts_h UNION ALL SELECT ts FROM ts_q;

CREATE OR REPLACE TABLE zones(zone VARCHAR);
INSERT INTO zones VALUES ('SE_1'), ('SE_2'), ('SE_3'), ('SE_4');

CREATE OR REPLACE TABLE psr(psr_type VARCHAR, base DOUBLE);
INSERT INTO psr VALUES
    ('Nuclear', 3000), ('Hydro Water Reservoir', 2000),
    ('Wind Onshore', 1500), ('Solar', 200), ('Biomass', 400);

-- Produktion: bas * slumpfaktor; vind extra volatil.
CREATE OR REPLACE TABLE gen AS
SELECT t.ts, z.zone, p.psr_type,
       GREATEST(0, p.base * (0.4 + 1.2 * random())
                  * CASE WHEN p.psr_type = 'Wind Onshore'
                         THEN 0.2 + 1.6 * random() ELSE 1 END) AS mw
FROM ts_all t CROSS JOIN zones z CROSS JOIN psr p;

COPY (SELECT ts, psr_type, mw, zone FROM gen)
TO 'data/raw/generation/seed.parquet' (FORMAT parquet);

-- Pris: 80 - 0.03 * vind + brus, plus spikar pa ~0.2% av perioderna.
CREATE OR REPLACE TABLE price AS
SELECT g.ts, g.zone,
       80 - 0.03 * g.mw + 30 * (random() - 0.5)
         + CASE WHEN random() < 0.002 THEN 700 ELSE 0 END AS eur_mwh
FROM gen g WHERE g.psr_type = 'Wind Onshore';

COPY (SELECT ts, eur_mwh, zone FROM price)
TO 'data/raw/prices/seed.parquet' (FORMAT parquet);

-- Floden: en grans per zon, vaxlande riktning.
COPY (
    SELECT t.ts, z.zone || '>DK' AS border,
           800 * (random() - 0.5) AS mw, z.zone
    FROM ts_h t CROSS JOIN zones z
) TO 'data/raw/flows/seed.parquet' (FORMAT parquet);

-- Obalans (anvands inte av marts annu, men haller schemat levande).
COPY (
    SELECT t.ts, 'A04' AS category,
           110 + 80 * random() AS eur_mwh, z.zone
    FROM ts_h t CROSS JOIN zones z
) TO 'data/raw/imbalance/seed.parquet' (FORMAT parquet);

SELECT 'syntetisk data klar' AS status;
