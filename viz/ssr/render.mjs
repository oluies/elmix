// Prerendering: samma diagram som Laminar-appen, ECharts SSR -> statisk SVG.
// Kor:  node render.mjs   (skriver ../prerendered.html)
import * as echarts from 'echarts'
import { readFileSync, writeFileSync } from 'node:fs'

const caps = JSON.parse(readFileSync('../data/capture.json', 'utf8'))
const pws  = JSON.parse(readFileSync('../data/pricewind.json', 'utf8'))
const pcaExp   = JSON.parse(readFileSync('../data/pca_explained.json', 'utf8'))
const pcaLoad  = JSON.parse(readFileSync('../data/pca_loadings.json', 'utf8'))
const pcaScore = JSON.parse(readFileSync('../data/pca_scores.json', 'utf8'))

const FARG = {
  'Vind': '#5470c6', 'Sol': '#fac858', 'Vattenkraft': '#91cc75',
  'Kärnkraft': '#ee6666', 'Kraftvärme/övr': '#73c0de'
}

function svg(option, width, height) {
  const chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width, height })
  chart.setOption(option)
  const out = chart.renderToSVGString()
  chart.dispose()
  return out
}

// ---- capture: small multiples ---------------------------------------------
const zones = [...new Set(caps.map(c => c.zone))].sort()
const years = [...new Set(caps.map(c => c.yr))].sort()
const slag  = [...new Set(caps.map(c => c.kraftslag))].sort()

// 2 kolumner, sa manga rader som behovs (SE1-SE4 -> 2x2)
const COLS = 2, ROW_H = 256
const col = i => i % COLS, row = i => Math.floor(i / COLS)
const rows = Math.ceil(zones.length / COLS)
const captureHeight = 64 + rows * ROW_H + 20

// Dynamiska y-granser sa inget kraftslag klipps bort (vattenkraft >1).
const crVals = caps.map(c => c.capture_rate).filter(v => v != null)
const yMin = Math.floor((Math.min(...crVals, 1.0) - 0.1) * 10) / 10
const yMax = Math.ceil((Math.max(...crVals, 1.0) + 0.1) * 10) / 10

const captureOption = {
  animation: false,
  title: zones.map((z, i) => ({
    text: z.replace('_', ''), left: `${8 + col(i) * 48 + 19}%`, top: 38 + row(i) * ROW_H,
    textAlign: 'center', textStyle: { fontSize: 13 }
  })),
  legend: { top: 0, data: slag },
  grid:  zones.map((_, i) => ({ left: `${8 + col(i) * 48}%`, width: '38%',
                                top: 64 + row(i) * ROW_H, height: 180 })),
  xAxis: zones.map((_, i) => ({ type: 'category', gridIndex: i, data: years.map(String) })),
  yAxis: zones.map((_, i) => ({ type: 'value', gridIndex: i, min: yMin, max: yMax,
                                name: i === 0 ? 'capture rate' : '' })),
  series: zones.flatMap((z, i) => slag.map(s => ({
    name: s, type: 'line', xAxisIndex: i, yAxisIndex: i,
    itemStyle: { color: FARG[s] ?? '#999' },
    data: years.map(y => caps.find(c => c.zone === z && c.kraftslag === s && c.yr === y)?.capture_rate ?? null),
    markLine: { silent: true, symbol: 'none',
                lineStyle: { type: 'dashed', color: '#aaa' },
                label: { show: i === zones.length - 1, formatter: '1.0' },
                data: [{ yAxis: 1.0 }] }
  })))
}

// ---- pris vs vind: statiskt forzoomad till senaste 14 dygnen ---------------
function priceWindOption(zone) {
  const rows = pws.filter(r => r.zone === zone)
  const maxT = Math.max(...rows.map(r => r.t))
  const from = maxT - 14 * 24 * 3600 * 1000
  const win  = rows.filter(r => r.t >= from)
  return {
    animation: false,
    title: { text: `Day-ahead-pris vs vindproduktion – ${zone.replace('_', '')} (senaste 14 dygnen)`, left: 'center' },
    legend: { top: 34 },
    grid: { top: 80, bottom: 48, left: 64, right: 64 },
    xAxis: { type: 'time' },
    yAxis: [
      { type: 'value', name: 'EUR/MWh' },
      { type: 'value', name: 'MWh/h', splitLine: { show: false } }
    ],
    series: [
      { name: 'Pris (EUR/MWh)', type: 'line', yAxisIndex: 0, showSymbol: false,
        itemStyle: { color: '#c0392b' }, lineStyle: { width: 1.2 },
        data: win.map(r => [r.t, r.eur_mwh]) },
      { name: 'Vind (MWh/h)', type: 'line', yAxisIndex: 1, showSymbol: false,
        itemStyle: { color: '#5470c6' }, areaStyle: { opacity: 0.22 },
        lineStyle: { width: 1 }, data: win.map(r => [r.t, r.wind_mwh]) }
    ]
  }
}

// ---- PCA: scree, loadings-heatmap, biplot ---------------------------------
const FUELS = ['Vind', 'Sol', 'Vattenkraft', 'Kärnkraft', 'Kraftvärme/övr']
const FUELABB = f => f.replace('Kraftvärme/övr', 'Kraftv.')
const PCS = [1, 2, 3, 4, 5]
const pcaHeight = 64 + rows * ROW_H + 20
const titleAt = (z, i, dx) => ({
  text: z.replace('_', ''), left: `${8 + col(i) * 48 + dx}%`, top: 38 + row(i) * ROW_H,
  textAlign: 'center', textStyle: { fontSize: 13 }
})
const loadOf = (z, p, f) =>
  pcaLoad.find(l => l.zone === z && l.pc === p && l.kraftslag === f)?.loading ?? 0

// Scree: förklarad varians per komponent (small multiples).
const screeOption = {
  animation: false,
  title: zones.map((z, i) => titleAt(z, i, 19)),
  grid: zones.map((_, i) => ({ left: `${8 + col(i) * 48}%`, width: '38%',
                               top: 64 + row(i) * ROW_H, height: 180 })),
  xAxis: zones.map((_, i) => ({ type: 'category', gridIndex: i, data: PCS.map(p => 'PC' + p) })),
  yAxis: zones.map((_, i) => ({ type: 'value', gridIndex: i, min: 0, max: 1,
                                name: i === 0 ? 'förklarad andel' : '' })),
  series: zones.map((z, i) => ({
    type: 'bar', xAxisIndex: i, yAxisIndex: i, itemStyle: { color: '#5470c6' },
    data: PCS.map(p => pcaExp.find(e => e.zone === z && e.pc === p)?.explained ?? 0),
    label: { show: true, position: 'top', fontSize: 9,
             formatter: o => (o.value * 100).toFixed(0) + '%' }
  }))
}

// Loadings: kraftslag × komponent, divergerande heatmap (small multiples).
const loadingsOption = {
  animation: false,
  title: zones.map((z, i) => titleAt(z, i, 17)),
  grid: zones.map((_, i) => ({ left: `${10 + col(i) * 48}%`, width: '32%',
                               top: 64 + row(i) * ROW_H, height: 180 })),
  xAxis: zones.map((_, i) => ({ type: 'category', gridIndex: i, data: PCS.map(p => 'PC' + p) })),
  yAxis: zones.map((_, i) => ({ type: 'category', gridIndex: i, data: FUELS.map(FUELABB) })),
  visualMap: { min: -1, max: 1, dimension: 2, calculable: false,
               orient: 'horizontal', left: 'center', bottom: 0,
               inRange: { color: ['#c0392b', '#f7f7f7', '#2c7fb8'] } },
  series: zones.map((z, i) => ({
    type: 'heatmap', xAxisIndex: i, yAxisIndex: i,
    data: FUELS.flatMap((f, fi) => PCS.map((p, pi) => [pi, fi, loadOf(z, p, f)])),
    label: { show: true, fontSize: 8, formatter: o => o.value[2].toFixed(2) }
  }))
}
const loadingsHeight = pcaHeight + 30   // plats for visualMap-legenden

// Biplot: dagsmedel-scores PC1 vs PC2, färgade per år, med loading-vektorer.
const sYears = [...new Set(pcaScore.map(s => s.yr))].sort()
const YFARG = { 2023: '#5470c6', 2024: '#91cc75', 2025: '#fac858', 2026: '#ee6666' }
function biplotOption(zone) {
  const rs = pcaScore.filter(s => s.zone === zone)
  const k = 0.9 * Math.max(...rs.map(r => Math.max(Math.abs(r.pc1), Math.abs(r.pc2))), 0.01)
  const scatter = sYears.map(y => ({
    name: String(y), type: 'scatter', symbolSize: 4,
    itemStyle: { color: YFARG[y] ?? '#999', opacity: 0.5 },
    data: rs.filter(r => r.yr === y).map(r => [r.pc1, r.pc2])
  }))
  const vectors = FUELS
    .map(f => ({ f, l1: loadOf(zone, 1, f), l2: loadOf(zone, 2, f) }))
    .filter(v => Math.abs(v.l1) > 1e-6 || Math.abs(v.l2) > 1e-6)
    .map(v => ({
      name: v.f, type: 'line', symbol: ['none', 'arrow'], symbolSize: 9, silent: true,
      lineStyle: { color: '#333', width: 1.5 }, data: [[0, 0], [k * v.l1, k * v.l2]],
      endLabel: { show: true, formatter: FUELABB(v.f), fontSize: 10, color: '#111' }
    }))
  return {
    animation: false,
    title: { text: `Biplot – ${zone.replace('_', '')} (PC1 vs PC2, dagsmedel)`, left: 'center' },
    legend: { top: 28, data: sYears.map(String) },
    grid: { top: 64, bottom: 48, left: 60, right: 70 },
    xAxis: { type: 'value', name: 'PC1' },
    yAxis: { type: 'value', name: 'PC2', splitLine: { show: true } },
    series: [...scatter, ...vectors]
  }
}

const parts = [
  '<h2>Capture rate per kraftslag</h2>',
  svg(captureOption, 1060, captureHeight),
  ...zones.flatMap(z => [
    `<h2>Pris vs vind – ${z.replace('_', '')}</h2>`,
    svg(priceWindOption(z), 1060, 420)
  ]),
  '<h2>PCA på produktionsmixen – förklarad varians (scree)</h2>',
  svg(screeOption, 1060, pcaHeight),
  '<h2>PCA – loadings (kraftslagens vikt per komponent)</h2>',
  svg(loadingsOption, 1060, loadingsHeight),
  ...zones.flatMap(z => [
    `<h2>PCA-biplot – ${z.replace('_', '')}</h2>`,
    svg(biplotOption(z), 1060, 460)
  ])
]

const html = `<!doctype html>
<html lang="sv"><head><meta charset="utf-8"><title>Elmix – prerendered</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; margin: 0 auto;
         max-width: 1100px; padding: 0 16px 40px; color: #222; }
  h1 { font-size: 20px; margin-top: 24px; }
  h2 { font-size: 15px; color: #555; margin: 28px 0 4px; }
  footer { margin-top: 36px; padding-top: 12px; border-top: 1px solid #ddd;
           font-size: 13px; color: #666; }
  footer a { color: #36c; }
</style></head><body>
<h1>Elmix – SE1–SE4 (ENTSO-E 2023–2026, prerenderad SVG)</h1>
${parts.join('\n')}
<footer>© 2026 Örjan Lundberg ·
  Källkod: <a href="https://github.com/oluies/elmix">github.com/oluies/elmix</a> ·
  Byggd med <a href="https://laminar.dev">Scala.js + Laminar</a></footer>
</body></html>`

writeFileSync('../prerendered.html', html)
console.log('skrev ../prerendered.html')
