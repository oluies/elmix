// Runda experimentvyer (round.html). Diagram-byggarna är rena funktioner
// (ingen DOM/echarts) så de kan SSR-röktestas i node; DOM-wiringen längst ner
// körs bara i webbläsaren.
(function () {
  'use strict'

  const FUELS = [
    { key: 'v',  name: 'Vind',          c: '#5470c6' },
    { key: 's',  name: 'Sol',           c: '#fac858' },
    { key: 'va', name: 'Vattenkraft',   c: '#91cc75' },
    { key: 'k',  name: 'Kärnkraft',     c: '#ee6666' },
    { key: 'kv', name: 'Kraftvärme/övr', c: '#73c0de' }
  ]
  const MNAME = ['Jan', 'Feb', 'Mar', 'Apr', 'Maj', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dec']
  const MDAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  const MSTART = (() => { const s = []; let a = 1; for (const m of MDAYS) { s.push(a); a += m } return s })()
  const monthOf = day => { for (let m = 11; m >= 0; m--) if (day >= MSTART[m]) return m; return 0 }

  // Delad layout: legend vertikalt till höger (utnyttjar tomrummet bredvid den
  // höjdbegränsade cirkeln), polär cirkel stor och nedflyttad under titeln.
  const legendRight = data => ({ type: 'scroll', orient: 'vertical', right: 10, top: 'middle', itemGap: 10, data })
  const POLAR = { center: ['44%', '56%'], radius: ['17%', '86%'] }
  const titleTop = text => ({ text, left: '44%', top: 8, textAlign: 'center', textStyle: { fontSize: 14 } })
  const monthLabel = { interval: 0, fontSize: 11, color: '#555', formatter: v => { const mi = MSTART.indexOf(+v); return mi >= 0 ? MNAME[mi] : '' } }

  // ---- datahjälpare (rena) --------------------------------------------------
  // Dagsaggregat: summera kraftslag per dag, snittpris per dag.
  function daily(d) {
    const by = new Map()
    for (let i = 0; i < d.h.length; i++) {
      const day = d.doy[i]
      let o = by.get(day)
      if (!o) { o = { day, v: 0, s: 0, va: 0, k: 0, kv: 0, ps: 0, pn: 0 }; by.set(day, o) }
      o.v += d.v[i]; o.s += d.s[i]; o.va += d.va[i]; o.k += d.k[i]; o.kv += d.kv[i]
      if (d.p[i] != null) { o.ps += d.p[i]; o.pn++ }
    }
    return [...by.values()].sort((a, b) => a.day - b.day)
      .map(o => ({ day: o.day, v: o.v, s: o.s, va: o.va, k: o.k, kv: o.kv, p: o.pn ? o.ps / o.pn : null }))
  }

  // Ett dygns timmar (sorterade).
  function dayHours(d, day) {
    const out = []
    for (let i = 0; i < d.h.length; i++) if (d.doy[i] === day)
      out.push({ h: d.h[i], v: d.v[i], s: d.s[i], va: d.va[i], k: d.k[i], kv: d.kv[i], p: d.p[i] })
    return out.sort((a, b) => a.h - b.h)
  }

  // Heatmap-celler [dagIndex, timme, pris, doy] + listan över doy:er.
  function heatData(d) {
    const doys = [...new Set(d.doy)].sort((a, b) => a - b)
    const idx = new Map(doys.map((dy, i) => [dy, i]))
    const out = []
    for (let i = 0; i < d.h.length; i++)
      if (d.p[i] != null) out.push([idx.get(d.doy[i]), d.h[i], d.p[i], d.doy[i]])
    return { out, doys }
  }

  const shareSeries = (rows) => FUELS.map(f => ({
    name: f.name, type: 'bar', coordinateSystem: 'polar', stack: 'mix', itemStyle: { color: f.c },
    data: rows.map(x => { const t = x.v + x.s + x.va + x.k + x.kv; return t ? +(x[f.key] / t * 100).toFixed(1) : 0 })
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
  function barYearOption(d, zone, year) {
    const rows = daily(d)
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – mix-andel över året`),
      legend: legendRight(FUELS.map(f => f.name).concat('Pris')),
      tooltip: { trigger: 'item' },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: rows.map(x => String(x.day)), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, z: 5, axisLabel: monthLabel
      },
      radiusAxis: { min: 0, max: 100, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [...shareSeries(rows), priceLine(rows)]
    }
  }
  function barDayOption(d, zone, year, day) {
    const rows = dayHours(d, day)
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – dygnsmix, dag ${day} (24 h)`),
      legend: legendRight(FUELS.map(f => f.name).concat('Pris')),
      tooltip: { trigger: 'item' },
      polar: POLAR,
      angleAxis: {
        type: 'category', data: rows.map(x => x.h + ':00'), startAngle: 90, clockwise: true,
        axisTick: { show: false }, splitLine: { show: false }, axisLabel: { interval: 1, fontSize: 11, color: '#555' }
      },
      radiusAxis: { min: 0, max: 100, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [...shareSeries(rows), priceLine(rows)]
    }
  }

  // ---- 2. Sunburst (månad → dag → kraftslag) --------------------------------
  function sunburstOption(d, zone, year) {
    const rows = daily(d)
    const months = new Map()
    for (const x of rows) { const m = monthOf(x.day); if (!months.has(m)) months.set(m, []); months.get(m).push(x) }
    const data = [...months.keys()].sort((a, b) => a - b).map(m => ({
      name: MNAME[m], itemStyle: { color: '#e9edf3' },
      children: months.get(m).map(x => ({
        name: 'Dag ' + x.day,
        children: FUELS.map(f => ({ name: f.name, value: Math.max(0, x[f.key]), itemStyle: { color: f.c } }))
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

  // ---- 3. Radiell heatmap (dag × timme, färg = pris) ------------------------
  function heatOption(d, zone, year) {
    const { out, doys } = heatData(d)
    const prices = out.map(o => o[2])
    const pmin = Math.min(...prices), pmax = Math.max(...prices)
    const N = doys.length
    return {
      title: titleTop(`${zone.replace('_', '')} ${year} – pris per dag × timme`),
      tooltip: { trigger: 'item', formatter: p => `dag ${p.value[3]}, kl ${String(p.value[1]).padStart(2, '0')}<br/>${p.value[2]} €/MWh` },
      visualMap: {
        type: 'continuous', min: pmin, max: pmax, dimension: 2, calculable: true,
        orient: 'vertical', right: 12, top: 'middle', text: ['dyrt', 'billigt'], itemHeight: 160,
        inRange: { color: ['#2c7fb8', '#7fcdbb', '#ffffcc', '#fd8d3c', '#c0392b'] }
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

  const API = { FUELS, daily, dayHours, heatData, barYearOption, barDayOption, sunburstOption, heatOption }
  if (typeof module !== 'undefined' && module.exports) module.exports = API

  // ---- DOM-wiring (endast webbläsare) ---------------------------------------
  if (typeof document === 'undefined') return
  const R = window.elmixRound
  let zone = 'SE_3', year = 2025, drillDay = null
  const rec = () => R.data.find(d => d.z === zone && d.y === year)

  const barChart = echarts.init(document.getElementById('pane-bar'))
  const sunChart = echarts.init(document.getElementById('pane-sun'))
  const heatChart = echarts.init(document.getElementById('pane-heat'))
  window.addEventListener('resize', () => { barChart.resize(); sunChart.resize(); heatChart.resize() })

  function renderBar() {
    const d = rec()
    barChart.setOption(drillDay ? barDayOption(d, zone, year, drillDay) : barYearOption(d, zone, year), true)
    document.getElementById('bar-toolbar').style.display = drillDay ? 'block' : 'none'
  }
  function renderAll() { renderBar(); sunChart.setOption(sunburstOption(rec(), zone, year), true); heatChart.setOption(heatOption(rec(), zone, year), true) }

  // Klick i års-klockan -> drilla det dygnet.
  barChart.on('click', p => {
    if (drillDay || !(p.seriesType === 'bar' || p.seriesType === 'line')) return
    const rows = daily(rec()); if (rows[p.dataIndex]) { drillDay = rows[p.dataIndex].day; renderBar() }
  })
  // Klick i heatmappen -> drilla samma dygn i panel 1.
  heatChart.on('click', p => {
    if (!p.value) return
    drillDay = p.value[3]; renderBar()
    document.getElementById('pane-bar').scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
  document.getElementById('bar-back').onclick = () => { drillDay = null; renderBar() }

  // Väljare.
  function radios(host, items, current, on) {
    const el = document.getElementById(host)
    items.forEach(it => {
      const id = host + '-' + it.val
      const lab = document.createElement('label'); lab.style.marginRight = '12px'
      const inp = document.createElement('input')
      inp.type = 'radio'; inp.name = host; inp.checked = it.val === current
      inp.onchange = () => { on(it.val); }
      lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + it.txt))
      el.appendChild(lab)
    })
  }
  radios('zone-picker', R.zones.map(z => ({ val: z, txt: z.replace('_', '') })), zone, z => { zone = z; drillDay = null; renderAll() })
  radios('year-picker', R.years.map(y => ({ val: y, txt: String(y) })), year, y => { year = +y; drillDay = null; renderAll() })

  renderAll()
})()
