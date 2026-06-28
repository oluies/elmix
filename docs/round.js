// Runda vyer (round.html + consumption.html). Config-styrt: window.ROUND_CFG
// väljer dataset, kraftslagslista och heatmap-läge. Diagram-byggarna är rena
// funktioner (ingen DOM/echarts) så de SSR-röktestas i node (round-check.mjs);
// DOM-wiringen längst ner körs bara i webbläsaren.
(function () {
  'use strict'

  const DEFAULT_FUELS = [
    { key: 'v',  name: 'Vind',          c: '#5470c6' },
    { key: 's',  name: 'Sol',           c: '#fac858' },
    { key: 'va', name: 'Vattenkraft',   c: '#91cc75' },
    { key: 'k',  name: 'Kärnkraft',     c: '#ee6666' },
    { key: 'kv', name: 'Kraftvärme/övr', c: '#73c0de' }
  ]
  // Heatmap-lägen: värde per timme + färgskala.
  const priceHeat = {
    suffix: 'pris per dag × timme', tip: v => `${v} €/MWh`, text: ['dyrt', 'billigt'],
    colors: ['#2c7fb8', '#7fcdbb', '#ffffcc', '#fd8d3c', '#c0392b'],
    value: (d, i) => d.p[i]
  }
  const importHeat = {
    suffix: 'nettoimportandel per dag × timme', tip: v => `${v} % från import`, text: ['hög', 'låg'],
    colors: ['#1a9850', '#a6d96a', '#ffffbf', '#fdae61', '#7b3294'],
    value: (d, i) => {
      const imp = d.imp ? d.imp[i] : 0
      const g = d.v[i] + d.s[i] + d.va[i] + d.k[i] + d.kv[i] + imp
      return g ? +(imp / g * 100).toFixed(1) : null
    }
  }

  const MNAME = ['Jan', 'Feb', 'Mar', 'Apr', 'Maj', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dec']
  const MDAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  const MSTART = (() => { const s = []; let a = 1; for (const m of MDAYS) { s.push(a); a += m } return s })()
  const monthOf = day => { for (let m = 11; m >= 0; m--) if (day >= MSTART[m]) return m; return 0 }

  // Delad layout: legend vertikalt till höger, stor cirkel nedflyttad under titeln.
  const legendRight = data => ({ type: 'scroll', orient: 'vertical', right: 10, top: 'middle', itemGap: 10, data })
  const POLAR = { center: ['44%', '56%'], radius: ['17%', '86%'] }
  const titleTop = text => ({ text, left: '44%', top: 8, textAlign: 'center', textStyle: { fontSize: 14 } })
  const monthLabel = { interval: 0, fontSize: 11, color: '#555', formatter: v => { const mi = MSTART.indexOf(+v); return mi >= 0 ? MNAME[mi] : '' } }

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

  // Ett dygns timmar (sorterade).
  function dayHours(d, day, fuels) {
    const keys = fuels.map(f => f.key)
    const out = []
    for (let i = 0; i < d.h.length; i++) if (d.doy[i] === day) {
      const r = { h: d.h[i], p: d.p ? d.p[i] : null }; keys.forEach(k => r[k] = d[k] ? d[k][i] : 0); out.push(r)
    }
    return out.sort((a, b) => a.h - b.h)
  }

  // Heatmap-celler [dagIndex, timme, värde, doy] via valueFn(d, i).
  function heatData(d, valueFn) {
    const doys = [...new Set(d.doy)].sort((a, b) => a - b)
    const idx = new Map(doys.map((dy, i) => [dy, i]))
    const out = []
    for (let i = 0; i < d.h.length; i++) {
      const val = valueFn(d, i)
      if (val != null) out.push([idx.get(d.doy[i]), d.h[i], val, d.doy[i]])
    }
    return { out, doys }
  }

  const shareSeries = (rows, fuels) => fuels.map(f => ({
    name: f.name, type: 'bar', coordinateSystem: 'polar', stack: 'mix', itemStyle: { color: f.c },
    data: rows.map(x => { const t = fuels.reduce((s, g) => s + x[g.key], 0); return t ? +(x[f.key] / t * 100).toFixed(1) : 0 })
  }))
  const priceLine = (rows) => {
    const maxP = Math.max(1, ...rows.map(x => x.p == null ? 0 : x.p))
    return {
      name: 'Pris', type: 'line', coordinateSystem: 'polar', smooth: true, showSymbol: false, z: 10,
      lineStyle: { color: '#111', width: 1.5 },
      data: rows.map(x => x.p == null ? null : +(x.p / maxP * 100).toFixed(1))
    }
  }

  // ---- 1. Polär stack-klocka (år) + drilldown (dygn) ------------------------
  function barYearOption(d, zone, year, fuels) {
    const rows = daily(d, fuels)
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – mix-andel över året`),
      legend: legendRight(fuels.map(f => f.name).concat('Pris')),
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
      title: titleTop(`${zone.replace('_', '')} ${year} – dygnsmix, dag ${day} (24 h)`),
      legend: legendRight(fuels.map(f => f.name).concat('Pris')),
      tooltip: { trigger: 'item' },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: rows.map(x => x.h + ':00'), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, axisLabel: { interval: 1, fontSize: 11, color: '#555' }
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
      name: MNAME[m], itemStyle: { color: '#e9edf3' },
      children: months.get(m).map(x => ({
        name: 'Dag ' + x.day,
        children: fuels.map(f => ({ name: f.name, value: Math.max(0, x[f.key]), itemStyle: { color: f.c } }))
      }))
    }))
    return {
      title: { text: `${zone.replace('_', '')} ${year} – mix (månad → dag → kraftslag)`, left: 'center', top: 8, textStyle: { fontSize: 14 } },
      tooltip: { formatter: p => `${p.name}<br/>${Math.round(p.value).toLocaleString('sv')} MWh` },
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
      title: titleTop(`${zone.replace('_', '')} ${year} – ${hc.suffix}`),
      tooltip: { trigger: 'item', formatter: p => `dag ${p.value[3]}, kl ${String(p.value[1]).padStart(2, '0')}<br/>${hc.tip(p.value[2])}` },
      visualMap: {
        type: 'continuous', min: vmin, max: vmax, dimension: 2, calculable: true,
        orient: 'vertical', right: 12, top: 'middle', text: hc.text, itemHeight: 160,
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

  const API = { DEFAULT_FUELS, priceHeat, importHeat, daily, dayHours, heatData, barYearOption, barDayOption, sunburstOption, heatOption }
  if (typeof module !== 'undefined' && module.exports) module.exports = API

  // ---- DOM-wiring (endast webbläsare) ---------------------------------------
  if (typeof document === 'undefined') return
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
    if (drillDay != null) {
      barChart.setOption(barDayOption(d, zone, year, drillDay, FUELS), true)
      const days = dayList(), i = days.indexOf(drillDay)
      document.getElementById('bar-prev').disabled = i <= 0
      document.getElementById('bar-next').disabled = i >= days.length - 1
      document.getElementById('bar-daylabel').textContent = `dag ${drillDay} (← → byter dag)`
    } else {
      barChart.setOption(barYearOption(d, zone, year, FUELS), true)
    }
    document.getElementById('bar-toolbar').style.display = drillDay != null ? 'block' : 'none'
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

  renderAll()
})()
