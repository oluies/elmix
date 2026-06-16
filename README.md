# elmix

[![CI](https://github.com/oluies/elmix/actions/workflows/ci.yml/badge.svg)](https://github.com/oluies/elmix/actions/workflows/ci.yml)

Hämtar elmarknadsdata för de fyra svenska elområdena (SE1–SE4) från
ENTSO-E och bygger analysunderlag (Parquet-marts) med DuckDB, plus en
webbvisualisering i Scala.js/Laminar med ECharts.

Rapporterna publiceras via GitHub Pages: https://oluies.github.io/elmix/
(interaktiv) och https://oluies.github.io/elmix/prerendered.html (statisk).

## Struktur

```
Elmix.scala         Mill single-file-script: hämtning, parsning, orkestrering
transform.sql       All analys (frusen – delas med Python-varianten)
seed_testdata.sql   Syntetisk rådata för röktest utan API-nyckel
viz/                Visualisering (eget Mill-projekt, rör inte rotscriptet)
  app/src/          Laminar-app + minimal ECharts-facade (Scala.js)
  ssr/render.mjs    Prerenderering till statisk SVG (ECharts Node-SSR)
  index.html        Interaktiv app (fungerar från file://)
  prerendered.html  Statisk variant, noll JS vid visning
data/raw/           Hämtad/seedad rådata per zon och år (Parquet)
data/marts/         Sex analysmarts (Parquet)
```

## Pipeline

Kräver Mill 1.1+ och DuckDB CLI.

```bash
export ENTSOE_API_KEY=...                                # nyckel från transparency.entsoe.eu
./mill Elmix.scala fetch --start 2016 --end 2026 --data all
./mill Elmix.scala transform                             # bygger data/marts/*.parquet
./mill Elmix.scala pca                                   # PCA på produktionsmixen
```

`fetch` är inkrementell (befintliga Parquet hoppas över) och pausar 2 s
mellan API-anrop. Alla fyra zonerna hämtas alltid; varje zon/år blir en
egen Parquet-fil.

`pca` läser `fct_gen` ur `elmix.duckdb` (kör `transform` först) och gör en
PCA på den timvisa produktionsmixen (kraftslagsandelar) per zon. Eftersom
DuckDB saknar egenvärdesberäkning ligger detta som ett medvetet undantag
i Elmix.scala – en ren, funktionell Jacobi-rotation utan externt
linjäralgebra-beroende (kovariansmatrisen är liten och symmetrisk).
Resultat: `data/marts/pca_explained.parquet` (förklarad varians per
komponent), `pca_loadings.parquet` (kraftslagens vikt per komponent) och
`pca_scores.parquet` (varje timmes projektion på PC1/PC2, för biplot).

`./mill Elmix.scala test` kör ett fristående självtest av PCA-kärnan
(egenvärden mot analytiskt kända värden, rekonstruktion VΛVᵀ, ortonormala
egenvektorer, singulära fall). Kräver ingen data.

### Röktest utan API-nyckel

```bash
mkdir -p data/raw/generation data/raw/prices data/raw/flows data/raw/imbalance data/marts
duckdb < seed_testdata.sql        # syntetisk data för SE1–SE4
./mill Elmix.scala transform
```

Förväntat: sex marts i `data/marts/`, `capture_rate < 1` för Vind och
~1 för Kärnkraft i `mart_capture`, icke-tom `mart_changepoints`.

## Visualisering

Två varianter över samma marts, alla fyra zonerna separat:

- **Capture rate per kraftslag** – small multiples (2×2, en panel per zon)
  med referenslinje på 1,0.
- **Pris vs vind, timupplöst** – dubbla y-axlar med zoom (`dataZoom`),
  förinställd på senaste 14 dygnen; zonväljare (SE1–SE4) via Laminar.
- **PCA på produktionsmixen** – scree (förklarad varians), loadings-heatmap
  (kraftslag × komponent) och biplot (PC1/PC2 per dygn, loading-vektorer).

```bash
./viz/export-data.sh              # elmix.duckdb -> viz/data/elmix-data.js
cd viz
mill app.fastLinkJS               # bygg appen -> öppna viz/index.html
cd ssr && npm install && node render.mjs   # prerendera -> viz/prerendered.html
```

Diagramdatan ligger som JS-global i `viz/data/elmix-data.js`, så
`index.html` fungerar utan webbserver. Kör om `export-data.sh` efter
varje ny `transform`. Zonerna i diagrammen styrs helt av datan – fler
eller färre zoner kräver inga kodändringar.

**Ingen DuckDB i webbläsaren.** Den interaktiva appen kör *inte* SQL i
realtid – `export-data.sh` förberäknar allt vid publicering och bakar in
det som en statisk ögonblicksbild (JSON-globaler). Webbläsaren läser bara
den och ritar med ECharts (helt klientsidan), vilket är vad GitHub Pages
statiska hosting tillåter. Vill man ha riktiga frågor mot marterna i
webbläsaren är nästa steg DuckDB-Wasm (parquet-marterna är förberedda för
det), i utbyte mot att ladda DuckDB-Wasm + parquet-filerna.

## Status

- Kompilerar, röktestat med syntetisk data och **skarpkört mot ENTSO-E**
  för 2023–2026 (alla fyra dokumenttyperna för SE1–SE4). EIC-koderna,
  MTU-bytet okt 2025 (tim → kvart) och Neighbours-kartan är därmed
  verifierade mot riktig data – nord/syd-prisgradienten, vindens
  capture rate < 1 och kärnkraftens ~1 (endast SE3) stämmer.
- Obalans (A85) levereras som zip-arkiv av ENTSO-E; `apiGet` packar upp
  zip-svar och returnerar ett dokument per fil. Datan hämtas och lagras
  i `data/raw/imbalance/` (kategori A04/A05, Sveriges enprismodell ger
  identiska värden) men används ännu inte i någon mart.

## Publicering

`./viz/publish-pages.sh` bygger den optimerade appen (`fullLinkJS`),
prerendrerar SVG-rapporten och paketerar allt i `docs/`, som GitHub
Pages serverar från `main`.

## Utveckling och CI

`./mill` är ett bootstrap-skript (pinnar Mill via `.mill-version`), så
varken lokal installation eller CI behöver ha Mill förinstallerat.

GitHub Actions (`.github/workflows/ci.yml`) kör vid varje push/PR:
- **Bygg + test** – kompilerar rotscriptet och viz, kör PCA-självtestet.
- **Scalafmt** – formatkontroll (`.scalafmt.conf`).
- **Länkkontroll** – lychee mot README och rapporterna.

Beroenden hålls uppdaterade av **Renovate** (`renovate.json`): npm i
`viz/ssr` (echarts) och GitHub Actions stöds direkt; Mill-beroendena
(duckdb_jdbc, scala-xml, laminar) täcks via custom regex-managers
eftersom Renovate saknar inbyggt Mill-stöd. Aktivera genom att installera
Renovate-appen på repot.

---
© 2026 Örjan Lundberg
