package elmix.bess

/** All lopande text pa sidan, sv + en. Halls samlad sa spraken inte glider isar. */
object Texts:

  final case class T(sv: String, en: String):
    def apply(lang: String): String = if lang == "en" then en else sv

  val title = T(
    "Betalar batteriet sig? SE1–SE4",
    "Does the battery pay for itself? SE1–SE4"
  )

  val lead = T(
    "Frågan är inte vad batterier tjänade i fjol, utan hur mycket av intäkten som hänger på " +
      "energin de kan lagra och hur mycket på effekten de kan erbjuda – för bara det första " +
      "växer när du lägger till en femte timmes varaktighet. Arbitraget är proportionellt mot " +
      "varaktigheten, men spreaden krymper när fönstret vidgas: den femte billigaste timmen är " +
      "dyrare än den första. Kapacitetsintäkten innehåller ingen varaktighet alls. Kostnaden gör " +
      "det, linjärt. Där de linjerna korsas står svaret.",
    "The question is not what batteries earned last year, but how much of the revenue rests on " +
      "the energy they can store and how much on the power they can offer – because only the " +
      "first grows when you add a fifth hour of duration. Arbitrage is proportional to duration, " +
      "but the spread shrinks as the window widens: the fifth cheapest hour is dearer than the " +
      "first. Capacity revenue contains no duration at all. Cost does, linearly. The answer is " +
      "where those lines cross."
  )

  val zoneLegend = T("Elområde", "Bidding zone")
  val yearLegend = T("År", "Year")
  val dayLegend = T("Dygnsunderlag", "Day basis")
  val dayMean = T("Medeldygn", "Mean day")
  val dayMedian = T("Mediandygn", "Median day")

  val sysLegend = T("Batteriet", "The battery")
  val ekoLegend = T("Kapitalet", "Capital")
  val markLegend = T("Marknaderna", "Markets")

  val hLabel = T("Varaktighet", "Duration")
  val capexLabel = T("Investering", "Capex")
  val waccLabel = T("WACC", "WACC")
  val lifeLabel = T("Livslängd", "Life")
  val opexLabel = T("Drift", "Opex")
  val cyclesLabel = T("Cykler/år", "Cycles/yr")
  val etaLabel = T("Verkningsgrad", "Round-trip")
  val dodLabel = T("Urladdningsdjup", "Depth of discharge")
  val fcrLabel = T("Tillgänglighet FCR-D", "Availability FCR-D")
  val afrrLabel = T("Tillgänglighet aFRR", "Availability aFRR")

  val revH =
    T("Intäkt per marknad mot kostnaden att äga", "Revenue by market against the cost of ownership")
  val revSub = T(
    "Tusen euro per MW ansluten effekt och år. Vänstergruppen betalas för levererad energi och " +
      "är netto laddningskostnad; högergruppen betalas för tillgänglig effekt och har ingen " +
      "laddning att dra av. Bandet är vad ägandet kostar vid vald investering. Summera dem inte: " +
      "reglagen finns just för att du ska välja hur mycket av effekten som faktiskt säljs till " +
      "varje marknad, och ingen enhet är tillgänglig till hundra procent överallt samtidigt.",
    "Thousand euros per MW of connected power per year. The left group is paid for energy " +
      "delivered and is net of charging; the right group is paid for power held available and " +
      "has no charging to deduct. The band is what ownership costs at the chosen capex. Do not " +
      "sum them: the sliders exist precisely so you choose how much of the power is actually " +
      "sold to each market, and no unit is available at a hundred per cent everywhere at once."
  )

  val durH = T("Intäkt mot varaktighet", "Revenue against duration")
  val durSub = T(
    "Sidans egentliga argument. Arbitragelinjen bågnar av eftersom spreaden krymper när fönstret " +
      "vidgas; kostnadslinjen är rak. Där de korsas slutar en timme till att löna sig.",
    "The page’s real argument. The arbitrage line bends over because the spread shrinks as the " +
      "window widens; the cost line is straight. Where they cross, one more hour stops paying."
  )

  val beH = T("Break-even-investering per elområde", "Break-even capex by zone")
  val beSub = T(
    "Det pris per kWh vid vilket arbitraget precis täcker årskostnaden, vid valda antaganden. " +
      "Ligger stapeln under marknadspriset för celler betalar arbitraget ensamt inte batteriet.",
    "The price per kWh at which arbitrage exactly covers the annual cost, at the chosen " +
      "assumptions. Where the bar sits below the market price for cells, arbitrage alone does " +
      "not pay for the battery."
  )

  val energyGroup = T("Betalt för levererad energi", "Paid for energy delivered")
  val powerGroup = T("Betalt för tillgänglig effekt", "Paid for power available")
  val arbitrage = T("Arbitrage", "Arbitrage")
  val costBand = T("Kostnad att äga", "Cost of ownership")
  val costLine = T("Årskostnad", "Annual cost")
  val kEurMw = T("k€/MW/år", "k€/MW/yr")
  val eurKwh = T("€/kWh", "€/kWh")
  val hours = T("timmar", "hours")
  val national = T("nationellt pris", "national price")
  val zonal = T("zonpris", "zonal price")
  val noZonePrice = T("inget publicerat pris för denna zon", "no published price for this zone")

  val statBreakEven = T("Break-even", "Break-even")
  val statPayback = T("Återbetalning", "Payback")
  val statNeeded = T("Krävd spread", "Spread needed")
  val statSpread = T("Faktisk spread", "Actual spread")
  val years = T("år", "yr")
  val never = T("aldrig", "never")

  val noData = T(
    "Prisdatan för SE1–SE4 kunde inte hämtas vid senaste publiceringen. Diagrammen är därför " +
      "tomma. Källa: Energy-Charts (ENTSO-E/SMARD).",
    "Price data for SE1–SE4 could not be fetched at the last publish, so the charts are empty. " +
      "Source: Energy-Charts (ENTSO-E/SMARD)."
  )

  val notes: Vector[(T, T)] = Vector(
    T(
      "Varför spreaden krymper när batteriet växer",
      "Why the spread shrinks as the battery grows"
    ) ->
      T(
        "Ett batteri med en timmes varaktighet köper dygnets billigaste timme och säljer den " +
          "dyraste. Ett med fem timmar måste också köpa den femte billigaste, som är dyrare, och " +
          "sälja den femte dyraste, som är billigare. Levererad energi växer linjärt med " +
          "varaktigheten medan marginalen per MWh faller, och produkten planar ut. Kostnaden " +
          "planar inte ut: en femte timme är en femtedel mer celler.",
        "A battery with one hour of duration buys the day’s cheapest hour and sells the dearest. " +
          "One with five hours must also buy the fifth cheapest, which is dearer, and sell the " +
          "fifth dearest, which is cheaper. Energy delivered grows linearly with duration while " +
          "the margin per MWh falls, and the product flattens. Cost does not flatten: a fifth " +
          "hour is a fifth more cells."
      ),
    T("Medeldygn eller mediandygn", "Mean day or median day") ->
      T(
        "Reglaget överst byter underlag, och skillnaden är inte kosmetisk. I SE1 och SE2 bärs " +
          "medelvärdet av ett fåtal extremdygn: mediandygnet ger ungefär en tredjedel av " +
          "medeldygnets arbitrage. I SE3 är skillnaden mindre. I SE4 är mediandygnet 2025 " +
          "faktiskt något bättre än medeldygnet, vilket är värt att veta innan man generaliserar " +
          "om att medelvärden alltid smickrar.",
        "The control at the top switches the basis, and the difference is not cosmetic. In SE1 " +
          "and SE2 the mean is carried by a handful of extreme days: the median day gives about " +
          "a third of the mean day’s arbitrage. In SE3 the gap is smaller. In SE4 the median day " +
          "in 2025 is actually slightly better than the mean day, which is worth knowing before " +
          "generalising that means always flatter."
      ),
    T("Nationellt och zonvis är inte samma sak", "National and zonal are not the same thing") ->
      T(
        "FCR-D och FCR-N upphandlas för hela Sverige, aFRR och mFRR per elområde. Sidan märker " +
          "därför varje reservstapel med vilket som gäller, och visar inget aFRR-pris för SE1 " +
          "och SE2 eftersom Svenska kraftnät inte publicerat något för dem i det underlag som " +
          "används här. Ett nationellt pris får aldrig ritas som om det vore zonens.",
        "FCR-D and FCR-N are procured for Sweden as a whole, aFRR and mFRR per bidding zone. The " +
          "page therefore marks each reserve bar with which applies, and shows no aFRR price for " +
          "SE1 and SE2 because Svenska kraftnät has published none for them in the material used " +
          "here. A national price must never be drawn as if it were the zone’s."
      ),
    T("Vad som inte är automatiskt", "What is not automatic") ->
      T(
        "Spotpriserna hämtas vid varje publicering. Reservpriserna gör det inte: Mimer har ett " +
          "internt API bakom webbgränssnittet men inget dokumenterat publikt, och " +
          "månadsrapporterna publiceras som PDF bakom en JS-renderad länklista. De reservtal som " +
          "visas är därför handmatade ur rapporterna, och varje tal bär sin månad och sin källa " +
          "i underlaget. De åldras tills någon uppdaterar dem.",
        "Spot prices are fetched at every publish. Reserve prices are not: Mimer has an internal " +
          "API behind its web interface but nothing documented and public, and the monthly " +
          "reports are published as PDFs behind a JS-rendered link list. The reserve figures " +
          "shown are therefore hand-entered from those reports, and each carries its month and " +
          "source in the payload. They age until somebody updates them."
      ),
    T("Läs med förbehåll", "Read with care") ->
      T(
        "Modellen är ett dygn per cykel och en perfekt förutsägelse av dygnets priser – en " +
          "verklig aktör ser inte facit i förväg och fångar därför mindre än spreaden här. " +
          "Nätavgifter, obalanskostnader och degradering ingår inte. Sidan gäller " +
          "nätanslutna batterier; hushållsbatterier har helt annan skattebehandling, med " +
          "energiskatt och moms på laddningen som undviks på urladdningen, och hör inte hemma i " +
          "samma jämförelse.",
        "The model is one cycle a day and perfect foresight of the day’s prices – a real " +
          "operator does not see the answer in advance and therefore captures less than the " +
          "spread shown here. Grid fees, imbalance costs and degradation are excluded. The page " +
          "covers grid-scale batteries; household batteries have an entirely different tax " +
          "treatment, with energy tax and VAT on charging that are avoided on discharge, and do " +
          "not belong in the same comparison."
      )
  )

  val src = T(
    "Spotpris: Energy-Charts (ENTSO-E/SMARD, CC BY 4.0), dygnsvisa spreadar räknade per " +
      "elområde och år. Reservpriser: Svenska kraftnäts månadsrapporter, handmatade – se noten " +
      "ovan. Investeringsnivåer: BloombergNEF Energy Storage Systems Cost Survey (global " +
      "nyckelfärdig 4 h) och uppgivna svenska projektkostnader.",
    "Spot price: Energy-Charts (ENTSO-E/SMARD, CC BY 4.0), daily spreads computed per bidding " +
      "zone and year. Reserve prices: Svenska kraftnät’s monthly reports, hand-entered – see the " +
      "note above. Capex levels: BloombergNEF Energy Storage Systems Cost Survey (global " +
      "turnkey 4 h) and reported Swedish project costs."
  )
