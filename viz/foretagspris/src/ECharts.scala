package elmix.foretagspris

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

/**
 * Data injicerad av data/euprices-data.js (samma fil som euprices.html laser). Sidan far bara den
 * svenska spoten darifran; den kinesiska tariffdatan ar statisk och bor i Data.scala. js.UndefOr
 * for att uttaget kan ha misslyckats vid publiceringen - da ska sidan saga det, inte rita tomt.
 */
@js.native
@JSGlobalScope
object Globals extends js.Object:
  val euPrices: js.UndefOr[EuPrices] = js.native

@js.native
trait EuPrices extends js.Object:
  val updated: String = js.native
  val months: js.Array[String] = js.native
  val zones: js.Array[EuZone] = js.native

@js.native
trait EuZone extends js.Object:
  val code: String = js.native

  /** Manadsmedel i EUR/MWh, alignade mot months. null dar manaden saknas - darfor js.Any. */
  val v: js.Array[js.Any] = js.native
