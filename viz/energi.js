// Volym & kostnad per elområde. Räknar aggregat KLIENTSIDAN ur elmixConsumption
// (SE1–SE4) + elmixEu (DE/FR): energivolym per kraftslag + nettoimport (TWh) och
// aggregerad spotkostnad = Σ(last × spotpris) (grossist-spot, ej full välfärd).
(function () {
  'use strict'
  const E = window.elmixConsumption
  const EU = window.elmixEu
  // Tioårsvyn (årsvolym per kraftslag, hela historiken). Eget dataset, egen
  // sektion; saknas den renderas resten av sidan som förut.
  const AN = window.elmixAnnual
  if (!E) return
  // SE saknar kol/gas (0); EU levererar termiskt som kol+gas+ov -> ov mappas till kv.
  const FUELS = [
    { key: 'v',   name: 'Vind',           nameEn: 'Wind',       c: '#4dc4d4' },
    { key: 's',   name: 'Sol',            nameEn: 'Solar',      c: '#fac858' },
    { key: 'va',  name: 'Vattenkraft',    nameEn: 'Hydro',      c: '#2e6fd6' },
    { key: 'k',   name: 'Kärnkraft',      nameEn: 'Nuclear',    c: '#4caf50' },
    { key: 'kol', name: 'Kol',            nameEn: 'Coal',       c: '#333' },
    { key: 'gas', name: 'Gas',            nameEn: 'Gas',        c: '#c98a52' },
    { key: 'kv',  name: 'Kraftvärme/övr', nameEn: 'CHP/other',  c: '#9c6b3f' },
    { key: 'imp', name: 'Import (netto)', nameEn: 'Net import', c: '#9aa7b8' }
  ]
  const EUR_SEK = 11.30
  const LANGS = ['sv', 'en']
  const TXT = {
    title: ['Energivolym & spotkostnad per elområde', 'Energy volume & spot cost per bidding zone'],
    lead: ['Absoluta volymer (TWh) – produktion per kraftslag + nettoimport – och den aggregerade grossist-spotkostnaden = Σ(förbrukning × spotpris) per elområde (SE1–SE4 samt DE och FR). Kostnaden är vad elen kostar på spotmarknaden (exkl. nät, skatt, elhandlarpåslag och bredare samhällsekonomiska effekter). Antaget 1 EUR = ' + EUR_SEK + ' SEK.',
      'Absolute volumes (TWh) – generation per source + net imports – and the aggregate wholesale spot cost = Σ(consumption × spot price) per zone (SE1–SE4 plus DE and FR). The cost is what electricity costs on the spot market (excl. grid, tax, retail margin and broader macroeconomic effects). Assumed 1 EUR = ' + EUR_SEK + ' SEK.'],
    vol: ['Energivolym (TWh)', 'Energy volume (TWh)'],
    cost: ['Aggregerad spotkostnad', 'Aggregate spot cost'],
    costBar: ['Spotkostnad (mdr SEK)', 'Spot cost (bn SEK)'],
    price: ['Snittpris (€/MWh)', 'Avg price (€/MWh)'],
    yr: ['År (FBMC)', 'Year (FBMC)'],
    showing: ['Visar år', 'Showing year'],
    volDesc: ['Stapeldiagram: energivolym i TWh per kraftslag och elområde', 'Bar chart: energy volume in TWh per source and bidding zone'],
    costDesc: ['Diagram: aggregerad spotkostnad (mdr SEK) och snittpris (€/MWh) per elområde', 'Chart: aggregate spot cost (bn SEK) and average price (€/MWh) per bidding zone'],
    tyTitle: ['Årsvolym per kraftslag – hela historiken', 'Annual volume per source – full history'],
    tyLead: ['Samma kraftslag, men över alla år ENTSO-E-historiken räcker till. Staplarna är INHEMSK PRODUKTION i TWh – nettoimport ingår inte, till skillnad från FBMC-vyn ovan. De fyra elområdesdiagrammen delar y-skala så att de är jämförbara med varandra; rikesdiagrammet har sin egen.',
      'The same sources, but across every year the ENTSO-E history covers. The bars are DOMESTIC GENERATION in TWh – net imports are not included, unlike the FBMC view above. The four bidding-zone charts share a y-scale so they are comparable with each other; the national chart has its own.'],
    tyWhole: ['Sverige totalt', 'Sweden total'],
    tyFoot: ['Markerade år (*) är ofullständiga – hämtningen går fram till idag, så den sista stapeln är kortare av kalenderskäl, inte av produktionsskäl.',
      'Years marked (*) are incomplete – the fetch runs up to today, so the last bar is shorter for calendar reasons, not production reasons.'],
    tyDesc: ['Stapeldiagram: årsvolym i TWh per kraftslag', 'Bar chart: annual volume in TWh per source']
  }
  let lang = 'sv', year = E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const zones = E.zones.concat(EU ? EU.zones : [])
  const zl = z => z === 'DE_LU' ? 'DE' : z.replace('_', '')
  const fname = f => (lang === 'en' && f.nameEn) ? f.nameEn : f.name
  const $ = id => document.getElementById(id)

  // Hittar zonposten i endera datasetet. eu=true för DE/FR (elmixEu).
  function rec(z, y) {
    const d = E.data.find(x => x.z === z && x.y === y)
    if (d) return { d, eu: false }
    if (EU) { const e = EU.data.find(x => x.z === z && x.y === y); if (e) return { d: e, eu: true } }
    return { d: null, eu: false }
  }

  // Aggregat per zon för valt år. EU:s "ov" fyller kv-hinken; kol/gas saknas i SE (=0).
  // ENHETER SKILJER: SE-datat (export-consumption.sh: sum(mwh)) är redan MWh per bucket
  // -> summera rakt. EU-datat (export-consumption-eu.sh: sum(mw)) är MW (effekt) ->
  // energi = MW × dt, där dt = 1/(antal buckets samma timme) (15-min = 0,25 h; MTU okt 2025).
  function agg(z) {
    const { d, eu } = rec(z, year)
    const sum = {}; FUELS.forEach(f => sum[f.key] = 0)
    let cost = 0, pSum = 0, pN = 0
    if (!d) return { sum, costEur: 0, avgP: 0 }
    const arrs = FUELS.map(f => d[f.key] || (f.key === 'kv' ? d.ov : null))
    let per = null
    if (eu) { per = {}; for (let i = 0; i < d.h.length; i++) { const key = d.doy[i] * 100 + d.h[i]; per[key] = (per[key] || 0) + 1 } }
    for (let i = 0; i < d.h.length; i++) {
      const dt = eu ? 1 / per[d.doy[i] * 100 + d.h[i]] : 1
      let load = 0
      arrs.forEach((a, fi) => { const v = a ? (a[i] || 0) : 0; sum[FUELS[fi].key] += v * dt; load += v })
      if (d.p[i] != null) { cost += load * d.p[i] * dt; pSum += d.p[i]; pN++ }
    }
    return { sum, costEur: cost, avgP: pN ? pSum / pN : 0 }
  }
  const A = () => Object.fromEntries(zones.map(z => [z, agg(z)]))

  const volChart = echarts.init($('vol'))
  const costChart = echarts.init($('cost'))
  // Smal skärm: fasta top-positioner krockar när legenden radbryts. Rita om
  // (debouncad) vid resize så layouten nedan får räknas om mot ny bredd.
  let rt
  window.addEventListener('resize', () => {
    volChart.resize(); costChart.resize()
    if (AN) AN.zones.forEach(z => tyCharts[z].resize())
    clearTimeout(rt); rt = setTimeout(renderAll, 160)
  })

  function volOption(a) {
    const nar = volChart.getWidth() < 620
    return {
      title: { text: `${TXT.vol[li()]} · ${year}`, left: 'center', top: 6, textStyle: { fontSize: nar ? 13 : 14 } },
      // Många kraftslag -> lägg legenden längst ned på mobil så den inte krockar med titeln.
      legend: nar ? { bottom: 6, left: 'center', itemGap: 9, textStyle: { fontSize: 11 } } : { top: 28 },
      grid: nar ? { top: 34, bottom: 90, left: 44, right: 12 } : { top: 64, bottom: 30, left: 60, right: 20 },
      tooltip: { trigger: 'axis', valueFormatter: v => (+v).toFixed(1) + ' TWh' },
      xAxis: { type: 'category', data: zones.map(zl) },
      yAxis: { type: 'value', name: 'TWh' },
      series: FUELS.map(f => ({
        name: fname(f), type: 'bar', stack: 'e', itemStyle: { color: f.c },
        data: zones.map(z => +(a[z].sum[f.key] / 1e6).toFixed(2))
      }))
    }
  }
  function costOption(a) {
    const nar = costChart.getWidth() < 620
    return {
      title: { text: `${TXT.cost[li()]} · ${year}`, left: 'center', top: 6, textStyle: { fontSize: nar ? 13 : 14 } },
      grid: nar ? { top: 76, bottom: 28, left: 48, right: 44 } : { top: 48, bottom: 30, left: 66, right: 66 },
      tooltip: {
        trigger: 'axis', formatter: p => {
          const z = p[0].name
          return `${z}<br/>${TXT.costBar[li()]}: ${p[0].value} <br/>${TXT.price[li()]}: ${p[1] ? p[1].value : ''}`
        }
      },
      legend: nar ? { top: 30, itemGap: 12, textStyle: { fontSize: 11 }, data: [TXT.costBar[li()], TXT.price[li()]] }
                  : { top: 22, data: [TXT.costBar[li()], TXT.price[li()]] },
      xAxis: { type: 'category', data: zones.map(zl) },
      yAxis: [
        { type: 'value', name: 'mdr SEK' },
        { type: 'value', name: '€/MWh', splitLine: { show: false } }
      ],
      series: [
        { name: TXT.costBar[li()], type: 'bar', itemStyle: { color: '#c0392b' },
          data: zones.map(z => +(a[z].costEur * EUR_SEK / 1e9).toFixed(1)) },
        { name: TXT.price[li()], type: 'line', yAxisIndex: 1, symbolSize: 7, itemStyle: { color: '#111' },
          data: zones.map(z => Math.round(a[z].avgP)) }
      ]
    }
  }

  // ---- Tioårsvy: årsvolym per kraftslag -------------------------------------
  // Produktion ur fct_gen (v/s/va/k/kv) – ingen import, inget kol/gas i SE.
  const TY_FUELS = FUELS.filter(f => ['v', 's', 'va', 'k', 'kv'].indexOf(f.key) >= 0)
  const tyRec = (z, y) => AN.data.find(x => x.z === z && x.y === y)
  // Ofullständigt år = färre än 360 dygn med data. Gäller hela årgången, så
  // riksposten får avgöra; en enskild zon kan ha en lucka utan att året är delår.
  const tyPartial = y => { const r = tyRec('SE', y); return !!r && r.dagar < 360 }
  const tyYearLabel = y => tyPartial(y) ? y + '*' : String(y)
  const tyTotal = (z, y) => { const r = tyRec(z, y); return r ? TY_FUELS.reduce((s, f) => s + (r[f.key] || 0), 0) : 0 }
  const tyZoneName = z => z === 'SE' ? TXT.tyWhole[li()] : zl(z)
  // Gemensamt y-tak för SE1–SE4 (riket skalas för sig) – annars läses fyra
  // olika skalor som fyra jämförbara zoner.
  function tyZoneMax() {
    let m = 0
    AN.zones.filter(z => z !== 'SE').forEach(z => AN.years.forEach(y => { m = Math.max(m, tyTotal(z, y)) }))
    return m ? Math.ceil(m * 1.05) : null
  }
  function tyOption(chart, z, max) {
    const nar = chart.getWidth() < 520
    return {
      title: { text: `${tyZoneName(z)} · TWh/${lang === 'en' ? 'year' : 'år'}`, left: 'center', top: 6, textStyle: { fontSize: nar ? 12 : 13 } },
      legend: nar ? { bottom: 4, left: 'center', itemGap: 8, textStyle: { fontSize: 10 } } : { top: 26, itemGap: 12, textStyle: { fontSize: 11 } },
      grid: nar ? { top: 30, bottom: 74, left: 44, right: 12 } : { top: 56, bottom: 28, left: 56, right: 18 },
      tooltip: {
        trigger: 'axis', axisPointer: { type: 'shadow' },
        valueFormatter: v => v == null ? '–' : (+v).toFixed(1) + ' TWh'
      },
      xAxis: { type: 'category', data: AN.years.map(tyYearLabel), axisLabel: { fontSize: 10, interval: 0, rotate: AN.years.length > 8 ? 45 : 0 } },
      yAxis: { type: 'value', name: 'TWh', max: z === 'SE' ? null : max },
      series: TY_FUELS.map(f => ({
        // barMaxWidth: med bara ett par år i datat blir staplarna annars
        // löjligt breda (en halv graf per år).
        name: fname(f), type: 'bar', stack: 'e', barMaxWidth: 46, itemStyle: { color: f.c },
        data: AN.years.map(y => { const r = tyRec(z, y); return r ? r[f.key] : null })
      }))
    }
  }
  // En chart per zon: riket i #ty-SE, elområdena i #ty-zones (2×2-grid).
  const tyCharts = {}
  if (AN) AN.zones.forEach(z => {
    let el = $('ty-' + z)
    if (!el) {
      el = document.createElement('div'); el.className = 'chart'; el.id = 'ty-' + z
      el.setAttribute('role', 'img')
      $('ty-zones').appendChild(el)
    }
    tyCharts[z] = echarts.init(el)
  })
  function renderTenYear() {
    if (!AN) return
    $('ty-title').textContent = TXT.tyTitle[li()]
    $('ty-lead').textContent = TXT.tyLead[li()]
    const part = AN.years.filter(tyPartial)
    $('ty-foot').textContent = part.length
      ? TXT.tyFoot[li()] + ' (' + part.map(y => y + ': ' + tyRec('SE', y).dagar + (lang === 'en' ? ' days' : ' dygn')).join(', ') + ')'
      : ''
    const max = tyZoneMax()
    AN.zones.forEach(z => {
      tyCharts[z].setOption(tyOption(tyCharts[z], z, max), true)
      tyCharts[z].resize()
      $('ty-' + z).setAttribute('aria-label', TXT.tyDesc[li()] + ' – ' + tyZoneName(z))
    })
    const head = '<tr><th>' + (lang === 'en' ? 'Zone' : 'Elområde') + '</th><th>' +
      (lang === 'en' ? 'Year' : 'År') + '</th>' +
      TY_FUELS.map(f => '<th>' + fname(f) + ' (TWh)</th>').join('') + '<th>Σ (TWh)</th></tr>'
    const rows = AN.zones.flatMap(z => AN.years.filter(y => tyRec(z, y)).map(y =>
      '<tr><th>' + tyZoneName(z) + '</th><td>' + tyYearLabel(y) + '</td>' +
      TY_FUELS.map(f => '<td>' + (tyRec(z, y)[f.key] || 0).toFixed(1) + '</td>').join('') +
      '<td>' + tyTotal(z, y).toFixed(1) + '</td></tr>')).join('')
    $('ty-data').innerHTML = '<table><caption>' + TXT.tyTitle[li()] + '</caption><thead>' +
      head + '</thead><tbody>' + rows + '</tbody></table>'
  }

  // Skärmläsartillgänglig datatabell + uppdaterade aria-etiketter (canvas är osynlig för AT).
  function renderTable(a) {
    const twh = (z, k) => (a[z].sum[k] / 1e6).toFixed(1)
    const head = '<tr><th>' + (lang === 'en' ? 'Zone' : 'Elområde') + '</th>' +
      FUELS.map(f => '<th>' + fname(f) + ' (TWh)</th>').join('') +
      '<th>' + TXT.costBar[li()] + '</th><th>' + TXT.price[li()] + '</th></tr>'
    const rows = zones.map(z => '<tr><th>' + zl(z) + '</th>' +
      FUELS.map(f => '<td>' + twh(z, f.key) + '</td>').join('') +
      '<td>' + (a[z].costEur * EUR_SEK / 1e9).toFixed(1) + '</td>' +
      '<td>' + Math.round(a[z].avgP) + '</td></tr>').join('')
    $('chart-data').innerHTML =
      '<table><caption>' + TXT.title[li()] + ' · ' + year + '</caption>' +
      '<thead>' + head + '</thead><tbody>' + rows + '</tbody></table>'
    $('vol').setAttribute('aria-label', TXT.volDesc[li()] + ' · ' + year)
    $('cost').setAttribute('aria-label', TXT.costDesc[li()] + ' · ' + year)
    $('chart-status').textContent = TXT.showing[li()] + ' ' + year
  }

  function renderAll() {
    const a = A()
    $('en-title').textContent = TXT.title[li()]
    $('en-lead').textContent = TXT.lead[li()]
    document.querySelector('#year-picker legend').textContent = TXT.yr[li()]
    volChart.setOption(volOption(a), true); volChart.resize()
    costChart.setOption(costOption(a), true); costChart.resize()
    renderTable(a)
    renderTenYear()
  }

  const yp = $('year-picker')
  E.years.forEach(y => {
    const lab = document.createElement('label'); lab.style.marginRight = '12px'
    const inp = document.createElement('input'); inp.type = 'radio'; inp.name = 'enyear'; inp.checked = y === year
    inp.onchange = () => { year = y; renderAll() }
    lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + y)); yp.appendChild(lab)
  })
  const sw = $('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase()
    b.setAttribute('aria-pressed', String(l === lang))
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) {
        const on = c.textContent === l.toUpperCase()
        c.classList.toggle('active', on); c.setAttribute('aria-pressed', String(on))
      }
      renderAll()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', lang)
  for (const c of sw.children) c.classList.toggle('active', c.textContent === lang.toUpperCase())
  renderAll()
})()
