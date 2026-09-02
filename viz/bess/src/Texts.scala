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

  val driftLegend = T("Driftprofil", "Dispatch")
  val driftEnCykel = T("En cykel/dygn", "One cycle a day")
  val driftOptimal = T("Optimal profil", "Optimal profile")

  val sysLegend = T("Batteriet", "The battery")
  val ekoLegend = T("Kapitalet", "Capital")
  val markLegend = T("Marknaderna", "Markets")

  val hLabel = T("Varaktighet", "Duration")
  val capexLabel = T("Investering", "Capex")
  val waccLabel = T("WACC", "WACC")
  val lifeLabel = T("Livslängd", "Life")
  val opexLabel = T("Drift & underhåll", "Opex")
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
      "laddning att dra av. Bandet är en fast referens: det spänner de två capex-nivåerna, " +
      "BNEF:s globala nyckelfärdiga och den uppgivna svenska, och rör sig därför inte med " +
      "reglaget – det gör den streckade årskostnadslinjen. Summera inte staplarna: reglagen " +
      "finns just för att du ska välja hur mycket av effekten som faktiskt säljs till varje " +
      "marknad, och ingen enhet är tillgänglig till hundra procent överallt samtidigt.",
    "Thousand euros per MW of connected power per year. The left group is paid for energy " +
      "delivered and is net of charging; the right group is paid for power held available and " +
      "has no charging to deduct. The band is a fixed reference: it spans the two capex levels, " +
      "BNEF’s global turnkey and the reported Swedish one, and therefore does not move with the " +
      "slider – the dashed annual-cost line does. Do not sum the bars: the sliders exist " +
      "precisely so you choose how much of the power is actually sold to each market, and no " +
      "unit is available at a hundred per cent everywhere at once."
  )

  val durH = T("Intäkt mot varaktighet", "Revenue against duration")
  val durSub = T(
    "DE-LU är streckad och med som referens, inte som en femte svensk zon: den går inte att " +
      "välja ovan, eftersom reservpriserna i underlaget är Svenska kraftnäts och inte gäller " +
      "Tyskland. Sidans egentliga argument. Arbitragelinjen bågnar av eftersom spreaden krymper när fönstret " +
      "vidgas; kostnadslinjen är rak. Där de korsas slutar en timme till att löna sig.",
    "DE-LU is dashed and included as a reference, not as a fifth Swedish zone: it cannot be " +
      "selected above, because the reserve prices in the payload are Svenska kraftnät’s and do " +
      "not apply to Germany. The page’s real argument. The arbitrage line bends over because the spread shrinks as the " +
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

  val crossPrefix = T("Korsning vid: ", "Crossing duration: ")
  val neverWithin8h = T("aldrig inom 8 h", "never within 8 h")
  val belowAt1h = T("redan under vid 1 h", "already below at 1 h")
  val noCrossData = T("ingen data", "no data")

  val thZone = T("Zon", "Zone")
  val thArbitrage = T("Arbitrage", "Arbitrage")
  val thBreakEven = T("Break-even", "Break-even")
  val thAnnualCost = T("Årskostnad", "Annual cost")

  val statBreakEven = T("Break-even", "Break-even")
  val statPayback = T("Återbetalning", "Payback")
  val statNeeded = T("Krävd spread", "Spread needed")
  val statSpread = T("Faktisk spread", "Actual spread")
  val years = T("år", "yr")
  val never = T("aldrig", "never")

  // aria-label pa diagramrutorna. Ligger har och inte i HTML:en for att de
  // annars blir kvar pa svenska nar sidan staller om till engelska.
  val ariaRev = T(
    "Intäkt per marknad mot kostnaden att äga, för valt elområde",
    "Revenue by market against the cost of ownership, for the selected zone"
  )
  val ariaDur = T(
    "Arbitrageintäkt mot varaktighet per elområde, med kostnadslinje",
    "Arbitrage revenue against duration per zone, with the cost line"
  )
  val ariaBe = T("Break-even-investering per elområde", "Break-even capex by bidding zone")

  /** Rad under kontrollerna nar den optimala profilen ar vald. */
  def optimalNot(cykler: String, eta: String, dod: String, lang: String): String =
    if lang == "en" then
      s"Optimal profile: perfect foresight over the whole year, $cykler cycles a year at the " +
        s"chosen duration. It is computed in the extract at $eta % round-trip and $dod % depth of " +
        "discharge, so the cycles, round-trip and depth sliders do not move this line - and the " +
        "cycle count is the optimiser’s own choice, not the slider’s. Cell degradation at that " +
        "rate is not costed anywhere on this page."
    else
      s"Optimal profil: perfekt förutsägelse över hela året, $cykler cykler per år vid vald " +
        s"varaktighet. Den räknas i uttaget vid $eta % verkningsgrad och $dod % urladdningsdjup, " +
        "så reglagen för cykler, verkningsgrad och urladdningsdjup rör inte den här linjen – och " +
        "cykelantalet är optimerarens eget val, inte reglagets. Cellslitaget vid den takten är " +
        "inte prissatt någonstans på sidan."

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
    T("Varför Tyskland är med", "Why Germany is here") ->
      T(
        "DE-LU har bredare dygnsspread än något svenskt elområde. För 2025 ger samma modell 112 " +
          "k€/MW/år mot SE4:s 93 och SE3:s 72, och korsningen ligger vid 6,9 timmar mot SE3:s 1,0 " +
          "– varaktighet fortsätter alltså löna sig långt efter att den slutat göra det i Sverige. " +
          "Bakgrunden är en tätare marknad: kärnkraften avvecklades i april 2023, den billiga " +
          "ryska rörgasen föll bort från 2022 efter Rysslands krig mot Ukraina, och en hög andel " +
          "vind och sol ger stora dygnssvängningar i ett nät med nord-sydliga flaskhalsar.\n\n" +
          "Två invändningar hör till. Den tyska flottan växer snabbare än den svenska, och hela " +
          "den här sajten handlar om att spreaden konkurreras bort när flottan växer – dagens " +
          "siffra är inte morgondagens. Och modellen antar perfekt förutsägelse av dygnets " +
          "priser; ju rörligare marknad, desto större blir gapet mellan modellerad och realiserad " +
          "spread, så försprånget är i praktiken mindre än talen antyder.",
        "DE-LU has a wider daily spread than any Swedish bidding zone. For 2025 the same model " +
          "gives 112 k€/MW/yr against SE4’s 93 and SE3’s 72, and the crossing sits at 6.9 hours " +
          "against SE3’s 1.0 – duration keeps paying long after it has stopped paying in Sweden. " +
          "The background is a tighter market: nuclear was shut down in April 2023, cheap Russian " +
          "pipeline gas fell away from 2022 after Russia’s war against Ukraine, and a high share " +
          "of wind and solar swings the day hard in a grid with north-south bottlenecks.\n\n" +
          "Two caveats belong with it. The German fleet is growing faster than the Swedish one, " +
          "and this whole site is about the spread being competed away as fleets grow – today’s " +
          "figure is not tomorrow’s. And the model assumes perfect foresight of the day’s prices; " +
          "the more volatile the market, the wider the gap between modelled and realised spread, " +
          "so the lead is smaller in practice than the numbers suggest."
      ),
    T("En cykel om dygnet är inte optimum", "One cycle a day is not the optimum") ->
      T(
        "Grundmodellen tar dygnets H dyraste timmar mot dygnets H billigaste och antar en cykel. " +
          "Det är optimalt givet EN cykel inom kalenderdygnet, men det är inte optimum: ett dygn " +
          "med två pristoppar bär mer än en cykel, ett platt dygn bär ingen alls, och de billiga " +
          "timmarna ligger ofta kring midnatt så affären spänner över dygnsgränsen.\n\n" +
          "Reglaget överst byter till en profil räknad med dynamisk programmering över hela året, " +
          "med effekt- och energigränser. Skillnaden är 7–26 % och störst vid korta varaktigheter, " +
          "eftersom ett tvåtimmarsbatteri hinner flera cykler per dygn medan ett åttatimmars knappt " +
          "hinner en. Priset är cykler: optimum landar på 400–830 per år mot grundmodellens 365, " +
          "och den takten sliter på cellerna på ett sätt sidan inte prissätter.\n\n" +
          "Sanningen ligger mellan de två. Grundmodellen är för pessimistisk om cykling men för " +
          "optimistisk om framförhållning; optimum antar facit i hand för hela året, vilket ingen " +
          "aktör har. Läs dem som undre och övre gräns, inte som två gissningar.",
        "The base model takes the day’s H dearest hours against its H cheapest and assumes one " +
          "cycle. That is optimal given ONE cycle inside the calendar day, but it is not the " +
          "optimum: a day with two price peaks carries more than one cycle, a flat day carries " +
          "none, and the cheap hours often sit around midnight so the trade spans the day " +
          "boundary.\n\nThe control at the top switches to a profile computed by dynamic " +
          "programming over the whole year, with power and energy limits. The difference is 7–26 % " +
          "and largest at short durations, because a two-hour battery fits several cycles a day " +
          "while an eight-hour one barely fits one. The price is cycles: the optimum lands at " +
          "400–830 a year against the base model’s 365, and that rate wears the cells in a way " +
          "this page does not cost.\n\nThe truth lies between the two. The base model is too " +
          "pessimistic about cycling but too optimistic about foresight; the optimum assumes " +
          "hindsight over the whole year, which no operator has. Read them as a lower and an " +
          "upper bound, not as two guesses."
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
