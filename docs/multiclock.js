// Generisk sida med FLERA synkade polära stack-klockor (en per zon). Config via
// window.MULTI_CFG: { data, fuels, langs, label, texts }. Återanvänder round.js:s
// byggare (window.RoundViz). Klick på en dag drillar ALLA klockor till samma dygn.
(function () {
  'use strict'
  const RV = window.RoundViz
  const cfg = window.MULTI_CFG
  if (!RV || !cfg || !cfg.data) return
  const E = cfg.data
  const FUELS = cfg.fuels
  const LANGS = cfg.langs || ['sv', 'en']
  const label = cfg.label || (z => z.replace('_', ''))
  const TXT = cfg.texts

  let lang = LANGS[0]
  let year = E.years.includes(2025) ? 2025 : E.years[E.years.length - 1]
  let drillDay = null
  const li = () => LANGS.indexOf(lang)
  const rec = z => E.data.find(d => d.z === z && d.y === year)
  const dayList = () => RV.daily(rec(E.zones[0]), FUELS).map(x => x.day)
  const $ = id => document.getElementById(id)

  // Skapa en chart-div per zon i #clocks.
  const clocksEl = $('clocks')
  const charts = {}
  E.zones.forEach(z => {
    const div = document.createElement('div')
    div.className = 'chart'; div.id = 'clock-' + z
    clocksEl.appendChild(div)
    charts[z] = echarts.init(div)
  })
  window.addEventListener('resize', () => E.zones.forEach(z => charts[z].resize()))

  function renderZone(z) {
    const d = rec(z)
    charts[z].setOption(drillDay != null
      ? RV.barDayOption(d, label(z), year, drillDay, FUELS)
      : RV.barYearOption(d, label(z), year, FUELS), true)
    charts[z].resize()
  }
  function updateText() {
    const i = li()
    $('mc-title').textContent = TXT.title[i]
    $('mc-lead').textContent = TXT.lead[i]
    $('mc-back').textContent = TXT.back[i]
    $('mc-prev').textContent = TXT.prev[i]
    $('mc-next').textContent = TXT.next[i]
    document.querySelector('#year-picker legend').textContent = TXT.yr[i]
    $('mc-toolbar').style.display = drillDay != null ? 'block' : 'none'
    $('mc-daylabel').textContent = drillDay != null ? RV.isoDate(year, drillDay) : ''
    const days = dayList(), k = days.indexOf(drillDay)
    $('mc-prev').disabled = drillDay == null || k <= 0
    $('mc-next').disabled = drillDay == null || k >= days.length - 1
  }
  function renderAll() { RV.setLang(lang); E.zones.forEach(renderZone); updateText() }

  E.zones.forEach(z => charts[z].on('click', p => {
    if (drillDay != null || !(p.seriesType === 'bar' || p.seriesType === 'line')) return
    const rows = RV.daily(rec(z), FUELS); if (rows[p.dataIndex]) { drillDay = rows[p.dataIndex].day; renderAll() }
  }))
  function stepDay(delta) {
    if (drillDay == null) return
    const days = dayList(), k = days.indexOf(drillDay), nk = Math.min(days.length - 1, Math.max(0, k + delta))
    if (days[nk] !== drillDay) { drillDay = days[nk]; renderAll() }
  }
  $('mc-back').onclick = () => { drillDay = null; renderAll() }
  $('mc-prev').onclick = () => stepDay(-1)
  $('mc-next').onclick = () => stepDay(1)
  document.addEventListener('keydown', e => {
    if (drillDay == null) return
    if (e.key === 'ArrowLeft') stepDay(-1); else if (e.key === 'ArrowRight') stepDay(1)
  })

  const yp = $('year-picker')
  E.years.forEach(y => {
    const lab = document.createElement('label'); lab.style.marginRight = '12px'
    const inp = document.createElement('input'); inp.type = 'radio'; inp.name = 'mcyear'; inp.checked = y === year
    inp.onchange = () => { year = y; drillDay = null; renderAll() }
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
