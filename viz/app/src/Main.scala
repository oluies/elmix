package elmix.viz

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as obj

final case class Cap(zone: String, yr: Int, kraftslag: String, rate: Double)
final case class Pw(t: Double, zone: String, eur: Double, wind: Double)
final case class PcaExp(zone: String, pc: Int, explained: Double)
final case class PcaLoad(zone: String, pc: Int, kraftslag: String, loading: Double)
final case class PcaScore(zone: String, yr: Int, pc1: Double, pc2: Double)

object Main:

  val KraftslagsFarg: Map[String, String] = Map(
    "Vind" -> "#5470c6",
    "Sol" -> "#fac858",
    "Vattenkraft" -> "#91cc75",
    "Kärnkraft" -> "#ee6666",
    "Kraftvärme/övr" -> "#73c0de"
  )

  lazy val caps: List[Cap] = Globals.elmixCapture.toList.map { d =>
    Cap(
      d.zone.toString,
      d.yr.asInstanceOf[Double].toInt,
      d.kraftslag.toString,
      d.capture_rate.asInstanceOf[Double]
    )
  }

  lazy val pws: List[Pw] = Globals.elmixPriceWind.toList.map { d =>
    Pw(
      d.t.asInstanceOf[Double],
      d.zone.toString,
      d.eur_mwh.asInstanceOf[Double],
      d.wind_mwh.asInstanceOf[Double]
    )
  }

  lazy val pcaExp: List[PcaExp] = Globals.elmixPcaExplained.toList.map { d =>
    PcaExp(d.zone.toString, d.pc.asInstanceOf[Double].toInt, d.explained.asInstanceOf[Double])
  }
  lazy val pcaLoad: List[PcaLoad] = Globals.elmixPcaLoadings.toList.map { d =>
    PcaLoad(
      d.zone.toString,
      d.pc.asInstanceOf[Double].toInt,
      d.kraftslag.toString,
      d.loading.asInstanceOf[Double]
    )
  }
  lazy val pcaScores: List[PcaScore] = Globals.elmixPcaScores.toList.map { d =>
    PcaScore(
      d.zone.toString,
      d.yr.asInstanceOf[Double].toInt,
      d.pc1.asInstanceOf[Double],
      d.pc2.asInstanceOf[Double]
    )
  }

  lazy val zones: List[String] = caps.map(_.zone).distinct.sorted

  // ----------------------------------------------------------------- PCA
  val Fuels = List("Vind", "Sol", "Vattenkraft", "Kärnkraft", "Kraftvärme/övr")
  val Pcs = List(1, 2, 3, 4, 5)
  def fuelAbb(f: String): String = if f == "Kraftvärme/övr" then "Kraftv." else f
  def loadOf(z: String, p: Int, f: String): Double =
    pcaLoad.find(l => l.zone == z && l.pc == p && l.kraftslag == f).map(_.loading).getOrElse(0.0)
  def jsfn[A](f: js.Dynamic => A): js.Function1[js.Dynamic, A] = (d: js.Dynamic) => f(d)
  // Per zon: bara kraftslag/komponenter som faktiskt finns (SE1 saknar t.ex.
  // kärnkraft och ska inte få en rad i heatmappen).
  def fuelsOf(z: String): List[String] =
    Fuels.filter(f => pcaLoad.exists(l => l.zone == z && l.kraftslag == f))
  def pcsOf(z: String): List[Int] =
    pcaLoad.filter(_.zone == z).map(_.pc).distinct.sorted

  // ------------------------------------------------ capture: small multiples

  // 2 kolumner, sa manga rader som behovs (SE1-SE4 -> 2x2)
  val GridCols = 2
  val RowH = 256
  def gridRows: Int = (zones.size + GridCols - 1) / GridCols
  def captureHeight: Int = 64 + gridRows * RowH + 20

  def captureOption(): js.Any =
    val years = caps.map(_.yr).distinct.sorted
    val slag = caps.map(_.kraftslag).distinct.sorted
    val grids = zones.zipWithIndex
    // Dynamiska y-granser sa inget kraftslag klipps bort (vattenkraft >1).
    val crVals = caps.map(_.rate)
    val yMin = math.floor((crVals.minOption.getOrElse(0.4).min(1.0) - 0.1) * 10) / 10
    val yMax = math.ceil((crVals.maxOption.getOrElse(1.1).max(1.0) + 0.1) * 10) / 10
    def col(i: Int) = i % GridCols
    def row(i: Int) = i / GridCols

    val grid = js.Array(grids.map { (_, i) =>
      obj(left = s"${8 + col(i) * 48}%", width = "38%", top = 64 + row(i) * RowH, height = 180)
    }*)
    val titles = js.Array(grids.map { (z, i) =>
      obj(
        text = z.replace("_", ""),
        left = s"${8 + col(i) * 48 + 19}%",
        top = 38 + row(i) * RowH,
        textAlign = "center",
        textStyle = obj(fontSize = 13)
      )
    }*)
    val xAxes = js.Array(grids.map { (_, i) =>
      obj(`type` = "category", gridIndex = i, data = js.Array(years.map(_.toString)*))
    }*)
    val yAxes = js.Array(grids.map { (_, i) =>
      obj(
        `type` = "value",
        gridIndex = i,
        min = yMin,
        max = yMax,
        name = if i == 0 then "capture rate" else ""
      )
    }*)
    val series = js.Array((for
      (z, i) <- grids
      s <- slag
    yield
      val data = years.map(y =>
        caps
          .find(c => c.zone == z && c.kraftslag == s && c.yr == y)
          .fold(null: Any)(_.rate)
      )
      obj(
        name = s,
        `type` = "line",
        xAxisIndex = i,
        yAxisIndex = i,
        data = js.Array(data.map(_.asInstanceOf[js.Any])*),
        itemStyle = obj(color = KraftslagsFarg.getOrElse(s, "#999")),
        markLine = obj(
          silent = true,
          symbol = "none",
          lineStyle = obj(`type` = "dashed", color = "#aaa"),
          label = obj(show = i == grids.size - 1, formatter = "1.0"),
          data = js.Array(obj(yAxis = 1.0))
        )
      )
    )*)

    obj(
      title = titles,
      legend = obj(top = 0, data = js.Array(slag*)),
      tooltip = obj(trigger = "axis"),
      grid = grid,
      xAxis = xAxes,
      yAxis = yAxes,
      series = series
    )

  // --------------------------------------- pris vs vind: timserie med zoom

  def priceWindOption(zone: String): js.Any =
    val rows = pws.filter(_.zone == zone)
    val maxT = rows.map(_.t).maxOption.getOrElse(0.0)
    val zoomFrom = maxT - 14L * 24 * 3600 * 1000 // default: senaste 14 dygnen

    val pris = js.Array(rows.map(r => js.Array[js.Any](r.t, r.eur))*)
    val vind = js.Array(rows.map(r => js.Array[js.Any](r.t, r.wind))*)

    obj(
      title =
        obj(text = s"Day-ahead-pris vs vindproduktion – ${zone.replace("_", "")}", left = "center"),
      legend = obj(top = 34),
      tooltip = obj(trigger = "axis"),
      grid = obj(top = 80, bottom = 80, left = 64, right = 64),
      xAxis = obj(`type` = "time"),
      yAxis = js.Array(
        obj(`type` = "value", name = "EUR/MWh"),
        obj(`type` = "value", name = "MWh/h", splitLine = obj(show = false))
      ),
      dataZoom = js.Array(
        obj(`type` = "inside", xAxisIndex = 0, startValue = zoomFrom, endValue = maxT),
        obj(`type` = "slider", xAxisIndex = 0, bottom = 14, startValue = zoomFrom, endValue = maxT)
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
          lineStyle = obj(width = 1.2)
        ),
        obj(
          name = "Vind (MWh/h)",
          `type` = "line",
          yAxisIndex = 1,
          showSymbol = false,
          sampling = "lttb",
          data = vind,
          itemStyle = obj(color = "#5470c6"),
          areaStyle = obj(opacity = 0.22),
          lineStyle = obj(width = 1)
        )
      )
    )

  // --------------------------------------------------- PCA: scree / loadings

  private def col(i: Int) = i % GridCols
  private def row(i: Int) = i / GridCols

  /** Scree: förklarad varians per komponent, small multiples. */
  def screeOption(): js.Any =
    val grids = zones.zipWithIndex
    obj(
      title = js.Array(grids.map { (z, i) =>
        obj(
          text = z.replace("_", ""),
          left = s"${8 + col(i) * 48 + 19}%",
          top = 38 + row(i) * RowH,
          textAlign = "center",
          textStyle = obj(fontSize = 13)
        )
      }*),
      grid = js.Array(grids.map { (_, i) =>
        obj(left = s"${8 + col(i) * 48}%", width = "38%", top = 64 + row(i) * RowH, height = 180)
      }*),
      xAxis = js.Array(grids.map { (z, i) =>
        obj(`type` = "category", gridIndex = i, data = js.Array(pcsOf(z).map(p => s"PC$p")*))
      }*),
      yAxis = js.Array(grids.map { (_, i) =>
        obj(
          `type` = "value",
          gridIndex = i,
          min = 0,
          max = 1,
          name = if i == 0 then "förklarad andel" else ""
        )
      }*),
      series = js.Array(grids.map { (z, i) =>
        obj(
          `type` = "bar",
          xAxisIndex = i,
          yAxisIndex = i,
          itemStyle = obj(color = "#5470c6"),
          data = js.Array(
            pcsOf(z).map(p =>
              pcaExp.find(e => e.zone == z && e.pc == p).map(_.explained).getOrElse(0.0)
            )*
          ),
          label = obj(
            show = true,
            position = "top",
            fontSize = 9,
            formatter = jsfn(o => s"${math.round(o.value.asInstanceOf[Double] * 100)}%")
          )
        )
      }*)
    )

  /** Loadings: kraftslag × komponent, divergerande heatmap, small multiples. */
  def loadingsOption(): js.Any =
    val grids = zones.zipWithIndex
    obj(
      title = js.Array(grids.map { (z, i) =>
        obj(
          text = z.replace("_", ""),
          left = s"${8 + col(i) * 48 + 17}%",
          top = 38 + row(i) * RowH,
          textAlign = "center",
          textStyle = obj(fontSize = 13)
        )
      }*),
      grid = js.Array(grids.map { (_, i) =>
        obj(left = s"${10 + col(i) * 48}%", width = "32%", top = 64 + row(i) * RowH, height = 180)
      }*),
      xAxis = js.Array(grids.map { (z, i) =>
        obj(`type` = "category", gridIndex = i, data = js.Array(pcsOf(z).map(p => s"PC$p")*))
      }*),
      yAxis = js.Array(grids.map { (z, i) =>
        obj(`type` = "category", gridIndex = i, data = js.Array(fuelsOf(z).map(fuelAbb)*))
      }*),
      visualMap = obj(
        min = -1,
        max = 1,
        dimension = 2,
        calculable = false,
        orient = "horizontal",
        left = "center",
        bottom = 0,
        inRange = obj(color = js.Array("#c0392b", "#f7f7f7", "#2c7fb8"))
      ),
      series = js.Array(grids.map { (z, i) =>
        obj(
          `type` = "heatmap",
          xAxisIndex = i,
          yAxisIndex = i,
          data = js.Array(fuelsOf(z).zipWithIndex.flatMap { (f, fi) =>
            pcsOf(z).zipWithIndex.map { (p, pi) =>
              js.Array[js.Any](pi, fi, loadOf(z, p, f))
            }
          }*),
          label = obj(
            show = true,
            fontSize = 8,
            formatter = jsfn(o => f"${o.value.asInstanceOf[js.Array[Double]](2)}%.2f")
          )
        )
      }*)
    )

  /** Biplot: dagsmedel-scores PC1 vs PC2, färgade per år, med loading-vektorer. */
  val YearFarg = Map(2023 -> "#5470c6", 2024 -> "#91cc75", 2025 -> "#fac858", 2026 -> "#ee6666")
  def biplotOption(zone: String): js.Any =
    val rs = pcaScores.filter(_.zone == zone)
    val yrs = rs.map(_.yr).distinct.sorted
    val k = 0.9 * rs.map(r => math.max(math.abs(r.pc1), math.abs(r.pc2))).maxOption.getOrElse(0.01)
    val scatter = yrs.map { y =>
      obj(
        name = y.toString,
        `type` = "scatter",
        symbolSize = 4,
        itemStyle = obj(color = YearFarg.getOrElse(y, "#999"), opacity = 0.5),
        data = js.Array(rs.filter(_.yr == y).map(r => js.Array[js.Any](r.pc1, r.pc2))*)
      )
    }
    val vectors = Fuels
      .map(f => (f, loadOf(zone, 1, f), loadOf(zone, 2, f)))
      .filter((_, l1, l2) => math.abs(l1) > 1e-6 || math.abs(l2) > 1e-6)
      .map { (f, l1, l2) =>
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
    obj(
      title =
        obj(text = s"Biplot – ${zone.replace("_", "")} (PC1 vs PC2, dagsmedel)", left = "center"),
      legend = obj(top = 28, data = js.Array(yrs.map(_.toString)*)),
      grid = obj(top = 64, bottom = 48, left = 60, right = 70),
      xAxis = obj(`type` = "value", name = "PC1"),
      yAxis = obj(`type` = "value", name = "PC2", splitLine = obj(show = true)),
      series = js.Array((scatter ++ vectors)*)
    )

  // ------------------------------------------------------------ Laminar-UI

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
    val zoneVar = Var(zones.lastOption.getOrElse("SE_4"))
    val biplotVar = Var(zones.find(_ == "SE_3").getOrElse(zones.lastOption.getOrElse("SE_4")))
    div(
      h1("Elmix – SE1–SE4 (ENTSO-E 2023–2026)"),
      h2("Capture rate per kraftslag"),
      chartDiv(captureHeight) { (chart, _) => chart.setOption(captureOption()) },
      h2("Pris vs vind, timupplöst"),
      div(
        cls := "zone-picker",
        zones.map { z =>
          label(
            input(
              typ := "radio",
              nameAttr := "zone",
              defaultChecked := z == zoneVar.now(),
              onChange.mapTo(z) --> zoneVar.writer
            ),
            s" ${z.replace("_", "")}  "
          )
        }
      ),
      chartDiv(460) { (chart, owner) =>
        chart.setOption(priceWindOption(zoneVar.now()))
        zoneVar.signal.changes.foreach(z => chart.setOption(priceWindOption(z)))(using owner)
      },
      h2("PCA på produktionsmixen – förklarad varians (scree)"),
      chartDiv(captureHeight) { (chart, _) => chart.setOption(screeOption()) },
      h2("PCA – loadings (kraftslagens vikt per komponent)"),
      chartDiv(captureHeight + 30) { (chart, _) => chart.setOption(loadingsOption()) },
      h2("PCA-biplot (PC1 vs PC2, dagsmedel)"),
      div(
        cls := "zone-picker",
        zones.map { z =>
          label(
            input(
              typ := "radio",
              nameAttr := "biplot-zone",
              defaultChecked := z == biplotVar.now(),
              onChange.mapTo(z) --> biplotVar.writer
            ),
            s" ${z.replace("_", "")}  "
          )
        }
      ),
      chartDiv(480) { (chart, owner) =>
        chart.setOption(biplotOption(biplotVar.now()))
        biplotVar.signal.changes.foreach(z => chart.setOption(biplotOption(z)))(using owner)
      }
    )

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), appElement())
