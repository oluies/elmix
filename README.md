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
PCA på den **kvartsvisa** produktionsmixen (kraftslagsandelar) per zon från
15-min-eran (2 dec 2025→). Eftersom DuckDB saknar egenvärdesberäkning ligger
detta som ett medvetet undantag i Elmix.scala – en ren, funktionell
Jacobi-rotation utan externt linjäralgebra-beroende (samma kärna återanvänds
klientsidan via Scala.js, se nedan).
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

15-min-rapporten (från 2 dec 2025), två varianter:

- **Interaktiv** (`index.html`): zonväljare + tidslinje (pris & vindandel)
  med tidsreglage (`dataZoom`). PCA på den kvartsvisa produktionsmixen
  räknas om **i webbläsaren** för vald period – scree (förklarad varians),
  loadings-heatmap (kraftslag × komponent), biplot (PC1/PC2 färgad efter
  pris) och prisdrivande komponenter (varje PC:s R² mot priset).
- **Prerenderad** (`prerendered.html`): samma vyer som statisk SVG över hela
  perioden, noll JS vid visning.

Båda varianterna visar dessutom **kannibaliseringen** av förnybar energi som
två förberäknade per-zon-diagram (statiska marter, ej periodberoende):

- **Capture rate per kraftslag** (`mart_capture`): värdeviktat snittpris /
  baspris över åren, med referenslinje vid 1.0. Vindens capture rate faller
  under 1 och sjunker med ökande utbyggnad; jämn kraft (kärnkraft) ligger
  nära 1 – kannibaliseringen direkt i siffror.
- **Pris vs vindnivå** (`mart_pris_vs_vind`): medianpris per vind-percentil,
  en linje per år. Nedåtlutande kurva (brantare för senare år) är mekaniken
  bakom kannibaliseringen. En förklaringssektion med källor (bl.a. Lannhard,
  KTH 2023) följer med rapporten.

```bash
./viz/export-data.sh              # elmix.duckdb -> viz/data/elmix-data.js
cd viz
mill app.fastLinkJS               # bygg appen -> öppna viz/index.html
cd ssr && npm install && node render.mjs   # prerendera -> viz/prerendered.html
```

**Klientsidig PCA, ingen DuckDB i webbläsaren.** `export-data.sh`
förberäknar 15-min-mixen + priset som en statisk ögonblicksbild
(`elmix15`) samt kannibaliseringsmarterna (`elmixCapture`, `elmixPrisVind`)
som JS-globaler i `viz/data/elmix-data.js`, så `index.html` fungerar
utan webbserver. Själva PCA:n körs sedan *i webbläsaren*: den rena
funktionella Jacobi-kärnan (`PcaCore.scala`, samma algoritm som i
Elmix.scala) kompileras till Scala.js och räknas om för vald tidsperiod.
Ingen SQL/DuckDB i klienten. Den prerenderade vyn läser i stället de
färdiga `pca_*.json` direkt (ECharts Node-SSR).

### CO₂-intensitet (metod)

CO₂-heatmapsen (förbrukningssidorna, `co2Heat`/`co2DayOption` i `round.js`) räknas
**klientsidan ur produktionsmixen** – ingen extra hämtning. Per intervall:

```
CO₂-intensitet (gCO₂eq/kWh) = Σ(produktion_kraftslag × faktor) / Σ produktion
```

Faktorerna är **livscykelmedianer från IPCC AR5** (WG3 2014, Annex III,
Table A.III.2), gCO₂eq/kWh:

| Kraftslag | Faktor | | Kraftslag | Faktor |
|---|---:|---|---|---:|
| Kärnkraft | 12 | | Gas (CCGT) | 490 |
| Vind (onshore) | 11 | | Kol | 820 |
| Sol-PV | 48 | | Biomassa | 230 |
| Vattenkraft | 24 | | | |

`Övrigt` (bio/olja/avfall) och SE `Kraftvärme/övr` (biomassa-CHP) approximeras som
biomassa (230). **Import exkluderas** (okänt ursprung) – siffran är intensiteten
för den *inhemska produktionen*. Färgskalan är **fast 0–600** så zonerna är
jämförbara (annars normaliseras varje heatmap mot sitt eget min/max). Typiska
värden 2025: DE ~300, FR ~32, SE1–3 ~20–26, SE4 ~58 gCO₂eq/kWh. Justera i
`CO2FACTOR` i `viz/round.js` (t.ex. biomassa 0 för biogen bokföring).

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
