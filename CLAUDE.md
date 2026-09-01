# CLAUDE.md

Mill single-file Scala-script (Mill 1.1+, YAML-huvud med `//| mvnDeps`) som
hämtar elmarknadsdata för SE1-SE4 från ENTSO-E och bygger analysunderlag
(Parquet-marts) med DuckDB. All SQL-analys ligger i transform.sql;
Elmix.scala gör hämtning, parsning och orkestrering. Ändra inte den
ansvarsfördelningen. Enda undantaget är `pca`-kommandot: PCA kräver
egenvärdesberäkning som DuckDB saknar, så den ligger som ren funktionell
Scala (Jacobi-rotation, inget externt beroende) i Elmix.scala.

## Kommandon

```bash
./mill Elmix.scala:compile                  # kompilera utan att kora
export ENTSOE_API_KEY=...
./mill Elmix.scala fetch --start 2016 --end 2026 --data all
./mill Elmix.scala fetch --start 2024 --end 2024 --data prices   # snabbtest
./mill Elmix.scala transform                # bygger data/marts/*.parquet
```

Kräver Mill 1.1+ (`mill --version`). Om mill saknas: installera enligt
mill-build.org, eller lägg in bootstrap-skriptet `./mill`.

Röktest utan API-nyckel (kräver duckdb CLI):

```bash
mkdir -p data/raw/generation data/raw/prices data/raw/flows data/raw/imbalance data/marts
duckdb < seed_testdata.sql
./mill Elmix.scala transform
```

Förväntat: sex parquetfiler i data/marts/, mart_capture visar
capture_rate < 1 för Vind och ~1 för Kärnkraft, mart_changepoints icke-tom.

## Arkitektur

Allt i Elmix.scala, tre sektioner:

- ENTSO-E: requests-scala (bundlad i Mill-scripts) mot XML-API:t,
  scala-xml för parsning, EIC-koder, forward-fill för curveType A03,
  finaste upplösningen vid blandade prisdokument (MTU-bytet okt 2025).
- DuckDB: JDBC, batchad INSERT -> COPY TO parquet, skriptkörare för
  transform.sql (splittar på `;` efter att radkommentarer strippats -
  lägg aldrig `;` i SQL-kommentarer).
- Orkestrering: mainargs-@main med fetch/transform. Inkrementell:
  befintliga Parquet hoppas över. 2 s paus mellan API-anrop - ta inte
  bort den.

transform.sql är verifierad mot DuckDB 1.5 och delas med Python-varianten -
rör den inte utan separat verifiering. seed_testdata.sql genererar
syntetisk rådata, ren DuckDB.

## Kända osäkerheter (skrivna ur minnet, ej integrationstestade)

1. EIC-koderna i Zones/Neighbours - verifierade mot riktig hämtning
   2023-2026 (nord/syd-prisgradienten stämmer); stäm ändå av mot
   arealistan på transparency.entsoe.eu vid oväntat tomma svar.
2. A85-obalans: verifierad mot riktig data. Elementnamnen
   (`imbalance_Price.amount`/`.category`) stämmer. OBS: ENTSO-E levererar
   A85 som zip-arkiv - `apiGet` upptäcker PK-magic och packar upp till ett
   dokument per fil. Sverige kör enprismodell (kategori A04/A05 identiska).
   Obalansdata hämtas och lagras men används ännu inte i någon mart.
3. Exakt syntax för mainargs-dispatch i Mill-scripts: kommandot är
   positionellt (`fetch`/`transform`), flaggorna kebab-case. Justera
   @main-signaturen om Mill klagar.

## Konventioner

- Schemafrysta kontrakt mot transform.sql: generation(ts, psr_type, mw,
  zone), prices(ts, eur_mwh, zone), flows(ts, border, mw, zone).
  Extra kolumner är ok, byt aldrig namn på dessa.
- Tidsstämplar är TIMESTAMPTZ; ENTSO-E levererar UTC, transform.sql sätter
  Europe/Stockholm. Inga naiva timestamps.
- Import positiv, export negativ i flows.

<!-- rtk-instructions v2 -->
# RTK (Rust Token Killer)

Prefixa kommandon med `rtk`. Finns ett filter används det, annars går
kommandot igenom oförändrat - så `rtk` är alltid säkert. Gäller även i
kedjor: `rtk git add . && rtk git commit -m "msg"`, inte bara det första
ledet.

**Blocket är medvetet beskuret** till det som faktiskt förekommer i
elmix. Den fullständiga kommandolistan står i `~/.claude/RTK.md`, som
laddas globalt i alla projekt - en `rtk init` här skulle skriva tillbaka
hela listan, inklusive cargo, tsc, jest, prisma, docker och kubectl, som
inget av det här repot använder.

```bash
rtk git status | log | diff | show | add | commit | push | fetch | branch
rtk gh pr view <num> | pr checks | run list | issue list | api
rtk ls <path> | find <pattern>
rtk grep <pattern>       # OBS: -c -l -L -o -Z kör ofiltrerat
rtk curl <url>
rtk err <cmd>            # bara felraderna ur valfritt kommando
rtk log <file>           # deduplicerade loggar med antal
```

Git-passthrough gäller alla subkommandon, även de som inte listas ovan.

Meta: `rtk gain` (besparing), `rtk gain --history`, `rtk discover`
(missad användning), `rtk proxy <cmd>` (kör ofiltrerat vid felsökning).

Projektlokala filter kan läggas i `.rtk/filters.toml`.
<!-- /rtk-instructions -->