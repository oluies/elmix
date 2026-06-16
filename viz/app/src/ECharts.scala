package elmix.viz

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.*

/**
 * Minimal handskriven facade mot ECharts (global fran script-tag). Byt mot ScalablyTyped om typade
 * options behovs senare.
 */
@js.native
@JSGlobal("echarts")
object ECharts extends js.Object:
  def init(el: dom.Element): EChartsInstance = js.native

@js.native
trait EChartsInstance extends js.Object:
  def setOption(option: js.Any): Unit = js.native
  def setOption(option: js.Any, notMerge: Boolean): Unit = js.native
  def resize(): Unit = js.native
  def on(event: String, handler: js.Function1[js.Dynamic, Unit]): Unit = js.native
  def getOption(): js.Dynamic = js.native

/** Data injicerad av data/elmix-data.js (window.elmixCapture / elmixPriceWind). */
@js.native
@JSGlobalScope
object Globals extends js.Object:
  val elmixCapture: js.Array[js.Dynamic] = js.native
  val elmixPriceWind: js.Array[js.Dynamic] = js.native
  val elmixPcaExplained: js.Array[js.Dynamic] = js.native
  val elmixPcaLoadings: js.Array[js.Dynamic] = js.native
  val elmixPcaScores: js.Array[js.Dynamic] = js.native
  val elmix15: js.Array[js.Dynamic] = js.native
