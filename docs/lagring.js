// Lagringskostnad mot varaktighet. Statisk referensdata (PNNL-33283, BNEF,
// Ember) - ingen hämtning, inget data/*.js. Varje teknik prissätts två gånger,
// vid 4 h dygnsskiftning och vid 100 h flerdygnslagring, och linjen mellan dem
// visar hur brant kostnaden per kWh faller med varaktigheten.
(function () {
  'use strict'
  const LANGS = ['sv', 'en']
  // Färg = vad som fysiskt håller energin. Det är den egenskapen som avgör
  // lutningen: celler köps linjärt per timme, bergrum och magasin per volym.
  const MED = { geo: '#2b6ca3', ech: '#7a4fb5', thm: '#c0392b', mec: '#b06a2c' }

  // Total installerad kostnad, USD/kWh lagringskapacitet, 2021 års nivå.
  // 4 h-kolumnen är 100 MW, 100 h-kolumnen 1 000 MW - så rapporterar PNNL dem.
  const TECH = [
    { sv: 'Tryckluft (CAES)', en: 'Compressed air (CAES)', med: 'geo', h4: 295, h100: 16 },
    { sv: 'Li-jon LFP', en: 'Li-ion LFP', med: 'ech', h4: 385, h100: null },
    { sv: 'Li-jon NMC', en: 'Li-ion NMC', med: 'ech', h4: 435, h100: 354 },
    { sv: 'Blysyra', en: 'Lead-acid', med: 'ech', h4: 447, h100: null },
    { sv: 'Termisk', en: 'Thermal', med: 'thm', h4: 521, h100: 70, lo: 452, hi: 590 },
    { sv: 'Gravitation', en: 'Gravitational', med: 'mec', h4: 715, h100: 131 },
    { sv: 'Vätgas', en: 'Hydrogen', med: 'geo', h4: null, h100: 34 },
    { sv: 'Pumpkraft (PSH)', en: 'Pumped hydro (PSH)', med: 'geo', h4: null, h100: 69 },
    { sv: 'Vanadin-flödesbatteri', en: 'Vanadium flow battery', med: 'ech', h4: null, h100: 296 }
  ]
  const ACTUAL = { lo: 110, hi: 125, mid: 117 }   // 4 h Li-jon, nyckelfärdigt 2025

  const MEDTXT = {
    geo: ['Geologisk volym', 'Geologic volume'],
    ech: ['Elektrokemisk', 'Electrochemical'],
    thm: ['Termisk massa', 'Thermal mass'],
    mec: ['Mekanisk', 'Mechanical']
  }
  const TXT = {
    title: ['Lagringens meritordning vänder med varaktigheten',
      'The storage merit order inverts with duration'],
    lead: ['Total installerad kostnad per kWh lagringskapacitet, logaritmisk skala. Varje teknik prissätts två gånger: vid 4 timmars dygnsskiftning och vid 100 timmars flerdygnslagring. Vid 4 timmar ryms hela fältet inom en faktor 2,4, så kapitalkostnaden skiljer knappt teknikerna åt. Vid 100 timmar öppnar spannet till en faktor 22 och rangordningen vänder: litiumjon faller från näst billigast till dyrast. Färgen markerar vad som fysiskt håller energin, för det är den egenskapen som avgör lutningen.',
      'Total installed cost per kWh of energy capacity, log scale. Every technology is priced twice: at a four-hour daily-shifting duty and at a hundred-hour multi-day duty. At four hours the whole field sits within a factor of 2.4, so capital cost barely separates the technologies. At a hundred hours the spread opens to a factor of 22 and the ranking reverses: lithium-ion falls from second-cheapest to dearest. Colour marks what physically holds the energy, because that is the property that sets the slope.'],
    chartH: ['Kostnad per kWh vid två driftfall', 'Cost per kWh at two duty cycles'],
    chartSub: ['Linje = samma teknik i båda kolumnerna. Punkter utan linje saknar publicerat värde för det andra driftfallet. Romben = nyckelfärdig 4 h-nivå 2025.',
      'A line means the same technology in both columns. Points without a line have no published value for the other duty. The diamond marks the 2025 turnkey 4 h level.'],
    d4: ['4 timmar', '4 hours'], d100: ['100 timmar', '100 hours'],
    d4s: ['dygnsskiftning · 100 MW', 'daily shifting · 100 MW'],
    d100s: ['flerdygn · 1 000 MW', 'multi-day · 1,000 MW'],
    unit: ['USD/kWh', 'USD/kWh'],
    actual: ['2025 faktiskt $110–125', '2025 actual $110–125'],
    actualSub: ['4 h Li-jon, redan under de $291 som DOE räknade med till 2030',
      '4 h Li-ion, already under the $291 DOE projected for 2030'],
    range: ['spann', 'range'],
    notes: [
      [['Varför mediet avgör', 'Why the medium decides'],
       ['Kostnaden delas i en effektdel som skalar med kW och en energidel som skalar med kWh gånger timmar. Batterier bär energidelen i själva cellerna, så fler timmar köps linjärt i cellstack. Bergrum, magasin och värmelager köper timmar som volym, vilket är nästan gratis när maskinhallen väl står.',
        'Cost splits into a power component scaling with kW and an energy component scaling with kWh times hours. Batteries carry the energy component in the cells, so hours are bought linearly in cell stack. Caverns, reservoirs and heat stores buy hours as volume, which is close to free once the powerhouse exists.']],
      [['Korrigeringen 2025', 'The 2025 correction'],
       ['4 h-kolumnen är 2021 års nivå och den är passerad. Nyckelfärdig 4 h litiumjon ligger nu kring $110–125/kWh, under de $291/kWh som DOE räknade med till 2030, så vid dygnsdrift drog batterierna ifrån fältet fyra år i förtid. 100 h-kolumnen har inte rört sig lika mycket, för där dominerar cellstacken och billiga celler löser inte ett energidelsproblem av den storleken.',
        'The four-hour column is 2021 vintage and has been overtaken. Turnkey four-hour lithium-ion now sits near $110–125/kWh, under the $291/kWh DOE projected for 2030, so at daily duty batteries broke away from the field four years early. The hundred-hour column has not moved comparably, because there the cell stack dominates and cheap cells do not fix an energy-component gap of that size.']],
      [['Det andra straffet', 'The second penalty'],
       ['Kapitalkostnaden är inte hela bilden. PNNL finner att den levererade kostnaden bottnar kring tio timmar och stiger därefter för samtliga tekniker, eftersom ett dygns- eller flerdygnslager cyklar mindre än ett varv om dygnet och har färre levererade kWh att slå ut kapitalet på. Lång varaktighet straffas två gånger, på kapital och på genomsättning.',
        'Capital cost is not the whole story. PNNL finds levelised cost bottoming near ten hours and rising beyond it for every technology, because a 24- or 100-hour asset cycles less than once a day and has fewer delivered kWh to spread its capital over. Long duration is charged twice, on capital and on throughput.']],
      [['Den nordiska fotnoten', 'The Nordic footnote'],
       ['Medvetet utanför denna axel: danskt gropvärmelager kostar $0,45–0,70/kWh, tre tiopotenser under den billigaste punkten i diagrammet, men det går bara åt ett håll, el till värme. Därför kan det inte rangordnas mot lager som lämnar tillbaka el, och därför hör det hemma i flexibiliteten på efterfrågesidan snarare än i lagringens meritordning.',
        'Deliberately off this axis: Danish pit thermal storage costs $0.45–0.70/kWh, three orders of magnitude below the cheapest point plotted, but it only runs one way, power to heat. That is why it cannot be ranked against stores that give electricity back, and why it belongs in demand-side flexibility rather than the storage merit order.']],
      [['Läs med förbehåll', 'Read with care'],
       ['De två kolumnerna har olika effektstorlek, 100 MW och 1 000 MW, eftersom det är så PNNL rapporterar dem, så högerkolumnen bär en skalfördel som vänsterkolumnen saknar. Pumpkraft och vätgas har ingen publicerad 4 h-punkt, och 100 h-värdet för litium är NMC snarare än LFP. Termisk visas som mittvärde av ett spann $452–590, eftersom systemen bakom omfattar sand, smält salt, betong och flytande luft.',
        'The two columns carry different power ratings, 100 MW and 1,000 MW, because that is how PNNL reports them, so the right-hand column holds a scale advantage the left does not. Pumped hydro and hydrogen have no published four-hour point, and the hundred-hour lithium figure is NMC rather than LFP. Thermal is shown as the midpoint of a $452–590 range, because the systems behind it span sand, molten salt, concrete and liquid air.']],
      [['Varför det spelar roll här', 'Why it matters here'],
       ['Meritordningen förklarar varför Norden har en särställning. Vattenmagasin är billig lagring i bulk och fjärrvärmens ackumulatorer är billig flexibilitet, medan batterier bara är billiga i dygnsfönstret. Den som planerar för flerdygnsvariation med batterier betalar den högra kolumnen; den som har magasin betalar den vänstra.',
        'The merit order explains why the Nordics hold a particular position. Hydro reservoirs are cheap bulk storage and district-heating accumulators are cheap flexibility, while batteries are cheap only in the daily window. Planning for multi-day variability with batteries means paying the right-hand column; having reservoirs means paying the left.']]
    ],
    src: ['Installerade kostnader: PNNL-33283, Energy Storage Grand Challenge Cost and Performance Assessment 2022, 2021 års punktestimat som de anges i löptexten. Kurvor per varaktighet publiceras bara som rasterfigurer och återges inte här. 2025 års litiumnivå: BloombergNEF och Ember. Gropvärmelager: Solarthermalworld och IEA DHC Annex XII.',
      'Installed costs: PNNL-33283, Energy Storage Grand Challenge Cost and Performance Assessment 2022, 2021 vintage point estimates as stated in the report text. Per-duration curves are published only as raster figures and are not reproduced here. The 2025 lithium level: BloombergNEF and Ember. Pit thermal storage: Solarthermalworld and IEA DHC Annex XII.']
  }

  let lang = 'sv'
  const li = () => LANGS.indexOf(lang)
  const $ = id => document.getElementById(id)
  const isNarrow = () => window.innerWidth < 620
  const chart = echarts.init($('slope'))

  // Etiketterna kolliderar mellan serier (t.ex. Blysyra $447 mot Li-jon NMC
  // $435). ECharts labelLayout.moveOverlap arbetar bara inom en serie, så vi
  // räknar förskjutningen själva: rita en gång, mät pixelläget, sprid isär och
  // rita om. DY slås upp av labelLayout via kolumnindex + tekniknamn.
  let DY = {}
  function computeStagger() {
    const gap = isNarrow() ? 13 : 19
    DY = {}
    for (let col = 0; col < 2; col++) {
      const items = TECH
        .map(t => ({ key: t[lang], v: col === 0 ? t.h4 : t.h100 }))
        .filter(x => x.v != null)
      if (col === 0) items.push({ key: '__actual', v: ACTUAL.mid })
      items.forEach(it => {
        const px = chart.convertToPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [col, it.v])
        it.y = px ? px[1] : 0
      })
      items.sort((a, b) => a.y - b.y)
      let last = -1e9
      items.forEach(it => {
        const y = Math.max(it.y, last + gap)
        DY[col + '|' + it.key] = y - it.y
        last = y
      })
    }
  }

  function draw() {
    chart.setOption(option(), true)   // pass 1: etablera layouten så vi kan mäta
    computeStagger()
    chart.setOption(option(), true)   // pass 2: med uträknade förskjutningar
  }
  window.addEventListener('resize', () => { chart.resize(); draw() })

  function option() {
    const nar = isNarrow()
    const series = TECH.map(t => ({
      name: t[lang], type: 'line', symbolSize: 10, connectNulls: false,
      lineStyle: { width: 1.6, opacity: 0.45, color: MED[t.med] },
      itemStyle: { color: MED[t.med] },
      emphasis: { focus: 'series', lineStyle: { opacity: 1, width: 2.6 } },
      labelLayout: p => ({ dy: DY[p.dataIndex + '|' + t[lang]] || 0 }),
      data: [
        t.h4 == null ? null : { value: t.h4, label: { show: true, position: 'left',
          fontSize: nar ? 9 : 12, color: '#333',
          formatter: `${t[lang]}  {b|$${t.lo ? t.lo + '–' + t.hi : t.h4}}`,
          rich: { b: { fontWeight: 'bold', color: '#111', fontSize: nar ? 9 : 12 } } } },
        t.h100 == null ? null : { value: t.h100, label: { show: true, position: 'right',
          fontSize: nar ? 9 : 12, color: '#333',
          formatter: `${t[lang]}  {b|$${t.h100}}`,
          rich: { b: { fontWeight: 'bold', color: '#111', fontSize: nar ? 9 : 12 } } } }
      ]
    }))
    // Nyckelfärdig 2025-nivå för 4 h litiumjon - egen serie så den inte läses
    // som en av PNNL-punkterna.
    series.push({
      name: TXT.actual[li()], type: 'scatter', symbol: 'diamond', symbolSize: 16,
      itemStyle: { color: MED.ech }, labelLayout: () => ({ dy: DY['0|__actual'] || 0 }),
      data: [{ value: [0, ACTUAL.mid], label: { show: true, position: 'right', distance: 12,
        fontSize: nar ? 10 : 12.5, color: MED.ech, fontWeight: 'bold',
        formatter: `${TXT.actual[li()]}\n{s|${TXT.actualSub[li()]}}`,
        rich: { s: { color: '#555', fontWeight: 'normal', fontSize: nar ? 9 : 11.5 } } } }]
    })
    return {
      grid: { top: 56, bottom: 40, left: nar ? 110 : 210, right: nar ? 110 : 210 },
      tooltip: {
        trigger: 'item',
        formatter: p => {
          const t = TECH[p.seriesIndex]
          if (!t) return `<b>${TXT.actual[li()]}</b><br/>${TXT.actualSub[li()]}`
          const dur = p.dataIndex === 0 ? TXT.d4[li()] : TXT.d100[li()]
          const v = (p.dataIndex === 0 && t.lo) ? `$${t.lo}–${t.hi} (${TXT.range[li()]})` : `$${p.value}`
          return `<b>${t[lang]}</b> · ${dur}<br/>${v} ${TXT.unit[li()]}`
        }
      },
      xAxis: {
        // Kategoriaxel tar boolean, inte det procentspann som värdeaxeln tar.
        type: 'category', boundaryGap: true,
        data: [TXT.d4[li()], TXT.d100[li()]],
        axisLabel: { fontSize: nar ? 12 : 15, fontWeight: 'bold', color: '#222', margin: 12 },
        axisTick: { show: false }, axisLine: { lineStyle: { color: '#c9d2e0' } }
      },
      yAxis: {
        type: 'log', min: 12, max: 900, name: TXT.unit[li()], nameGap: 16,
        axisLabel: { fontSize: 11, formatter: v => '$' + v },
        splitLine: { lineStyle: { color: '#e6e9ee' } }
      },
      series
    }
  }

  function renderTable() {
    const head = '<tr><th>' + (lang === 'en' ? 'Technology' : 'Teknik') + '</th><th>' +
      TXT.d4[li()] + ' ' + TXT.unit[li()] + '</th><th>' + TXT.d100[li()] + ' ' + TXT.unit[li()] + '</th></tr>'
    const body = TECH.map(t => '<tr><th>' + t[lang] + '</th><td>' +
      (t.lo ? t.lo + '–' + t.hi : (t.h4 == null ? '–' : t.h4)) + '</td><td>' +
      (t.h100 == null ? '–' : t.h100) + '</td></tr>').join('')
    $('chart-data').innerHTML = '<table><caption>' + TXT.title[li()] + '</caption><thead>' +
      head + '</thead><tbody>' + body + '</tbody></table>'
  }

  function render() {
    document.title = 'Elmix – ' + TXT.title[li()]
    $('lg-title').textContent = TXT.title[li()]
    $('lg-lead').textContent = TXT.lead[li()]
    $('chart-h').textContent = TXT.chartH[li()]
    $('chart-sub').textContent = TXT.chartSub[li()]
    $('legend').innerHTML = Object.keys(MED).map(k =>
      `<span class="lgi"><i style="background:${MED[k]}"></i>${MEDTXT[k][li()]}</span>`).join('')
    $('notes').innerHTML = TXT.notes.map(n =>
      `<div class="note"><h3>${n[0][li()]}</h3><p>${n[1][li()]}</p></div>`).join('')
    $('src').textContent = TXT.src[li()]
    chart.resize(); draw()
    renderTable()
  }

  const sw = $('lang-switch')
  LANGS.forEach(l => {
    const b = document.createElement('button')
    b.type = 'button'; b.textContent = l.toUpperCase()
    b.setAttribute('aria-pressed', String(l === lang))
    b.onclick = () => {
      lang = l; document.documentElement.setAttribute('data-lang', l)
      for (const c of sw.children) {
        const on = c.textContent === l.toUpperCase()
        c.classList.toggle('active', on); c.setAttribute('aria-pressed', String(on))
      }
      render()
    }
    sw.appendChild(b)
  })
  document.documentElement.setAttribute('data-lang', lang)
  for (const c of sw.children) c.classList.toggle('active', c.textContent === lang.toUpperCase())
  render()
})()
