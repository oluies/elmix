// Förbrukningsmix DE vs FR: två polära stack-klockor sida vid sida. Återanvänder
// round.js:s byggare via window.RoundViz. Fyrspråkig (SV/EN/FR/DE). Klick på en
// dag drillar BÅDA klockorna till samma dygn (jämförelse).
(function () {
  'use strict'
  const RV = window.RoundViz
  const E = window.elmixEu
  if (!RV || !E) return

  const FUELS = [
    { key: 'k',   name: 'Kärnkraft',       nameEn: 'Nuclear',    nameFr: 'Nucléaire',    nameDe: 'Kernkraft',      c: '#ee6666' },
    { key: 'kol', name: 'Kol',             nameEn: 'Coal',       nameFr: 'Charbon',      nameDe: 'Kohle',          c: '#4d4d4d' },
    { key: 'gas', name: 'Gas',             nameEn: 'Gas',        nameFr: 'Gaz',          nameDe: 'Gas',            c: '#e8853a' },
    { key: 'v',   name: 'Vind',            nameEn: 'Wind',       nameFr: 'Éolien',       nameDe: 'Wind',           c: '#5470c6' },
    { key: 's',   name: 'Sol',             nameEn: 'Solar',      nameFr: 'Solaire',      nameDe: 'Solar',          c: '#fac858' },
    { key: 'va',  name: 'Vattenkraft',     nameEn: 'Hydro',      nameFr: 'Hydro',        nameDe: 'Wasser',         c: '#91cc75' },
    { key: 'ov',  name: 'Övrigt',          nameEn: 'Other',      nameFr: 'Autres',       nameDe: 'Sonstige',       c: '#9a60b4' },
    { key: 'imp', name: 'Import (netto)',  nameEn: 'Net import', nameFr: 'Import (net)', nameDe: 'Import (netto)', c: '#9aa7b8' }
  ]
  const LANGS = ['sv', 'en', 'fr', 'de']
  const TXT = {
    title: ['Förbrukningsmix: Tyskland vs Frankrike', 'Consumption mix: Germany vs France',
      'Mix de consommation : Allemagne vs France', 'Verbrauchsmix: Deutschland vs Frankreich'],
    lead: ['Förbrukningsmix (produktion + nettoimport) sida vid sida. Nettoimport = total last − produktion; grå kil = import. Klicka på en dag för dygnsvy (båda klockorna). Endast FBMC-eran (2025/2026).',
      'Consumption mix (generation + net imports) side by side. Net import = total load − generation; grey wedge = imports. Click a day for the daily view (both clocks). FBMC era only (2025/2026).',
      'Mix de consommation (production + import net) côte à côte. Import net = charge totale − production ; coin gris = import. Cliquez un jour pour la vue journalière (les deux). Ère FBMC uniquement (2025/2026).',
      'Verbrauchsmix (Erzeugung + Nettoimport) nebeneinander. Nettoimport = Gesamtlast − Erzeugung; graue Keil = Import. Klicken Sie einen Tag für die Tagesansicht (beide). Nur FBMC-Ära (2025/2026).'],
    back: ['← Helår', '← Full year', '← Année', '← Ganzes Jahr'],
    prev: ['‹ Föreg. dag', '‹ Prev day', '‹ Jour préc.', '‹ Vortag'],
    next: ['Nästa dag ›', 'Next day ›', 'Jour suiv. ›', 'Nächster Tag ›'],
    yr: ['År (FBMC)', 'Year (FBMC)', 'Année (FBMC)', 'Jahr (FBMC)']
  }
  let lang = 'sv', year = 2025, drillDay = null
  const li = () => LANGS.indexOf(lang)
  const rec = z => E.data.find(d => d.z === z && d.y === year)
  const zlabel = z => z === 'DE_LU' ? 'DE' : 'FR'
  const dayList = () => RV.daily(rec(E.zones[0]), FUELS).map(x => x.day)

  const charts = {}
  E.zones.forEach(z => { charts[z] = echarts.init(document.getElementById('clock-' + z)) })
  window.addEventListener('resize', () => E.zones.forEach(z => charts[z].resize()))

  function renderZone(z) {
    const d = rec(z)
    charts[z].setOption(drillDay != null
      ? RV.barDayOption(d, zlabel(z), year, drillDay, FUELS)
      : RV.barYearOption(d, zlabel(z), year, FUELS), true)
    charts[z].resize()
  }
  function updateText() {
    const i = li()
    document.getElementById('eu-title').textContent = TXT.title[i]
    document.getElementById('eu-lead').textContent = TXT.lead[i]
    document.getElementById('eu-back').textContent = TXT.back[i]
    document.getElementById('eu-prev').textContent = TXT.prev[i]
    document.getElementById('eu-next').textContent = TXT.next[i]
    document.querySelector('#year-picker legend').textContent = TXT.yr[i]
    document.getElementById('eu-toolbar').style.display = drillDay != null ? 'block' : 'none'
    document.getElementById('eu-daylabel').textContent = drillDay != null ? RV.isoDate(year, drillDay) : ''
    const days = dayList(), k = days.indexOf(drillDay)
    document.getElementById('eu-prev').disabled = drillDay == null || k <= 0
    document.getElementById('eu-next').disabled = drillDay == null || k >= days.length - 1
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
  document.getElementById('eu-back').onclick = () => { drillDay = null; renderAll() }
  document.getElementById('eu-prev').onclick = () => stepDay(-1)
  document.getElementById('eu-next').onclick = () => stepDay(1)
  document.addEventListener('keydown', e => {
    if (drillDay == null) return
    if (e.key === 'ArrowLeft') stepDay(-1); else if (e.key === 'ArrowRight') stepDay(1)
  })

  const yp = document.getElementById('year-picker')
  E.years.forEach(y => {
    const lab = document.createElement('label'); lab.style.marginRight = '12px'
    const inp = document.createElement('input'); inp.type = 'radio'; inp.name = 'euyear'; inp.checked = y === year
    inp.onchange = () => { year = y; drillDay = null; renderAll() }
    lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + y)); yp.appendChild(lab)
  })

  const sw = document.getElementById('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase()
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) c.classList.toggle('active', c.textContent === l.toUpperCase())
      renderAll()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', 'sv')
  for (const c of sw.children) c.classList.toggle('active', c.textContent === 'SV')

  renderAll()
})()
