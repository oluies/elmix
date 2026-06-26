// Prerendering: samma diagram som Laminar-appen, ECharts SSR -> statisk SVG.
// Kor:  node render.mjs   (skriver ../prerendered.html)
import * as echarts from 'echarts'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'

const e15 = JSON.parse(readFileSync('../data/elmix15.json', 'utf8'))
const pcaExp   = JSON.parse(readFileSync('../data/pca_explained.json', 'utf8'))
const pcaLoad  = JSON.parse(readFileSync('../data/pca_loadings.json', 'utf8'))
const pcaScore = JSON.parse(readFileSync('../data/pca_scores.json', 'utf8'))
// Kannibaliseringsmarter – kan saknas (då hoppas diagrammen över).
const readJson = p => existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : []
const capture  = readJson('../data/capture.json')
const prisVind = readJson('../data/pris_vs_vind.json')

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

// Layout för PCA-small-multiples.
const zones = [...new Set(pcaExp.map(e => e.zone))].sort()
const COLS = 2, ROW_H = 256
const col = i => i % COLS, row = i => Math.floor(i / COLS)
const rows = Math.ceil(zones.length / COLS)

// ---- pris & vindandel (15-min, hela perioden) -----------------------------
function priceWindOption(zone) {
  const rs = e15.filter(r => r.z === zone)
  return {
    animation: false,
    title: { text: `Pris & vindandel – ${zone.replace('_', '')} (15-min)`, left: 'center', textStyle: { fontSize: 13 } },
    legend: { top: 26 },
    grid: { top: 64, bottom: 40, left: 60, right: 60 },
    xAxis: { type: 'time' },
    yAxis: [
      { type: 'value', name: 'EUR/MWh' },
      { type: 'value', name: 'vind %', min: 0, max: 100, splitLine: { show: false } }
    ],
    series: [
      { name: 'Pris (EUR/MWh)', type: 'line', yAxisIndex: 0, showSymbol: false, sampling: 'lttb',
        itemStyle: { color: '#c0392b' }, lineStyle: { width: 0.8 },
        data: rs.map(r => [r.t, r.p]) },
      { name: 'Vindandel (%)', type: 'line', yAxisIndex: 1, showSymbol: false, sampling: 'lttb',
        itemStyle: { color: '#5470c6' }, areaStyle: { opacity: 0.18 },
        lineStyle: { width: 0.8 }, data: rs.map(r => [r.t, Math.round(r.v * 1000) / 10]) }
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
// Per zon: bara kraftslag/komponenter som faktiskt finns (SE1 saknar t.ex.
// kärnkraft -> ska inte visas som rad i heatmappen).
const fuelsOf = z => FUELS.filter(f => pcaLoad.some(l => l.zone === z && l.kraftslag === f))
const pcsOf = z => [...new Set(pcaLoad.filter(l => l.zone === z).map(l => l.pc))].sort((a, b) => a - b)

// Scree: förklarad varians per komponent (small multiples).
const screeOption = {
  animation: false,
  title: zones.map((z, i) => titleAt(z, i, 19)),
  grid: zones.map((_, i) => ({ left: `${8 + col(i) * 48}%`, width: '38%',
                               top: 64 + row(i) * ROW_H, height: 180 })),
  xAxis: zones.map((z, i) => ({ type: 'category', gridIndex: i, data: pcsOf(z).map(p => 'PC' + p) })),
  yAxis: zones.map((_, i) => ({ type: 'value', gridIndex: i, min: 0, max: 1,
                                name: i === 0 ? 'förklarad andel' : '' })),
  series: zones.map((z, i) => ({
    type: 'bar', xAxisIndex: i, yAxisIndex: i, itemStyle: { color: '#5470c6' },
    data: pcsOf(z).map(p => pcaExp.find(e => e.zone === z && e.pc === p)?.explained ?? 0),
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
  xAxis: zones.map((z, i) => ({ type: 'category', gridIndex: i, data: pcsOf(z).map(p => 'PC' + p) })),
  yAxis: zones.map((z, i) => ({ type: 'category', gridIndex: i, data: fuelsOf(z).map(FUELABB) })),
  visualMap: { min: -1, max: 1, dimension: 2, calculable: false,
               orient: 'horizontal', left: 'center', bottom: 0,
               inRange: { color: ['#c0392b', '#f7f7f7', '#2c7fb8'] } },
  series: zones.map((z, i) => ({
    type: 'heatmap', xAxisIndex: i, yAxisIndex: i,
    data: fuelsOf(z).flatMap((f, fi) => pcsOf(z).map((p, pi) => [pi, fi, loadOf(z, p, f)])),
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

// ---- Kannibalisering: capture rate-trend + pris-vs-vind -------------------
const FUEL_ORDER = ['Vind', 'Sol', 'Vattenkraft', 'Kärnkraft', 'Kraftvärme/övr']
const YRAMP = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#9a60b4', '#fc8452']

function captureOption(zone) {
  const rows = capture.filter(r => r.zone === zone)
  const years = [...new Set(rows.map(r => r.yr))].sort((a, b) => a - b)
  const fuels = FUEL_ORDER.filter(f => rows.some(r => r.kraftslag === f))
  const series = fuels.map(f => {
    const byYr = new Map(rows.filter(r => r.kraftslag === f).map(r => [r.yr, r.capture_rate]))
    return {
      name: FUELABB(f), type: 'line', connectNulls: true, showSymbol: true, symbolSize: 5,
      itemStyle: { color: FARG[f] ?? '#888' }, lineStyle: { width: 2 },
      data: years.map(y => byYr.has(y) ? byYr.get(y) : null)
    }
  })
  series.push({
    name: 'baspris', type: 'line', data: [],
    markLine: { silent: true, symbol: 'none', lineStyle: { color: '#999', type: 'dashed' },
      data: [{ yAxis: 1 }], label: { formatter: '1.0 = baspris', position: 'insideEndTop', fontSize: 9 } }
  })
  return {
    animation: false,
    title: { text: `Capture rate per kraftslag – ${zone.replace('_', '')}`,
      subtext: 'värdeviktat snittpris / baspris · < 1 = kannibalisering',
      left: 'center', textStyle: { fontSize: 13 }, subtextStyle: { fontSize: 11 } },
    legend: { top: 46, data: fuels.map(FUELABB) },
    grid: { top: 84, bottom: 36, left: 52, right: 28 },
    xAxis: { type: 'category', data: years.map(String) },
    yAxis: { type: 'value', name: 'capture rate', min: 0 },
    series
  }
}

function prisVindOption(zone) {
  const rows = prisVind.filter(r => r.zone === zone)
  const years = [...new Set(rows.map(r => r.yr))].sort((a, b) => a - b)
  const series = years.map((y, i) => ({
    name: String(y), type: 'line', showSymbol: false, smooth: true,
    itemStyle: { color: YRAMP[i % YRAMP.length] }, lineStyle: { width: 1.6 },
    data: rows.filter(r => r.yr === y).sort((a, b) => a.vind_bin - b.vind_bin)
      .map(r => [r.vind_bin, r.pris_median])
  }))
  return {
    animation: false,
    title: { text: `Pris vs vindnivå – ${zone.replace('_', '')}`,
      subtext: 'medianpris per vind-percentil (1 = låg vind … 20 = hög) · nedåtlutning = kannibalisering',
      left: 'center', textStyle: { fontSize: 13 }, subtextStyle: { fontSize: 11 } },
    legend: { top: 46, type: 'scroll' },
    grid: { top: 84, bottom: 40, left: 58, right: 28 },
    xAxis: { type: 'category', name: 'vind-bin', data: Array.from({ length: 20 }, (_, i) => String(i + 1)) },
    yAxis: { type: 'value', name: 'EUR/MWh' },
    series
  }
}

const capZones = [...new Set(capture.map(r => r.zone))].sort()
const pvZones  = [...new Set(prisVind.map(r => r.zone))].sort()
const kannibalParts = []
if (capZones.length || pvZones.length) {
  kannibalParts.push('<h2>Kannibalisering – förnybar energis värdefall</h2>')
  kannibalParts.push('<p>Väderberoende kraft med noll marginalkostnad pressar själv ner ' +
    'priset i de timmar den producerar mest. <em>Capture rate</em> (fångat pris / baspris) ' +
    'faller då under 1 – kannibalisering i siffror.</p>')
  for (const z of capZones) kannibalParts.push(svg(captureOption(z), 1060, 320))
  for (const z of pvZones)  kannibalParts.push(svg(prisVindOption(z), 1060, 340))
}

const parts = [
  ...zones.flatMap(z => [
    `<h2>Pris &amp; vindandel – ${z.replace('_', '')}</h2>`,
    svg(priceWindOption(z), 1060, 360)
  ]),
  ...kannibalParts,
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
  .forklaring { margin-top: 32px; background: #f7f9fc; border: 1px solid #e3e8ef;
                border-radius: 8px; padding: 4px 20px 12px; font-size: 14px;
                line-height: 1.5; }
  .forklaring li { margin: 5px 0; }
</style></head><body>
<h1>Elmix – SE1–SE4 (ENTSO-E, 15-min, från 2 dec 2025, prerenderad SVG)</h1>
<p>Statisk variant. Den <a href="index.html">interaktiva rapporten</a> låter
dig välja zon och tidsperiod och räknar om PCA:n live i webbläsaren.</p>
${parts.join('\n')}
<section class="forklaring">
  <h2>Så läser du PCA-diagrammen</h2>
  <p>PCA (principalkomponentanalys) sammanfattar hur den kvartsvisa
  produktionsmixen varierar över tid. Varje kvart beskrivs av kraftslagens
  andelar – Vind, Sol, Vattenkraft, Kärnkraft, Kraftvärme/övr – och PCA
  hittar de riktningar där mixen varierar mest.</p>
  <ul>
    <li><strong>PC1</strong> är den kombination av kraftslag som fångar
    <em>mest</em> av variationen; PC2 fångar näst mest och är oberoende av PC1.</li>
    <li><strong>Loadings</strong> (heatmappen) är vikterna – vilka kraftslag
    som bygger upp varje komponent. Kraftslag med motsatt tecken byter mot
    varandra: i SE3 betyder PC1 <em>vind upp ⇔ kärnkraft/vatten ner</em>.</li>
    <li><strong>Förklarad varians</strong> (scree) visar hur stor andel av
    all variation varje komponent fångar. I SE1/SE2 räcker en enda axel
    (~100 %) – mixen svänger nästan bara mellan vatten och vind.</li>
    <li><strong>Tecknet är godtyckligt</strong>; det är kontrasten mellan
    kraftslagen som betyder något, inte om vikten är plus eller minus.</li>
    <li><strong>Biplotten</strong> visar PC1/PC2 per dygn. I den interaktiva
    vyn är punkterna färgade efter pris, så man ser vilka mix-lägen som är dyra.</li>
  </ul>
</section>
<section class="forklaring">
  <h2>Om kannibalisering</h2>
  <p>Förnybar kannibalisering är fenomenet där en ökande marknadspenetration av
  förnybar energi med noll marginalkostnad – vind och sol – sänker deras eget
  marknadsvärde, eftersom de pressar ner spotpriset i just de timmar de
  producerar mest. I förlängningen hotar det investeringsincitamenten.</p>
  <ul>
    <li><strong>Capture rate</strong> = det värdeviktade snittpris ett kraftslag
    faktiskt fångar dividerat med baspriset (tidsviktat snitt). Vind faller under
    1 och sjunker när utbyggnaden ökar; jämn kraft som kärnkraft ligger nära 1.</li>
    <li><strong>Pris vs vindnivå</strong> visar mekaniken bakom: medianpriset per
    vind-percentil. En nedåtlutande kurva – och brantare för senare år – är
    kannibaliseringen direkt avläst.</li>
    <li>Batterilagring kan i teorin lyfta capture rate genom att flytta energi
    till dyrare timmar, men arbitrage-spreaden krymper när mycket lagring
    konkurrerar (batterikannibalisering).</li>
  </ul>
  <h2>Källor &amp; vidare läsning</h2>
  <ul>
    <li>Lannhard, Fredrik (2023). <em>Cannibalization of Renewable Energy in
    Spain: Market Implications and Mitigation Strategies through Carbon Pricing
    and Guarantees of Origin.</em> KTH.
    <a href="https://www.diva-portal.org/smash/record.jsf?pid=diva2%3A1768389">DiVA</a> ·
    <a href="http://www.diva-portal.org/smash/get/diva2:1768389/FULLTEXT01.pdf">PDF</a></li>
    <li>McKinsey &amp; Company. <em>How US battery operators can navigate a
    transitioning energy market.</em>
    <a href="https://www.mckinsey.com/industries/energy-and-materials/our-insights/blog/how-us-battery-operators-can-navigate-a-transitioning-energy-market">mckinsey.com</a></li>
    <li>Montel News (2024). <em>Spain’s battery market may face cannibalisation
    risk.</em>
    <a href="https://montelnews.com/news/5ae4b928-cd1d-4358-af5a-77f90d1ac6e3/spains-battery-market-may-face-cannibalisation-risk-expert">montelnews.com</a>
    (kräver inloggning)</li>
  </ul>
</section>
<footer>© 2026 Örjan Lundberg ·
  <a href="https://github.com/oluies">GitHub</a> ·
  <a href="https://www.linkedin.com/in/orjanlundberg/">LinkedIn</a> ·
  Källkod: <a href="https://github.com/oluies/elmix">github.com/oluies/elmix</a> ·
  Byggd med <a href="https://laminar.dev">Scala.js + Laminar</a></footer>
</body></html>`

writeFileSync('../prerendered.html', html)
console.log('skrev ../prerendered.html')
