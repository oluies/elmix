package elmix.foretagspris

/**
 * Statisk referensdata for prisjamforelsen. Ingen hamtning - samma upplagg som lagring.js:
 * siffrorna ar publicerade tariffer och skattesatser, inte nagot som gar att fraga ett API om.
 */
object Data:

  // ECB:s referenskurser 2026-08-28. CNY/SEK harleds ur korset i stallet for att
  // hamtas separat, sa de tre kurserna alltid ar inbordes konsistenta.
  val FxDate = "2026-08-28"
  val EurSek = 11.0885
  val EurCny = 7.8251
  val CnySek: Double = EurSek / EurCny

  /**
   * Energiskatt pa el 2026, ore/kWh exkl. moms (Skatteverket). Normalskattesatsen sanktes
   * 2026-01-01 fran 43,9 till 36,0 ore. El i tillverkningsprocessen i industriell verksamhet
   * beskattas med 0,6 ore - EU:s miniminiva - via aterbetalning av mellanskillnaden.
   */
  val TaxNormal = 36.0
  val TaxIndustry = 0.6

  /**
   * Kinesisk provinstariff, elnatsbolagens agentkopspris (代理购电), maj 2025, CNY/kWh. Priset ar
   * allt-i-ett: inmatningspris + overforing + natforluster + statliga avgifter, exkl. moms och
   * exkl. den fasta effektavgiften.
   *
   * @param t35
   *   tvadelstariff 35 kV, plansegment (stor industri)
   * @param p35
   *   tvadelstariff 35 kV, toppsegment
   * @param v35
   *   tvadelstariff 35 kV, dalsegment
   * @param t10
   *   tvadelstariff 1-10 kV, plansegment (mellanstor industri)
   * @param s10
   *   endelstariff 1-10 kV, plansegment (mindre foretag)
   */
  final case class Province(
      sv: String,
      en: String,
      t35: Double,
      p35: Double,
      v35: Double,
      t10: Double,
      s10: Double
  ):
    def name(lang: String): String = if lang == "en" then en else sv

  val Provinces: Vector[Province] = Vector(
    Province("Ningxia", "Ningxia", 0.396, 0.574, 0.247, 0.411, 0.483),
    Province("Qinghai", "Qinghai", 0.399, 0.578, 0.215, 0.405, 0.502),
    Province("Xinjiang", "Xinjiang", 0.399, 0.549, 0.272, 0.410, 0.450),
    Province("Shanxi", "Shanxi", 0.486, 0.688, 0.301, 0.516, 0.538),
    Province("Yunnan", "Yunnan", 0.499, 0.656, 0.342, 0.524, 0.547),
    Province("Inre Mongoliet (öst)", "Inner Mongolia (East)", 0.507, 0.701, 0.358, 0.514, 0.701),
    Province("Shaanxi", "Shaanxi", 0.509, 0.787, 0.231, 0.529, 0.607),
    Province("Guangxi", "Guangxi", 0.539, 0.718, 0.359, 0.581, 0.689),
    Province("Sichuan", "Sichuan", 0.576, 0.859, 0.293, 0.605, 0.696),
    Province("Liaoning", "Liaoning", 0.598, 0.814, 0.382, 0.616, 0.722),
    Province("Zhejiang", "Zhejiang", 0.601, 0.902, 0.271, 0.632, 0.720),
    Province("Guangdong (öst/väst)", "Guangdong (East/West)", 0.602, 1.004, 0.246, 0.627, 0.701),
    Province("Shenzhen", "Shenzhen", 0.602, 1.004, 0.246, 0.627, 0.700),
    Province("Fujian", "Fujian", 0.605, 0.854, 0.334, 0.625, 0.659),
    Province("Jiangsu", "Jiangsu", 0.607, 1.044, 0.254, 0.632, 0.710),
    Province("Heilongjiang", "Heilongjiang", 0.609, 0.808, 0.409, 0.630, 0.767),
    Province("Hubei", "Hubei", 0.609, 0.823, 0.381, 0.628, 0.692),
    Province("Peking", "Beijing", 0.610, 0.847, 0.371, 0.650, 0.833),
    Province("Guizhou", "Guizhou", 0.614, 0.967, 0.261, 0.628, 0.706),
    Province("Shanghai", "Shanghai", 0.620, 0.946, 0.348, 0.651, 0.755),
    Province("Shandong", "Shandong", 0.628, 0.977, 0.279, 0.643, 0.701),
    Province("Hebei", "Hebei", 0.630, 0.920, 0.340, 0.650, 0.673),
    Province("Anhui", "Anhui", 0.634, 1.027, 0.306, 0.660, 0.678),
    Province("Jiangxi", "Jiangxi", 0.644, 0.933, 0.355, 0.659, 0.670),
    Province("Guangdong (Huizhou)", "Guangdong (Huizhou)", 0.661, 1.105, 0.268, 0.686, 0.760),
    Province("Henan", "Henan", 0.670, 1.067, 0.367, 0.693, 0.710),
    Province("Chongqing", "Chongqing", 0.673, 1.019, 0.316, 0.699, 0.758),
    Province("Guangdong (Jiangmen)", "Guangdong (Jiangmen)", 0.684, 1.143, 0.278, 0.709, 0.782),
    Province(
      "Guangdong (Pärlflodsdeltat)",
      "Guangdong (Pearl River Delta)",
      0.688,
      1.150,
      0.278,
      0.713,
      0.786
    ),
    Province("Hainan", "Hainan", 0.691, 1.090, 0.350, 0.745, 0.846),
    Province("Tianjin", "Tianjin", 0.695, 0.988, 0.378, 0.718, 0.800),
    Province("Hunan", "Hunan", 0.698, 1.089, 0.307, 0.728, 0.795)
  )

  /**
   * Kundprofil. Varje profil valjer BADE svensk nattariff och skattesats OCH motsvarande kinesisk
   * tariffkolumn - annars jamfors en svensk storindustri med en kinesisk kiosk. Natavgiften ar ett
   * antagande och gar att dra i; grid ar utgangsvardet, gridMax reglagets tak.
   */
  final case class Profile(
      key: String,
      sv: String,
      en: String,
      descSv: String,
      descEn: String,
      cnPrice: Province => Double,
      cnDescSv: String,
      cnDescEn: String,
      grid: Double,
      gridMax: Double,
      tax: Double
  ):
    def name(lang: String): String = if lang == "en" then en else sv
    def desc(lang: String): String = if lang == "en" then descEn else descSv
    def cnDesc(lang: String): String = if lang == "en" then cnDescEn else cnDescSv

  val Profiles: Vector[Profile] = Vector(
    Profile(
      "stor",
      "Stor industri",
      "Large industry",
      "tillverkningsprocess, region-/stamnät",
      "manufacturing process, regional/transmission grid",
      _.t35,
      "tvådelstariff 35 kV",
      "two-part tariff, 35 kV",
      grid = 8,
      gridMax = 40,
      tax = TaxIndustry
    ),
    Profile(
      "mellan",
      "Mellanstor industri",
      "Mid-size industry",
      "tillverkningsprocess, lokalnät",
      "manufacturing process, local grid",
      _.t10,
      "tvådelstariff 1–10 kV",
      "two-part tariff, 1–10 kV",
      grid = 20,
      gridMax = 60,
      tax = TaxIndustry
    ),
    Profile(
      "mindre",
      "Mindre företag / tjänst",
      "Small business / services",
      "ingen skattenedsättning, lokalnät",
      "no tax reduction, local grid",
      _.s10,
      "endelstariff 1–10 kV",
      "single-part tariff, 1–10 kV",
      grid = 40,
      gridMax = 90,
      tax = TaxNormal
    )
  )

  /**
   * Kinas tariff ar allt-i-ett. NDRC:s egen uppdelning av totalkostnaden ar ~60 % inmatningspris,
   * ~30 % overforing, ~5 % natforluster/systemdrift och ~5 % statliga avgifter. Andelarna ar
   * rikssnitt, inte per provins, sa de anvands bara for att gora stapeln visuellt jamforbar med den
   * svenska - natdelen bar har aven forlusterna.
   */
  val CnShareEnergy = 0.60
  val CnShareGrid = 0.35
  val CnShareLevy = 0.05

  val Zones: Vector[String] = Vector("SE1", "SE2", "SE3", "SE4")

  val ZoneColor: Map[String, String] =
    Map("SE1" -> "#2e6fd6", "SE2" -> "#4dc4d4", "SE3" -> "#e08a3c", "SE4" -> "#c0392b")

  val ColEnergy = "#4477aa"
  val ColGrid = "#88a4c4"
  val ColTax = "#c0392b"
  val ColRebate = "#e8b4ae"
