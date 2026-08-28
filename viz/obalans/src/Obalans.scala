package elmix.obalans

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as obj
import scala.scalajs.js.Thenable.Implicits.given
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Rullande fönster genom obalansserien. Histogrammet räknas om i webbläsaren av
  * DuckDB-WASM (viz/modell/obalans-boot.mjs) och ritas med ECharts. Motorn hämtas
  * först när läsaren trycker Spela; de statiska diagrammen på sidan klarar sig
  * utan den.
  */
@js.native
@js.annotation.JSGlobal("ObalansDB")
object ObalansDB extends js.Object:
  def ready(onStatus: js.Function1[String, Unit]): js.Promise[Unit] = js.native
  def query(sql: String): js.Promise[js.Array[js.Dynamic]] = js.native

object Obalans:

  val Zones = Vector("SE1", "SE2", "SE3", "SE4")
  val Windows = Vector(7 -> "7 dagar", 30 -> "30 dagar", 90 -> "90 dagar")
  val Lim = 150.0
  val Bins = 150                     // 2 EUR per stapel mellan -150 och +150
  val StepDays = 5                   // hur långt fönstret flyttas per bildruta

  final case class Frame(day: Double, label: String)
  final case class Shape(xs: Vector[Double], ys: Vector[Double],
                         near: Double, zero: Double, out: Double, n: Double)

  private val zone = Var("SE3")
  private val win = Var(30)
  private val idx = Var(0)
  private val frames = Var(Vector.empty[Frame])
  private val shape = Var(Option.empty[Shape])
  private val status = Var("")
  private val playing = Var(false)
  private val booted = Var(false)

  private val DayMs = 86400000.0

  private def q(sql: String): Future[js.Array[js.Dynamic]] = ObalansDB.query(sql).toFuture

  /** Bildrutorna: fönstrets slutdatum, StepDays isär, över hela seriens spann. */
  private def buildFrames(): Future[Unit] =
    q("SELECT epoch_ms(min(ts)) AS lo, epoch_ms(max(ts)) AS hi FROM imb").map { rows =>
      val lo = rows(0).lo.asInstanceOf[Double]
      val hi = rows(0).hi.asInstanceOf[Double]
      val first = lo + win.now() * DayMs
      val fs = Iterator.iterate(first)(_ + StepDays * DayMs)
        .takeWhile(_ <= hi)
        .map(t => Frame(t, fmtDate(t)))
        .toVector
      frames.set(fs)
      idx.set(math.min(idx.now(), math.max(fs.size - 1, 0)))
    }

  private def fmtDate(ms: Double): String =
    val d = new js.Date(ms)
    val m = Vector("jan", "feb", "mar", "apr", "maj", "jun",
                   "jul", "aug", "sep", "okt", "nov", "dec")
    s"${d.getUTCDate()} ${m(d.getUTCMonth().toInt)} ${d.getUTCFullYear()}"

  /** Ett fönster: histogram plus de tre talen som står i den statiska bilden. */
  private def loadShape(): Future[Unit] =
    val fs = frames.now()
    if fs.isEmpty then Future.successful(())
    else
      val to = fs(math.min(idx.now(), fs.size - 1)).day
      val from = to - win.now() * DayMs
      val z = zone.now()
      val where = s"zone = '$z' AND ts > epoch_ms(${from.toLong}) AND ts <= epoch_ms(${to.toLong})"
      val sql =
        s"""WITH w AS (SELECT diff FROM imb WHERE $where),
           |  tot AS (SELECT count(*) AS n,
           |    count(*) FILTER (WHERE diff = 0) AS z,
           |    count(*) FILTER (WHERE abs(diff) < 10) AS near,
           |    count(*) FILTER (WHERE abs(diff) > $Lim) AS out FROM w),
           |  h AS (SELECT least(greatest(CAST(floor(diff / 2) AS INTEGER), -75), 74) AS b,
           |               count(*) AS c
           |        FROM w WHERE diff <> 0 AND abs(diff) <= $Lim GROUP BY b)
           |SELECT t.n, t.z, t.near, t.out, h.b, h.c
           |FROM tot t LEFT JOIN h ON true ORDER BY h.b""".stripMargin
      q(sql).map { rows =>
        if rows.length == 0 then shape.set(None)
        else
          val n = rows(0).n.asInstanceOf[Double]
          val counts = scala.collection.mutable.Map.empty[Int, Double]
          rows.foreach { r =>
            if !js.isUndefined(r.b) && r.b != null then
              counts(r.b.asInstanceOf[Double].toInt) = r.c.asInstanceOf[Double]
          }
          val xs = (-75 to 74).map(b => b * 2.0 + 1.0).toVector
          val raw = (-75 to 74).map(b => counts.getOrElse(b, 0.0) / math.max(n, 1) / 2.0).toVector
          val sm = raw.indices.map { i =>
            val lo = math.max(0, i - 2); val hi = math.min(raw.size - 1, i + 2)
            raw.slice(lo, hi + 1).sum / (hi - lo + 1)
          }.toVector
          shape.set(Some(Shape(xs, sm,
            100 * rows(0).near.asInstanceOf[Double] / math.max(n, 1),
            100 * rows(0).z.asInstanceOf[Double] / math.max(n, 1),
            100 * rows(0).out.asInstanceOf[Double] / math.max(n, 1), n)))
      }

  private def chartOption(s: Shape, yMax: Double): js.Any =
    obj(
      animation = false,
      grid = obj(left = 62, right = 26, top = 34, bottom = 46),
      xAxis = obj(`type` = "value", min = -Lim, max = Lim,
        name = "obalanspris minus day-ahead, EUR/MWh", nameLocation = "middle", nameGap = 28,
        nameTextStyle = obj(fontSize = 11.5, color = "#7b8683"),
        axisLabel = obj(fontSize = 11, color = "#7b8683"),
        splitLine = obj(show = false)),
      yAxis = obj(`type` = "value", min = 0, max = yMax, show = false),
      tooltip = obj(trigger = "axis",
        valueFormatter = ((v: js.Dynamic) => s"${(v.asInstanceOf[Double] * 100).toInt / 100.0}"): js.Function1[js.Dynamic, String]),
      series = js.Array(obj(
        `type` = "line", showSymbol = false, smooth = false,
        lineStyle = obj(width = 2, color = "#6b4a9e"),
        areaStyle = obj(color = "rgba(107,74,158,0.17)"),
        markLine = obj(silent = true, symbol = "none",
          lineStyle = obj(color = "#7b8683", `type` = "dashed", width = 1),
          label = obj(show = false),
          data = js.Array(obj(xAxis = 0))),
        data = js.Array(s.xs.zip(s.ys).map((x, y) => js.Array(x, y): js.Any)*)))
    )

  def main(args: Array[String]): Unit =
    val host = dom.document.getElementById("anim")
    if host == null then ()
    else
      host.innerHTML = ""     // platshållaren gäller bara tills bygget är på plats
      render(host, app())

  private def app(): HtmlElement =
    var chart: Option[EChartsInstance] = None
    var timer = 0

    def redraw(): Unit =
      for c <- chart; s <- shape.now() do
        val yMax = math.max(s.ys.maxOption.getOrElse(0.02) * 1.15, 0.004)
        c.setOption(chartOption(s, yMax), true)

    def refresh(): Unit =
      loadShape().foreach(_ => redraw())

    def stop(): Unit =
      if timer != 0 then dom.window.clearInterval(timer); timer = 0
      playing.set(false)

    def start(): Unit =
      if timer == 0 then
        timer = dom.window.setInterval(() => {
          val fs = frames.now()
          if fs.isEmpty then () else idx.update(i => if i + 1 >= fs.size then 0 else i + 1)
        }, 220)
        playing.set(true)

    def bootThenPlay(): Unit =
      if booted.now() then (if playing.now() then stop() else start())
      else
        status.set("hämtar DuckDB-WASM, cirka 10 MB komprimerat …")
        ObalansDB.ready(((s: String) => status.set(s"$s …")): js.Function1[String, Unit])
          .toFuture
          .flatMap(_ => buildFrames())
          .flatMap(_ => loadShape())
          .onComplete {
            case scala.util.Success(_) =>
              booted.set(true); status.set(""); redraw(); start()
            case scala.util.Failure(e) =>
              status.set(s"DuckDB kunde inte startas: ${e.getMessage}. De statiska diagrammen ovan är opåverkade.")
          }

    div(
      cls := "animwrap",
      div(cls := "ctrl",
        button(tpe := "button",
          aria.pressed <-- playing.signal.map(_.toString),
          child.text <-- playing.signal.map(p => if p then "Pausa" else "Spela"),
          onClick --> { _ => bootThenPlay() }),
        Zones.map(z =>
          button(tpe := "button", z,
            aria.pressed <-- zone.signal.map(cur => (cur == z).toString),
            onClick --> { _ => zone.set(z); if booted.now() then refresh() })),
        select(
          Windows.map((d, lab) => option(value := d.toString, lab)),
          value <-- win.signal.map(_.toString),
          onChange.mapToValue --> { v =>
            win.set(v.toInt)
            if booted.now() then buildFrames().flatMap(_ => loadShape()).foreach(_ => redraw())
          }),
        input(tpe := "range", minAttr := "0", stepAttr := "1",
          maxAttr <-- frames.signal.map(f => math.max(f.size - 1, 0).toString),
          controlled(
            value <-- idx.signal.map(_.toString),
            onInput.mapToValue --> { v =>
              stop(); idx.set(v.toInt); if booted.now() then refresh()
            })),
        span(cls := "rdg",
          child.text <-- frames.signal.combineWith(idx.signal).map { (fs, i) =>
            if fs.isEmpty then "—" else fs(math.min(i, fs.size - 1)).label
          })
      ),
      div(
        cls := "animstat",
        child.text <-- shape.signal.map {
          case Some(s) => f"${s.near}%.0f%% inom ±10 EUR/MWh · ${s.zero}%.0f%% exakt noll · ${s.out}%.1f%% utanför skalan · ${s.n.toLong} perioder"
          case None => ""
        }
      ),
      div(
        width := "100%", height := "340px",
        onMountCallback { ctx =>
          val c = ECharts.init(ctx.thisNode.ref)
          chart = Some(c)
          dom.window.addEventListener("resize", (_: dom.Event) => c.resize())
        }
      ),
      p(cls := "warn", child.text <-- status.signal),
      idx.signal.changes --> { _ => if booted.now() then refresh() }
    )
