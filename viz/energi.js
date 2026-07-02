// Volym & kostnad per elområde. Räknar aggregat KLIENTSIDAN ur elmixConsumption
// (MWh + spotpris per bucket): energivolym per kraftslag + nettoimport (TWh) och
// aggregerad spotkostnad = Σ(last × spotpris) (grossist-spot, ej full välfärd).
(function () {
  'use strict'
  const E = window.elmixConsumption
  if (!E) return
  const FUELS = [
    { key: 'v',   name: 'Vind',           nameEn: 'Wind',       c: '#4dc4d4' },
    { key: 's',   name: 'Sol',            nameEn: 'Solar',      c: '#fac858' },
    { key: 'va',  name: 'Vattenkraft',    nameEn: 'Hydro',      c: '#2e6fd6' },
    { key: 'k',   name: 'Kärnkraft',      nameEn: 'Nuclear',    c: '#4caf50' },
    { key: 'kv',  name: 'Kraftvärme/övr', nameEn: 'CHP/other',  c: '#9c6b3f' },
    { key: 'imp', name: 'Import (netto)', nameEn: 'Net import', c: '#9aa7b8' }
  ]
  const EUR_SEK = 11.30
  const LANGS = ['sv', 'en']
  const TXT = {
    title: ['Energivolym & spotkostnad per elområde', 'Energy volume & spot cost per bidding zone'],
    lead: ['Absoluta volymer (TWh) – produktion per kraftslag + nettoimport – och den aggregerade grossist-spotkostnaden = Σ(förbrukning × spotpris) per elområde. Kostnaden är vad elen kostar på spotmarknaden (exkl. nät, skatt, elhandlarpåslag och bredare samhällsekonomiska effekter). Antaget 1 EUR = ' + EUR_SEK + ' SEK.',
      'Absolute volumes (TWh) – generation per source + net imports – and the aggregate wholesale spot cost = Σ(consumption × spot price) per zone. The cost is what electricity costs on the spot market (excl. grid, tax, retail margin and broader macroeconomic effects). Assumed 1 EUR = ' + EUR_SEK + ' SEK.'],
    vol: ['Energivolym (TWh)', 'Energy volume (TWh)'],
    cost: ['Aggregerad spotkostnad', 'Aggregate spot cost'],
    costBar: ['Spotkostnad (mdr SEK)', 'Spot cost (bn SEK)'],
    price: ['Snittpris (€/MWh)', 'Avg price (€/MWh)'],
    yr: ['År (FBMC)', 'Year (FBMC)']
  }
  let lang = 'sv', year = E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const zones = E.zones
  const zl = z => z.replace('_', '')
  const fname = f => (lang === 'en' && f.nameEn) ? f.nameEn : f.name
  const $ = id => document.getElementById(id)

  // Aggregat per zon för valt år.
  function agg(z) {
    const d = E.data.find(x => x.z === z && x.y === year)
    const sum = {}; FUELS.forEach(f => sum[f.key] = 0)
    let cost = 0, pSum = 0, pN = 0
    for (let i = 0; i < d.h.length; i++) {
      let load = 0
      FUELS.forEach(f => { const v = d[f.key][i] || 0; sum[f.key] += v; load += v })
      if (d.p[i] != null) { cost += load * d.p[i]; pSum += d.p[i]; pN++ }
    }
    return { sum, costEur: cost, avgP: pN ? pSum / pN : 0 }
  }
  const A = () => Object.fromEntries(zones.map(z => [z, agg(z)]))

  const volChart = echarts.init($('vol'))
  const costChart = echarts.init($('cost'))
  window.addEventListener('resize', () => { volChart.resize(); costChart.resize() })

  function volOption(a) {
    return {
      title: { text: `${TXT.vol[li()]} · ${year}`, left: 'center', textStyle: { fontSize: 14 } },
      legend: { top: 26 }, grid: { top: 64, bottom: 30, left: 60, right: 20 },
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
    return {
      title: { text: `${TXT.cost[li()]} · ${year}`, left: 'center', textStyle: { fontSize: 14 } },
      grid: { top: 48, bottom: 30, left: 66, right: 66 },
      tooltip: {
        trigger: 'axis', formatter: p => {
          const z = p[0].name
          return `${z}<br/>${TXT.costBar[li()]}: ${p[0].value} <br/>${TXT.price[li()]}: ${p[1] ? p[1].value : ''}`
        }
      },
      legend: { top: 22, data: [TXT.costBar[li()], TXT.price[li()]] },
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
  function renderAll() {
    const a = A()
    $('en-title').textContent = TXT.title[li()]
    $('en-lead').textContent = TXT.lead[li()]
    document.querySelector('#year-picker legend').textContent = TXT.yr[li()]
    volChart.setOption(volOption(a), true); volChart.resize()
    costChart.setOption(costOption(a), true); costChart.resize()
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
