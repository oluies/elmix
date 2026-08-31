package elmix.foretagspris

/** All lopande text pa sidan, sv + en. Halls samlad sa spraken inte glider isar. */
object Texts:

  final case class T(sv: String, en: String):
    def apply(lang: String): String = if lang == "en" then en else sv

  val title = T(
    "Vad elen kostar företaget – SE1–SE4 mot Kinas provinser",
    "What electricity costs a business – SE1–SE4 versus China’s provinces"
  )

  val lead = T(
    "Spotpriset är inte priset. Ett företag betalar spot plus nätavgift plus energiskatt, och det " +
      "är först där jämförelsen med Kina blir meningsfull. Svensk industri i tillverkningsprocessen " +
      "betalar 0,6 öre/kWh i energiskatt mot normalsatsens 36,0 – en nedsättning på 35,4 öre/kWh " +
      "som är stor nog att ensam flytta Sverige förbi hela den kinesiska provinsfördelningen. Kina " +
      "har inget rikspris: tarifferna sätts per provins, spänningsnivå och tid på dygnet, och " +
      "spannet mellan billigaste och dyraste provins är omkring en faktor 1,8.",
    "The spot price is not the price. A business pays spot plus grid fee plus energy tax, and only " +
      "there does a comparison with China mean anything. Swedish industry in the manufacturing " +
      "process pays 0.6 öre/kWh in energy tax against the standard 36.0 – a 35.4 öre/kWh reduction " +
      "large enough on its own to move Sweden past the entire Chinese provincial distribution. " +
      "China has no national price: tariffs are set per province, voltage level and time of day, " +
      "and the spread between cheapest and dearest province is roughly a factor of 1.8."
  )

  val profileLegend = T("Kundtyp", "Customer type")
  val gridLegend = T("Nätavgift (antagande)", "Grid fee (assumption)")
  val periodLegend = T("Period för svensk spot", "Period for Swedish spot")
  val last12 = T("Senaste 12 mån", "Last 12 months")

  val stackH = T("Prisstapeln: vad varje öre går till", "The price stack: where every öre goes")
  val stackSub = T(
    "Öre/kWh exkl. moms. Svenska staplar = spot + nätavgift + energiskatt. Kinesiska staplar = " +
      "provinsens allt-i-ett-tariff, uppdelad efter NDRC:s rikssnitt (60 % energi, 35 % nät, 5 % " +
      "avgifter). Den bleka toppen på de svenska staplarna är energiskatt som företaget slipper " +
      "genom nedsättningen. Etiketten läses \u201dbetalas \u2192 utan nedsättning\u201d: stapelns " +
      "fulla längd är priset utan nedsättning, den mättade delen är vad som faktiskt betalas.",
    "Öre/kWh excluding VAT. Swedish bars = spot + grid fee + energy tax. Chinese bars = the " +
      "province’s all-in tariff, split by NDRC’s national averages (60 % energy, 35 % grid, 5 % " +
      "levies). The pale cap on the Swedish bars is energy tax the business avoids through the " +
      "reduction. The label reads \u201cpaid \u2192 without the reduction\u201d: the full bar is the " +
      "price without it, the saturated part is what is actually paid."
  )

  val spreadH = T("Kina har inget rikspris", "China has no national price")
  val spreadSub = T(
    "Alla 32 provinser och nätområden, sorterade. Linjerna är de svenska elområdenas totalpris för " +
      "samma kundtyp, så man ser direkt var i den kinesiska fördelningen respektive elområde hamnar.",
    "All 32 provinces and grid areas, sorted. The lines are the Swedish bidding zones’ total price " +
      "for the same customer type, so you can read off where each zone lands in the Chinese " +
      "distribution."
  )

  val tsH = T("Samma jämförelse över tid", "The same comparison over time")
  val tsSub = T(
    "Hela historiken, till skillnad från de övriga vyerna – periodvalet ovan skuggar sitt fönster " +
      "här i stället för att klippa serien, eftersom det är rörelsen över åren som är poängen. " +
      "Månadsvis svenskt totalpris per elområde mot det kinesiska spannet. Det kinesiska bandet är " +
      "en ögonblicksbild från maj 2025 som hålls konstant – kinesiska tariffer revideras månadsvis " +
      "men publiceras inte som öppen tidsserie, så bandet visar nivå, inte rörelse.",
    "Full history, unlike the other views – the period selector above shades its window here " +
      "rather than clipping the series, because the movement across years is the point. " +
      "Monthly Swedish total price per bidding zone against the Chinese range. The Chinese band is a " +
      "May 2025 snapshot held flat – Chinese tariffs are revised monthly but are not published as " +
      "an open time series, so the band shows level, not movement."
  )

  val touH = T("Dygnsprofilen är Kinas andra vapen", "The daily profile is China’s other lever")
  val touSub = T(
    "Topp-, plan- och dalpris i tvådelstariffen på 35 kV. En kinesisk fabrik som kan lägga " +
      "produktionen i dalen betalar en bra bit under plansegmentet; en som kör dagtid i Guangdong " +
      "betalar mer än dubbelt så mycket som en som kör natt.",
    "Peak, flat and valley prices in the 35 kV two-part tariff. A Chinese plant that can shift " +
      "production into the valley pays well below the flat segment; one running daytime in " +
      "Guangdong pays more than twice what a night-shift plant pays."
  )

  val sEnergy = T("Spot / inmatningspris", "Spot / on-grid price")
  val sGrid = T("Nät & överföring", "Grid & transmission")
  val sTax = T("Energiskatt & avgifter", "Energy tax & levies")
  val sRebate = T("Skatt som faller bort", "Tax that falls away")
  val sPeak = T("Topp", "Peak")
  val sFlat = T("Plan", "Flat")
  val sValley = T("Dal", "Valley")
  val unit = T("öre/kWh", "öre/kWh")
  val china = T("Kina", "China")
  val cheapest = T("Kina billigast", "China cheapest")
  val median = T("Kina median", "China median")
  val dearest = T("Kina dyrast", "China dearest")
  val cnBand = T("Kina, billigaste–dyraste provins", "China, cheapest–dearest province")
  val cnMedian = T("Kina, medianprovins", "China, median province")
  val totalPaid = T("Summa att betala", "Total paid")
  val atNormalTax = T("Vid normalskatt", "At standard tax")

  val thArea = T("Område", "Area")
  val thEnergy = T("Spot/energi", "Spot/energy")
  val thGrid = T("Nät", "Grid")
  val thTax = T("Skatt & avgifter", "Tax & levies")
  val thTotal = T("Summa (öre/kWh)", "Total (öre/kWh)")
  val tableCaption = T("Totalt elpris per område", "Total electricity price per area")

  val noData = T(
    "Spotdatan för SE1–SE4 kunde inte hämtas vid senaste publiceringen. Diagrammen är därför " +
      "tomma. Källa: Energy-Charts (ENTSO-E).",
    "Spot data for SE1–SE4 could not be fetched at the last publish, so the charts are empty. " +
      "Source: Energy-Charts (ENTSO-E)."
  )

  val notes: Vector[(T, T)] = Vector(
    T("Nedsättningen är hela skillnaden", "The reduction is the whole difference") ->
      T(
        "El i tillverkningsprocessen i industriell verksamhet beskattas med 0,6 öre/kWh i stället " +
          "för 36,0. Företaget betalar först full skatt och ansöker sedan hos Skatteverket om " +
          "återbetalning av mellanskillnaden. Nedsättningen på 35,4 öre/kWh är i samma " +
          "storleksordning som hela det kinesiska prisspannet mellan billigaste och dyraste " +
          "provins, vilket är varför den avgör utfallet av jämförelsen snarare än nyanserar den.",
        "Electricity used in the manufacturing process of industrial activity is taxed at 0.6 " +
          "öre/kWh instead of 36.0. The business first pays the full tax and then applies to " +
          "Skatteverket for repayment of the difference. The 35.4 öre/kWh reduction is the same " +
          "order of magnitude as the entire Chinese spread between cheapest and dearest province, " +
          "which is why it decides the comparison rather than qualifying it."
      ),
    T("Vad som inte ingår", "What is not included") ->
      T(
        "Moms är utelämnad på båda sidor eftersom den är avdragsgill för företag. Den fasta " +
          "effektavgiften är också utelämnad: i Kina 16–51 CNY/kW/månad utöver tariffen, i " +
          "Sverige den abonnemangsdel av nätavgiften som inte fördelas per kWh. Båda gynnar hög " +
          "utnyttjandegrad, och båda gör bilden sämre för den som kör få timmar om året. " +
          "Elcertifikat är nära noll sedan kvotplikten fasas ut och räknas inte separat.",
        "VAT is left out on both sides since it is deductible for businesses. The fixed capacity " +
          "charge is also left out: in China 16–51 CNY/kW/month on top of the tariff, in Sweden " +
          "the subscription component of the grid fee that is not spread per kWh. Both reward " +
          "high utilisation, and both worsen the picture for anyone running few hours a year. " +
          "Electricity certificates are near zero as the quota obligation is phased out and are " +
          "not counted separately."
      ),
    T(
      "Nätavgiften är ett antagande, inte en mätning",
      "The grid fee is an assumption, not a measurement"
    ) ->
      T(
        "Svenska nätavgifter sätts per nätägare och kundkategori och publiceras inte som en " +
          "jämförbar öre/kWh-siffra. Reglaget finns därför för att sätta den mot den egna " +
          "fakturan. Utgångsvärdena går från en storindustri på region- eller stamnät, där " +
          "Svenska kraftnäts transmissionstariff ensam motsvarar ungefär 3 öre/kWh, upp till ett " +
          "mindre företag på lokalnät. Den kinesiska sidan behöver inget motsvarande reglage: där " +
          "ligger överföringen redan inne i tariffen.",
        "Swedish grid fees are set per network owner and customer category and are not published " +
          "as a comparable öre/kWh figure. The slider is there so you can set it against your own " +
          "invoice. The defaults run from a large industrial site on the regional or transmission " +
          "grid, where Svenska kraftnät’s transmission tariff alone is roughly 3 öre/kWh, up to a " +
          "small business on the local grid. The Chinese side needs no equivalent slider: there " +
          "transmission is already inside the tariff."
      ),
    T("Varför Kina inte kan ha ett pris", "Why China cannot have one price") ->
      T(
        "Sedan reformen 2021 köper alla industri- och handelskunder el via marknaden, men " +
          "tariffen sätts fortfarande provinsvis av nätbolagen inom NDRC:s ramar. Vattenrika " +
          "Yunnan och Sichuan och kolnära Xinjiang, Ningxia och Inre Mongoliet ligger lågt; " +
          "Hainan, Hunan, Tianjin och Pärlflodsdeltat ligger högt. Därtill delas dygnet i topp, " +
          "plan och dal i tjugo provinser, och i fyra eller fem nivåer i tolv till, så två " +
          "fabriker i samma provins kan betala olika pris för att de kör olika skift.",
        "Since the 2021 reform all industrial and commercial customers buy through the market, but " +
          "the tariff is still set province by province by the grid companies within NDRC’s " +
          "framework. Hydro-rich Yunnan and Sichuan and coal-adjacent Xinjiang, Ningxia and Inner " +
          "Mongolia sit low; Hainan, Hunan, Tianjin and the Pearl River Delta sit high. On top of " +
          "that the day is split into peak, flat and valley in twenty provinces, and into four or " +
          "five levels in twelve more, so two plants in the same province can pay different " +
          "prices because they run different shifts."
      ),
    T("Läs med förbehåll", "Read with care") ->
      T(
        "De kinesiska tarifferna är daterade maj 2025 och revideras månadsvis; de svenska " +
          "spotpriserna löper till senaste publicerade månad. Jämförelsen är därför inte tagen " +
          "vid samma tidpunkt, vilket spelar roll eftersom svensk spot rör sig långt mer mellan " +
          "månader än kinesiska tariffer gör. Uppdelningen av den kinesiska stapeln i energi, nät " +
          "och avgifter är ett rikssnitt applicerat på varje provins, inte provinsens egen " +
          "kostnadsstruktur.",
        "The Chinese tariffs are dated May 2025 and are revised monthly; the Swedish spot prices " +
          "run to the latest published month. The comparison is therefore not taken at one point " +
          "in time, which matters because Swedish spot moves far more between months than Chinese " +
          "tariffs do. The split of the Chinese bar into energy, grid and levies is a national " +
          "average applied to every province, not each province’s own cost structure."
      )
  )

  val src = T(
    "Svensk spot: Energy-Charts (ENTSO-E/SMARD, CC BY 4.0) via denna sajts euprices-uttag. " +
      "Energiskatt: Skatteverket, 2026 års satser. Kinesiska tariffer: nätbolagens agentköpspriser " +
      "(代理购电) sammanställda av China Briefing/Dezan Shira & Associates, maj 2025. " +
      "Växelkurser: ECB:s referenskurser " + Data.FxDate + ".",
    "Swedish spot: Energy-Charts (ENTSO-E/SMARD, CC BY 4.0) via this site’s euprices extract. " +
      "Energy tax: Skatteverket, 2026 rates. Chinese tariffs: grid companies’ agency purchase " +
      "prices (代理购电) compiled by China Briefing/Dezan Shira & Associates, May 2025. " +
      "Exchange rates: ECB reference rates " + Data.FxDate + "."
  )

  /**
   * Kinesiska primarkallor. Den svenska sidan gar att folja till ENTSO-E via de ovriga sidorna pa
   * sajten, men den kinesiska tariffdatan ar handmatad statisk text i Data.scala - da racker det
   * inte att namna varifran den kommer, den maste ga att klicka sig till och kontrollera.
   */
  final case class Kalla(label: T, url: String)

  val srcHeading = T("Kinesiska primärkällor", "Chinese primary sources")

  val srcLinks: Vector[Kalla] = Vector(
    Kalla(
      T(
        "Provinstariffer maj 2025 – tabellen sidans siffror är hämtade ur (China Briefing)",
        "Provincial tariffs May 2025 – the table this page’s figures come from (China Briefing)"
      ),
      "https://www.china-briefing.com/news/chinas-industrial-power-rates-category-electricity-usage-region-classification/"
    ),
    Kalla(
      T(
        "NDRC om tidsdifferentierade priser, 发改价格〔2021〕1093号 – topp/plan/dal-mekanismen",
        "NDRC on time-of-use pricing, NDRC Price [2021] No. 1093 – the peak/flat/valley mechanism"
      ),
      "https://www.gov.cn/zhengce/zhengceku/2021-07/29/content_5628297.htm"
    ),
    // Varden serverar varken HTTPS eller en icke-mobil variant under /mobile/;
    // desktop-URL:en nedan ar den enda som svarar 200, och den bara over http.
    Kalla(
      T(
        "Månadssammanställning av工商业-priser per provins, februari 2026 (光伏产业网)",
        "Monthly per-province commercial & industrial prices, February 2026 (Solaren PV)"
      ),
      "http://www.solarenpv.com/index.php?moduleid=24&itemid=2100"
    ),
    Kalla(
      T(
        "Månadssammanställning med störst topp/dal-spann, januari 2026 (CNESA)",
        "Monthly summary of the largest peak-valley spreads, January 2026 (CNESA)"
      ),
      "https://www.cnesa.org/information/detail/?column_id=3&id=7747"
    )
  )

  val srcNote = T(
    "De två månadssammanställningarna publicerar sina tabeller som bilder, inte som text, och " +
      "därför går de inte att läsa maskinellt. Det är skälet till att sidan bygger på China " +
      "Briefings avskrivna maj 2025-tabell i stället för innevarande månad. Kinesiska källor kan " +
      "dessutom vara långsamma eller oåtkomliga utanför Kina.",
    "The two monthly summaries publish their tables as images rather than text, so they cannot be " +
      "read programmatically. That is why this page builds on China Briefing’s transcribed May " +
      "2025 table rather than the current month. Chinese sources may also be slow or unreachable " +
      "outside China."
  )
