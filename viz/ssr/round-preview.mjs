import * as echarts from 'echarts'
import { readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const RV = require('../round.js')
const window = {}; eval(readFileSync('../data/round-data.js','utf8')); const R = window.elmixRound
const d = R.data.find(x => x.z === 'SE_3' && x.y === 2025)
const svg = (opt,w,h) => { const c=echarts.init(null,null,{renderer:'svg',ssr:true,width:w,height:h}); c.setOption(opt); const s=c.renderToSVGString(); c.dispose(); return s }
const blocks = [
  ['Polär stack-klocka (helår)', 'Ett år medurs, varje stapel = en dags mix normaliserad till 100 %. Svart linje = pris (skalat) – dippar = kannibalisering. I appen: klicka en dag → 24h.', RV.barYearOption(d,'SE_3',2025)],
  ['Polär stack-klocka (drilldown, dygn 166)', 'Samma klocka men 24 timmar – intradagsmix + prisprofil för ett enskilt dygn.', RV.barDayOption(d,'SE_3',2025,166)],
  ['Sunburst (månad → dag → kraftslag)', 'Innerring månader, mitten dagar, ytterring kraftslagens andel. Klick zoomar in.', RV.sunburstOption(d,'SE_3',2025)],
  ['Radiell heatmap (dag × timme, färg = pris)', 'Vinkel = dag, radie = timme (0 i mitten → 23 ytterst), färg = pris (blått billigt/kannibaliserat, rött dyrt).', RV.heatOption(d,'SE_3',2025)]
]
const html = `<!doctype html><html lang="sv"><head><meta charset="utf-8"><title>round – preview SE3 2025</title>
<style>body{font-family:system-ui,sans-serif;max-width:900px;margin:0 auto;padding:0 16px 40px;color:#222}
h1{font-size:20px}h2{font-size:15px;margin:24px 0 2px}.d{color:#555;font-size:13px;margin:0 0 6px;line-height:1.5}</style></head><body>
<h1>Runda vyer – statisk förhandsbild (SE3, 2025)</h1>
<p class="d">Detta är bara en stillbild. Den interaktiva sidan är <code>viz/round.html</code> (zon/år-väljare + klick-drilldown).</p>
${blocks.map(([t,desc,opt])=>`<h2>${t}</h2><p class="d">${desc}</p>${svg(opt,1040,680)}`).join('\n')}
</body></html>`
writeFileSync('../round-preview.html', html)
console.log('skrev viz/round-preview.html ('+html.length+' byte)')
