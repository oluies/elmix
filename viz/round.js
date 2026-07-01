// Runda vyer (round.html + consumption.html). Config-styrt: window.ROUND_CFG
// väljer dataset, kraftslagslista och heatmap-läge. Diagram-byggarna är rena
// funktioner (ingen DOM/echarts) så de SSR-röktestas i node (round-check.mjs);
// DOM-wiringen längst ner körs bara i webbläsaren.
(function () {
  'use strict'

  const DEFAULT_FUELS = [
    { key: 'v',  name: 'Vind',           nameEn: 'Wind',      c: '#5470c6' },
    { key: 's',  name: 'Sol',            nameEn: 'Solar',     c: '#fac858' },
    { key: 'va', name: 'Vattenkraft',    nameEn: 'Hydro',     c: '#91cc75' },
    { key: 'k',  name: 'Kärnkraft',      nameEn: 'Nuclear',   c: '#ee6666' },
    { key: 'kv', name: 'Kraftvärme/övr', nameEn: 'CHP/other', c: '#73c0de' }
  ]
  // Heatmap-lägen: värde per timme + färgskala (sv/en).
  const priceHeat = {
    suffix: 'pris per dag × timme', suffixEn: 'price per day × hour',
    unit: '€/MWh', unitEn: '€/MWh', text: ['dyrt', 'billigt'], textEn: ['expensive', 'cheap'],
    colors: ['#2c7fb8', '#7fcdbb', '#ffffcc', '#fd8d3c', '#c0392b'],
    value: (d, i) => d.p[i]
  }
  const importHeat = {
    suffix: 'nettoimportandel per dag × timme', suffixEn: 'net-import share per day × hour',
    unit: '% från import', unitEn: '% from import', text: ['hög', 'låg'], textEn: ['high', 'low'],
    colors: ['#1a9850', '#a6d96a', '#ffffbf', '#fdae61', '#7b3294'],
    value: (d, i) => {
      const imp = d.imp ? d.imp[i] : 0
      const g = d.v[i] + d.s[i] + d.va[i] + d.k[i] + d.kv[i] + imp
      return g ? +(imp / g * 100).toFixed(1) : null
    }
  }

  const MNAME = ['Jan', 'Feb', 'Mar', 'Apr', 'Maj', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dec']
  const MNAME_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const MNAME_FR = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.']
  const MNAME_DE = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']
  const MDAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  const MSTART = (() => { const s = []; let a = 1; for (const m of MDAYS) { s.push(a); a += m } return s })()
  const monthOf = day => { for (let m = 11; m >= 0; m--) if (day >= MSTART[m]) return m; return 0 }
  // doy (1..365) -> ISO-datum (YYYY-MM-DD); UTC för att undvika tidszonsskift.
  const isoDate = (year, doy) => new Date(Date.UTC(year, 0, doy)).toISOString().slice(0, 10)

  // Språk (sv/en). Byggarna läser modulvariabeln LANG vid render; DOM-lagret
  // sätter den via språkväljaren. Node-röktestet kör default 'sv'.
  let LANG = 'sv'
  const t = (sv, en, fr, de) => ({ sv, en, fr, de })[LANG] ?? sv
  const NKEY = { sv: 'name', en: 'nameEn', fr: 'nameFr', de: 'nameDe' }
  const fname = f => f[NKEY[LANG]] || f.name
  const MONTHS = { sv: MNAME, en: MNAME_EN, fr: MNAME_FR, de: MNAME_DE }
  const mName = m => (MONTHS[LANG] || MNAME)[m]

  // Delad layout: legend vertikalt till höger, stor cirkel nedflyttad under titeln.
  const legendRight = data => ({ type: 'scroll', orient: 'vertical', right: 10, top: 'middle', itemGap: 10, data })
  const POLAR = { center: ['44%', '56%'], radius: ['17%', '86%'] }
  const titleTop = text => ({ text, left: '44%', top: 8, textAlign: 'center', textStyle: { fontSize: 14 } })
  const monthLabel = { interval: 0, fontSize: 11, color: '#555', formatter: v => { const mi = MSTART.indexOf(+v); return mi >= 0 ? mName(mi) : '' } }

  // ---- datahjälpare (rena) --------------------------------------------------
  // Dagsaggregat: summera valda kraftslag per dag, snittpris per dag.
  function daily(d, fuels) {
    const keys = fuels.map(f => f.key)
    const by = new Map()
    for (let i = 0; i < d.h.length; i++) {
      const day = d.doy[i]
      let o = by.get(day)
      if (!o) { o = { day, _p: 0, _pn: 0 }; keys.forEach(k => o[k] = 0); by.set(day, o) }
      keys.forEach(k => { o[k] += d[k] ? d[k][i] : 0 })
      if (d.p && d.p[i] != null) { o._p += d.p[i]; o._pn++ }
    }
    return [...by.values()].sort((a, b) => a.day - b.day).map(o => {
      const r = { day: o.day, p: o._pn ? o._p / o._pn : null }; keys.forEach(k => r[k] = o[k]); return r
    })
  }

  // Ett dygns intervall (timme eller 15-min) sorterade på h*4+q.
  function dayHours(d, day, fuels) {
    const keys = fuels.map(f => f.key)
    const out = []
    for (let i = 0; i < d.h.length; i++) if (d.doy[i] === day) {
      const q = d.q ? d.q[i] : 0
      const r = { h: d.h[i], q, p: d.p ? d.p[i] : null }; keys.forEach(k => r[k] = d[k] ? d[k][i] : 0); out.push(r)
    }
    return out.sort((a, b) => (a.h * 4 + a.q) - (b.h * 4 + b.q))
  }

  // Heatmap-celler [dagIndex, timme, värde, doy] via valueFn(d, i).
  // Aggregerar ev. 15-min till TIMME (snitt) så heatmappen alltid har 24 ringar.
  function heatData(d, valueFn) {
    const doys = [...new Set(d.doy)].sort((a, b) => a - b)
    const idx = new Map(doys.map((dy, i) => [dy, i]))
    const acc = new Map()
    for (let i = 0; i < d.h.length; i++) {
      const val = valueFn(d, i); if (val == null) continue
      const di = idx.get(d.doy[i]), key = di * 24 + d.h[i]
      let o = acc.get(key); if (!o) { o = { di, h: d.h[i], doy: d.doy[i], sum: 0, n: 0 }; acc.set(key, o) }
      o.sum += val; o.n++
    }
    const out = [...acc.values()].map(o => [o.di, o.h, +(o.sum / o.n).toFixed(1), o.doy])
    return { out, doys }
  }

  const shareSeries = (rows, fuels) => fuels.map(f => ({
    name: fname(f), type: 'bar', coordinateSystem: 'polar', stack: 'mix', itemStyle: { color: f.c },
    data: rows.map(x => { const t = fuels.reduce((s, g) => s + x[g.key], 0); return t ? +(x[f.key] / t * 100).toFixed(1) : 0 })
  }))
  // Pris min–max-skalat över perioden: billigaste -> 0 (centrum), dyraste -> 100
  // (kant). Då spänner linjen hela radien och dipparna (kannibalisering) syns.
  const priceLine = (rows) => {
    const ps = rows.map(x => x.p).filter(v => v != null)
    const pmin = ps.length ? Math.min(...ps) : 0
    const pmax = ps.length ? Math.max(...ps) : 1
    const span = (pmax - pmin) || 1
    return {
      name: t('Pris', 'Price', 'Prix', 'Preis'), type: 'line', coordinateSystem: 'polar', smooth: true, showSymbol: false, z: 10,
      lineStyle: { color: '#111', width: 1.5 },
      data: rows.map(x => x.p == null ? null : +((x.p - pmin) / span * 100).toFixed(1))
    }
  }

  // Pristabell (öre/kWh) för dagsvyn: spot, + moms, + energiskatt + moms.
  // Tydligt deklarerade antaganden – justera konstanterna vid behov.
  const EUR_SEK = 11.30          // antagen växelkurs
  const ENERGISKATT_ORE = 35.12 // energiskatt exkl moms, öre/kWh (sv 2025)
  const MOMS = 1.25
  function priceTableHtml(rows) {
    const body = rows.filter(x => x.p != null).map(x => {
      const spot = x.p * EUR_SEK / 10 // EUR/MWh -> öre/kWh
      const moms = spot * MOMS
      const full = (spot + ENERGISKATT_ORE) * MOMS
      const tm = `${String(x.h).padStart(2, '0')}:${String(x.q * 15).padStart(2, '0')}`
      return `<tr><td>${tm}</td><td>${spot.toFixed(1)}</td><td>${moms.toFixed(1)}</td><td>${full.toFixed(1)}</td></tr>`
    }).join('')
    return `<div class="cap">${t('öre/kWh · spot = day-ahead · antaget', 'öre/kWh · spot = day-ahead · assuming')} ` +
      `1 EUR = ${EUR_SEK} SEK · ${t('energiskatt', 'energy tax')} ${ENERGISKATT_ORE} öre/kWh ` +
      `(${t('exkl moms', 'excl VAT')}) · ${t('moms 25 %', 'VAT 25 %')}</div>` +
      `<table><thead><tr><th>${t('Tid', 'Time')}</th><th>Spot</th>` +
      `<th>${t('+moms', '+VAT')}</th><th>${t('+skatt+moms', '+tax+VAT')}</th></tr></thead>` +
      `<tbody>${body}</tbody></table>`
  }

  // ---- 1. Polär stack-klocka (år) + drilldown (dygn) ------------------------
  function barYearOption(d, zone, year, fuels) {
    const rows = daily(d, fuels)
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – ${t('mix-andel över året', 'mix share over the year', 'part du mix sur l’année', 'Mix-Anteil übers Jahr')}`),
      legend: legendRight(fuels.map(fname).concat(t('Pris', 'Price', 'Prix', 'Preis'))),
      tooltip: { trigger: 'item' },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: rows.map(x => String(x.day)), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, z: 5, axisLabel: monthLabel
      },
      radiusAxis: { min: 0, max: 100, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [...shareSeries(rows, fuels), priceLine(rows)]
    }
  }
  function barDayOption(d, zone, year, day, fuels) {
    const rows = dayHours(d, day, fuels)
    return {
      title: titleTop(`${zone.replace('_', '')} – ${isoDate(year, day)} (${t('dag', 'day', 'jour', 'Tag')} ${day}, ${rows.length > 24 ? rows.length + t(' kvart', ' quarters', ' quarts', ' Viertel') : '24 h'})`),
      legend: legendRight(fuels.map(fname).concat(t('Pris', 'Price', 'Prix', 'Preis'))),
      tooltip: { trigger: 'item' },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: rows.map(x => x.q === 0 ? x.h + ':00' : ''), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, axisLabel: { interval: 0, fontSize: 11, color: '#555' }
      },
      radiusAxis: { min: 0, max: 100, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [...shareSeries(rows, fuels), priceLine(rows)]
    }
  }

  // ---- 2. Sunburst (månad → dag → kraftslag) --------------------------------
  function sunburstOption(d, zone, year, fuels) {
    const rows = daily(d, fuels)
    const months = new Map()
    for (const x of rows) { const m = monthOf(x.day); if (!months.has(m)) months.set(m, []); months.get(m).push(x) }
    const data = [...months.keys()].sort((a, b) => a - b).map(m => ({
      name: mName(m), itemStyle: { color: '#e9edf3' },
      children: months.get(m).map(x => ({
        name: t('Dag ', 'Day ') + x.day,
        children: fuels.map(f => ({ name: fname(f), value: Math.max(0, x[f.key]), itemStyle: { color: f.c } }))
      }))
    }))
    return {
      title: { text: `${zone.replace('_', '')} ${year} – ${t('mix (månad → dag → kraftslag)', 'mix (month → day → source)')}`, left: 'center', top: 8, textStyle: { fontSize: 14 } },
      tooltip: { formatter: p => `${p.name}<br/>${Math.round(p.value).toLocaleString(LANG === 'en' ? 'en' : 'sv')} MWh` },
      series: [{
        type: 'sunburst', center: ['50%', '54%'], radius: ['8%', '94%'], data, sort: null, animationDurationUpdate: 500,
        emphasis: { focus: 'ancestor' }, nodeClick: 'rootToNode',
        levels: [
          {},
          { r0: '6%', r: '26%', label: { rotate: 'tangential' }, itemStyle: { borderWidth: 1, borderColor: '#fff' } },
          { r0: '26%', r: '30%', label: { show: false }, itemStyle: { borderWidth: 0 } },
          { r0: '30%', r: '95%', label: { show: false }, itemStyle: { borderWidth: 0 } }
        ]
      }]
    }
  }

  // ---- 3. Radiell heatmap (dag × timme, färg = pris ELLER importandel) ------
  function heatOption(d, zone, year, hc) {
    const { out, doys } = heatData(d, hc.value)
    const vals = out.map(o => o[2])
    const vmin = Math.min(...vals), vmax = Math.max(...vals)
    const N = doys.length
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – ${t(hc.suffix, hc.suffixEn)}`),
      tooltip: { trigger: 'item', formatter: p => `${isoDate(year, p.value[3])} (${t('dag', 'day')} ${p.value[3]}), ${t('kl', 'h')} ${String(p.value[1]).padStart(2, '0')}<br/>${p.value[2]} ${t(hc.unit, hc.unitEn)}` },
      visualMap: {
        type: 'continuous', min: vmin, max: vmax, dimension: 2, calculable: true,
        orient: 'vertical', right: 12, top: 'middle', text: LANG === 'en' ? hc.textEn : hc.text, itemHeight: 160,
        inRange: { color: hc.colors }
      },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: doys.map(String), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, axisLine: { show: false }, axisLabel: monthLabel
      },
      radiusAxis: {
        type: 'category', data: Array.from({ length: 24 }, (_, i) => String(i)),
        axisLabel: { interval: 5, fontSize: 10, color: '#999' }, axisTick: { show: false },
        axisLine: { show: false }, splitLine: { show: false }
      },
      series: [{
        type: 'custom', coordinateSystem: 'polar', data: out, animation: false,
        renderItem: (params, api) => {
          const di = api.value(0), hh = api.value(1)
          const cs = params.coordSys // {cx, cy, r, r0}
          const cx = cs.cx, cy = cs.cy, r0 = cs.r0, r = cs.r
          const a0 = Math.PI / 2 - (di / N) * 2 * Math.PI
          const a1 = Math.PI / 2 - ((di + 1) / N) * 2 * Math.PI
          const rr = r - r0
          const ri = r0 + (hh / 24) * rr, ro = r0 + ((hh + 1) / 24) * rr
          return {
            type: 'sector',
            shape: { cx, cy, r0: ri, r: ro, startAngle: a0, endAngle: a1, clockwise: true },
            style: { fill: api.visual('color') }
          }
        }
      }]
    }
  }

  const API = { DEFAULT_FUELS, priceHeat, importHeat, daily, dayHours, heatData, barYearOption, barDayOption, sunburstOption, heatOption, priceTableHtml }
  if (typeof module !== 'undefined' && module.exports) module.exports = API

  // ---- DOM-wiring (endast webbläsare) ---------------------------------------
  if (typeof document === 'undefined') return
  // Exponera rena byggare + språk-setter för fristående sidor (eu.js).
  window.RoundViz = {
    barYearOption, barDayOption, daily, dayHours, isoDate, t,
    setLang: l => { LANG = l }, getLang: () => LANG
  }
  // Standardsidans wiring (round/consumption) körs bara om dess layout finns.
  if (!document.getElementById('pane-bar')) return
  const cfg = window.ROUND_CFG || {}
  const R = cfg.data || window.elmixRound
  const FUELS = cfg.fuels || DEFAULT_FUELS
  const HEAT = cfg.heat === 'import' ? importHeat : priceHeat
  let zone = R.zones.includes('SE_3') ? 'SE_3' : R.zones[0]
  let year = R.years.includes(2025) ? 2025 : R.years[R.years.length - 1]
  let drillDay = null
  const rec = () => R.data.find(d => d.z === zone && d.y === year)

  const barChart = echarts.init(document.getElementById('pane-bar'))
  const sunChart = echarts.init(document.getElementById('pane-sun'))
  const heatChart = echarts.init(document.getElementById('pane-heat'))
  window.addEventListener('resize', () => { barChart.resize(); sunChart.resize(); heatChart.resize() })

  const dayList = () => daily(rec(), FUELS).map(x => x.day)
  function renderBar() {
    const d = rec()
    const tbl = document.getElementById('bar-table')
    if (drillDay != null) {
      barChart.setOption(barDayOption(d, zone, year, drillDay, FUELS), true)
      if (tbl) { tbl.innerHTML = priceTableHtml(dayHours(d, drillDay, FUELS)); tbl.style.display = 'block' }
      const days = dayList(), i = days.indexOf(drillDay)
      document.getElementById('bar-prev').disabled = i <= 0
      document.getElementById('bar-next').disabled = i >= days.length - 1
      document.getElementById('bar-daylabel').textContent = `${isoDate(year, drillDay)} · ${t('dag', 'day')} ${drillDay} ${t('(← → byter dag)', '(← → change day)')}`
    } else {
      barChart.setOption(barYearOption(d, zone, year, FUELS), true)
      if (tbl) tbl.style.display = 'none'
    }
    document.getElementById('bar-toolbar').style.display = drillDay != null ? 'block' : 'none'
    barChart.resize() // bredden ändras när tabellen visas/döljs
  }
  function stepDay(delta) {
    if (drillDay == null) return
    const days = dayList(), i = days.indexOf(drillDay)
    const ni = Math.min(days.length - 1, Math.max(0, i + delta))
    if (days[ni] !== drillDay) { drillDay = days[ni]; renderBar() }
  }
  function renderAll() {
    renderBar()
    sunChart.setOption(sunburstOption(rec(), zone, year, FUELS), true)
    heatChart.setOption(heatOption(rec(), zone, year, HEAT), true)
  }

  barChart.on('click', p => {
    if (drillDay || !(p.seriesType === 'bar' || p.seriesType === 'line')) return
    const rows = daily(rec(), FUELS); if (rows[p.dataIndex]) { drillDay = rows[p.dataIndex].day; renderBar() }
  })
  heatChart.on('click', p => {
    if (!p.value) return
    drillDay = p.value[3]; renderBar()
    document.getElementById('pane-bar').scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
  document.getElementById('bar-back').onclick = () => { drillDay = null; renderBar() }
  document.getElementById('bar-prev').onclick = () => stepDay(-1)
  document.getElementById('bar-next').onclick = () => stepDay(1)
  document.addEventListener('keydown', e => {
    if (drillDay == null) return
    if (e.key === 'ArrowLeft') stepDay(-1)
    else if (e.key === 'ArrowRight') stepDay(1)
  })

  function radios(host, items, current, on) {
    const el = document.getElementById(host)
    items.forEach(it => {
      const lab = document.createElement('label'); lab.style.marginRight = '12px'
      const inp = document.createElement('input')
      inp.type = 'radio'; inp.name = host; inp.checked = it.val === current
      inp.onchange = () => on(it.val)
      lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + it.txt))
      el.appendChild(lab)
    })
  }
  radios('zone-picker', R.zones.map(z => ({ val: z, txt: z.replace('_', '') })), zone, z => { zone = z; drillDay = null; renderAll() })
  radios('year-picker', R.years.map(y => ({ val: y, txt: String(y) })), year, y => { year = +y; drillDay = null; renderAll() })

  // Språkväljare (SV/EN): byter LANG, sätter html[data-lang] (statisk text via
  // CSS) och ritar om diagrammen.
  function setLang(l) {
    LANG = l
    document.documentElement.setAttribute('data-lang', l)
    const el = document.getElementById('lang-switch')
    if (el) el.querySelectorAll('button').forEach(b => b.classList.toggle('active', b.dataset.lang === l))
    renderAll()
  }
  const langSwitch = document.getElementById('lang-switch')
  if (langSwitch) ['sv', 'en'].forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase(); b.dataset.lang = l
    b.onclick = () => setLang(l); langSwitch.appendChild(b)
  })
  setLang('sv')
})()
