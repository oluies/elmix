// Prerendering: samma diagram som Laminar-appen, ECharts SSR -> statisk SVG.
// Kor:  node render.mjs   (skriver ../prerendered.html)
import * as echarts from 'echarts'
import { readFileSync, writeFileSync } from 'node:fs'

const caps = JSON.parse(readFileSync('../data/capture.json', 'utf8'))
const pws  = JSON.parse(readFileSync('../data/pricewind.json', 'utf8'))

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
  yAxis: zones.map((_, i) => ({ type: 'value', gridIndex: i, min: 0.4, max: 1.1,
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

const parts = [
  '<h2>Capture rate per kraftslag</h2>',
  svg(captureOption, 1060, captureHeight),
  ...zones.flatMap(z => [
    `<h2>Pris vs vind – ${z.replace('_', '')}</h2>`,
    svg(priceWindOption(z), 1060, 420)
  ])
]

const html = `<!doctype html>
<html lang="sv"><head><meta charset="utf-8"><title>Elmix – prerendered</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; margin: 0 auto;
         max-width: 1100px; padding: 0 16px 40px; color: #222; }
  h1 { font-size: 20px; margin-top: 24px; }
  h2 { font-size: 15px; color: #555; margin: 28px 0 4px; }
</style></head><body>
<h1>Elmix – SE1–SE4 (syntetisk demo-data, prerenderad SVG)</h1>
${parts.join('\n')}
</body></html>`

writeFileSync('../prerendered.html', html)
console.log('skrev ../prerendered.html')
