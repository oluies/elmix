package elmix.foretagspris

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js

import elmix.echarts.{ECharts, EChartsInstance}
import scala.scalajs.js.Dynamic.literal as obj

/**
 * Vad ett foretag faktiskt betalar for elen: SE1-SE4 mot Kinas provinser.
 *
 * Svensk sida byggs ur window.euPrices (samma manadsspot som euprices.html); kinesisk sida ar
 * statisk tariffdata i Data.scala - ingen hamtning.
 *
 * Poangen med sidan ar att spot inte ar priset. Ett svenskt industriforetag betalar spot + nat +
 * energiskatt, och energiskatten ar 0,6 ore/kWh i tillverkningsprocessen mot 36,0 for alla andra.
 * Den skillnaden ar stor nog att flytta Sverige forbi hela den kinesiska provinsfordelningen, sa
 * den maste ritas ut - inte gommas i en fotnot.
 */
object Foretagspris:

  import Data.*
  import Texts.T

  // ------------------------------------------------------------------ tillstand
  private val lang = Var("sv")
  private val profile = Var(Profiles.head)
  private val grid = Var(Profiles.head.grid)
  private val period = Var("last12")

  // ------------------------------------------------------------------ svensk data
  private lazy val ep: Option[EuPrices] = Globals.euPrices.toOption

  private def num(a: js.Any): Option[Double] =
    if a == null || js.isUndefined(a) then None else Some(a.asInstanceOf[Double])

  private lazy val months: Vector[String] = ep.map(_.months.toVector).getOrElse(Vector.empty)

  /** Manadsmedel per elomrade, alignade mot months. */
  private lazy val spotEur: Map[String, Vector[Option[Double]]] =
    ep.map(
      _.zones.toVector
        .filter(z => Zones.contains(z.code))
        .map(z => z.code -> z.v.toVector.map(num))
        .toMap
    ).getOrElse(Map.empty)

  /**
   * Racker med EN zon. export-euprices.sh hoppar over en zon icke-fatalt vid upprepade 429, och
   * euprices_build.py skriver anda ut en giltig payload - med `forall` blev en delvis storning en
   * helt blank sida, inklusive dygnsprofilen som aldrig ror svensk spot. Allt nedstroms tal en
   * saknad zon redan: Zones.flatMap hoppar over den, spotAvg ger None och tsOption ger
   * null-punkter.
   */
  private lazy val hasData: Boolean = months.nonEmpty && Zones.exists(spotEur.contains)

  /** Perioder: senaste 12 manaderna forst, sedan kalenderaren fallande. */
  private lazy val periods: Vector[(String, Vector[Int])] =
    val years = months.map(_.take(4)).distinct.sorted.reverse
    val last12 = "last12" -> months.indices.toVector.takeRight(12)
    last12 +: years.map(y => y -> months.indices.filter(i => months(i).startsWith(y)).toVector)

  private def periodLabel(key: String, l: String): String =
    if key == "last12" then Texts.last12(l) else key

  private def curPeriod: Vector[Int] =
    periods.find(_._1 == period.now()).getOrElse(periods.head)._2

  /** Spot i ore/kWh. 1 EUR/MWh = EurSek/10 ore/kWh. */
  private def spotOre(zone: String, i: Int): Option[Double] =
    spotEur.get(zone).flatMap(_.lift(i).flatten).map(_ * EurSek / 10)

  private def spotAvg(zone: String): Option[Double] =
    val vs = curPeriod.flatMap(i => spotOre(zone, i))
    if vs.isEmpty then None else Some(vs.sum / vs.size)

  /** Svenskt totalpris for vald kundtyp: spot + nat + energiskatt. */
  private def seTotal(zone: String): Option[Double] =
    spotAvg(zone).map(_ + grid.now() + profile.now().tax)

  /** Skatt som faller bort tack vare nedsattningen; noll for profilen utan nedsattning. */
  private def taxSaved: Double = TaxNormal - profile.now().tax

  // ------------------------------------------------------------------ kinesisk data
  private def cnOre(p: Province): Double = profile.now().cnPrice(p) * CnySek * 100

  private def cnSorted: Vector[(Province, Double)] =
    Provinces.map(p => p -> cnOre(p)).sortBy(_._2)

  private def cnMedian: Double =
    val s = cnSorted.map(_._2)
    if s.size % 2 == 1 then s(s.size / 2) else (s(s.size / 2 - 1) + s(s.size / 2)) / 2

  // ------------------------------------------------------------------ formatering
  private def fmt(v: Double, d: Int): String =
    v.asInstanceOf[js.Dynamic]
      .toLocaleString(
        if lang.now() == "en" then "en" else "sv",
        obj(minimumFractionDigits = d, maximumFractionDigits = d)
      )
      .asInstanceOf[String]

  private def t(x: T): String = x(lang.now())

  // ------------------------------------------------------------------ diagram 1
  private final case class Row(
      name: String,
      energy: Double,
      gridFee: Double,
      tax: Double,
      rebate: Double
  ):
    def paid: Double = energy + gridFee + tax
    def full: Double = paid + rebate

  private def stackRows: Vector[Row] =
    val se = Zones.flatMap { z =>
      spotAvg(z).map(sp => Row(z, sp, grid.now(), profile.now().tax, taxSaved))
    }
    val s = cnSorted
    // Medianen har ingen egen provins att namnge, till skillnad fran ytterlagena.
    // Flaggan bars explicit i stallet for att jamfora renderade etiketter mot
    // varandra - de kommer ur samma T och skulle ge "Kina median · Kina median".
    val cn = Vector(
      (t(Texts.cheapest), Some(s.head._1.name(lang.now())), s.head._2),
      (t(Texts.median), None, cnMedian),
      (t(Texts.dearest), Some(s.last._1.name(lang.now())), s.last._2)
    )
    se ++ cn.map { (lbl, provins, ore) =>
      val name = provins.fold(lbl)(p => s"$lbl · $p")
      Row(name, ore * CnShareEnergy, ore * CnShareGrid, ore * CnShareLevy, 0.0)
    }

  private def stackOption(): js.Any =
    val rows = stackRows
    def bar(name: String, color: String, get: Row => Double): js.Any =
      obj(
        name = name,
        `type` = "bar",
        stack = "p",
        itemStyle = obj(color = color),
        data = js.Array(rows.map(r => math.round(get(r) * 10) / 10.0: js.Any)*)
      )

    obj(
      grid = obj(top = 34, bottom = 58, left = 178, right = 92),
      legend = obj(bottom = 4, left = "center", itemGap = 12, textStyle = obj(fontSize = 11)),
      tooltip = obj(
        trigger = "axis",
        axisPointer = obj(`type` = "shadow"),
        formatter = { (ps: js.Array[js.Dynamic]) =>
          val r = rows(ps(0).dataIndex.asInstanceOf[Int])
          val parts = ps.toVector
            .filter(_.seriesName.asInstanceOf[String] != t(Texts.sRebate))
            .map(p =>
              s"${p.marker.asInstanceOf[String]}${p.seriesName.asInstanceOf[String]}: " +
                fmt(p.value.asInstanceOf[Double], 1)
            )
          val total = s"<b>${t(Texts.totalPaid)}: ${fmt(r.paid, 1)} ${t(Texts.unit)}</b>"
          val alt =
            if r.rebate > 0 then
              Vector(
                s"""<span style="color:#888">${t(Texts.atNormalTax)}: ${fmt(r.full, 1)}</span>"""
              )
            else Vector.empty
          (s"<b>${r.name}</b>" +: (parts ++ Vector(total) ++ alt)).mkString("<br>")
        }: js.Function1[js.Array[js.Dynamic], String]
      ),
      xAxis = obj(`type` = "value", name = t(Texts.unit), nameLocation = "end"),
      yAxis = obj(
        `type` = "category",
        inverse = true,
        data = js.Array(rows.map(_.name: js.Any)*),
        axisLabel = obj(fontSize = 11)
      ),
      series = js.Array(
        bar(t(Texts.sEnergy), ColEnergy, _.energy),
        bar(t(Texts.sGrid), ColGrid, _.gridFee),
        bar(t(Texts.sTax), ColTax, _.tax),
        // Bortfallen skatt ritas som en blek forlangning: stapelns fulla langd ar
        // priset utan nedsattning, den mattade delen ar vad som betalas. Etiketten
        // sitter i stapelns ande och visar darfor den fulla langden; det betalda
        // beloppet star i tooltipen.
        obj(
          name = t(Texts.sRebate),
          `type` = "bar",
          stack = "p",
          itemStyle = obj(
            color = ColRebate,
            opacity = 0.55,
            borderColor = ColTax,
            borderWidth = 1,
            borderType = "dashed"
          ),
          label = obj(
            show = true,
            position = "right",
            fontSize = 11,
            color = "#666",
            formatter = { (p: js.Dynamic) =>
              // "51 → 86": vad som betalas, och var stapeln hade slutat utan
              // nedsattningen. Bara den fulla siffran nar det inte finns nagon.
              val r = rows(p.dataIndex.asInstanceOf[Int])
              if r.rebate > 0 then s"${fmt(r.paid, 0)} → ${fmt(r.full, 0)}"
              else fmt(r.full, 0)
            }: js.Function1[js.Dynamic, String]
          ),
          data = js.Array(rows.map(r => math.round(r.rebate * 10) / 10.0: js.Any)*)
        )
      )
    )

  // ------------------------------------------------------------------ diagram 2
  private def spreadOption(): js.Any =
    val s = cnSorted
    val lines = Zones.flatMap(z => seTotal(z).map(z -> _))
    obj(
      grid = obj(top = 30, bottom = 108, left = 58, right = 20),
      tooltip = obj(
        trigger = "item",
        valueFormatter = { (v: js.Any) =>
          fmt(v.asInstanceOf[Double], 1) + " " + t(Texts.unit)
        }: js.Function1[js.Any, String]
      ),
      xAxis = obj(
        `type` = "category",
        data = js.Array(s.map(_._1.name(lang.now()): js.Any)*),
        axisLabel = obj(rotate = 55, fontSize = 10, interval = 0)
      ),
      yAxis = obj(`type` = "value", name = t(Texts.unit)),
      series = js.Array(
        obj(
          `type` = "bar",
          itemStyle = obj(color = "#7a9cc0"),
          data = js.Array(s.map(x => math.round(x._2 * 10) / 10.0: js.Any)*),
          markLine = obj(
            symbol = "none",
            silent = true,
            lineStyle = obj(color = "#c0392b", width = 1.5, `type` = "dashed"),
            label = obj(
              formatter = "{b}",
              fontSize = 11,
              color = "#c0392b",
              position = "insideEndTop"
            ),
            data = js.Array(
              lines.map { (z, v) =>
                obj(name = s"$z ${fmt(v, 0)}", yAxis = math.round(v * 10) / 10.0): js.Any
              }*
            )
          )
        )
      )
    )

  // ------------------------------------------------------------------ diagram 3
  private def tsOption(): js.Any =
    val s = cnSorted
    val lo = s.head._2
    val hi = s.last._2
    val med = cnMedian
    def flat(v: Double): js.Array[js.Any] =
      js.Array(months.map(_ => math.round(v * 10) / 10.0: js.Any)*)

    obj(
      grid = obj(top = 34, bottom = 62, left = 58, right = 20),
      legend = obj(
        bottom = 4,
        left = "center",
        itemGap = 12,
        textStyle = obj(fontSize = 11),
        data = js.Array(
          (Vector(t(Texts.cnBand), t(Texts.cnMedian)) ++ Zones).map(x => x: js.Any)*
        )
      ),
      tooltip = obj(
        trigger = "axis",
        valueFormatter = { (v: js.Any) =>
          if v == null then "–" else fmt(v.asInstanceOf[Double], 0) + " " + t(Texts.unit)
        }: js.Function1[js.Any, String]
      ),
      xAxis = obj(
        `type` = "category",
        data = js.Array(months.map(m => m: js.Any)*),
        axisLabel = obj(fontSize = 10)
      ),
      yAxis = obj(`type` = "value", name = t(Texts.unit)),
      series = js
        .Array(
          // ECharts har ingen intervallserie: bandet ar en osynlig botten plus en
          // staplad hojd ovanpa.
          obj(
            name = "cn-base",
            `type` = "line",
            stack = "cn",
            silent = true,
            symbol = "none",
            lineStyle = obj(opacity = 0),
            tooltip = obj(show = false),
            data = flat(lo)
          ),
          obj(
            name = t(Texts.cnBand),
            `type` = "line",
            stack = "cn",
            symbol = "none",
            lineStyle = obj(opacity = 0),
            areaStyle = obj(color = "#d9c48a", opacity = 0.45),
            tooltip = obj(show = false),
            data = flat(hi - lo)
          ),
          obj(
            name = t(Texts.cnMedian),
            `type` = "line",
            symbol = "none",
            lineStyle = obj(color = "#a8862c", width = 1.5, `type` = "dashed"),
            data = flat(med),
            // Skuggar den valda perioden. Serien klipps INTE: kontrollen styr de
            // ovriga vyerna, men har ar rorelsen over aren sjalva poangen, och en
            // 12-manaders klippning skulle ta bort det som gor vyn vard att ha.
            markArea = obj(
              silent = true,
              itemStyle = obj(color = "#36c", opacity = 0.07),
              data = js.Array(
                js.Array[js.Any](
                  obj(xAxis = months(curPeriod.head)),
                  obj(xAxis = months(curPeriod.last))
                )
              )
            )
          )
        )
        .concat(
          js.Array(
            Zones.map { z =>
              obj(
                name = z,
                `type` = "line",
                symbol = "none",
                lineStyle = obj(color = ZoneColor(z), width = 1.6),
                itemStyle = obj(color = ZoneColor(z)),
                data = js.Array(
                  months.indices.toVector.map { i =>
                    spotOre(z, i)
                      .map(sp => math.round((sp + grid.now() + profile.now().tax) * 10) / 10.0)
                      .map(x => x: js.Any)
                      .getOrElse(null)
                  }*
                )
              ): js.Any
            }*
          )
        )
    )

  // ------------------------------------------------------------------ diagram 4
  private def touOption(): js.Any =
    val s = Provinces.sortBy(_.t35)
    def ore(v: Double): Double = math.round(v * CnySek * 100 * 10) / 10.0
    def line(name: String, color: String, w: Double, get: Province => Double): js.Any =
      obj(
        name = name,
        `type` = "line",
        symbol = "circle",
        symbolSize = 5,
        lineStyle = obj(color = color, width = w),
        itemStyle = obj(color = color),
        data = js.Array(s.map(p => ore(get(p)): js.Any)*)
      )

    obj(
      grid = obj(top = 34, bottom = 108, left = 58, right = 20),
      legend = obj(top = 2, left = "center", itemGap = 12, textStyle = obj(fontSize = 11)),
      tooltip = obj(
        trigger = "axis",
        valueFormatter = { (v: js.Any) =>
          fmt(v.asInstanceOf[Double], 1) + " " + t(Texts.unit)
        }: js.Function1[js.Any, String]
      ),
      xAxis = obj(
        `type` = "category",
        data = js.Array(s.map(_.name(lang.now()): js.Any)*),
        axisLabel = obj(rotate = 55, fontSize = 10, interval = 0)
      ),
      yAxis = obj(`type` = "value", name = t(Texts.unit)),
      series = js.Array(
        line(t(Texts.sPeak), "#c0392b", 1.2, _.p35),
        line(t(Texts.sFlat), "#4477aa", 1.6, _.t35),
        line(t(Texts.sValley), "#4caf50", 1.2, _.v35)
      )
    )

  // ------------------------------------------------------------------ kontroller
  private def controls(): HtmlElement =
    div(
      cls := "controls",
      fieldSet(
        legend(child.text <-- lang.signal.map(Texts.profileLegend.apply)),
        span(
          cls := "segswitch",
          Profiles.map { p =>
            button(
              tpe := "button",
              child.text <-- lang.signal.map(p.name),
              cls("active") <-- profile.signal.map(_.key == p.key),
              aria.pressed <-- profile.signal.map(cur => (cur.key == p.key).toString),
              // Byte av kundtyp aterstaller natavgiften till profilens utgangsvarde;
              // annars slapar ett storindustrivarde med in i tjanstesektorn.
              // Batchat: tva separata set ger tva transaktioner, och den forsta
              // omritningen parar da ny profil med foregaende profils natavgift -
              // over den nya profilens eget gridMax.
              onClick --> { _ => Var.set(profile -> p, grid -> p.grid) }
            )
          }
        )
      ),
      fieldSet(
        idAttr := "grid-picker",
        legend(child.text <-- lang.signal.map(Texts.gridLegend.apply)),
        input(
          tpe := "range",
          idAttr := "grid-slider",
          minAttr := "0",
          maxAttr <-- profile.signal.map(_.gridMax.toInt.toString),
          stepAttr := "1",
          controlled(
            value <-- grid.signal.map(_.toInt.toString),
            onInput.mapToValue.map(_.toDoubleOption.getOrElse(0.0)) --> grid
          )
        ),
        span(
          idAttr := "grid-label",
          child.text <-- grid.signal.combineWith(lang.signal).map { (g, l) =>
            f"${g.toInt}%d ${Texts.unit(l)}"
          }
        )
      ),
      fieldSet(
        legend(child.text <-- lang.signal.map(Texts.periodLegend.apply)),
        span(
          cls := "segswitch",
          periods.map { (key, _) =>
            button(
              tpe := "button",
              child.text <-- lang.signal.map(periodLabel(key, _)),
              cls("active") <-- period.signal.map(_ == key),
              aria.pressed <-- period.signal.map(cur => (cur == key).toString),
              onClick --> { _ => period.set(key) }
            )
          }
        )
      )
    )

  private def langSwitch(): HtmlElement =
    div(
      display := "contents",
      Vector("sv", "en").map { l =>
        button(
          tpe := "button",
          l.toUpperCase,
          cls("active") <-- lang.signal.map(_ == l),
          aria.pressed <-- lang.signal.map(cur => (cur == l).toString),
          onClick --> { _ =>
            lang.set(l)
            dom.document.documentElement.setAttribute("data-lang", l)
          }
        )
      }
    )

  // ------------------------------------------------------------------ statisk text
  /** HTML-escape for text som interpoleras in i innerHTML. */
  private def esc(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  private def setText(id: String, s: String): Unit =
    val el = dom.document.getElementById(id)
    if el != null then el.textContent = s

  private def renderTable(): Unit =
    val l = lang.now()
    val head = Vector(Texts.thArea, Texts.thEnergy, Texts.thGrid, Texts.thTax, Texts.thTotal)
      .map(_(l))
    val seRows = Zones.flatMap { z =>
      spotAvg(z).map { sp =>
        Vector(
          z,
          fmt(sp, 1),
          fmt(grid.now(), 1),
          fmt(profile.now().tax, 1),
          fmt(sp + grid.now() + profile.now().tax, 1)
        )
      }
    }
    val cnRows = cnSorted.map { (p, ore) =>
      Vector(
        p.name(l),
        fmt(ore * CnShareEnergy, 1),
        fmt(ore * CnShareGrid, 1),
        fmt(ore * CnShareLevy, 1),
        fmt(ore, 1)
      )
    }
    val body = (seRows ++ cnRows)
      .map(r => r.map(c => s"<td>$c</td>").mkString("<tr>", "", "</tr>"))
      .mkString
    val el = dom.document.getElementById("chart-data")
    if el != null then
      el.innerHTML = s"<table><caption>${t(Texts.tableCaption)}</caption>" +
        head.map(h => s"<th>$h</th>").mkString("<tr>", "", "</tr>") + body + "</table>"

    val se3 = seTotal("SE3")
    val s = cnSorted
    setText(
      "chart-status",
      se3.fold("") { v =>
        if l == "en" then
          s"SE3 total ${fmt(v, 0)} öre/kWh; Chinese provinces ${fmt(s.head._2, 0)}–" +
            s"${fmt(s.last._2, 0)}, median ${fmt(cnMedian, 0)}."
        else
          s"SE3 summa ${fmt(v, 0)} öre/kWh; kinesiska provinser ${fmt(s.head._2, 0)}–" +
            s"${fmt(s.last._2, 0)}, median ${fmt(cnMedian, 0)}."
      }
    )

  private def renderNotes(): Unit =
    val l = lang.now()
    val el = dom.document.getElementById("notes")
    if el != null then
      el.innerHTML = Texts.notes.map((h, b) => s"<h3>${h(l)}</h3><p>${b(l)}</p>").mkString

    // Kallblocket ar HTML, inte textContent: den kinesiska tariffdatan ar
    // handmatad i Data.scala och maste darfor ga att klicka sig till och
    // kontrollera. rel=noopener pa allt som oppnas i ny flik.
    //
    // Allt som interpoleras escapas. Strangarna ar kompileringskonstanter, sa
    // det finns ingen XSS-risk - men kalltexten innehaller redan ett rat & i
    // "Dezan Shira & Associates" och tva av URL:erna har &-separatorer, och da
    // ar blocket en redigering fran att tyst ga sonder pa en namngiven entitet.
    val srcEl = dom.document.getElementById("src")
    if srcEl != null then
      val lankar = Texts.srcLinks
        .map(k =>
          s"""<li><a href="${esc(k.url)}" target="_blank" rel="noopener">${esc(
              k.label(l)
            )}</a></li>"""
        )
        .mkString
      srcEl.innerHTML = s"<p>${esc(Texts.src(l))}</p>" +
        s"<p class=\"src-h\">${esc(Texts.srcHeading(l))}</p><ul>$lankar</ul>" +
        s"<p>${esc(Texts.srcNote(l))}</p>"

  // ------------------------------------------------------------------ rita om
  private lazy val chartIds = Vector("stack", "spread", "ts", "tou")

  private lazy val charts: Map[String, EChartsInstance] =
    chartIds.flatMap { id =>
      val el = dom.document.getElementById(id)
      if el == null then None else Some(id -> ECharts.init(el))
    }.toMap

  /**
   * Text som inte beror pa spotdatan. Bruten ur redraw for att den maste ritas aven pa fel-grenen:
   * annars far lasaren fyra tomma <h2>, fyra tomma underrubriker och en tom forklaringsruta, och
   * inget av det behover data. Prenumereras dessutom pa lang.signal i bada grenarna, sa
   * sprakvaljaren gor nagot aven nar spoten saknas.
   */
  private def renderStatic(): Unit =
    val l = lang.now()
    dom.document.title = "Elmix – " + Texts.title(l)
    setText("fp-title", Texts.title(l))
    setText("stack-h", Texts.stackH(l))
    setText("stack-sub", Texts.stackSub(l))
    setText("spread-h", Texts.spreadH(l))
    setText("spread-sub", Texts.spreadSub(l))
    setText("ts-h", Texts.tsH(l))
    setText("ts-sub", Texts.tsSub(l))
    setText("tou-h", Texts.touH(l))
    setText("tou-sub", Texts.touSub(l))
    renderNotes()

  private def redraw(): Unit =
    val l = lang.now()
    renderStatic()
    setText("fp-lead", Texts.lead(l))
    val p = profile.now()
    setText("profile-desc", s"${p.name(l)}: ${p.desc(l)} · ${Texts.china(l)} ${p.cnDesc(l)}")
    charts.get("stack").foreach(_.setOption(stackOption(), true))
    charts.get("spread").foreach(_.setOption(spreadOption(), true))
    charts.get("ts").foreach(_.setOption(tsOption(), true))
    charts.get("tou").foreach(_.setOption(touOption(), true))
    renderTable()

  /**
   * Saknad spotdata far inte se ut som lyckad rendering. Bara de svenska vyerna doljs -
   * dygnsprofilen ar ren kinesisk tariffdata och ror aldrig spoten, sa den ritas anda.
   */
  private def renderEmpty(): Unit =
    val l = lang.now()
    renderStatic()
    val saknas = Zones.filterNot(spotEur.contains)
    val msg = Texts.noData(l) + (if saknas.isEmpty then "" else " (" + saknas.mkString(", ") + ")")
    val leadEl = dom.document.getElementById("fp-lead")
    if leadEl != null then
      leadEl.textContent = msg
      leadEl.asInstanceOf[dom.html.Element].style.color = "#b3261e"
    // Dolj bade diagrammen och deras rubriker - en rubrik utan diagram under sig
    // ar mer forvirrande an ingen alls. Metodnoterna och kallorna star kvar; de
    // ar hela poangen med att rendera statisk text pa den har grenen.
    Vector("stack", "spread", "ts").flatMap(id => Vector(id, s"$id-h", s"$id-sub")).foreach { id =>
      val el = dom.document.getElementById(id)
      if el != null then el.asInstanceOf[dom.html.Element].style.display = "none"
    }
    charts.get("tou").foreach(_.setOption(touOption(), true))
    setText("chart-status", msg)

  def main(args: Array[String]): Unit =
    dom.document.documentElement.setAttribute("data-lang", lang.now())
    val sw = dom.document.getElementById("lang-switch")
    if sw != null then
      sw.innerHTML = ""
      render(sw, langSwitch())

    if !hasData then
      // Aven fel-grenen ska folja sprakvaljaren - annars far en engelsk lasare
      // ett svenskt felmeddelande och knappar som inte gor nagot.
      lang.signal.foreach(_ => renderEmpty())(unsafeWindowOwner)
      dom.window.addEventListener("resize", (_: dom.Event) => charts.values.foreach(_.resize()))
    else
      val host = dom.document.getElementById("controls")
      if host != null then
        host.innerHTML = ""
        render(host, controls())
      // En enda prenumeration som ritar om allt: diagrammen ar imperativa och
      // billiga nog att sattas om i sin helhet.
      val all = lang.signal
        .combineWith(profile.signal, grid.signal, period.signal)
      all.foreach(_ => redraw())(unsafeWindowOwner)
      dom.window.addEventListener("resize", (_: dom.Event) => charts.values.foreach(_.resize()))
