// Elpris (day-ahead månadsmedel) per elområde för EU + Norge. Två vyer på samma
// data (window.euPrices, byggd av export-euprices.sh från Energy-Charts):
//   1. Rangordnade staplar för vald månad – sorteras fallande, Sverige framhävt,
//      snittlinje för alla zoner. Månadsreglaget sorterar om live.
//   2. Värmekarta zon × månad – rader sorterade på totalmedel (fallande),
//      svenska rader markerade. Grön = billigt, rött = dyrt.
(function () {
  'use strict'
  const E = window.euPrices
  if (!E || !E.zones || !E.months.length) return

  const SE = '#1a6b54', OTHER = '#9fb0c0', AVG = '#e07b39'
  const LANGS = ['sv', 'en']
  const MON = {
    sv: ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'dec'],
    en: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  }
  const TXT = {
    title: ['Elpris per elområde – EU & Norge', 'Electricity price by bidding zone – EU & Norway'],
    lead: ['Day-ahead-priser (månadsmedel, EUR/MWh) för Europas elområden inklusive alla norska och svenska zoner. Staplarna rangordnar vald månad från dyrast till billigast och sorterar om när du drar i reglaget – svenska zoner (SE1–SE4) är framhävda, den streckade linjen är snittet för alla zoner. Värmekartan visar hela tidsserien: rader sorterade på totalmedel, grönt billigt och rött dyrt. Sverige – särskilt norra SE1/SE2 – ligger nästan alltid i den billiga änden.',
      'Day-ahead prices (monthly mean, EUR/MWh) for Europe’s bidding zones including every Norwegian and Swedish zone. The bars rank the selected month from dearest to cheapest and re-sort as you drag the slider – Swedish zones (SE1–SE4) are highlighted and the dashed line is the all-zone average. The heatmap shows the whole time series: rows sorted by overall mean, green cheap and red dear. Sweden – especially the northern SE1/SE2 – sits almost always at the cheap end.'],
    barsH: ['Rangordning för vald månad', 'Ranking for the selected month'],
    barsSub: ['Fallande pris. Svenska zoner framhävda; streckad linje = snitt alla zoner.',
      'Descending price. Swedish zones highlighted; dashed line = all-zone average.'],
    heatH: ['Hela tidsserien (zon × månad)', 'Full time series (zone × month)'],
    heatSub: ['Rader sorterade på totalmedel. Grön = billigt, röd = dyrt. ★ = svensk zon. Grå cell = data saknas hos källan (t.ex. IT-zonerna jan–aug 2025; IT-Calabria fanns ej före 2021).',
      'Rows sorted by overall mean. Green = cheap, red = dear. ★ = Swedish zone. Grey cell = no data at source (e.g. the Italian zones Jan–Aug 2025; IT-Calabria did not exist before 2021).'],
    month: ['Månad', 'Month'],
    price: ['Pris', 'Price'], avg: ['snitt alla zoner', 'all-zone average'],
    rank: ['plats', 'rank'], showing: ['Visar', 'Showing'],
    noData: ['ingen data denna månad', 'no data this month']
  }

  let lang = 'sv'
  let mi = E.months.length - 1               // vald månadsindex (senaste)
  const li = () => LANGS.indexOf(lang)
  const $ = id => document.getElementById(id)
  const isNarrow = () => window.innerWidth < 620
  const fmtMonth = ym => { const [y, m] = ym.split('-'); return MON[lang][+m - 1] + ' ' + y }
  const star = z => (z.se ? '★ ' : '') + z.label

  const barsChart = echarts.init($('bars'))
  const heatChart = echarts.init($('heat'))
  window.addEventListener('resize', () => { barsChart.resize(); heatChart.resize() })

  // --- Rangordnade staplar för månad mi -------------------------------------
  function barsOption() {
    const nar = isNarrow()
    const rows = E.zones
      .map(z => ({ label: z.label, land: z.land, se: z.se, val: z.v[mi] }))
      .filter(r => r.val != null)
      .sort((a, b) => a.val - b.val)          // stigande -> yAxis (default) sätter dyrast överst? nej
    // yAxis category (ej inverse): index 0 nederst. Vi vill dyrast överst ->
    // sortera stigande så dyrast hamnar sist = överst.
    const avg = rows.reduce((s, r) => s + r.val, 0) / (rows.length || 1)
    return {
      grid: { top: 12, bottom: nar ? 46 : 34, left: nar ? 58 : 70, right: 54 },
      tooltip: {
        trigger: 'axis', axisPointer: { type: 'shadow' },
        formatter: p => { const r = rows[p[0].dataIndex]
          const rank = rows.length - p[0].dataIndex
          return `<b>${r.label}</b> · ${r.land}<br/>${TXT.price[li()]}: <b>${r.val}</b> ${E.unit}` +
            `<br/>${TXT.rank[li()]}: ${rank}/${rows.length} · ${(r.val / avg * 100).toFixed(0)}% ${TXT.avg[li()]}` }
      },
      xAxis: { type: 'value', name: E.unit, nameGap: 22, axisLabel: { fontSize: 11 } },
      yAxis: {
        type: 'category', data: rows.map(star),
        axisLabel: {
          fontSize: nar ? 9 : 11, margin: 8,
          formatter: v => v, rich: {},
          color: '#333'
        }
      },
      series: [{
        type: 'bar',
        data: rows.map(r => ({ value: r.val, itemStyle: { color: r.se ? SE : OTHER } })),
        label: { show: true, position: 'right', fontSize: nar ? 9 : 11, formatter: p => Math.round(p.value) },
        markLine: {
          symbol: 'none', silent: true, lineStyle: { color: AVG, type: 'dashed', width: 2 },
          label: { formatter: `${TXT.avg[li()]} ${Math.round(avg)}`, position: 'insideEndTop', color: AVG, fontSize: 11 },
          data: [{ xAxis: Math.round(avg) }]
        }
      }]
    }
  }

  // --- Värmekarta zon × månad -----------------------------------------------
  function heatOption() {
    const nar = isNarrow()
    const zones = E.zones            // redan sorterade fallande på totalmedel
    const data = [], gaps = []
    let vmax = 0
    zones.forEach((z, yi) => z.v.forEach((val, xi) => {
      if (val != null) { data.push([xi, yi, val]); if (val > vmax) vmax = val }
      else { gaps.push([xi, yi, 0]) }   // källan saknar data -> grå "ingen data"-cell
    }))
    return {
      grid: { top: 10, bottom: 70, left: nar ? 58 : 74, right: 14 },
      tooltip: {
        position: 'top',
        formatter: p => { const z = zones[p.value[1]], m = fmtMonth(E.months[p.value[0]])
          return p.seriesIndex === 1
            ? `<b>${z.label}</b> · ${z.land}<br/>${m}: <i>${TXT.noData[li()]}</i>`
            : `<b>${z.label}</b> · ${z.land}<br/>${m}: <b>${p.value[2]}</b> ${E.unit}` }
      },
      xAxis: {
        type: 'category', data: E.months, splitArea: { show: false },
        axisLabel: { fontSize: 10, interval: 0, formatter: v => v.endsWith('-01') ? v.slice(0, 4) : '' },
        axisTick: { alignWithLabel: true }
      },
      yAxis: {
        type: 'category', inverse: true, data: zones.map(z => z.label),
        axisLabel: {
          fontSize: nar ? 9 : 11,
          formatter: v => /^SE\d/.test(v) ? `{se|★ ${v}}` : v,
          rich: { se: { color: SE, fontWeight: 'bold' } }
        }
      },
      visualMap: {
        seriesIndex: 0,   // färgskalan gäller bara pris-cellerna, ej grå-cellerna
        min: 0, max: Math.min(250, Math.round(vmax)), calculable: true,
        orient: 'horizontal', left: 'center', bottom: 12, itemWidth: 14, itemHeight: 160,
        text: [`${TXT.price[li()]} ${E.unit}`, ''],
        inRange: { color: ['#1a9850', '#a6d96a', '#ffffbf', '#fdae61', '#d73027'] }
      },
      series: [
        { type: 'heatmap', data, progressive: 3000,
          emphasis: { itemStyle: { borderColor: '#222', borderWidth: 1 } } },
        { type: 'heatmap', data: gaps, silent: true, progressive: 3000,
          itemStyle: { color: '#e6e9ee' } }   // ingen data hos källan
      ]
    }
  }

  function renderTable() {
    const ym = E.months[mi]
    const rows = E.zones.map(z => ({ label: z.label, land: z.land, se: z.se, val: z.v[mi], mean: z.mean }))
      .filter(r => r.val != null).sort((a, b) => b.val - a.val)
    const head = '<tr><th>#</th><th>' + (lang === 'en' ? 'Zone' : 'Elområde') + '</th><th>' +
      (lang === 'en' ? 'Country' : 'Land') + '</th><th>' + fmtMonth(ym) + ' ' + E.unit +
      '</th><th>' + (lang === 'en' ? 'overall mean' : 'totalmedel') + '</th></tr>'
    const body = rows.map((r, i) => '<tr><td>' + (i + 1) + '</td><th>' + (r.se ? '★ ' : '') + r.label +
      '</th><td>' + r.land + '</td><td>' + r.val + '</td><td>' + (r.mean ?? '–') + '</td></tr>').join('')
    $('chart-data').innerHTML = '<table><caption>' + TXT.title[li()] + ' · ' + fmtMonth(ym) +
      '</caption><thead>' + head + '</thead><tbody>' + body + '</tbody></table>'
    $('chart-status').textContent = TXT.showing[li()] + ' ' + fmtMonth(ym)
  }

  function render() {
    $('ep-title').textContent = TXT.title[li()]
    $('ep-lead').textContent = TXT.lead[li()]
    $('month-legend').textContent = TXT.month[li()]
    $('bars-h').textContent = TXT.barsH[li()]
    $('bars-sub').textContent = TXT.barsSub[li()]
    $('heat-h').textContent = TXT.heatH[li()]
    $('heat-sub').textContent = TXT.heatSub[li()]
    $('month-label').textContent = fmtMonth(E.months[mi])
    barsChart.setOption(barsOption(), true); barsChart.resize()
    heatChart.setOption(heatOption(), true); heatChart.resize()
    renderTable()
  }

  // Reglage + stegknappar
  const slider = $('month-slider')
  slider.max = String(E.months.length - 1); slider.value = String(mi)
  const setMi = v => { mi = Math.max(0, Math.min(E.months.length - 1, v)); slider.value = String(mi)
    $('month-label').textContent = fmtMonth(E.months[mi]); barsChart.setOption(barsOption(), true); renderTable() }
  slider.oninput = () => setMi(+slider.value)
  $('prev').onclick = () => setMi(mi - 1)
  $('next').onclick = () => setMi(mi + 1)

  const sw = $('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button'); b.textContent = l.toUpperCase()
    b.setAttribute('aria-pressed', String(l === lang))
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) { const on = c.textContent === l.toUpperCase(); c.classList.toggle('active', on); c.setAttribute('aria-pressed', String(on)) }
      render()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', lang)
  for (const c of sw.children) c.classList.toggle('active', c.textContent === lang.toUpperCase())
  render()
})()
