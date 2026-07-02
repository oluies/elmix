// Kina vs världen: årlig produktionsmix (andel), total elproduktion (skala) och
// livscykel-CO2-intensitet. Data: Ember (via Our World in Data), TWh per kraftslag
// och år. Rena aggregat klientsidan; inga spot-/timvyer (finns ej öppet för Kina).
(function () {
  'use strict'
  const E = window.emberMix
  if (!E) return
  // Stapelordning: fossilt först, sedan kärnkraft + förnybart.
  const FUELS = [
    { key: 'kol',  name: 'Kol',              nameEn: 'Coal',         c: '#333' },
    { key: 'gas',  name: 'Gas',              nameEn: 'Gas',          c: '#c98a52' },
    { key: 'olja', name: 'Olja',             nameEn: 'Oil',          c: '#7b5e57' },
    { key: 'k',    name: 'Kärnkraft',        nameEn: 'Nuclear',      c: '#4caf50' },
    { key: 'va',   name: 'Vattenkraft',      nameEn: 'Hydro',        c: '#2e6fd6' },
    { key: 'v',    name: 'Vind',             nameEn: 'Wind',         c: '#4dc4d4' },
    { key: 's',    name: 'Sol',              nameEn: 'Solar',        c: '#fac858' },
    { key: 'bio',  name: 'Bioenergi',        nameEn: 'Bioenergy',    c: '#9c6b3f' },
    { key: 'ov',   name: 'Övrigt förnybart', nameEn: 'Other renew.', c: '#9aa7b8' }
  ]
  // Livscykel-CO2 (gCO2eq/kWh), IPCC AR5-medianer; olja ~650, övrigt förnybart ~38.
  const CO2 = { kol: 820, gas: 490, olja: 650, k: 12, va: 24, v: 11, s: 48, bio: 230, ov: 38 }
  // Fast landordning (Kina överst via yAxis.inverse).
  const ORDER = ['China', 'India', 'United States', 'EU', 'Germany', 'France', 'Sweden', 'World']

  const LANGS = ['sv', 'en']
  const TXT = {
    title: ['Kina vs världen – produktionsmix & CO₂-intensitet', 'China vs the world – generation mix & CO₂ intensity'],
    lead: ['Årlig elproduktion per kraftslag för Kina jämfört med Indien, USA, EU och de nordiska/europeiska zonerna. Andel (%) gör länderna jämförbara trots att Kina producerar ~20× Sverige; totalgrafen visar den absoluta skalan. CO₂-intensiteten är livscykelviktad (Σ produktion × faktor / Σ produktion, import exkluderad). Kina saknar öppna tim-/spotdata, så inga klock- eller prisvyer.',
      'Annual electricity generation by source for China versus India, the US, the EU and the Nordic/European zones. Share (%) makes countries comparable although China generates ~20× Sweden; the totals chart shows the absolute scale. CO₂ intensity is lifecycle-weighted (Σ generation × factor / Σ generation, imports excluded). China lacks open hourly/spot data, so no clock or price views.'],
    mix: ['Produktionsmix (andel av inhemsk produktion)', 'Generation mix (share of domestic generation)'],
    tot: ['Total elproduktion', 'Total electricity generation'],
    co2: ['CO₂-intensitet (livscykel)', 'CO₂ intensity (lifecycle)'],
    twh: ['TWh', 'TWh'],
    gkwh: ['g CO₂/kWh', 'g CO₂/kWh'],
    yr: ['År', 'Year'],
    showing: ['Visar år', 'Showing year']
  }
  let lang = 'sv', year = E.years[E.years.length - 1]
  const li = () => LANGS.indexOf(lang)
  const $ = id => document.getElementById(id)
  const isNarrow = () => window.innerWidth < 620
  const cname = r => (lang === 'en' ? r.nameEn : r.name)
  const fname = f => (lang === 'en' && f.nameEn) ? f.nameEn : f.name

  // Länder för valt år i fast ordning.
  const recs = () => ORDER.map(en => E.data.find(d => d.nameEn === en && d.y === year)).filter(Boolean)
  const total = r => FUELS.reduce((s, f) => s + (r[f.key] || 0), 0)
  const co2int = r => { const t = total(r); return t ? Math.round(FUELS.reduce((s, f) => s + (r[f.key] || 0) * CO2[f.key], 0) / t) : 0 }
  // Grön (ren) -> röd (smutsig) på skala 0..900 g/kWh.
  const co2color = v => {
    const cols = ['#1a9850', '#a6d96a', '#ffffbf', '#fdae61', '#d73027']
    const x = Math.max(0, Math.min(1, v / 900)) * (cols.length - 1)
    const i = Math.min(cols.length - 2, Math.floor(x)), f = x - i
    const hx = h => [1, 3, 5].map(k => parseInt(h.slice(k, k + 2), 16))
    const a = hx(cols[i]), b = hx(cols[i + 1])
    return `rgb(${a.map((av, k) => Math.round(av + (b[k] - av) * f)).join(',')})`
  }

  const mixChart = echarts.init($('mix'))
  const totChart = echarts.init($('tot'))
  const co2Chart = echarts.init($('co2'))
  window.addEventListener('resize', () => { mixChart.resize(); totChart.resize(); co2Chart.resize() })

  // Horisontella staplar; land på y-axeln (inverse -> Kina överst).
  const cats = rs => rs.map(cname)

  function mixOption(rs) {
    const nar = isNarrow()
    return {
      title: { text: `${TXT.mix[li()]} · ${year}`, left: 'center', top: 6, textStyle: { fontSize: nar ? 13 : 14 } },
      legend: { bottom: 4, left: 'center', itemGap: 10, textStyle: { fontSize: 11 } },
      grid: { top: nar ? 34 : 40, bottom: nar ? 78 : 62, left: 84, right: 20 },
      tooltip: { trigger: 'item', valueFormatter: v => (+v).toFixed(1) + ' %' },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', inverse: true, data: cats(rs) },
      series: FUELS.map(f => ({
        name: fname(f), type: 'bar', stack: 'mix', itemStyle: { color: f.c },
        data: rs.map(r => { const t = total(r); return t ? +((r[f.key] || 0) / t * 100).toFixed(1) : 0 })
      }))
    }
  }
  function totOption(rs) {
    return {
      title: { text: `${TXT.tot[li()]} · ${year}`, left: 'center', top: 6, textStyle: { fontSize: isNarrow() ? 13 : 14 } },
      grid: { top: 44, bottom: 30, left: 84, right: 60 },
      tooltip: { trigger: 'item', valueFormatter: v => (+v).toLocaleString(lang === 'en' ? 'en' : 'sv') + ' TWh' },
      xAxis: { type: 'value', name: TXT.twh[li()] },
      yAxis: { type: 'category', inverse: true, data: cats(rs) },
      series: [{
        type: 'bar', itemStyle: { color: '#4477aa' },
        label: { show: true, position: 'right', formatter: p => Math.round(p.value).toLocaleString(lang === 'en' ? 'en' : 'sv') },
        data: rs.map(r => Math.round(total(r)))
      }]
    }
  }
  function co2Option(rs) {
    return {
      title: { text: `${TXT.co2[li()]} · ${year}`, left: 'center', top: 6, textStyle: { fontSize: isNarrow() ? 13 : 14 } },
      grid: { top: 44, bottom: 30, left: 84, right: 48 },
      tooltip: { trigger: 'item', valueFormatter: v => v + ' g CO₂/kWh' },
      xAxis: { type: 'value', name: TXT.gkwh[li()] },
      yAxis: { type: 'category', inverse: true, data: cats(rs) },
      series: [{
        type: 'bar',
        label: { show: true, position: 'right', formatter: '{c}' },
        data: rs.map(r => { const v = co2int(r); return { value: v, itemStyle: { color: co2color(v) } } })
      }]
    }
  }

  function renderTable(rs) {
    const head = '<tr><th>' + (lang === 'en' ? 'Country' : 'Land') + '</th>' +
      FUELS.map(f => '<th>' + fname(f) + ' %</th>').join('') +
      '<th>TWh</th><th>g CO₂/kWh</th></tr>'
    const rows = rs.map(r => { const t = total(r) || 1
      return '<tr><th>' + cname(r) + '</th>' +
        FUELS.map(f => '<td>' + ((r[f.key] || 0) / t * 100).toFixed(1) + '</td>').join('') +
        '<td>' + Math.round(total(r)) + '</td><td>' + co2int(r) + '</td></tr>' }).join('')
    $('chart-data').innerHTML = '<table><caption>' + TXT.title[li()] + ' · ' + year + '</caption>' +
      '<thead>' + head + '</thead><tbody>' + rows + '</tbody></table>'
    $('mix').setAttribute('aria-label', TXT.mix[li()] + ' · ' + year)
    $('tot').setAttribute('aria-label', TXT.tot[li()] + ' · ' + year)
    $('co2').setAttribute('aria-label', TXT.co2[li()] + ' · ' + year)
    $('chart-status').textContent = TXT.showing[li()] + ' ' + year
  }

  function renderAll() {
    const rs = recs()
    $('cn-title').textContent = TXT.title[li()]
    $('cn-lead').textContent = TXT.lead[li()]
    document.querySelector('#year-picker legend').textContent = TXT.yr[li()]
    mixChart.setOption(mixOption(rs), true); mixChart.resize()
    totChart.setOption(totOption(rs), true); totChart.resize()
    co2Chart.setOption(co2Option(rs), true); co2Chart.resize()
    renderTable(rs)
  }

  const yp = $('year-picker')
  E.years.forEach(y => {
    const lab = document.createElement('label'); lab.style.marginRight = '12px'
    const inp = document.createElement('input'); inp.type = 'radio'; inp.name = 'cnyear'; inp.checked = y === year
    inp.onchange = () => { year = y; renderAll() }
    lab.appendChild(inp); lab.appendChild(document.createTextNode(' ' + y)); yp.appendChild(lab)
  })
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
