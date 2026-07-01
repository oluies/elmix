package elmix.viz

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as obj

/** En 15-min-observation: tid (epoch ms), kraftslagsandelar, day-ahead-pris. */
final case class Obs15(t: Double, shares: Vector[Double], price: Double)

object Main:

  val Fuels = Vector("Vind", "Sol", "Vattenkraft", "Kärnkraft", "Kraftvärme/övr")
  def fuelAbb(f: String): String = if f == "Kraftvärme/övr" then "Kraftv." else f

  val FuelColor: Map[String, String] = Map(
    "Vind" -> "#4dc4d4",
    "Sol" -> "#fac858",
    "Vattenkraft" -> "#2e6fd6",
    "Kärnkraft" -> "#4caf50",
    "Kraftvärme/övr" -> "#9c6b3f"
  )

  // elmix15: {z,t,v,s,va,k,kv,p} -> per zon, sorterade observationer.
  lazy val byZone: Map[String, Vector[Obs15]] =
    Globals.elmix15.toList
      .map { d =>
        val o = Obs15(
          d.t.asInstanceOf[Double],
          Vector(d.v, d.s, d.va, d.k, d.kv).map(_.asInstanceOf[Double]),
          d.p.asInstanceOf[Double]
        )
        (d.z.toString, o)
      }
      .groupBy(_._1)
      .map((z, ps) => z -> ps.map(_._2).toVector.sortBy(_.t))

  lazy val zones: List[String] = byZone.keys.toList.sorted

  def fullRange(z: String): (Double, Double) =
    val ts = byZone(z).map(_.t)
    (ts.min, ts.max)

  // ------------------------------------------------- PCA för zon + tidsfönster
  final case class PcaResult(
      fuels: Vector[String],
      pca: Pca,
      scores: Mat,
      prices: Vector[Double],
      n: Int
  )

  def pcaFor(zone: String, tFrom: Double, tTo: Double): PcaResult =
    val win = byZone(zone).filter(o => o.t >= tFrom && o.t <= tTo)
    val obs = if win.size >= 24 then win else byZone(zone) // fallback vid för smått fönster
    val present = Fuels.indices.filter(i => obs.map(_.shares(i)).distinct.size > 1).toVector
    val fuels = present.map(Fuels)
    val mat = obs.map(o => present.map(o.shares)).toVector
    val p = PcaCore.pca(mat)
    val scores = PcaCore.project(mat, p, p.eigenvalues.size)
    PcaResult(fuels, p, scores, obs.map(_.price).toVector, obs.size)

  /** Per komponent: R² mot priset (PC-scorer ortogonala -> corr²). */
  def priceR2(r: PcaResult): Vector[Double] =
    r.scores.transpose.map(col => math.pow(PcaCore.corr(r.prices, col), 2))

  // ----------------------------------------------------------- Diagramoptioner
  def fmtDay(t: Double): String =
    val d = new js.Date(t)
    f"${d.getFullYear()}%04.0f-${d.getMonth() + 1}%02.0f-${d.getDate()}%02.0f"

  /** Tidslinje: pris + vindandel; dataZoom väljer PCA-period. */
  def timelineOption(zone: String): js.Any =
    val rs = byZone(zone)
    val pris = js.Array(rs.map(o => js.Array[js.Any](o.t, o.price))*)
    val vind = js.Array(rs.map(o => js.Array[js.Any](o.t, math.round(o.shares(0) * 1000) / 10.0))*)
    obj(
      title = obj(
        text =
          s"Pris & vindandel – ${zone.replace("_", "")} (dra i reglaget för att välja PCA-period)",
        left = "center",
        textStyle = obj(fontSize = 13)
      ),
      legend = obj(top = 28),
      tooltip = obj(trigger = "axis"),
      grid = obj(top = 70, bottom = 70, left = 60, right = 60),
      xAxis = obj(`type` = "time"),
      yAxis = js.Array(
        obj(`type` = "value", name = "EUR/MWh"),
        obj(`type` = "value", name = "vind %", min = 0, max = 100, splitLine = obj(show = false))
      ),
      dataZoom = js.Array(
        obj(`type` = "inside", xAxisIndex = 0),
        obj(`type` = "slider", xAxisIndex = 0, bottom = 12)
      ),
      series = js.Array(
        obj(
          name = "Pris (EUR/MWh)",
          `type` = "line",
          yAxisIndex = 0,
          showSymbol = false,
          sampling = "lttb",
          data = pris,
          itemStyle = obj(color = "#c0392b"),
          lineStyle = obj(width = 1)
        ),
        obj(
          name = "Vindandel (%)",
          `type` = "line",
          yAxisIndex = 1,
          showSymbol = false,
          sampling = "lttb",
          data = vind,
          itemStyle = obj(color = "#5470c6"),
          areaStyle = obj(opacity = 0.18),
          lineStyle = obj(width = 0.8)
        )
      )
    )

  def screeOption(r: PcaResult): js.Any =
    val pcs = (1 to r.pca.explained.size).toList
    obj(
      title =
        obj(text = "Förklarad varians (scree)", left = "center", textStyle = obj(fontSize = 13)),
      grid = obj(top = 40, bottom = 30, left = 50, right = 20),
      xAxis = obj(`type` = "category", data = js.Array(pcs.map(p => s"PC$p")*)),
      yAxis = obj(`type` = "value", min = 0, max = 1, name = "andel"),
      series = js.Array(
        obj(
          `type` = "bar",
          itemStyle = obj(color = "#5470c6"),
          data = js.Array(r.pca.explained.map(_.asInstanceOf[js.Any])*),
          label = obj(
            show = true,
            position = "top",
            fontSize = 9,
            formatter = jsfn(o => s"${math.round(o.value.asInstanceOf[Double] * 100)}%")
          )
        )
      )
    )

  def loadingsOption(r: PcaResult): js.Any =
    val pcs = (1 to r.pca.explained.size).toList
    val data = r.fuels.zipWithIndex.flatMap { (f, fi) =>
      r.pca.loadings.zipWithIndex.map { (vec, pi) => js.Array[js.Any](pi, fi, vec(fi)) }
    }
    obj(
      title = obj(
        text = "Loadings (kraftslag × komponent)",
        left = "center",
        textStyle = obj(fontSize = 13)
      ),
      grid = obj(top = 40, bottom = 60, left = 90, right = 20),
      xAxis = obj(`type` = "category", data = js.Array(pcs.map(p => s"PC$p")*)),
      yAxis = obj(`type` = "category", data = js.Array(r.fuels.map(fuelAbb)*)),
      visualMap = obj(
        min = -1,
        max = 1,
        dimension = 2,
        calculable = false,
        orient = "horizontal",
        left = "center",
        bottom = 6,
        inRange = obj(color = js.Array("#c0392b", "#f7f7f7", "#2c7fb8"))
      ),
      series = js.Array(
        obj(
          `type` = "heatmap",
          data = js.Array(data*),
          label = obj(
            show = true,
            fontSize = 9,
            formatter = jsfn(o => f"${o.value.asInstanceOf[js.Array[Double]](2)}%.2f")
          )
        )
      )
    )

  /** Biplot: PC1 vs PC2, punkter färgade efter pris, med loading-vektorer. */
  def biplotOption(r: PcaResult): js.Any =
    val s = r.scores
    val k = 0.9 * s
      .map(row => math.max(math.abs(row(0)), math.abs(row.lift(1).getOrElse(0.0))))
      .maxOption
      .getOrElse(0.01)
    val pts = js.Array(
      s.indices.map(i => js.Array[js.Any](s(i)(0), s(i).lift(1).getOrElse(0.0), r.prices(i)))*
    )
    val vectors = r.fuels.zipWithIndex.map { (f, fi) =>
      val l1 = r.pca.loadings(0)(fi)
      val l2 = r.pca.loadings.lift(1).map(_(fi)).getOrElse(0.0)
      obj(
        name = f,
        `type` = "line",
        symbol = js.Array("none", "arrow"),
        symbolSize = 9,
        silent = true,
        lineStyle = obj(color = "#333", width = 1.5),
        data = js.Array[js.Any](js.Array[js.Any](0, 0), js.Array[js.Any](k * l1, k * l2)),
        endLabel = obj(show = true, formatter = fuelAbb(f), fontSize = 10, color = "#111")
      )
    }
    val pmin = r.prices.minOption.getOrElse(0.0)
    val pmax = r.prices.maxOption.getOrElse(1.0)
    obj(
      title = obj(
        text = "Biplot (PC1 vs PC2, färg = pris)",
        left = "center",
        textStyle = obj(fontSize = 13)
      ),
      grid = obj(top = 44, bottom = 60, left = 56, right = 60),
      tooltip = obj(),
      visualMap = obj(
        min = pmin,
        max = pmax,
        dimension = 2,
        calculable = true,
        orient = "horizontal",
        left = "center",
        bottom = 6,
        text = js.Array("dyrt", "billigt"),
        inRange = obj(color = js.Array("#2c7fb8", "#fee090", "#c0392b"))
      ),
      xAxis = obj(`type` = "value", name = "PC1"),
      yAxis = obj(`type` = "value", name = "PC2", splitLine = obj(show = true)),
      series = js.Array(
        (obj(
          name = "dygn",
          `type` = "scatter",
          symbolSize = 4,
          itemStyle = obj(opacity = 0.5),
          data = pts
        ) +: vectors)*
      )
    )

  /** Prisdrivande: per PC, R² mot priset. */
  def driverOption(r: PcaResult): js.Any =
    val r2 = priceR2(r)
    val pcs = (1 to r2.size).toList
    obj(
      title = obj(
        text =
          s"Prisdrivande komponenter (mix förklarar ${math.round(r2.sum * 100)}% av prisvariationen)",
        left = "center",
        textStyle = obj(fontSize = 13)
      ),
      grid = obj(top = 40, bottom = 30, left = 50, right = 20),
      xAxis = obj(`type` = "category", data = js.Array(pcs.map(p => s"PC$p")*)),
      yAxis = obj(`type` = "value", name = "R²", min = 0),
      series = js.Array(
        obj(
          `type` = "bar",
          itemStyle = obj(color = "#c0392b"),
          data = js.Array(r2.map(_.asInstanceOf[js.Any])*),
          label = obj(
            show = true,
            position = "top",
            fontSize = 9,
            formatter = jsfn(o => s"${math.round(o.value.asInstanceOf[Double] * 100)}%")
          )
        )
      )
    )

  def jsfn[A](f: js.Dynamic => A): js.Function1[js.Dynamic, A] = (d: js.Dynamic) => f(d)

  // ------------------------------------ Kannibalisering: capture & pris-vs-vind
  // Statiska per-zon-marter (mart_capture, mart_pris_vs_vind), förberäknade i
  // export-data.sh. Saknas globalerna (ej exporterade än) blir kartorna tomma
  // och diagrammen hoppas över i appElement.
  final case class Cap(yr: Int, fuel: String, captureRate: Double, baspris: Double, twh: Double)
  lazy val capByZone: Map[String, Vector[Cap]] =
    Globals.elmixCapture.toOption
      .map(_.toList)
      .getOrElse(Nil)
      .map { d =>
        (
          d.zone.toString,
          Cap(
            d.yr.asInstanceOf[Double].toInt,
            d.kraftslag.toString,
            d.capture_rate.asInstanceOf[Double],
            d.baspris.asInstanceOf[Double],
            d.twh.asInstanceOf[Double]
          )
        )
      }
      .groupBy(_._1)
      .map((z, xs) => z -> xs.map(_._2).toVector)

  final case class PV(yr: Int, bin: Int, prisMedian: Double)
  lazy val pvByZone: Map[String, Vector[PV]] =
    Globals.elmixPrisVind.toOption
      .map(_.toList)
      .getOrElse(Nil)
      .map { d =>
        (
          d.zone.toString,
          PV(
            d.yr.asInstanceOf[Double].toInt,
            d.vind_bin.asInstanceOf[Double].toInt,
            d.pris_median.asInstanceOf[Double]
          )
        )
      }
      .groupBy(_._1)
      .map((z, xs) => z -> xs.map(_._2).toVector)

  /** Stabil färgramp per förekommande år. */
  def yearColors(years: Seq[Int]): Map[Int, String] =
    val ramp = Vector("#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de", "#9a60b4", "#fc8452")
    years.sorted.zipWithIndex.map((y, i) => y -> ramp(i % ramp.size)).toMap

  /** Capture rate per kraftslag över åren; streckad referenslinje vid 1 = baspris. */
  def captureOption(zone: String): js.Any =
    val rows = capByZone.getOrElse(zone, Vector.empty)
    val years = rows.map(_.yr).distinct.sorted
    val fuels = Fuels.filter(f => rows.exists(_.fuel == f))
    val series = fuels.map { f =>
      val byYr = rows.filter(_.fuel == f).map(r => r.yr -> r.captureRate).toMap
      obj(
        name = fuelAbb(f),
        `type` = "line",
        connectNulls = true,
        showSymbol = true,
        symbolSize = 5,
        itemStyle = obj(color = FuelColor.getOrElse(f, "#888")),
        lineStyle = obj(width = 2),
        data = js.Array(years.map(y => byYr.get(y).map(_.asInstanceOf[js.Any]).getOrElse(null))*)
      )
    }
    val refLine = obj(
      name = "baspris",
      `type` = "line",
      data = js.Array[js.Any](),
      markLine = obj(
        silent = true,
        symbol = "none",
        lineStyle = obj(color = "#999", `type` = "dashed"),
        data = js.Array(obj(yAxis = 1)),
        label = obj(formatter = "1.0 = baspris", position = "insideEndTop", fontSize = 9)
      )
    )
    obj(
      title = obj(
        text = s"Capture rate per kraftslag – ${zone.replace("_", "")}",
        subtext = "värdeviktat snittpris / baspris · < 1 = kannibalisering",
        left = "center",
        textStyle = obj(fontSize = 13),
        subtextStyle = obj(fontSize = 11)
      ),
      legend = obj(top = 46, data = js.Array(fuels.map(fuelAbb)*)),
      tooltip = obj(trigger = "axis"),
      grid = obj(top = 84, bottom = 36, left = 52, right = 28),
      xAxis = obj(`type` = "category", data = js.Array(years.map(_.toString)*)),
      yAxis = obj(`type` = "value", name = "capture rate", min = 0),
      series = js.Array((series :+ refLine)*)
    )

  /** Pris mot vindnivå: medianpris per vind-percentil, en linje per år. */
  def prisVindOption(zone: String): js.Any =
    val rows = pvByZone.getOrElse(zone, Vector.empty)
    val years = rows.map(_.yr).distinct.sorted
    val yc = yearColors(years)
    val series = years.map { y =>
      val pts = rows.filter(_.yr == y).sortBy(_.bin)
      obj(
        name = y.toString,
        `type` = "line",
        showSymbol = false,
        smooth = true,
        itemStyle = obj(color = yc(y)),
        lineStyle = obj(width = 1.6),
        data = js.Array(pts.map(p => js.Array[js.Any](p.bin, p.prisMedian))*)
      )
    }
    obj(
      title = obj(
        text = s"Pris vs vindnivå – ${zone.replace("_", "")}",
        subtext =
          "medianpris per vind-percentil (1 = låg vind … 20 = hög) · nedåtlutning = kannibalisering",
        left = "center",
        textStyle = obj(fontSize = 13),
        subtextStyle = obj(fontSize = 11)
      ),
      legend = obj(top = 46, `type` = "scroll"),
      tooltip = obj(trigger = "axis"),
      grid = obj(top = 84, bottom = 40, left = 58, right = 28),
      xAxis =
        obj(`type` = "category", name = "vind-bin", data = js.Array((1 to 20).map(_.toString)*)),
      yAxis = obj(`type` = "value", name = "EUR/MWh"),
      series = js.Array(series*)
    )

  // -------------------------------------------------------------- Laminar-UI
  def chartDiv(height: Int)(init: (EChartsInstance, Owner) => Unit): HtmlElement =
    div(
      styleAttr := s"width:100%;height:${height}px;",
      onMountCallback { ctx =>
        val chart = ECharts.init(ctx.thisNode.ref)
        init(chart, ctx.owner)
        dom.window.addEventListener("resize", _ => chart.resize())
      }
    )

  def appElement(): HtmlElement =
    val zoneVar = Var(zones.find(_ == "SE_3").getOrElse(zones.head))
    val periodVar = Var(fullRange(zoneVar.now()))
    // PCA-resultat = derived signal (räknas om en gång per zon/period-ändring).
    val resultSig = zoneVar.signal
      .combineWith(periodVar.signal)
      .map { case (z, from, to) => pcaFor(z, from, to) }

    def bindPca(height: Int)(opt: PcaResult => js.Any): HtmlElement =
      chartDiv(height) { (chart, owner) =>
        resultSig.foreach(r => chart.setOption(opt(r), true))(using owner)
      }

    // Statiska per-zon-diagram (kannibalisering): ritas om vid zonbyte, ej period.
    def zoneChart(height: Int)(opt: String => js.Any): HtmlElement =
      chartDiv(height) { (chart, owner) =>
        def applyZone(z: String): Unit = chart.setOption(opt(z), true)
        applyZone(zoneVar.now())
        zoneVar.signal.changes.foreach(applyZone)(using owner)
      }

    val kannibal: List[HtmlElement] =
      val cap = if capByZone.nonEmpty then List(zoneChart(300)(captureOption)) else Nil
      val pv = if pvByZone.nonEmpty then List(zoneChart(320)(prisVindOption)) else Nil
      if cap.isEmpty && pv.isEmpty then Nil
      else
        h2("Kannibalisering – förnybar energis värdefall") ::
          p(
            cls := "intro",
            "Väderberoende kraft med noll marginalkostnad pressar själv ner priset i ",
            "just de timmar den producerar mest. ",
            em("Capture rate"),
            " (fångat pris / baspris) faller då under 1 – kannibalisering i siffror."
          ) :: (cap ++ pv)

    div(
      h1("Elmix – SE1–SE4 (ENTSO-E, 15-min, från 2 dec 2025)"),
      p(
        cls := "intro",
        "PCA på den kvartsvisa produktionsmixen. Välj zon och dra i tidsreglaget – ",
        "PCA, loadings och prisdrivande komponenter räknas om för vald period ",
        "direkt i webbläsaren (ren funktionell Scala via Scala.js)."
      ),
      div(
        cls := "zone-picker",
        zones.map { z =>
          label(
            input(
              typ := "radio",
              nameAttr := "zone",
              defaultChecked := z == zoneVar.now(),
              onChange.mapTo(z) --> { z =>
                zoneVar.set(z); periodVar.set(fullRange(z))
              }
            ),
            s" ${z.replace("_", "")}  "
          )
        }
      ),
      // Tidslinje med dataZoom som styr PCA-perioden.
      chartDiv(320) { (chart, owner) =>
        def applyZone(z: String): Unit = chart.setOption(timelineOption(z), true)
        applyZone(zoneVar.now())
        zoneVar.signal.changes.foreach(applyZone)(using owner)
        chart.on(
          "datazoom",
          _ => {
            val dz = chart.getOption().dataZoom.asInstanceOf[js.Array[js.Dynamic]](0)
            val (minT, maxT) = fullRange(zoneVar.now())
            val s = dz.start.asInstanceOf[Double]
            val e = dz.end.asInstanceOf[Double]
            periodVar.set((minT + (maxT - minT) * s / 100.0, minT + (maxT - minT) * e / 100.0))
          }
        )
      },
      h2(child.text <-- resultSig.map(r => s"PCA för vald period – ${r.n} kvart")),
      bindPca(240)(screeOption),
      bindPca(280)(loadingsOption),
      bindPca(460)(biplotOption),
      bindPca(240)(driverOption),
      kannibal
    )

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), appElement())
