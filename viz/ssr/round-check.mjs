// SSR-röktest av round.js diagram-byggare. Kör: node round-check.mjs
import * as echarts from 'echarts'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const RV = require('../round.js')

const code = readFileSync('../data/round-data.js', 'utf8')
const window = {}
eval(code)
const R = window.elmixRound
const d = R.data.find(x => x.z === 'SE_3' && x.y === 2025)

function svg(opt, w, h) {
  const c = echarts.init(null, null, { renderer: 'svg', ssr: true, width: w, height: h })
  c.setOption(opt)
  const s = c.renderToSVGString()
  c.dispose()
  return s
}

const cases = [
  ['barYear (helår)', RV.barYearOption(d, 'SE_3', 2025)],
  ['barDay (dygn 166)', RV.barDayOption(d, 'SE_3', 2025, 166)],
  ['sunburst', RV.sunburstOption(d, 'SE_3', 2025)],
  ['heat', RV.heatOption(d, 'SE_3', 2025)]
]
for (const [name, opt] of cases) {
  const s = svg(opt, 720, 560)
  const paths = (s.match(/<path/g) || []).length
  console.log(name.padEnd(20), 'OK —', s.length, 'byte,', paths, 'paths')
}
console.log('ALLA OK')
