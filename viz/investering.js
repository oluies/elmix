// Investeringssignal: LCOE (kostnad för ny produktion) mot UPPNÅTT pris (capture-
// pris = intäktsviktat spotpris) per teknik och elområde. Efter Fortums investerar-
// slide (jun 2026) men på riktig SE1–SE4-data. Vind delas i land/hav och kärnkraft i
// kostnadsvarianter (Kina/SMR/konv.) – capture är per RESURS (all vind = SE-vindprofil,
// alla kärnkraftsvarianter = SE-kärnkraftsprofil). capture < snittpris = kannibalisering;
// capture < LCOE = ny investering betalar sig inte vid dagens pris.
(function () {
  'use strict'
  const E = window.elmixConsumption
  if (!E) return
  // Benchmark-tekniker. capKey = vilken SE-produktion capture-priset räknas ur.
  // lcoe €/MWh: Fortum jun 2026 (land/havsvind, sol, konv. kärnkraft); IRENA 2023
  // (vatten); Kina Hualong ~$2500/kWe; RR SMR-mål <£70/MWh; koreansk SMR est. (FOAK-
  // spann). Stjärnmärkta = utvecklarmål/estimat, faktiskt utfall kan bli högre.
  const ITEMS = [
    { name: 'Landvind',         nameEn: 'Onshore wind',    capKey: 'v', lcoe: 51,  lcoeTxt: '51' },
    { name: 'Havsvind',         nameEn: 'Offshore wind',   capKey: 'v', lcoe: 126, lcoeTxt: '126' },
    { name: 'Sol',              nameEn: 'Solar',           capKey: 's', lcoe: 56,  lcoeTxt: '56' },
    { name: 'Vattenkraft',      nameEn: 'Hydro',           capKey: 'va', lcoe: 50, lcoeTxt: '~50' },
    { name: 'Kärnkraft Kina',   nameEn: 'Nuclear China',   capKey: 'k', lcoe: 60,  lcoeTxt: '~60' },
    { name: 'Svensk kärnkr. 4%', nameEn: 'Swedish nucl. 4%', capKey: 'k', lcoe: 72, lcoeTxt: '~72' },
    { name: 'Rolls-Royce SMR',  nameEn: 'Rolls-Royce SMR', capKey: 'k', lcoe: 75,  lcoeTxt: '~75*' },
    { name: 'Koreansk SMR',     nameEn: 'Korean SMR',      capKey: 'k', lcoe: 85,  lcoeTxt: '~85*' },
    { name: 'Kärnkraft konv.',  nameEn: 'Nuclear conv.',   capKey: 'k', lcoe: 175, lcoeTxt: '150–200' }
  ]
  const CAPKEYS = ['v', 's', 'va', 'k']
  const BAR = '#1a6b54', DIAMOND = '#c94fb0', AVGLINE = '#2aa876'
  const LANGS = ['sv', 'en']
  const TXT = {
    title: ['Betalar ny produktion sig? LCOE vs uppnått pris', 'Does new build pay? LCOE vs achieved price'],
    lead: ['Staplarna är LCOE – kostnaden för att bygga ny produktion (extern benchmark). Vinden delas i land/hav och kärnkraften i kostnadsvarianter (kinesisk, svensk med 4 % statsränta, Rolls-Royce SMR, koreansk SMR, västlig konventionell merchant) – de skiljer sig kraftigt, mest pga kalkylränta och byggrisk. Diamanterna är det UPPNÅDDA priset (capture-pris) räknat på riktig timdata för valt elområde och år; capture är per resurs, så alla kärnkraftsvarianter delar SE-kärnkraftens capture och all vind SE-vindprofilen (≈landvind – Sverige har knappt havsvind). Streckad linje = marknadens snittpris (baslast). Diamant under linjen = kraftslaget kannibaliserar sitt pris; under stapeln = ny produktion betalar sig inte. SMR-siffror (*) är utvecklarmål/estimat.',
      'Bars are LCOE – the cost of building new generation (external benchmark). Wind is split into onshore/offshore and nuclear into cost variants (Chinese, Swedish at 4% state rate, Rolls-Royce SMR, Korean SMR, Western conventional merchant) – they differ sharply, mostly due to discount rate and construction risk. Diamonds are the ACHIEVED (capture) price on real hourly data for the selected zone and year; capture is per resource, so all nuclear variants share the SE nuclear capture and all wind the SE wind profile (≈onshore – Sweden has almost no offshore). Dashed line = market average (baseload) price. Diamond below the line = the source cannibalises its price; below the bar = new build does not pay. SMR figures (*) are developer targets/estimates.'],
    lcoe: ['LCOE – ny produktion (€/MWh)', 'LCOE – new build (€/MWh)'],
    cap: ['Uppnått pris / capture (€/MWh)', 'Achieved / capture price (€/MWh)'],
    avg: ['Snittpris (baslast)', 'Average price (baseload)'],
    rate: ['capture-rate', 'capture rate'],
    yr: ['År', 'Year'], zn: ['Elområde', 'Bidding zone'], showing: ['Visar', 'Showing'],
    none: ['ingen produktion i zonen', 'no generation in the zone'],
    proxy: ['baslast-proxy: ingen kärnkraft i zonen', 'baseload proxy: no nuclear in this zone']
  }
  let lang = 'sv'
  let zone = E.zones.includes('SE_3') ? 'SE_3' : E.zones[0]
  let year = E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const $ = id => document.getElementById(id)
  const isNarrow = () => window.innerWidth < 620
  const zl = z => z.replace('_', '')
  const iname = it => (lang === 'en' && it.nameEn) ? it.nameEn : it.name

  // Capture-pris per SE-resurs (v/s/va/k) + tidsviktat snittpris (mixad 60/15-min).
  function agg(z, y) {
    const d = E.data.find(x => x.z === z && x.y === y)
    if (!d) return null
    const per = {}
    for (let i = 0; i < d.h.length; i++) { const k = d.doy[i] * 100 + d.h[i]; per[k] = (per[k] || 0) + 1 }
    let pw = 0, wd = 0
    const rev = {}, vol = {}; CAPKEYS.forEach(k => { rev[k] = 0; vol[k] = 0 })
    for (let i = 0; i < d.h.length; i++) {
      if (d.p[i] == null) continue
      const dt = 1 / per[d.doy[i] * 100 + d.h[i]]
      pw += d.p[i] * dt; wd += dt
      CAPKEYS.forEach(k => { const g = d[k] ? (d[k][i] || 0) : 0; rev[k] += g * d.p[i]; vol[k] += g })
    }
    const avg = wd ? pw / wd : 0
    const cap = {}; CAPKEYS.forEach(k => { cap[k] = vol[k] ? rev[k] / vol[k] : null })
    return { avg, cap }
  }
  // Kärnkraft är baslast -> capture ≈ tidsviktat snittpris. I zoner utan kärnkraft
  // (SE1/2/4) finns ingen egen produktion att mäta på; använd snittpriset som proxy
  // så alla zoner får diamanter. Övriga resurser (vind/sol/vatten) finns i alla zoner.
  const isProxy = (a, it) => a.cap[it.capKey] == null && it.capKey === 'k'
  const capOf = (a, it) => { const c = a.cap[it.capKey]; return c != null ? c : (it.capKey === 'k' ? a.avg : null) }

  const chart = echarts.init($('iv'))
  window.addEventListener('resize', () => chart.resize())

  function option(a) {
    const nar = isNarrow()
    return {
      title: {
        text: `${TXT.title[li()]} · ${zl(zone)} ${year}`,
        subtext: `${TXT.avg[li()]}: ${Math.round(a.avg)} €/MWh`,
        left: 'center', top: 6, textStyle: { fontSize: nar ? 12 : 15 }, subtextStyle: { fontSize: 12 }
      },
      legend: { bottom: 4, left: 'center', data: [TXT.lcoe[li()], TXT.cap[li()]], itemGap: 16, textStyle: { fontSize: 11 } },
      grid: { top: nar ? 58 : 64, bottom: nar ? 108 : 84, left: 52, right: 16 },
      tooltip: {
        trigger: 'axis', formatter: p => {
          const it = ITEMS[p[0].dataIndex], cp = capOf(a, it)
          const lc = it.lcoeTxt + ' €/MWh'
          if (cp == null) return `${iname(it)}<br/>${TXT.none[li()]}<br/>LCOE: ${lc}`
          const px = isProxy(a, it) ? `<br/><i>${TXT.proxy[li()]}</i>` : ''
          return `${iname(it)}<br/>${TXT.cap[li()]}: <b>${Math.round(cp)}</b> €/MWh` +
            `<br/>${TXT.rate[li()]}: ${(cp / a.avg * 100).toFixed(0)}%<br/>LCOE: ${lc}${px}`
        }
      },
      xAxis: {
        type: 'category', data: ITEMS.map(iname),
        axisLabel: { interval: 0, rotate: nar ? 42 : 28, fontSize: nar ? 9 : 11 }
      },
      yAxis: { type: 'value', name: '€/MWh' },
      series: [
        {
          name: TXT.lcoe[li()], type: 'bar', barWidth: '52%', itemStyle: { color: BAR }, data: ITEMS.map(it => it.lcoe), z: 1,
          label: { show: true, position: 'top', formatter: p => ITEMS[p.dataIndex].lcoeTxt, color: BAR, fontWeight: 600, fontSize: nar ? 9 : 11 },
          markLine: {
            symbol: 'none', silent: true, lineStyle: { color: AVGLINE, type: 'dashed', width: 2 },
            label: { formatter: `${TXT.avg[li()]} ${Math.round(a.avg)}`, position: 'insideStartTop', color: AVGLINE, fontSize: 11 },
            data: [{ yAxis: Math.round(a.avg) }]
          }
        },
        {
          name: TXT.cap[li()], type: 'scatter', symbol: 'diamond', symbolSize: nar ? 15 : 22,
          itemStyle: { color: DIAMOND, borderColor: '#fff', borderWidth: 1.5 }, z: 5,
          data: ITEMS.map((it, i) => { const cp = capOf(a, it); return cp != null ? [i, Math.round(cp)] : null }).filter(Boolean),
          label: { show: true, position: 'bottom', formatter: p => p.value[1], color: '#8a2d78', fontSize: nar ? 9 : 11, fontWeight: 600 }
        }
      ]
    }
  }

  function renderTable(a) {
    const head = '<tr><th>' + (lang === 'en' ? 'Technology' : 'Teknik') + '</th><th>LCOE</th><th>' +
      TXT.cap[li()] + '</th><th>' + TXT.rate[li()] + '</th></tr>'
    const rows = ITEMS.map(it => { const cp = capOf(a, it), px = isProxy(a, it) ? ' †' : ''
      return '<tr><th>' + iname(it) + '</th><td>' + it.lcoeTxt + '</td><td>' +
        (cp != null ? Math.round(cp) + px : '–') + '</td><td>' +
        (cp != null ? (cp / a.avg * 100).toFixed(0) + '%' : '–') + '</td></tr>' }).join('')
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
