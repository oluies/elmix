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
  val WindowDays = Vector(7, 30, 90)
  val Lim = 150.0
  val Bins = 150                     // 2 EUR per stapel mellan -150 och +150
  val StepDays = 5                   // hur långt fönstret flyttas per bildruta

  final case class Frame(day: Double, label: String)
  final case class ZoneShape(ys: Vector[Double], near: Double, zero: Double,
                             out: Double, n: Double)
  final case class Shape(xs: Vector[Double], zones: Map[String, ZoneShape])

  /** Samma färger som de statiska diagrammen, lästa ur sidans CSS-variabler så
    * att diagrammet följer ljust och mörkt läge i stället för att frysa ljusa
    * värden. Reservvärdena är de ljusa. */
  private val ZoneVar = Map("SE1" -> ("--ind", "#94671c"), "SE2" -> ("--flex", "#1a7f6b"),
                            "SE3" -> ("--pol", "#6b4a9e"), "SE4" -> ("--eco", "#b8402e"))

  private def cssVar(name: String, fallback: String): String =
    val v = dom.window.getComputedStyle(dom.document.documentElement)
      .getPropertyValue(name).trim
    if v.isEmpty then fallback else v

  private def zoneColor(z: String): String =
    val (name, fb) = ZoneVar(z); cssVar(name, fb)

  private def inkMuted: String = cssVar("--ink3", "#7b8683")

  private val win = Var(30)
  private val idx = Var(0)
  private val frames = Var(Vector.empty[Frame])
  private val shape = Var(Option.empty[Shape])
  private val status = Var("")
  private val playing = Var(false)
  private val booted = Var(false)

  /** Sidan byter språk med html[data-lang]; statisk text sköts av CSS, och den
    * här signalen sköter texten som Scala.js ritar. */
  private val lang = Var(Option(dom.document.documentElement.getAttribute("data-lang"))
    .filter(_ == "en").getOrElse("sv"))
  private def t(sv: String, en: String): Signal[String] =
    lang.signal.map(l => if l == "en" then en else sv)
  private def now(sv: String, en: String): String =
    if lang.now() == "en" then en else sv

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

  private val MonthsSv = Vector("jan", "feb", "mar", "apr", "maj", "jun",
                                "jul", "aug", "sep", "okt", "nov", "dec")
  private val MonthsEn = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  private def fmtDate(ms: Double): String =
    val d = new js.Date(ms)
    val m = if lang.now() == "en" then MonthsEn else MonthsSv
    s"${d.getUTCDate()} ${m(d.getUTCMonth().toInt)} ${d.getUTCFullYear()}"

  /** Ett fönster, alla fyra elområden i samma svep. */
  private def loadShape(): Future[Unit] =
    val fs = frames.now()
    if fs.isEmpty then Future.successful(())
    else
      val to = fs(math.min(idx.now(), fs.size - 1)).day
      val from = to - win.now() * DayMs
      val sql =
        s"""WITH w AS (SELECT zone, diff FROM imb
           |           WHERE ts > epoch_ms(${from.toLong}) AND ts <= epoch_ms(${to.toLong})),
           |  tot AS (SELECT zone, count(*) AS n,
           |    count(*) FILTER (WHERE diff = 0) AS z,
           |    count(*) FILTER (WHERE abs(diff) < 10) AS near,
           |    count(*) FILTER (WHERE abs(diff) > $Lim) AS out FROM w GROUP BY zone),
           |  h AS (SELECT zone, least(greatest(CAST(floor(diff / 2) AS INTEGER), -75), 74) AS b,
           |               count(*) AS c
           |        FROM w WHERE diff <> 0 AND abs(diff) <= $Lim GROUP BY zone, b)
           |SELECT t.zone, t.n, t.z, t.near, t.out, h.b, h.c
           |FROM tot t LEFT JOIN h ON h.zone = t.zone
           |ORDER BY t.zone, h.b""".stripMargin
      q(sql).map { rows =>
        val counts = scala.collection.mutable.Map.empty[(String, Int), Double]
        val totals = scala.collection.mutable.Map.empty[String, (Double, Double, Double, Double)]
        rows.foreach { r =>
          val z = r.zone.asInstanceOf[String]
          totals(z) = (r.n.asInstanceOf[Double], r.z.asInstanceOf[Double],
                       r.near.asInstanceOf[Double], r.out.asInstanceOf[Double])
          if !js.isUndefined(r.b) && r.b != null then
            counts((z, r.b.asInstanceOf[Double].toInt)) = r.c.asInstanceOf[Double]
        }
        if totals.isEmpty then shape.set(None)
        else
          val xs = (-75 to 74).map(b => b * 2.0 + 1.0).toVector
          val zs = Zones.flatMap { z =>
            totals.get(z).map { (n, zero, near, out) =>
              val raw = (-75 to 74).map(b => counts.getOrElse((z, b), 0.0) / math.max(n, 1) / 2.0).toVector
              val sm = raw.indices.map { i =>
                val lo = math.max(0, i - 2); val hi = math.min(raw.size - 1, i + 2)
                raw.slice(lo, hi + 1).sum / (hi - lo + 1)
              }.toVector
              z -> ZoneShape(sm, 100 * near / math.max(n, 1), 100 * zero / math.max(n, 1),
                             100 * out / math.max(n, 1), n)
            }
          }.toMap
          shape.set(Some(Shape(xs, zs)))
      }

  private def chartOption(s: Shape, yMax: Double): js.Any =
    obj(
      animation = false,
      grid = obj(left = 62, right = 26, top = 46, bottom = 46),
      legend = obj(top = 4, itemWidth = 18, itemHeight = 3,
        textStyle = obj(fontSize = 12, color = inkMuted),
        data = js.Array(Zones.filter(s.zones.contains)*)),
      xAxis = obj(`type` = "value", min = -Lim, max = Lim,
        name = now("obalanspris minus day-ahead, EUR/MWh",
                   "imbalance price minus day-ahead, EUR/MWh"),
        nameLocation = "middle", nameGap = 28,
        nameTextStyle = obj(fontSize = 11.5, color = inkMuted),
        axisLabel = obj(fontSize = 11, color = inkMuted),
        splitLine = obj(show = false)),
      yAxis = obj(`type` = "value", min = 0, max = yMax, show = false),
      tooltip = obj(trigger = "axis"),
      series = js.Array(
        Zones.filter(s.zones.contains).zipWithIndex.map { (z, i) =>
          val zs = s.zones(z)
          obj(
            name = z, `type` = "line", showSymbol = false, smooth = false,
            lineStyle = obj(width = 2, color = zoneColor(z)),
            itemStyle = obj(color = zoneColor(z)),
            markLine =
              if i == 0 then
                obj(silent = true, symbol = "none",
                  lineStyle = obj(color = inkMuted, `type` = "dashed", width = 1),
                  label = obj(show = false), data = js.Array(obj(xAxis = 0)))
              else obj(data = js.Array()),
            data = js.Array(s.xs.zip(zs.ys).map((x, y) => js.Array(x, y): js.Any)*)
          ): js.Any
        }*)
    )

  def main(args: Array[String]): Unit =
    val host = dom.document.getElementById("anim")
    if host == null then ()
    else
      host.innerHTML = ""     // platshållaren gäller bara tills bygget är på plats
      render(host, app())
      val sw = dom.document.getElementById("lang-switch")
      if sw != null then
        sw.innerHTML = ""
        render(sw, langSwitch())

  private def langSwitch(): HtmlElement =
    div(
      display := "contents",
      Vector("sv", "en").map { l =>
        button(tpe := "button", l.toUpperCase,
          aria.pressed <-- lang.signal.map(cur => (cur == l).toString),
          onClick --> { _ =>
            lang.set(l)
            dom.document.documentElement.setAttribute("data-lang", l)
          })
      }
    )

  private def app(): HtmlElement =
    var chart: Option[EChartsInstance] = None
    var timer = 0

    def redraw(): Unit =
      for c <- chart; s <- shape.now() do
        val peak = s.zones.values.flatMap(_.ys).maxOption.getOrElse(0.02)
        val yMax = math.max(peak * 1.15, 0.004)
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
        status.set(now("hämtar DuckDB-WASM, cirka 10 MB komprimerat …",
                       "fetching DuckDB-WASM, about 10 MB compressed …"))
        ObalansDB.ready(((s: String) => status.set(s"$s …")): js.Function1[String, Unit])
          .toFuture
          .flatMap(_ => buildFrames())
          .flatMap(_ => loadShape())
          .onComplete {
            case scala.util.Success(_) =>
              booted.set(true); status.set(""); redraw(); start()
            case scala.util.Failure(e) =>
              status.set(now(
                s"DuckDB kunde inte startas: ${e.getMessage}. De statiska diagrammen ovan är opåverkade.",
                s"DuckDB could not start: ${e.getMessage}. The static charts above are unaffected."))
          }

    div(
      cls := "animwrap",
      div(cls := "ctrl",
        button(tpe := "button", cls := "play",
          aria.pressed <-- playing.signal.map(_.toString),
          child.text <-- playing.signal.combineWith(lang.signal).map { (p, l) =>
            if p then (if l == "en" then "\u23f8 Pause" else "\u23f8 Pausa")
            else (if l == "en" then "\u25b6 Play" else "\u25b6 Spela")
          },
          onClick --> { _ => bootThenPlay() }),
        select(
          WindowDays.map(d => option(value := d.toString,
            child.text <-- t(s"$d dagar", s"$d days"))),
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
        child.text <-- shape.signal.combineWith(lang.signal).map {
          case (Some(s), l) =>
            val per = Zones.filter(s.zones.contains)
              .map(z => f"$z ${s.zones(z).near}%.0f%%").mkString(" · ")
            val n = s.zones.values.map(_.n).maxOption.getOrElse(0.0).toLong
            if l == "en" then s"$per within ±10 EUR/MWh · $n periods per zone"
            else s"$per inom ±10 EUR/MWh · $n perioder per zon"
          case _ => ""
        }
      ),
      div(
        width := "100%", height := "340px",
        onMountCallback { ctx =>
          val c = ECharts.init(ctx.thisNode.ref)
          chart = Some(c)
          dom.window.addEventListener("resize", (_: dom.Event) => c.resize())
          val mq = dom.window.matchMedia("(prefers-color-scheme: dark)")
          mq.addEventListener("change", (_: dom.Event) => redraw())
        }
      ),
      p(cls := "warn", child.text <-- status.signal),
      idx.signal.changes --> { _ => if booted.now() then refresh() }
    )
