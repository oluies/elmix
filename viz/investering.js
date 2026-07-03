// Investeringssignal: LCOE (kostnad för ny produktion) mot UPPNÅTT pris (capture-
// pris = intäktsviktat spotpris) per kraftslag och elområde. Efter Fortums
// investerarslide (jun 2026) men på riktig SE1–SE4-data. Capture < marknadssnitt =
// kannibalisering; capture < LCOE = ny investering betalar sig inte vid dagens pris.
(function () {
  'use strict'
  const E = window.elmixConsumption
  if (!E) return
  // LCOE €/MWh – extern benchmark (Fortum, jun 2026, "New investments require higher
  // prices", genomsnitt av flera källor). Vattenkraft = befintlig, ingen ny-LCOE.
  const TECHS = [
    { key: 'v',  name: 'Vind',        nameEn: 'Wind',    c: '#4dc4d4', lcoe: 51,  lcoeTxt: '51' },
    { key: 's',  name: 'Sol',         nameEn: 'Solar',   c: '#fac858', lcoe: 56,  lcoeTxt: '56' },
    { key: 'va', name: 'Vattenkraft', nameEn: 'Hydro',   c: '#2e6fd6', lcoe: 50,  lcoeTxt: '~50' },
    { key: 'k',  name: 'Kärnkraft',   nameEn: 'Nuclear', c: '#4caf50', lcoe: 175, lcoeTxt: '150–200' }
  ]
  const BAR = '#1a6b54', DIAMOND = '#c94fb0', AVGLINE = '#2aa876'
  const LANGS = ['sv', 'en']
  const TXT = {
    title: ['Betalar ny produktion sig? LCOE vs uppnått pris', 'Does new build pay? LCOE vs achieved price'],
    lead: ['Staplarna är LCOE – kostnaden för att bygga ny produktion (extern benchmark, Fortum jun 2026). Diamanterna är det UPPNÅDDA priset (capture-pris) – det intäktsviktade spotpriset varje kraftslag faktiskt fångar, räknat på riktig timdata för valt elområde och år. Den streckade linjen är marknadens snittpris (baslast). Ligger diamanten under linjen kannibaliserar kraftslaget sitt eget pris; ligger den under stapeln betalar sig inte ny produktion vid dagens prisnivå.',
      'Bars are LCOE – the cost of building new generation (external benchmark, Fortum Jun 2026). Diamonds are the ACHIEVED price (capture price) – the revenue-weighted spot price each source actually captures, computed on real hourly data for the selected bidding zone and year. The dashed line is the market average (baseload) price. A diamond below the line means the source cannibalises its own price; below the bar means new build does not pay at current prices.'],
    lcoe: ['LCOE – ny produktion (€/MWh)', 'LCOE – new build (€/MWh)'],
    cap: ['Uppnått pris / capture (€/MWh)', 'Achieved / capture price (€/MWh)'],
    avg: ['Snittpris (baslast)', 'Average price (baseload)'],
    rate: ['capture-rate', 'capture rate'],
    yr: ['År', 'Year'], zn: ['Elområde', 'Bidding zone'], showing: ['Visar', 'Showing'],
    none: ['ingen produktion i zonen', 'no generation in the zone']
  }
  let lang = 'sv'
  let zone = E.zones.includes('SE_3') ? 'SE_3' : E.zones[0]
  let year = E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const $ = id => document.getElementById(id)
  const isNarrow = () => window.innerWidth < 620
  const zl = z => z.replace('_', '')
  const tname = t => (lang === 'en' && t.nameEn) ? t.nameEn : t.name

  // Capture-pris per kraftslag + tidsviktat snittpris (mixad 60/15-min: vikt dt).
  function agg(z, y) {
    const d = E.data.find(x => x.z === z && x.y === y)
    if (!d) return null
    const per = {}
    for (let i = 0; i < d.h.length; i++) { const k = d.doy[i] * 100 + d.h[i]; per[k] = (per[k] || 0) + 1 }
    let pw = 0, wd = 0
    const rev = {}, vol = {}; TECHS.forEach(t => { rev[t.key] = 0; vol[t.key] = 0 })
    for (let i = 0; i < d.h.length; i++) {
      if (d.p[i] == null) continue
      const dt = 1 / per[d.doy[i] * 100 + d.h[i]]
      pw += d.p[i] * dt; wd += dt
      TECHS.forEach(t => { const g = d[t.key] ? (d[t.key][i] || 0) : 0; rev[t.key] += g * d.p[i]; vol[t.key] += g })
    }
    const avg = wd ? pw / wd : 0
    const out = { avg, tech: {} }
    TECHS.forEach(t => {
      const cp = vol[t.key] ? rev[t.key] / vol[t.key] : null
      out.tech[t.key] = { cap: cp, rate: cp != null && avg ? cp / avg : null, vol: vol[t.key] }
    })
    return out
  }

  const chart = echarts.init($('iv'))
  window.addEventListener('resize', () => chart.resize())

  function option(a) {
    const nar = isNarrow()
    const cats = TECHS.map(tname)
    const bars = TECHS.map(t => t.lcoe)
    // Diamant bara där kraftslaget faktiskt producerar (>0).
    const caps = TECHS.map((t, i) => a.tech[t.key].cap != null ? [i, Math.round(a.tech[t.key].cap)] : null).filter(Boolean)
    return {
      title: {
        text: `${TXT.title[li()]} · ${zl(zone)} ${year}`,
        subtext: `${TXT.avg[li()]}: ${Math.round(a.avg)} €/MWh`,
        left: 'center', top: 6, textStyle: { fontSize: nar ? 13 : 15 }, subtextStyle: { fontSize: 12 }
      },
      legend: { bottom: 4, left: 'center', data: [TXT.lcoe[li()], TXT.cap[li()]], itemGap: 16, textStyle: { fontSize: 11 } },
      grid: { top: nar ? 60 : 66, bottom: nar ? 56 : 48, left: 56, right: 20 },
      tooltip: {
        trigger: 'axis', formatter: p => {
          const i = p[0].dataIndex, t = TECHS[i], td = a.tech[t.key]
          const lc = t.lcoe != null ? t.lcoeTxt + ' €/MWh' : '–'
          if (td.cap == null) return `${tname(t)}<br/>${TXT.none[li()]}<br/>LCOE: ${lc}`
          return `${tname(t)}<br/>${TXT.cap[li()]}: <b>${Math.round(td.cap)}</b> €/MWh` +
            `<br/>${TXT.rate[li()]}: ${(td.rate * 100).toFixed(0)}%<br/>LCOE: ${lc}`
        }
      },
      xAxis: { type: 'category', data: cats },
      yAxis: { type: 'value', name: '€/MWh' },
      series: [
        {
          name: TXT.lcoe[li()], type: 'bar', barWidth: '46%', itemStyle: { color: BAR },
          data: bars, z: 1,
          label: { show: true, position: 'top', formatter: p => TECHS[p.dataIndex].lcoe != null ? TECHS[p.dataIndex].lcoeTxt : '', color: BAR, fontWeight: 600 },
          markLine: {
            symbol: 'none', silent: true, lineStyle: { color: AVGLINE, type: 'dashed', width: 2 },
            label: { formatter: `${TXT.avg[li()]} ${Math.round(a.avg)}`, position: 'insideStartTop', color: AVGLINE, fontSize: 11 },
            data: [{ yAxis: Math.round(a.avg) }]
          }
        },
        {
          name: TXT.cap[li()], type: 'scatter', symbol: 'diamond', symbolSize: nar ? 18 : 24,
          itemStyle: { color: DIAMOND, borderColor: '#fff', borderWidth: 1.5 }, z: 5, data: caps,
          label: { show: true, position: 'bottom', formatter: p => p.value[1], color: '#8a2d78', fontSize: 11, fontWeight: 600 }
        }
      ]
    }
  }

  function renderTable(a) {
    const head = '<tr><th>' + (lang === 'en' ? 'Source' : 'Kraftslag') + '</th><th>LCOE</th><th>' +
      TXT.cap[li()] + '</th><th>' + TXT.rate[li()] + '</th></tr>'
    const rows = TECHS.map(t => { const td = a.tech[t.key]
      return '<tr><th>' + tname(t) + '</th><td>' + t.lcoeTxt + '</td><td>' +
        (td.cap != null ? Math.round(td.cap) : '–') + '</td><td>' +
        (td.rate != null ? (td.rate * 100).toFixed(0) + '%' : '–') + '</td></tr>' }).join('')
    $('chart-data').innerHTML = '<table><caption>' + TXT.title[li()] + ' · ' + zl(zone) + ' ' + year +
      ' · ' + TXT.avg[li()] + ' ' + Math.round(a.avg) + ' €/MWh</caption>' +
      '<thead>' + head + '</thead><tbody>' + rows + '</tbody></table>'
    $('iv').setAttribute('aria-label', TXT.title[li()] + ' · ' + zl(zone) + ' ' + year)
    $('chart-status').textContent = TXT.showing[li()] + ' ' + zl(zone) + ' ' + year
  }

  function renderAll() {
    const a = agg(zone, year)
    $('iv-title').textContent = TXT.title[li()]
    $('iv-lead').textContent = TXT.lead[li()]
    document.querySelector('#zone-picker legend').textContent = TXT.zn[li()]
    document.querySelector('#year-picker legend').textContent = TXT.yr[li()]
    if (!a) return
    chart.setOption(option(a), true); chart.resize()
    renderTable(a)
  }

  function radios(host, items, current, on) {
    const el = $(host); el.querySelectorAll('label').forEach(l => l.remove())
    items.forEach(it => {
      const lab = document.createElement('label'); lab.style.marginRight = '12px'
      const inp = document.createElement('input'); inp.type = 'radio'; inp.name = host; inp.checked = it.val === current
      inp.onchange = () => on(it.val)
      lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + it.txt)); el.appendChild(lab)
    })
  }
  radios('zone-picker', E.zones.map(z => ({ val: z, txt: zl(z) })), zone, z => { zone = z; renderAll() })
  radios('year-picker', E.years.map(y => ({ val: y, txt: String(y) })), year, y => { year = y; renderAll() })

  const sw = $('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase()
    b.setAttribute('aria-pressed', String(l === lang))
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) { const on = c.textContent === l.toUpperCase(); c.classList.toggle('active', on); c.setAttribute('aria-pressed', String(on)) }
      renderAll()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', lang)
  for (const c of sw.children) c.classList.toggle('active', c.textContent === lang.toUpperCase())
  renderAll()
})()
