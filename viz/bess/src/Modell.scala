package elmix.bess

/**
 * Batterimodellen. Allt per megawatt ansluten effekt, per ar, i euro.
 *
 * Ren och fri fran DOM och ECharts, sa siffrorna gar att folja utan att lasa nagon rendering. H ar
 * varaktighet i timmar, sa ett 1 MW / 4 MWh-batteri har H = 4.
 *
 * Poangen med hela sidan sitter i tva egenskaper hos de har formlerna:
 *
 *   - `arbitrage` ar proportionell mot levererad energi, som ar proportionell mot H - men `pHog -
 *     pLag/eta` KRYMPER nar H vaxer, eftersom den femte billigaste timmen ar dyrare an den forsta
 *     och den femte dyraste ar billigare an den forsta. Produkten ar hela historien.
 *   - `kapacitet` innehaller inte H alls. `arskostnad` gor det. Pa reservmarknaderna ar varaktighet
 *     alltsa ren kostnad.
 */
object Modell:

  /** Kapitaltjanstfaktor: andel av kapitalet som maste betalas varje ar. */
  def crf(wacc: Double, livslangd: Int): Double =
    val q = math.pow(1 + wacc, livslangd)
    wacc * q / (q - 1)

  /** Arskostnad for agandet, EUR/MW/ar. Vaxer linjart med H - batteriet ar celler. */
  def arskostnad(
      capexEurPerKWh: Double,
      H: Double,
      wacc: Double,
      livslangd: Int,
      opexAndel: Double
  ): Double =
    capexEurPerKWh * 1000 * H * (crf(wacc, livslangd) + opexAndel)

  /** Levererad energi, MWh per MW och ar. */
  def levereradMwh(cykler: Double, dod: Double, eta: Double, H: Double): Double =
    cykler * dod * eta * H

  /**
   * Arbitrage netto laddningskostnad, EUR/MW/ar. Laddningen delas med eta eftersom batteriet maste
   * kopa mer energi an det saljer; tappar man den divisionen ser de flacka norra zonerna battre ut
   * an de ar.
   */
  def arbitrage(pHog: Double, pLag: Double, eta: Double, mwh: Double): Double =
    (pHog - pLag / eta) * mwh

  /** Kapacitetsintakt, EUR/MW/ar. Ingen laddningskostnad, och ingen H. */
  def kapacitet(prisEurPerMwH: Double, tillganglighet: Double): Double =
    prisEurPerMwH * tillganglighet * 8760

  /** Det capex dar intakten precis tacker arskostnaden, EUR/kWh. */
  def breakEvenCapex(
      intakt: Double,
      H: Double,
      wacc: Double,
      livslangd: Int,
      opexAndel: Double
  ): Double =
    val n = 1000 * H * (crf(wacc, livslangd) + opexAndel)
    if n <= 0 then 0.0 else intakt / n

  /**
   * Enkel, odiskonterad aterbetalningstid i ar: kapital delat med arligt overskott efter drift.
   *
   * None nar overskottet ar noll eller negativt, OCH nar aterbetalningen skulle ta langre tid an
   * tillgangen lever - ett tal som "30 ar" bredvid ett 15-arsreglage laser som en lang men
   * barkraftig investering, inte som "aldrig", vilket ar vad det betyder.
   *
   * Tar medvetet inte wacc: det har ar den enkla aterbetalningen, inte den diskonterade. Vill man
   * ha den senare ar det en annan funktion, inte en extra parameter som tyst ignoreras.
   */
  def payback(
      intakt: Double,
      capexEurPerKWh: Double,
      H: Double,
      opexAndel: Double,
      livslangd: Int
  ): Option[Double] =
    val kapital = capexEurPerKWh * 1000 * H
    val netto = intakt - kapital * opexAndel
    if netto <= 0 then None
    else
      val ar = kapital / netto
      if ar > livslangd then None else Some(ar)

  /** Den spread som skulle kravas for att tacka arskostnaden, EUR/MWh. */
  def kravdSpread(arskostnad: Double, mwh: Double): Option[Double] =
    if mwh <= 0 then None else Some(arskostnad / mwh)
