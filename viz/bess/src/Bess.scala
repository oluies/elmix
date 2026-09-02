package elmix.bess

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as obj

import elmix.echarts.{ECharts, EChartsInstance}

/**
 * Betalar batteriet sig? Per elomrade, mot varaktighet.
 *
 * Payloaden (data/bess-data.js, skriven av viz/bess_agg.py) bar bara det som ar dyrt att rakna:
 * dygnsvisa prisspreadar per zon, ar och varaktighet. Allt som reglagen ror - capex, WACC,
 * livslangd, cykler, verkningsgrad, tillganglighet - raknas i webblasaren, sa sidan svarar direkt
 * pa varje drag.
 *
 * Sjalva modellen ligger i Modell.scala, fri fran DOM.
 */
object Bess:

  import Texts.T

  private val Zoner = Vector("SE1", "SE2", "SE3", "SE4")
  private val ZonFarg =
    Map(
      "SE1" -> "#2e6fd6",
      "SE2" -> "#4dc4d4",
      "SE3" -> "#e08a3c",
      "SE4" -> "#c0392b",
      "DE-LU" -> "#7a4fb5"
    )
  private val Varaktigheter = (1 to 8).toVector

  // BloombergNEF:s globala nyckelfardiga 4 h-niva omraknad till euro, och den uppgivna svenska.
  private val CapexGlobal = 98.0
  private val CapexSverige = 172.0

  // ------------------------------------------------------------------ tillstand
  private val lang = Var("sv")
  private val zon = Var("SE3")
  private val ar = Var(0)
  private val median = Var(false)
  private val H = Var(4.0)
  private val capex = Var(CapexSverige)
  private val wacc = Var(7.0)
  private val livslangd = Var(15.0)
  private val opex = Var(2.0)
  private val cykler = Var(365.0)
  private val eta = Var(88.0)
  private val dod = Var(90.0)
  private val tillgFcr = Var(90.0)
  private val tillgAfrr = Var(40.0)

  // ------------------------------------------------------------------ payload
  private lazy val data: Option[js.Dynamic] = Globals.bessData.toOption

  private def falt(o: js.Dynamic, k: String): Option[js.Dynamic] =
    val v = o.selectDynamic(k)
    if js.isUndefined(v) || v == null then None else Some(v)

  private lazy val zoner: Vector[String] =
    data
      .flatMap(falt(_, "zones"))
      .map(_.asInstanceOf[js.Array[String]].toVector)
      .getOrElse(Vector.empty)

  /**
   * Referenszoner ritas men gar inte att valja. Reservpriserna i payloaden ar Svenska kraftnats -
   * en valbar DE-LU skulle rita svenska FCR-priser bredvid tyskt arbitrage, vilket ar precis det
   * fel sidans egen not varnar for. Referensen hor darfor hemma i varaktighets- och
   * break-even-vyerna, som bara handlar om spot.
   */
  private lazy val referens: Vector[String] =
    data
      .flatMap(falt(_, "reference"))
      .map(_.asInstanceOf[js.Array[String]].toVector)
      .getOrElse(Vector.empty)

  /** Zoner som ritas: valbara plus referens. */
  private lazy val ritade: Vector[String] = zoner ++ referens

  private lazy val aren: Vector[Int] =
    data
      .flatMap(falt(_, "years"))
      .map(_.asInstanceOf[js.Array[Int]].toVector)
      .getOrElse(Vector.empty)

  private lazy val harData: Boolean = zoner.nonEmpty && aren.nonEmpty

  private lazy val uppdaterad: String =
    data.flatMap(falt(_, "updated")).map(_.asInstanceOf[String]).getOrElse("")

  /** Dygnsspread for zon/ar/varaktighet, (hog, lag). Foljer median-reglaget. */
  private def spread(z: String, y: Int, h: Int): Option[(Double, Double)] =
    for
      d <- data
      s <- falt(d, "spread")
      zz <- falt(s, z)
      yy <- falt(zz, y.toString)
      hh <- falt(yy, h.toString)
    yield
      val (kh, kl) = if median.now() then ("hiMed", "loMed") else ("hi", "lo")
      (hh.selectDynamic(kh).asInstanceOf[Double], hh.selectDynamic(kl).asInstanceOf[Double])

  private lazy val produkter: Vector[js.Dynamic] =
    data
      .flatMap(falt(_, "reserves"))
      .flatMap(falt(_, "products"))
      .map(_.asInstanceOf[js.Array[js.Dynamic]].toVector)
      .getOrElse(Vector.empty)

  /** Snittpris for en reservprodukt i en zon, EUR/MW/h. None = inget publicerat pris. */
  private def reservpris(p: js.Dynamic, z: String): Option[Double] =
    val obs =
      if p.basis.asInstanceOf[String] == "national" then falt(p, "observations")
      else falt(p, "byZone").flatMap(falt(_, z))
    obs
      .map(_.asInstanceOf[js.Array[js.Dynamic]].toVector)
      .filter(_.nonEmpty)
      .map(v => v.map(_.price.asInstanceOf[Double]).sum / v.size)

  /** Perioderna en reservprodukts observationer kommer fran, t.ex. "2025-03-10". */
  private def reservperiod(p: js.Dynamic): String =
    val obs =
      if p.basis.asInstanceOf[String] == "national" then falt(p, "observations")
      else falt(p, "byZone").flatMap(falt(_, zon.now()))
    val perioder = obs
      .map(_.asInstanceOf[js.Array[js.Dynamic]].toVector)
      .getOrElse(Vector.empty)
      .map(_.period.asInstanceOf[String])
      .distinct
      .sorted
    if perioder.isEmpty then "?"
    else if perioder.size == 1 then perioder.head
    else s"${perioder.head}\u2013${perioder.last}"

  // ------------------------------------------------------------------ harledda tal
  private def h: Double = H.now()
  private def mwh: Double = Modell.levereradMwh(cykler.now(), dod.now() / 100, eta.now() / 100, h)
  private def kostnad: Double =
    Modell.arskostnad(capex.now(), h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)

  private def arbitrageFor(z: String, hh: Int): Option[Double] =
    spread(z, ar.now(), hh).map { (hi, lo) =>
      Modell.arbitrage(
        hi,
        lo,
        eta.now() / 100,
        Modell.levereradMwh(cykler.now(), dod.now() / 100, eta.now() / 100, hh.toDouble)
      )
    }

  private def arbitrageNu(z: String): Option[Double] = arbitrageFor(z, math.round(h).toInt)

  // ------------------------------------------------------------------ formatering
  private def fmt(v: Double, d: Int): String =
    v.asInstanceOf[js.Dynamic]
      .toLocaleString(
        if lang.now() == "en" then "en" else "sv",
        obj(minimumFractionDigits = d, maximumFractionDigits = d)
      )
      .asInstanceOf[String]

  private def t(x: T): String = x(lang.now())
  private def k(v: Double): Double = math.round(v / 100.0) / 10.0 // EUR -> k€, en decimal

  // ------------------------------------------------------------------ diagram 1
  private def intaktOption(): js.Any =
    val z = zon.now()
    val arb = arbitrageNu(z).getOrElse(0.0)
    // Produkter UTAN pris for zonen ritas som en namngiven nollstapel i stallet
    // for att forsvinna: att aFRR saknas i SE1 och SE2 ar ett resultat, inte
    // ett skal att dolja marknaden.
    val utanPris = produkter.filter(p => reservpris(p, z).isEmpty).map { p =>
      val namn = (if lang.now() == "en" then p.en else p.sv).asInstanceOf[String]
      (s"$namn\n(${t(Texts.noZonePrice)})", 0.0)
    }
    val kap = produkter.flatMap { p =>
      reservpris(p, z).map { pris =>
        val tg =
          if p.id.asInstanceOf[String].startsWith("afrr") then tillgAfrr.now() else tillgFcr.now()
        val namn = (if lang.now() == "en" then p.en else p.sv).asInstanceOf[String]
        val bas =
          if p.basis.asInstanceOf[String] == "national" then t(Texts.national)
          else t(Texts.zonal)
        // Perioden star pa stapeln: reservpriserna ar handmatade och rors inte av
        // arsvaljaren, sa utan den star ett 2021-arbitrage bredvid ett 2025-reservpris
        // i samma diagram utan att nagot sager att halva bilden inte flyttade sig.
        (s"$namn\n($bas, ${reservperiod(p)})", Modell.kapacitet(pris, tg / 100))
      }
    }
    val kat = (t(Texts.arbitrage) + s"\n(${z})") +: (kap ++ utanPris).map(_._1)
    val varden = k(arb) +: (kap ++ utanPris).map(x => k(x._2))
    val farger = ZonFarg(z) +: (kap ++ utanPris).map(_ => "#8a8a85")
    val kost = k(kostnad)
    val bandA = k(
      Modell.arskostnad(CapexGlobal, h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)
    )
    val bandB = k(
      Modell.arskostnad(CapexSverige, h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)
    )
    val kostBandLag = math.min(bandA, bandB)
    val kostBandHog = math.max(bandA, bandB)

    obj(
      grid = obj(top = 42, bottom = 76, left = 62, right = 24),
      tooltip = obj(
        trigger = "item",
        valueFormatter = (
            (v: js.Any) => fmt(v.asInstanceOf[Double], 1) + " " + t(Texts.kEurMw)
        ): js.Function1[js.Any, String]
      ),
      xAxis = obj(
        `type` = "category",
        data = js.Array(kat.map(x => x: js.Any)*),
        axisLabel = obj(fontSize = 10, interval = 0, lineHeight = 13)
      ),
      yAxis = obj(`type` = "value", name = t(Texts.kEurMw)),
      series = js.Array(
        obj(
          `type` = "bar",
          data = js.Array(varden.zip(farger).map { (v, f) =>
            obj(value = v, itemStyle = obj(color = f)): js.Any
          }*),
          label = obj(
            show = true,
            position = "top",
            fontSize = 11,
            formatter = ((p: js.Dynamic) => fmt(p.value.asInstanceOf[Double], 0)): js.Function1[
              js.Dynamic,
              String
            ]
          ),
          markLine = obj(
            symbol = "none",
            silent = true,
            lineStyle = obj(color = "#161d1b", width = 1.6, `type` = "dashed"),
            label = obj(
              formatter = s"${t(Texts.costLine)} ${fmt(kost, 0)}",
              fontSize = 11,
              position = "insideEndTop"
            ),
            data = js.Array(obj(yAxis = kost): js.Any)
          ),
          // Bandet spanner de tva capex-nivaerna, inte bara den valda: det ar
          // spannet mellan BNEF:s globala nyckelfardiga niva och den uppgivna
          // svenska som sager om en stapel raknas som betald eller inte.
          // Grupperna markeras dessutom pa x-axeln - uppdelningen i "betalt for
          // energi" och "betalt for effekt" ar sjalva argumentet och far inte
          // bara sta i brodtexten.
          markArea = obj(
            silent = true,
            data = js
              .Array(
                js.Array[js.Any](
                  obj(
                    yAxis = kostBandLag,
                    itemStyle = obj(color = "#8a8a85", opacity = 0.16),
                    label = obj(
                      show = true,
                      position = "insideTopLeft",
                      fontSize = 10,
                      color = "#5a5a55",
                      formatter = t(Texts.costBand)
                    )
                  ),
                  obj(yAxis = kostBandHog)
                ),
                js.Array[js.Any](
                  obj(
                    xAxis = kat.head,
                    itemStyle = obj(color = "#4477aa", opacity = 0.06),
                    label = obj(
                      show = true,
                      position = "insideTop",
                      fontSize = 10,
                      color = "#4477aa",
                      formatter = t(Texts.energyGroup)
                    )
                  ),
                  obj(xAxis = kat.head)
                )
              )
              .concat(
                if kap.isEmpty then js.Array[js.Any]()
                else
                  js.Array(
                    js.Array[js.Any](
                      obj(
                        xAxis = kat(1),
                        itemStyle = obj(color = "#8a8a85", opacity = 0.06),
                        label = obj(
                          show = true,
                          position = "insideTop",
                          fontSize = 10,
                          color = "#6a6a65",
                          formatter = t(Texts.powerGroup)
                        )
                      ),
                      obj(xAxis = kat.last)
                    ): js.Any
                  )
              )
          )
        )
      )
    )

  // ------------------------------------------------------------------ diagram 2
  private def varaktighetOption(): js.Any =
    val kostLinje = Varaktigheter.map { hh =>
      k(
        Modell.arskostnad(
          capex.now(),
          hh.toDouble,
          wacc.now() / 100,
          livslangd.now().toInt,
          opex.now() / 100
        )
      )
    }
    val serier = ritade.map { z =>
      obj(
        name = z,
        `type` = "line",
        symbol = "circle",
        symbolSize = 5,
        // Referensen streckas: den ar med for jamforelsens skull, inte som en
        // femte svensk zon, och ska inte lasa som en av dem.
        lineStyle = obj(
          color = ZonFarg(z),
          width = 1.8,
          `type` = if referens.contains(z) then "dashed" else "solid"
        ),
        itemStyle = obj(color = ZonFarg(z)),
        data = js.Array(
          Varaktigheter.map(hh => arbitrageFor(z, hh).map(a => k(a): js.Any).getOrElse(null))*
        )
      ): js.Any
    }
    obj(
      grid = obj(top = 40, bottom = 58, left = 62, right = 24),
      legend = obj(bottom = 4, left = "center", itemGap = 12, textStyle = obj(fontSize = 11)),
      tooltip = obj(
        trigger = "axis",
        valueFormatter = (
            (v: js.Any) =>
              if v == null then "–" else fmt(v.asInstanceOf[Double], 1) + " " + t(Texts.kEurMw)
        ): js.Function1[js.Any, String]
      ),
      xAxis = obj(
        `type` = "category",
        name = t(Texts.hours),
        data = js.Array(Varaktigheter.map(x => x.toString: js.Any)*)
      ),
      yAxis = obj(`type` = "value", name = t(Texts.kEurMw)),
      series = js
        .Array(serier*)
        .concat(
          js.Array(
            obj(
              name = t(Texts.costLine),
              `type` = "line",
              symbol = "none",
              lineStyle = obj(color = "#161d1b", width = 2, `type` = "dashed"),
              // Legendsymbolen tar sin farg fran itemStyle, inte lineStyle - utan
              // den blir kostnadslinjen svart i diagrammet men bla i legenden.
              itemStyle = obj(color = "#161d1b"),
              data = js.Array(kostLinje.map(x => x: js.Any)*)
            ): js.Any
          )
        )
    )

  // ------------------------------------------------------------------ diagram 3
  private def breakEvenOption(): js.Any =
    val rader = ritade.map { z =>
      (
        z,
        arbitrageNu(z).map(a =>
          Modell.breakEvenCapex(a, h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)
        )
      )
    }
    obj(
      grid = obj(top = 34, bottom = 40, left = 62, right = 60),
      tooltip = obj(
        trigger = "item",
        valueFormatter = (
            (v: js.Any) => fmt(v.asInstanceOf[Double], 0) + " " + t(Texts.eurKwh)
        ): js.Function1[js.Any, String]
      ),
      xAxis = obj(`type` = "value", name = t(Texts.eurKwh)),
      yAxis =
        obj(`type` = "category", inverse = true, data = js.Array(rader.map(x => x._1: js.Any)*)),
      series = js.Array(
        obj(
          `type` = "bar",
          data = js.Array(rader.map { (z, v) =>
            v.map(x =>
              obj(value = math.round(x).toDouble, itemStyle = obj(color = ZonFarg(z))): js.Any
            ).getOrElse(null)
          }*),
          label = obj(
            show = true,
            position = "right",
            fontSize = 11,
            formatter = ((p: js.Dynamic) => fmt(p.value.asInstanceOf[Double], 0)): js.Function1[
              js.Dynamic,
              String
            ]
          ),
          markLine = obj(
            symbol = "none",
            silent = true,
            lineStyle = obj(color = "#8a8a85", width = 1.2, `type` = "dashed"),
            label = obj(fontSize = 10, color = "#8a8a85"),
            data = js.Array(
              obj(name = "BNEF", xAxis = CapexGlobal): js.Any,
              obj(name = "SE", xAxis = CapexSverige): js.Any
            )
          )
        )
      )
    )

  /**
   * Varaktigheten dar arbitraget slutar tacka arskostnaden, linjart interpolerad mellan hela
   * timmar. None nar linjerna aldrig korsas inom 1-8 h - antingen for att arbitraget ligger under
   * kostnaden redan vid en timme, eller for att det ligger over hela vagen.
   */
  private def korsning(z: String): Option[Double] =
    def kost(hh: Double) =
      Modell.arskostnad(capex.now(), hh, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)
    val diff = Varaktigheter.map(hh => arbitrageFor(z, hh).map(_ - kost(hh.toDouble)))
    if diff.exists(_.isEmpty) then None
    else
      val d = diff.map(_.get)
      // Forsta bytet fran plus till minus. Ett arbitrage som aldrig ar positivt
      // korsar inte - det ligger under hela vagen och har ingen brytpunkt.
      val i = (1 until d.length).find(j => d(j - 1) > 0 && d(j) <= 0)
      i.map { j =>
        val (a, b) = (d(j - 1), d(j))
      Varaktigheter(j - 1) + a / (a - b)
      }

  private def korsningsText(): String =
    val delar = ritade.map { z =>
      // Tre skilda lagen som alla gav None forut: ingen data alls for zonen och
      // aret, arbitrage over kostnaden hela vagen, och arbitrage under redan vid
      // en timme. Utan atskillnaden pastod texten "redan under vid 1 h" om en zon
      // sidan inte har nagon data for - medan diagrammet korrekt ritade null.
      if !harAllaVaraktigheter(z) then s"$z ${t(Texts.noCrossData)}"
      else
        korsning(z) match
          case Some(v) => s"$z ${fmt(v, 1)} h"
          case None =>
            val over = arbitrageNu(z).exists(_ > kostnad)
            s"$z " + (if over then t(Texts.neverWithin8h) else t(Texts.belowAt1h))
    }
    t(Texts.crossPrefix) + delar.mkString(" · ") + "."

  /** Har zonen en spread for varje varaktighet 1-8 det valda aret? */
  private def harAllaVaraktigheter(z: String): Boolean =
    Varaktigheter.forall(hh => spread(z, ar.now(), hh).isDefined)

  // ------------------------------------------------------------------ nyckeltal
  private def statRad(): String =
    val z = zon.now()
    val l = lang.now()
    arbitrageNu(z).fold("") { arb =>
      val be =
        Modell.breakEvenCapex(arb, h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100)
      val pb =
        Modell.payback(arb, capex.now(), h, opex.now() / 100, livslangd.now().toInt)
      val kravd = Modell.kravdSpread(kostnad, mwh)
      val faktisk = spread(z, ar.now(), math.round(h).toInt)
        .map((hi, lo) => hi - lo / (eta.now() / 100))
      def ruta(namn: String, varde: String) =
        s"""<div class="stat"><span class="k">$namn</span><span class="v">$varde</span></div>"""
      ruta(t(Texts.statBreakEven), fmt(be, 0) + " " + t(Texts.eurKwh)) +
        ruta(t(Texts.statPayback), pb.fold(t(Texts.never))(v => fmt(v, 1) + " " + t(Texts.years))) +
        ruta(t(Texts.statNeeded), kravd.fold("–")(v => fmt(v, 0)) + " €/MWh") +
        ruta(t(Texts.statSpread), faktisk.fold("–")(v => fmt(v, 0)) + " €/MWh")
    }

  // ------------------------------------------------------------------ URL
  /** Senast skrivna query-strang, sa identiska skrivningar hoppas over. */
  private var sistaUrl = ""

  private def skrivUrl(): Unit =
    val q = Seq(
      "zon" -> zon.now(),
      "ar" -> ar.now().toString,
      "h" -> fmt0(h),
      "capex" -> fmt0(capex.now()),
      "wacc" -> fmt0(wacc.now()),
      "liv" -> fmt0(livslangd.now()),
      "opex" -> fmt0(opex.now()),
      "cykler" -> fmt0(cykler.now()),
      "eta" -> fmt0(eta.now()),
      "dod" -> fmt0(dod.now()),
      "fcr" -> fmt0(tillgFcr.now()),
      "afrr" -> fmt0(tillgAfrr.now()),
      "median" -> (if median.now() then "1" else "0")
    ).map((a, b) => s"$a=$b").mkString("&")
    // Safari kastar SecurityError over 100 replaceState per 30 s, och ett drag i
    // ett reglage ger langt fler input-handelser an sa. Skriv darfor bara nar
    // stangen faktiskt andrats, och lat aldrig ett undantag har riva omritningen.
    if q != sistaUrl then
      sistaUrl = q
      try dom.window.history.replaceState(js.undefined, "", "?" + q)
      catch case _: Throwable => ()

  private def fmt0(v: Double): String =
    if v == math.floor(v) then math.round(v).toString else v.toString

  private def lasUrl(): Unit =
    val p = new dom.URLSearchParams(dom.window.location.search)
    def num(k: String, f: Double => Unit): Unit =
      Option(p.get(k)).flatMap(_.toDoubleOption).foreach(f)
    Option(p.get("zon")).filter(zoner.contains).foreach(zon.set)
    num("ar", v => if aren.contains(v.toInt) then ar.set(v.toInt))
    // Snappa till reglagets heltalssteg: spreaden slas upp pa avrundat h medan
    // kostnaden anvander det rada, sa ?h=3.7 hade gett tre olika varden for
    // samma parameter utan nagot pa skarmen som avslojade det.
    num("h", v => H.set(math.round(v).toDouble.max(1).min(8)))
    num("capex", v => capex.set(v.max(60).min(260)))
    num("wacc", v => wacc.set(v.max(3).min(12)))
    num("liv", v => livslangd.set(v.max(8).min(25)))
    num("opex", v => opex.set(v.max(0).min(5)))
    num("cykler", v => cykler.set(v.max(100).min(700)))
    num("eta", v => eta.set(v.max(50).min(100)))
    num("dod", v => dod.set(v.max(50).min(100)))
    num("fcr", v => tillgFcr.set(v.max(0).min(100)))
    num("afrr", v => tillgAfrr.set(v.max(0).min(100)))
    Option(p.get("median")).foreach(v => median.set(v == "1"))

  // ------------------------------------------------------------------ kontroller
  private def seg[A](items: Vector[A], v: Var[A], etikett: A => String): HtmlElement =
    span(
      cls := "segswitch",
      items.map { it =>
        button(
          tpe := "button",
          etikett(it),
          cls("active") <-- v.signal.map(_ == it),
          aria.pressed <-- v.signal.map(cur => (cur == it).toString),
          onClick --> { _ => v.set(it) }
        )
      }
    )

  private def reglage(
      namn: T,
      v: Var[Double],
      lo: Double,
      hi: Double,
      steg: Double,
      enhet: String,
      dec: Int = 0
  ): HtmlElement =
    label(
      cls := "slider",
      span(cls := "sn", child.text <-- lang.signal.map(namn.apply)),
      input(
        tpe := "range",
        minAttr := lo.toString,
        maxAttr := hi.toString,
        stepAttr := steg.toString,
        controlled(
          value <-- v.signal.map(_.toString),
          onInput.mapToValue.map(_.toDoubleOption.getOrElse(lo)) --> v
        )
      ),
      span(
        cls := "sv2",
        child.text <-- v.signal.combineWith(lang.signal).map((x, _) => fmt(x, dec) + " " + enhet)
      )
    )

  private def kontroller(): HtmlElement =
    div(
      cls := "controls",
      fieldSet(
        legend(child.text <-- lang.signal.map(Texts.zoneLegend.apply)),
        seg(zoner, zon, identity)
      ),
      fieldSet(
        legend(child.text <-- lang.signal.map(Texts.yearLegend.apply)),
        seg(aren, ar, (y: Int) => y.toString)
      ),
      fieldSet(
        legend(child.text <-- lang.signal.map(Texts.dayLegend.apply)),
        seg(
          Vector(false, true),
          median,
          (b: Boolean) => if b then t(Texts.dayMedian) else t(Texts.dayMean)
        )
      ),
      fieldSet(
        cls := "wide",
        legend(child.text <-- lang.signal.map(Texts.sysLegend.apply)),
        reglage(Texts.hLabel, H, 1, 8, 1, "h"),
        reglage(Texts.cyclesLabel, cykler, 100, 700, 5, ""),
        reglage(Texts.etaLabel, eta, 50, 100, 1, "%"),
        reglage(Texts.dodLabel, dod, 50, 100, 1, "%")
      ),
      fieldSet(
        cls := "wide",
        legend(child.text <-- lang.signal.map(Texts.ekoLegend.apply)),
        reglage(Texts.capexLabel, capex, 60, 260, 1, "€/kWh"),
        reglage(Texts.waccLabel, wacc, 3, 12, 0.5, "%", 1),
        reglage(Texts.lifeLabel, livslangd, 8, 25, 1, "år"),
        reglage(Texts.opexLabel, opex, 0, 5, 0.5, "%", 1)
      ),
      fieldSet(
        cls := "wide",
        legend(child.text <-- lang.signal.map(Texts.markLegend.apply)),
        reglage(Texts.fcrLabel, tillgFcr, 0, 100, 5, "%"),
        reglage(Texts.afrrLabel, tillgAfrr, 0, 100, 5, "%")
      )
    )

  private def langSwitch(): HtmlElement =
    div(
      display := "contents",
      Vector("sv", "en").map { l =>
        button(
          tpe := "button",
          l.toUpperCase,
          cls("active") <-- lang.signal.map(_ == l),
          aria.pressed <-- lang.signal.map(cur => (cur == l).toString),
          onClick --> { _ =>
            lang.set(l); dom.document.documentElement.setAttribute("data-lang", l)
          }
        )
      }
    )

  /** Sparr mot de fjorton startskotten nar signalerna kopplas upp. */
  private var igang = false

  // ------------------------------------------------------------------ rendering
  private def satt(id: String, s: String): Unit =
    val el = dom.document.getElementById(id)
    if el != null then el.textContent = s

  private def sattAria(id: String, s: String): Unit =
    val el = dom.document.getElementById(id)
    if el != null then el.setAttribute("aria-label", s)

  private def html(id: String, s: String): Unit =
    val el = dom.document.getElementById(id)
    if el != null then el.innerHTML = s

  private lazy val diagramIds = Vector("rev", "dur", "be")
  private lazy val diagram: Map[String, EChartsInstance] =
    diagramIds.flatMap { id =>
      val el = dom.document.getElementById(id)
      if el == null then None else Some(id -> ECharts.init(el))
    }.toMap

  private def statiskText(): Unit =
    val l = lang.now()
    dom.document.title = "Elmix – " + Texts.title(l)
    satt("bess-title", Texts.title(l))
    satt("rev-h", Texts.revH(l)); satt("rev-sub", Texts.revSub(l))
    satt("dur-h", Texts.durH(l)); satt("dur-sub", Texts.durSub(l))
    satt("be-h", Texts.beH(l)); satt("be-sub", Texts.beSub(l))
    sattAria("rev", Texts.ariaRev(l))
    sattAria("dur", Texts.ariaDur(l))
    sattAria("be", Texts.ariaBe(l))
    html("notes", Texts.notes.map((h2, b) => s"<h3>${h2(l)}</h3><p>${b(l)}</p>").mkString)
    satt("src", Texts.src(l) + (if uppdaterad.isEmpty then "" else s" · ${uppdaterad}"))

  private def rita(): Unit =
    statiskText()
    satt("bess-lead", Texts.lead(lang.now()))
    html("stats", statRad())
    satt("dur-cross", if harData then korsningsText() else "")
    diagram.get("rev").foreach(_.setOption(intaktOption(), true))
    diagram.get("dur").foreach(_.setOption(varaktighetOption(), true))
    diagram.get("be").foreach(_.setOption(breakEvenOption(), true))
    tabell()
    skrivUrl()

  private def tabell(): Unit =
    val l = lang.now()
    val huvud =
      Vector(Texts.thZone, Texts.thArbitrage, Texts.thBreakEven, Texts.thAnnualCost).map(_(l))
    val rader = ritade.map { z =>
      val a = arbitrageNu(z)
      Vector(
        z,
        a.fold("–")(x => fmt(k(x), 1)),
        a.fold("–")(x =>
          fmt(
            Modell.breakEvenCapex(x, h, wacc.now() / 100, livslangd.now().toInt, opex.now() / 100),
            0
          )
        ),
        fmt(k(kostnad), 1)
      )
    }
    html(
      "chart-data",
      s"<table><caption>${Texts.title(l)}</caption>" +
        huvud.map(x => s"<th>$x</th>").mkString("<tr>", "", "</tr>") +
        rader.map(r => r.map(c => s"<td>$c</td>").mkString("<tr>", "", "</tr>")).mkString +
        "</table>"
    )
    val z = zon.now()
    satt(
      "chart-status",
      arbitrageNu(z).fold("") { a =>
        if l == "en" then
          s"$z arbitrage ${fmt(k(a), 0)} k€/MW/yr against annual cost ${fmt(k(kostnad), 0)} at ${fmt(h, 0)} h."
        else
          s"$z arbitrage ${fmt(k(a), 0)} k€/MW/år mot årskostnad ${fmt(k(kostnad), 0)} vid ${fmt(h, 0)} h."
      }
    )

  private def ritaTomt(): Unit =
    statiskText()
    val msg = Texts.noData(lang.now())
    val el = dom.document.getElementById("bess-lead")
    if el != null then
      el.textContent = msg
      el.asInstanceOf[dom.html.Element].style.color = "#b3261e"
    diagramIds.flatMap(id => Vector(id, s"$id-h", s"$id-sub")).foreach { id =>
      val e = dom.document.getElementById(id)
      if e != null then e.asInstanceOf[dom.html.Element].style.display = "none"
    }
    satt("chart-status", msg)

  def main(args: Array[String]): Unit =
    dom.document.documentElement.setAttribute("data-lang", lang.now())
    val sw = dom.document.getElementById("lang-switch")
    if sw != null then { sw.innerHTML = ""; render(sw, langSwitch()) }

    if !harData then lang.signal.foreach(_ => ritaTomt())(unsafeWindowOwner)
    else
      // Tillgangligheten hor hemma i underlaget, inte som en andra sanning har.
      // Vardet klampas och snappas till reglagets steg om 5: ett 0.42 i JSON
      // hade annars gett 42, som <input type=range step=5> renderar som 40
      // medan etiketten skriver 42 - samma glapp som h just fatt bort. Och
      // FORSTA produkten per marknad vinner, sa iterationsordningen i JSON inte
      // tyst avgor vilket varde som galler nar tva skiljer sig.
      def snappa(v: Double): Double = (math.round(v / 5.0) * 5.0).max(0).min(100)
      var fcrSatt = false
      var afrrSatt = false
      produkter.foreach { p =>
        falt(p, "defaultAvailability").map(_.asInstanceOf[Double] * 100).foreach { v =>
          if p.id.asInstanceOf[String].startsWith("afrr") then
            if !afrrSatt then { tillgAfrr.set(snappa(v)); afrrSatt = true }
          else if !fcrSatt then { tillgFcr.set(snappa(v)); fcrSatt = true }
        }
      }
      if aren.nonEmpty then ar.set(aren.last)
      if !zoner.contains(zon.now()) then zon.set(zoner.head)
      lasUrl()
      val host = dom.document.getElementById("controls")
      if host != null then { host.innerHTML = ""; render(host, kontroller()) }
      // Varje reglage ritar om allt. En combineWith over samtliga fjorton
      // signaler overskrider Airstreams aritet, och ett tuppelvarde behovs inte -
      // rita() laser tillstandet sjalv. Signaler sander sitt nuvarande varde vid
      // prenumeration, sa sparren nedan haller de fjorton startskotten tysta och
      // sidan ritas en gang nar allt ar uppkopplat.
      val alla: Vector[Signal[Any]] = Vector(
        lang.signal,
        zon.signal,
        ar.signal,
        median.signal,
        H.signal,
        capex.signal,
        wacc.signal,
        livslangd.signal,
        opex.signal,
        cykler.signal,
        eta.signal,
        dod.signal,
        tillgFcr.signal,
        tillgAfrr.signal
      )
      alla.foreach(_.foreach(_ => if igang then rita())(unsafeWindowOwner))
      igang = true
      rita()
      dom.window.addEventListener("resize", (_: dom.Event) => diagram.values.foreach(_.resize()))
