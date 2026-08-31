package elmix.echarts

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.*

/**
 * Minimal handskriven facade mot ECharts (global fran script-tag). Byt mot ScalablyTyped om typade
 * options behovs senare.
 *
 * Delad av app, obalans och foretagspris. Lag tidigare ordagrant i alla tre, sa varje fix mot
 * facaden behovde goras pa tre stallen. Modulen bar BARA facaden - varje sida har sitt eget
 * Globals, eftersom de laser olika datafiler under data/.
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
