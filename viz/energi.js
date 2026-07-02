// Volym & kostnad per elområde. Räknar aggregat KLIENTSIDAN ur elmixConsumption
// (SE1–SE4) + elmixEu (DE/FR): energivolym per kraftslag + nettoimport (TWh) och
// aggregerad spotkostnad = Σ(last × spotpris) (grossist-spot, ej full välfärd).
(function () {
  'use strict'
  const E = window.elmixConsumption
  const EU = window.elmixEu
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
    costDesc: ['Diagram: aggregerad spotkostnad (mdr SEK) och snittpris (€/MWh) per elområde', 'Chart: aggregate spot cost (bn SEK) and average price (€/MWh) per bidding zone']
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
