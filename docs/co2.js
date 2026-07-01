// CO2-intensitets-heatmap per zon sida vid sida. Config via window.CO2_CFG
// { data, langs, label, texts }. Använder round.js:s heatOption + co2Heat
// (RoundViz). Ingen drilldown – heatmappen är en helårsöversikt (dag × timme).
(function () {
  'use strict'
  const RV = window.RoundViz
  const cfg = window.CO2_CFG
  if (!RV || !cfg || !cfg.data) return
  const E = cfg.data
  const LANGS = cfg.langs || ['sv', 'en']
  const label = cfg.label || (z => z.replace('_', ''))
  const TXT = cfg.texts

  let lang = LANGS[0]
  let year = E.years.includes(2025) ? 2025 : E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const rec = z => E.data.find(d => d.z === z && d.y === year)
  const $ = id => document.getElementById(id)

  const clocksEl = $('clocks')
  const charts = {}
  E.zones.forEach(z => {
    const div = document.createElement('div'); div.className = 'chart'; div.id = 'co2-' + z
    clocksEl.appendChild(div); charts[z] = echarts.init(div)
  })
  window.addEventListener('resize', () => E.zones.forEach(z => charts[z].resize()))

  function renderAll() {
    RV.setLang(lang)
    E.zones.forEach(z => { charts[z].setOption(RV.heatOption(rec(z), label(z), year, RV.co2Heat), true); charts[z].resize() })
    const i = li()
    $('mc-title').textContent = TXT.title[i]
    $('mc-lead').textContent = TXT.lead[i]
    document.querySelector('#year-picker legend').textContent = TXT.yr[i]
  }

  const yp = $('year-picker')
  E.years.forEach(y => {
    const lab = document.createElement('label'); lab.style.marginRight = '12px'
    const inp = document.createElement('input'); inp.type = 'radio'; inp.name = 'co2year'; inp.checked = y === year
    inp.onchange = () => { year = y; renderAll() }
    lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + y)); yp.appendChild(lab)
  })

  const sw = $('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase()
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) c.classList.toggle('active', c.textContent === l.toUpperCase())
      renderAll()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', lang)
  for (const c of sw.children) c.classList.toggle('active', c.textContent === lang.toUpperCase())

  renderAll()
})()
