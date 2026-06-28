// SSR-röktest av round.js diagram-byggare (både round- och consumption-config).
import * as echarts from 'echarts'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const RV = require('../round.js')

function loadGlobal(path, name) { const window = {}; eval(readFileSync(path, 'utf8')); return window[name] }
const R = loadGlobal('../data/round-data.js', 'elmixRound')
const C = loadGlobal('../data/consumption-data.js', 'elmixConsumption')
const CONS_FUELS = [...RV.DEFAULT_FUELS, { key: 'imp', name: 'Import (netto)', c: '#9aa7b8' }]
const r = (data, z, y) => data.find(x => x.z === z && x.y === y)

function svg(opt, w, h) {
  const c = echarts.init(null, null, { renderer: 'svg', ssr: true, width: w, height: h })
  c.setOption(opt); const s = c.renderToSVGString(); c.dispose(); return s
}

const rr = r(R.data, 'SE_3', 2025)
const cc = r(C.data, 'SE_4', 2025)
const cases = [
  ['round barYear', RV.barYearOption(rr, 'SE_3', 2025, RV.DEFAULT_FUELS)],
  ['round barDay', RV.barDayOption(rr, 'SE_3', 2025, 166, RV.DEFAULT_FUELS)],
  ['round sunburst', RV.sunburstOption(rr, 'SE_3', 2025, RV.DEFAULT_FUELS)],
  ['round heat', RV.heatOption(rr, 'SE_3', 2025, RV.priceHeat)],
  ['cons barYear', RV.barYearOption(cc, 'SE_4', 2025, CONS_FUELS)],
  ['cons barDay', RV.barDayOption(cc, 'SE_4', 2025, 166, CONS_FUELS)],
  ['cons sunburst', RV.sunburstOption(cc, 'SE_4', 2025, CONS_FUELS)],
  ['cons heat(imp)', RV.heatOption(cc, 'SE_4', 2025, RV.importHeat)]
]
for (const [name, opt] of cases) {
  const s = svg(opt, 720, 560)
  console.log(name.padEnd(16), 'OK —', (s.match(/<path/g) || []).length, 'paths')
}
console.log('ALLA OK')
