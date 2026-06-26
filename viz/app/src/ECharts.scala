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

/**
 * Data injicerad av data/elmix-data.js. elmix15 finns alltid; capture- och pris-vs-vind-marterna
 * kan saknas om datan inte exporterats än (då hoppas kannibaliseringsdiagrammen över) – därför
 * js.UndefOr.
 */
@js.native
@JSGlobalScope
object Globals extends js.Object:
  val elmix15: js.Array[js.Dynamic] = js.native
  val elmixCapture: js.UndefOr[js.Array[js.Dynamic]] = js.native
  val elmixPrisVind: js.UndefOr[js.Array[js.Dynamic]] = js.native
