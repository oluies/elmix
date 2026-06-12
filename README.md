# elmix

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
```

`fetch` är inkrementell (befintliga Parquet hoppas över) och pausar 2 s
mellan API-anrop. Alla fyra zonerna hämtas alltid; varje zon/år blir en
egen Parquet-fil.

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

## Status

- Kompilerar och är röktestat med syntetisk data (SE1–SE4).
- Skarp hämtning mot ENTSO-E är **inte** integrationstestad ännu:
  EIC-koderna, A85-parsningen och Neighbours-kartan verifieras först vid
  riktig hämtning. Se "Kända osäkerheter" i `CLAUDE.md`.

## Publicering

`./viz/publish-pages.sh` bygger den optimerade appen (`fullLinkJS`),
prerendrerar SVG-rapporten och paketerar allt i `docs/`, som GitHub
Pages serverar från `main`.

---
© 2026 Örjan Lundberg
