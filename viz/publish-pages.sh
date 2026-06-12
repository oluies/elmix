#!/usr/bin/env bash
# Bygger och paketerar GitHub Pages-sajten i docs/ (interaktiv app +
# prerenderad rapport). Kor fran projektroten: ./viz/publish-pages.sh
set -euo pipefail
cd "$(dirname "$0")/.."

./viz/export-data.sh
(cd viz && mill app.fullLinkJS)
(cd viz/ssr && node render.mjs)

rm -rf docs
mkdir -p docs/vendor docs/data
cp viz/out/app/fullLinkJS.dest/main.js docs/
cp viz/vendor/echarts.min.js docs/vendor/
cp viz/data/elmix-data.js docs/data/
cp viz/prerendered.html docs/
sed 's|out/app/fastLinkJS.dest/main.js|main.js|' viz/index.html > docs/index.html

echo "docs/ klar:"
ls docs/
