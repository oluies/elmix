package elmix.bess

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/**
 * Data injicerad av data/bess-data.js (skriven av viz/bess_agg.py). Facaden mot ECharts delas med
 * ovriga sidor via elmix.echarts - bara payloaden ar sidspecifik.
 *
 * js.UndefOr for att uttaget kan ha misslyckats vid publiceringen; da ska sidan saga det, inte rita
 * tomt.
 */
@js.native
@JSGlobalScope
object Globals extends js.Object:
  val bessData: js.UndefOr[js.Dynamic] = js.native
