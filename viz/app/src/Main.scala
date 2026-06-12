package elmix.viz

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as obj

final case class Cap(zone: String, yr: Int, kraftslag: String, rate: Double)
final case class Pw(t: Double, zone: String, eur: Double, wind: Double)

object Main:

  val KraftslagsFarg: Map[String, String] = Map(
    "Vind"           -> "#5470c6",
    "Sol"            -> "#fac858",
    "Vattenkraft"    -> "#91cc75",
    "Kärnkraft"      -> "#ee6666",
    "Kraftvärme/övr" -> "#73c0de"
  )

  lazy val caps: List[Cap] = Globals.elmixCapture.toList.map { d =>
    Cap(d.zone.toString, d.yr.asInstanceOf[Double].toInt,
        d.kraftslag.toString, d.capture_rate.asInstanceOf[Double])
  }

  lazy val pws: List[Pw] = Globals.elmixPriceWind.toList.map { d =>
    Pw(d.t.asInstanceOf[Double], d.zone.toString,
       d.eur_mwh.asInstanceOf[Double], d.wind_mwh.asInstanceOf[Double])
  }

  lazy val zones: List[String] = caps.map(_.zone).distinct.sorted

  // ------------------------------------------------ capture: small multiples

  // 2 kolumner, sa manga rader som behovs (SE1-SE4 -> 2x2)
  val GridCols = 2
  val RowH     = 256
  def gridRows: Int = (zones.size + GridCols - 1) / GridCols
  def captureHeight: Int = 64 + gridRows * RowH + 20

  def captureOption(): js.Any =
    val years = caps.map(_.yr).distinct.sorted
    val slag  = caps.map(_.kraftslag).distinct.sorted
    val grids = zones.zipWithIndex
    def col(i: Int) = i % GridCols
    def row(i: Int) = i / GridCols

    val grid = js.Array(grids.map { (_, i) =>
      obj(left = s"${8 + col(i) * 48}%", width = "38%",
          top = 64 + row(i) * RowH, height = 180)
    }*)
    val titles = js.Array(grids.map { (z, i) =>
      obj(text = z.replace("_", ""), left = s"${8 + col(i) * 48 + 19}%",
          top = 38 + row(i) * RowH, textAlign = "center",
          textStyle = obj(fontSize = 13))
    }*)
    val xAxes = js.Array(grids.map { (_, i) =>
      obj(`type` = "category", gridIndex = i, data = js.Array(years.map(_.toString)*))
    }*)
    val yAxes = js.Array(grids.map { (_, i) =>
      obj(`type` = "value", gridIndex = i, min = 0.4, max = 1.1,
          name = if i == 0 then "capture rate" else "")
    }*)
    val series = js.Array((for
      (z, i) <- grids
      s      <- slag
    yield
      val data = years.map(y => caps.find(c => c.zone == z && c.kraftslag == s && c.yr == y)
                                    .fold(null: Any)(_.rate))
      obj(
        name = s, `type` = "line", xAxisIndex = i, yAxisIndex = i,
        data = js.Array(data.map(_.asInstanceOf[js.Any])*),
        itemStyle = obj(color = KraftslagsFarg.getOrElse(s, "#999")),
        markLine = obj(silent = true, symbol = "none",
          lineStyle = obj(`type` = "dashed", color = "#aaa"),
          label = obj(show = i == grids.size - 1, formatter = "1.0"),
          data = js.Array(obj(yAxis = 1.0)))
      ))*)

    obj(
      title = titles,
      legend = obj(top = 0, data = js.Array(slag*)),
      tooltip = obj(trigger = "axis"),
      grid = grid, xAxis = xAxes, yAxis = yAxes, series = series
    )

  // --------------------------------------- pris vs vind: timserie med zoom

  def priceWindOption(zone: String): js.Any =
    val rows  = pws.filter(_.zone == zone)
    val maxT  = rows.map(_.t).maxOption.getOrElse(0.0)
    val zoomFrom = maxT - 14L * 24 * 3600 * 1000   // default: senaste 14 dygnen

    val pris = js.Array(rows.map(r => js.Array[js.Any](r.t, r.eur))*)
    val vind = js.Array(rows.map(r => js.Array[js.Any](r.t, r.wind))*)

    obj(
      title = obj(text = s"Day-ahead-pris vs vindproduktion – ${zone.replace("_", "")}", left = "center"),
      legend = obj(top = 34),
      tooltip = obj(trigger = "axis"),
      grid = obj(top = 80, bottom = 80, left = 64, right = 64),
      xAxis = obj(`type` = "time"),
      yAxis = js.Array(
        obj(`type` = "value", name = "EUR/MWh"),
        obj(`type` = "value", name = "MWh/h", splitLine = obj(show = false))
      ),
      dataZoom = js.Array(
        obj(`type` = "inside", xAxisIndex = 0,
            startValue = zoomFrom, endValue = maxT),
        obj(`type` = "slider", xAxisIndex = 0, bottom = 14,
            startValue = zoomFrom, endValue = maxT)
      ),
      series = js.Array(
        obj(name = "Pris (EUR/MWh)", `type` = "line", yAxisIndex = 0,
            showSymbol = false, sampling = "lttb", data = pris,
            itemStyle = obj(color = "#c0392b"), lineStyle = obj(width = 1.2)),
        obj(name = "Vind (MWh/h)", `type` = "line", yAxisIndex = 1,
            showSymbol = false, sampling = "lttb", data = vind,
            itemStyle = obj(color = "#5470c6"),
            areaStyle = obj(opacity = 0.22), lineStyle = obj(width = 1))
      )
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
    div(
      h1("Elmix – SE1–SE4 (syntetisk demo-data)"),
      h2("Capture rate per kraftslag"),
      chartDiv(captureHeight) { (chart, _) => chart.setOption(captureOption()) },
      h2("Pris vs vind, timupplöst"),
      div(
        cls := "zone-picker",
        zones.map { z =>
          label(
            input(typ := "radio", nameAttr := "zone",
              defaultChecked := z == zoneVar.now(),
              onChange.mapTo(z) --> zoneVar.writer),
            s" ${z.replace("_", "")}  "
          )
        }
      ),
      chartDiv(460) { (chart, owner) =>
        chart.setOption(priceWindOption(zoneVar.now()))
        zoneVar.signal.changes.foreach(z => chart.setOption(priceWindOption(z)))(using owner)
      }
    )

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), appElement())
