// SSR-röktest av round.js diagram-byggare (både round- och consumption-config).
import * as echarts from 'echarts'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const RV = require('../round.js')

function loadGlobal(path, name) { const window = {}; eval(readFileSync(path, 'utf8')); return window[name] }
const R = loadGlobal('../data/round-data.js', 'elmixRound')
const C = loadGlobal('../data/consumption-data.js', 'elmixConsumption')
const E = loadGlobal('../data/consumption-eu-data.js', 'elmixEu')
const D = loadGlobal('../data/diurnal-data.js', 'elmixDiurnal')
const CONS_FUELS = [...RV.DEFAULT_FUELS, { key: 'imp', name: 'Import (netto)', c: '#9aa7b8' }]
const EU_FUELS = ['k', 'kol', 'gas', 'v', 's', 'va', 'ov', 'imp'].map(k => ({ key: k, name: k, c: '#888' }))
const r = (data, z, y) => data.find(x => x.z === z && x.y === y)

function svg(opt, w, h) {
  const c = echarts.init(null, null, { renderer: 'svg', ssr: true, width: w, height: h })
  c.setOption(opt); const s = c.renderToSVGString(); c.dispose(); return s
}

const rr = r(R.data, 'SE_3', 2025)
const cc = r(C.data, 'SE_4', 2025)
const cc26 = r(C.data, 'SE_4', 2026)
const dayBuckets = (rec, day) => rec.h.reduce((n, _, i) => n + (rec.doy[i] === day ? 1 : 0), 0)
const cases = [
  ['round barYear', RV.barYearOption(rr, 'SE_3', 2025, RV.DEFAULT_FUELS)],
  ['round barDay', RV.barDayOption(rr, 'SE_3', 2025, 166, RV.DEFAULT_FUELS)],
  ['round sunburst', RV.sunburstOption(rr, 'SE_3', 2025, RV.DEFAULT_FUELS)],
  ['round heat', RV.heatOption(rr, 'SE_3', 2025, RV.priceHeat)],
  ['cons barYear', RV.barYearOption(cc, 'SE_4', 2025, CONS_FUELS)],
  ['cons barDay-h', RV.barDayOption(cc, 'SE_4', 2025, 166, CONS_FUELS)],
  ['cons barDay-15m', RV.barDayOption(cc26, 'SE_4', 2026, 90, CONS_FUELS)],
  ['cons sunburst', RV.sunburstOption(cc, 'SE_4', 2025, CONS_FUELS)],
  ['cons heat(imp)', RV.heatOption(cc, 'SE_4', 2025, RV.importHeat)],
  ['eu DE barYear', RV.barYearOption(r(E.data, 'DE_LU', 2025), 'DE', 2025, EU_FUELS)],
  ['eu DE barDay', RV.barDayOption(r(E.data, 'DE_LU', 2026), 'DE', 2026, 100, EU_FUELS)],
  ['eu FR barYear', RV.barYearOption(r(E.data, 'FR', 2025), 'FR', 2025, EU_FUELS)],
  ['eu DE co2', RV.heatOption(r(E.data, 'DE_LU', 2025), 'DE', 2025, RV.co2Heat)],
  ['eu FR co2', RV.heatOption(r(E.data, 'FR', 2025), 'FR', 2025, RV.co2Heat)],
  ['eu DE co2Day', RV.co2DayOption(r(E.data, 'DE_LU', 2026), 'DE', 2026, 100, EU_FUELS)],
  ['se co2Day', RV.co2DayOption(r(C.data, 'SE_4', 2026), 'SE4', 2026, 100, CONS_FUELS)],
  ['diurnal SE', RV.diurnalOption(r(D.data, 'SE', D.years.at(-1)), 'Sverige', D.years.at(-1), RV.DEFAULT_FUELS)],
  ['diurnal SE3', RV.diurnalOption(r(D.data, 'SE_3', D.years.at(-1)), 'SE3', D.years.at(-1), RV.DEFAULT_FUELS)]
]
const meanCo2 = z => {
  const d = r(E.data, z, 2025); let s = 0, n = 0
  for (let i = 0; i < d.h.length; i++) { const v = RV.co2Heat.value(d, i); if (v != null) { s += v; n++ } }
  return Math.round(s / n)
}
console.log(`CO2-intensitet 2025: DE ${meanCo2('DE_LU')} g/kWh · FR ${meanCo2('FR')} g/kWh`)
console.log(`2026 dag 90 har ${dayBuckets(cc26, 90)} buckets (väntat 96), heatmap aggregerar -> 24 ringar`)
const tbl = RV.priceTableHtml(RV.dayHours(cc26, 90, CONS_FUELS))
const tradeRows = (tbl.match(/<tr>/g) || []).length - 1 // minus header
console.log(`pristabell: ${tradeRows} rader, har kolumner: ${/\+skatt\+moms/.test(tbl) && /Spot/.test(tbl)}`)
const diSum = y => ['SE_1', 'SE_2', 'SE_3', 'SE_4'].reduce((a, z) => {
  const d = r(D.data, z, y); return a + ['v', 's', 'va', 'k', 'kv'].reduce((b, k) => b + d[k].reduce((c, v) => c + v, 0), 0)
}, 0)
const diY = D.years.at(-1)
const diSE = ['v', 's', 'va', 'k', 'kv'].reduce((b, k) => b + r(D.data, 'SE', diY)[k].reduce((c, v) => c + v, 0), 0)
for (const z of D.zones) {
  const n = r(D.data, z, diY).h.length
  if (n !== 24) throw new Error(`dygnsprofil ${z} ${diY} har ${n} timmar, väntat 24`)
}
// SE ska vara summan av SE1-SE4 (avrundning per timme ger <0,5 % glapp).
const diErr = Math.abs(diSE - diSum(diY)) / diSum(diY)
if (diErr > 0.005) throw new Error(`dygnsprofil SE != Σ SE1-4: ${(diErr * 100).toFixed(2)} %`)
console.log(`dygnsprofil ${diY}: 24 timmar × ${D.zones.length} zoner, SE = Σ SE1-4 (avvikelse ${(diErr * 100).toFixed(3)} %)`)
for (const [name, opt] of cases) {
  const s = svg(opt, 720, 560)
  console.log(name.padEnd(16), 'OK —', (s.match(/<path/g) || []).length, 'paths')
}
console.log('ALLA OK')
