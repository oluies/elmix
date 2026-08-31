package elmix.viz

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import elmix.echarts.{ECharts, EChartsInstance}

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
