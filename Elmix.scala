//| mvnDeps:
//| - org.duckdb:duckdb_jdbc:1.3.2.0
//| - org.scala-lang.modules::scala-xml:2.3.0

// Elmix.scala - Mill single-file script (Mill 1.1+).
//
// Hamtar elmarknadsdata for SE1-SE4 fran ENTSO-E och bygger analysunderlag
// med DuckDB. All analys ligger i transform.sql; det har skriptet gor bara
// hamtning, parsning och orkestrering.
//
//   export ENTSOE_API_KEY=...
//   ./mill Elmix.scala fetch --start 2016 --end 2026 --data all
//   ./mill Elmix.scala transform
//
// fetch ar inkrementell (befintliga Parquet hoppas over) och pausar 2 s
// mellan anrop. requests och mainargs ar bundlade i Mill-scripts.

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.sql.{Connection, DriverManager}
import java.time.{Duration as JDuration, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import scala.xml.{Node, XML}

// ----------------------------------------------------------------- ENTSO-E

val Zones: Map[String, String] = Map(
  "SE_1" -> "10Y1001A1001A44P",
  "SE_2" -> "10Y1001A1001A45N",
  "SE_3" -> "10Y1001A1001A46L",
  "SE_4" -> "10Y1001A1001A47J"
)

/** Grannar per elomrade for fysiska floden (A11). */
val Neighbours: Map[String, Map[String, String]] = Map(
  "SE_1" -> Map(
    "NO_4" -> "10YNO-4--------9", "FI" -> "10YFI-1--------U",
    "SE_2" -> Zones("SE_2")),
  "SE_2" -> Map(
    "NO_3" -> "10YNO-3--------J", "NO_4" -> "10YNO-4--------9",
    "SE_1" -> Zones("SE_1"), "SE_3" -> Zones("SE_3")),
  "SE_3" -> Map(
    "NO_1" -> "10YNO-1--------2", "DK_1" -> "10YDK-1--------W",
    "FI" -> "10YFI-1--------U",
    "SE_2" -> Zones("SE_2"), "SE_4" -> Zones("SE_4")),
  "SE_4" -> Map(
    "DK_2" -> "10YDK-2--------M", "DE_LU" -> "10Y1001A1001A82H",
    "PL" -> "10YPL-AREA-----S", "LT" -> "10YLT-1001A0008Q",
    "SE_3" -> Zones("SE_3"))
)

/** PSR-koder -> klartext, samma namn som dim_psr i transform.sql. */
val PsrNames: Map[String, String] = Map(
  "B01" -> "Biomass", "B02" -> "Fossil Brown coal/Lignite",
  "B03" -> "Fossil Coal-derived gas", "B04" -> "Fossil Gas",
  "B05" -> "Fossil Hard coal", "B06" -> "Fossil Oil",
  "B07" -> "Fossil Oil shale", "B08" -> "Fossil Peat",
  "B09" -> "Geothermal", "B10" -> "Hydro Pumped Storage",
  "B11" -> "Hydro Run-of-river and poundage",
  "B12" -> "Hydro Water Reservoir", "B13" -> "Marine",
  "B14" -> "Nuclear", "B15" -> "Other renewable", "B16" -> "Solar",
  "B17" -> "Waste", "B18" -> "Wind Offshore", "B19" -> "Wind Onshore",
  "B20" -> "Other"
)

final case class Row(ts: OffsetDateTime, key: String, value: Double)

val Base = "https://web-api.tp.entsoe.eu/api"
val ReqFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

def token: String =
  sys.env.getOrElse("ENTSOE_API_KEY", sys.error("Satt ENTSOE_API_KEY forst."))

/** Packar upp ENTSO-E:s zip-svar (t.ex. A85) till XML-strangar; en post per fil. */
def unzipXml(bytes: Array[Byte]): Seq[String] =
  val zis = new ZipInputStream(new ByteArrayInputStream(bytes))
  try
    Iterator.continually(zis.getNextEntry).takeWhile(_ != null).map { _ =>
      val out = new ByteArrayOutputStream()
      val buf = new Array[Byte](8192)
      var n = zis.read(buf)
      while n != -1 do { out.write(buf, 0, n); n = zis.read(buf) }
      new String(out.toByteArray, StandardCharsets.UTF_8)
    }.toList
  finally zis.close()

/** Hamtar ett API-svar. Stora svar (A85 m.fl.) levereras som zip med ett
  * eller flera dokument - returnerar ett Elem per dokument, tomt vid
  * Acknowledgement (ingen data) eller HTTP-fel. */
def apiGet(params: Map[String, String]): Seq[scala.xml.Elem] =
  val resp = requests.get(
    Base,
    params = (params + ("securityToken" -> token)).toSeq,
    connectTimeout = 30000,
    readTimeout = 180000,
    check = false
  )
  if resp.statusCode != 200 then
    System.err.println(s"  HTTP ${resp.statusCode}: ${resp.text().take(200)}")
    Nil
  else
    val bytes = resp.bytes
    val isZip = bytes.length >= 2 && bytes(0) == 'P'.toByte && bytes(1) == 'K'.toByte
    val docs = if isZip then unzipXml(bytes) else Seq(new String(bytes, StandardCharsets.UTF_8))
    docs.flatMap { s =>
      val xml = XML.loadString(s)
      // "Ingen data" kommer som Acknowledgement_MarketDocument
      if xml.label.startsWith("Acknowledgement") then None else Some(xml)
    }

def fmtReq(t: OffsetDateTime): String =
  t.withOffsetSameInstant(ZoneOffset.UTC).format(ReqFmt)

/** Expandera en Period till (ts, varde) med forward-fill for curveType A03
  * dar positioner med oforandrat varde utelamnas.
  */
def expandPeriod(p: Node, valueLabel: String): Seq[Row] =
  val start = OffsetDateTime.parse((p \ "timeInterval" \ "start").text.trim)
  val end   = OffsetDateTime.parse((p \ "timeInterval" \ "end").text.trim)
  val resMin = (p \ "resolution").text.trim match
    case "PT15M" => 15
    case "PT30M" => 30
    case "PT60M" => 60
    case "P1D"   => 1440
    case other   => sys.error(s"Okand resolution: $other")
  val byPos: Map[Int, Double] = (p \ "Point").flatMap { pt =>
    val pos = (pt \ "position").text.trim.toInt
    (pt \ valueLabel).headOption.map(v => pos -> v.text.trim.toDouble)
  }.toMap
  val n = (JDuration.between(start, end).toMinutes / resMin).toInt
  var last: Option[Double] = None
  (1 to n).flatMap { i =>
    byPos.get(i).foreach(v => last = Some(v))
    last.map(v => Row(start.plusMinutes((i - 1).toLong * resMin), "", v))
  }

/** Vid blandade upplosningar i samma dokument (MTU-bytet): behall den finaste. */
def finestPeriods(series: Seq[Node]): Seq[Node] =
  val periods = series.flatMap(_ \ "Period")
  val finest = periods.map(p => (p \ "resolution").text.trim).distinct
    .minByOption { case "PT15M" => 15; case "PT30M" => 30; case _ => 60 }
  periods.filter(p => finest.forall(_ == (p \ "resolution").text.trim))

/** A75: faktisk produktion per kraftslag. key = psr_type-namn. */
def fetchGeneration(eic: String, from: OffsetDateTime, to: OffsetDateTime): Seq[Row] =
  apiGet(Map(
    "documentType" -> "A75", "processType" -> "A16",
    "in_Domain" -> eic,
    "periodStart" -> fmtReq(from), "periodEnd" -> fmtReq(to)
  )).flatMap { xml =>
    (xml \ "TimeSeries")
      // produktion har inBiddingZone_Domain; konsumtion har outBiddingZone_Domain
      .filter(ts => (ts \ "inBiddingZone_Domain.mRID").nonEmpty)
      .flatMap { ts =>
        val psr = PsrNames.getOrElse((ts \ "MktPSRType" \ "psrType").text.trim, "Other")
        (ts \ "Period").flatMap(expandPeriod(_, "quantity")).map(_.copy(key = psr))
      }
  }

/** A44: day-ahead-priser i EUR/MWh. */
def fetchPrices(eic: String, from: OffsetDateTime, to: OffsetDateTime): Seq[Row] =
  apiGet(Map(
    "documentType" -> "A44",
    "in_Domain" -> eic, "out_Domain" -> eic,
    "periodStart" -> fmtReq(from), "periodEnd" -> fmtReq(to)
  )).flatMap { xml =>
    finestPeriods(xml \ "TimeSeries").flatMap(expandPeriod(_, "price.amount"))
  }

/** A85: obalanspriser. key = priskategori. */
def fetchImbalance(eic: String, from: OffsetDateTime, to: OffsetDateTime): Seq[Row] =
  apiGet(Map(
    "documentType" -> "A85", "controlArea_Domain" -> eic,
    "periodStart" -> fmtReq(from), "periodEnd" -> fmtReq(to)
  )).flatMap { xml =>
    (xml \ "TimeSeries").flatMap { ts =>
      val cat = (ts \\ "imbalance_Price.category").headOption
        .map(_.text.trim).getOrElse("NA")
      (ts \ "Period").flatMap(expandPeriod(_, "imbalance_Price.amount"))
        .map(_.copy(key = cat))
    }
  }

/** A11: fysiska floden for en riktning. key = gransnamn. */
def fetchFlows(inEic: String, outEic: String, border: String,
               from: OffsetDateTime, to: OffsetDateTime): Seq[Row] =
  apiGet(Map(
    "documentType" -> "A11",
    "in_Domain" -> inEic, "out_Domain" -> outEic,
    "periodStart" -> fmtReq(from), "periodEnd" -> fmtReq(to)
  )).flatMap { xml =>
    (xml \ "TimeSeries").flatMap(_ \ "Period")
      .flatMap(expandPeriod(_, "quantity"))
      .map(_.copy(key = border))
  }

// ------------------------------------------------------------------ DuckDB

def withConn[A](db: String)(f: Connection => A): A =
  val conn = DriverManager.getConnection(s"jdbc:duckdb:$db")
  try f(conn) finally conn.close()

val Iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/** Skriver rader till en Parquet-fil via en temporar DuckDB-tabell. */
def writeParquet(path: Path, zone: String, keyCol: String, valCol: String,
                 rows: Seq[Row]): Unit =
  if rows.isEmpty then
    println(s"  ingen data -> hoppar $path")
    return
  Files.createDirectories(path.getParent)
  withConn(":memory:") { conn =>
    val st = conn.createStatement()
    st.execute(s"CREATE TABLE t (ts TIMESTAMPTZ, $keyCol VARCHAR, $valCol DOUBLE, zone VARCHAR)")
    val ps = conn.prepareStatement("INSERT INTO t VALUES (CAST(? AS TIMESTAMPTZ), ?, ?, ?)")
    var i = 0
    rows.foreach { r =>
      ps.setString(1, r.ts.format(Iso)); ps.setString(2, r.key)
      ps.setDouble(3, r.value); ps.setString(4, zone)
      ps.addBatch(); i += 1
      if i % 5000 == 0 then ps.executeBatch()
    }
    ps.executeBatch()
    st.execute(s"COPY t TO '${path.toString.replace("'", "''")}' (FORMAT parquet, COMPRESSION zstd, COMPRESSION_LEVEL 22)")
    println(s"  -> $path  (${rows.size} rader)")
  }

/** Kor ett SQL-skript sats for sats. Inga semikolon i SQL-kommentarer! */
def runScript(db: String, scriptPath: Path): Unit =
  val stripped = Files.readString(scriptPath).linesIterator
    .map(l => l.indexOf("--") match
      case -1 => l
      case ix => l.substring(0, ix))
    .mkString("\n")
  withConn(db) { conn =>
    val st = conn.createStatement()
    stripped.split(";").map(_.trim).filter(_.nonEmpty).foreach { stmt =>
      print(s"  ${stmt.linesIterator.next().take(60)} ... ")
      st.execute(stmt)
      println("ok")
    }
  }

// ------------------------------------------------------------------ PCA
// Funktionell PCA på produktionsmixen. Rena transformationer (center ->
// kovarians -> Jacobi -> sortering); effekter (DuckDB) bara i kanterna.
// Kovariansmatrisen är liten och symmetrisk (kraftslag × kraftslag), så
// Jacobi-rotation ger exakta egenvärden utan externt linjäralgebra-beroende.

type Mat = Vector[Vector[Double]]

final case class Pca(eigenvalues: Vector[Double], loadings: Mat,
                     explained: Vector[Double])

object LinAlg:
  def identity(n: Int): Mat =
    Vector.tabulate(n, n)((i, j) => if i == j then 1.0 else 0.0)

  def transpose(m: Mat): Mat = m.transpose

  def matmul(x: Mat, y: Mat): Mat =
    val yt = y.transpose
    x.map(row => yt.map(col => row.lazyZip(col).map(_ * _).sum))

  /** Centrera varje kolumn (dra bort kolumnmedelvärdet). */
  def center(rows: Mat): Mat =
    val means = rows.transpose.map(c => c.sum / c.size)
    rows.map(_.lazyZip(means).map(_ - _))

  /** Stickprovskovarians (kolumn × kolumn) för redan centrerad data. */
  def covariance(centered: Mat): Mat =
    val n = centered.size
    val cols = centered.transpose
    Vector.tabulate(cols.size, cols.size) { (i, j) =>
      cols(i).lazyZip(cols(j)).map(_ * _).sum / (n - 1)
    }

  /** Givens-rotation som identitet utom 2×2-blocket (p,q). */
  private def givens(n: Int, p: Int, q: Int, c: Double, s: Double): Mat =
    Vector.tabulate(n, n) { (i, j) =>
      if i == p && j == p then c
      else if i == q && j == q then c
      else if i == p && j == q then s
      else if i == q && j == p then -s
      else if i == j then 1.0
      else 0.0
    }

  /** Egenvärden/-vektorer för symmetrisk matris via Jacobi-rotationer.
    * Returnerar (egenvärden, V) där kolumn i i V hör till egenvärde i. */
  def jacobi(a0: Mat, tol: Double = 1e-12, maxIter: Int = 1000): (Vector[Double], Mat) =
    val n = a0.size
    def largestOffDiag(a: Mat): (Int, Int, Double) =
      val pairs = for i <- 0 until n; j <- i + 1 until n yield (i, j, math.abs(a(i)(j)))
      pairs.maxByOption(_._3).getOrElse((0, 0, 0.0))
    @annotation.tailrec
    def loop(a: Mat, v: Mat, iter: Int): (Mat, Mat) =
      val (p, q, mx) = largestOffDiag(a)
      if mx < tol || iter >= maxIter then (a, v)
      else
        val phi = 0.5 * math.atan2(2 * a(p)(q), a(p)(p) - a(q)(q))
        val g = givens(n, p, q, math.cos(phi), math.sin(phi))
        loop(matmul(matmul(transpose(g), a), g), matmul(v, g), iter + 1)
    val (a, v) = loop(a0, identity(n), 0)
    (Vector.tabulate(n)(i => a(i)(i)), v)

/** PCA: centrera -> kovarians -> egendekomposition -> sortera fallande. */
def pca(rows: Mat): Pca =
  val cov = LinAlg.covariance(LinAlg.center(rows))
  val (eig, vecs) = LinAlg.jacobi(cov)
  val order = eig.indices.sortBy(i => -eig(i)).toVector
  val sortedEig = order.map(eig)
  val total = sortedEig.map(math.max(_, 0.0)).sum
  val explained = sortedEig.map(e => if total > 0 then math.max(e, 0.0) / total else 0.0)
  val loadings = order.map(idx => vecs.map(_(idx)))   // kolumn idx = egenvektor
  Pca(sortedEig, loadings, explained)

// ----------------------------------------------------------- Orkestrering

val Raw = Path.of("data", "raw")
val SleepMs = 2000L
val Kraftslag = Vector("Vind", "Sol", "Vattenkraft", "Kärnkraft", "Kraftvärme/övr")

def yearSpan(year: Int): (OffsetDateTime, OffsetDateTime) =
  val s = OffsetDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  (s, if s.plusYears(1).isAfter(now) then now else s.plusYears(1))

def skipIfExists(p: Path): Boolean =
  if Files.exists(p) then { println(s"  finns: $p"); true } else false

def doFetch(start: Int, end: Int, data: String): Unit =
  val kinds = data match
    case "all" => List("generation", "prices", "imbalance", "flows")
    case k     => List(k)
  for
    year <- start to end
    (zone, eic) <- Zones
    kind <- kinds
  do
    val (from, to) = yearSpan(year)
    if !from.isAfter(to) then
      println(s"$kind $zone $year")
      val p = Raw.resolve(kind).resolve(s"${zone}_$year.parquet")
      kind match
        case "generation" if !skipIfExists(p) =>
          writeParquet(p, zone, "psr_type", "mw", fetchGeneration(eic, from, to))
          Thread.sleep(SleepMs)
        case "prices" if !skipIfExists(p) =>
          writeParquet(p, zone, "kontrakt", "eur_mwh", fetchPrices(eic, from, to))
          Thread.sleep(SleepMs)
        case "imbalance" if !skipIfExists(p) =>
          writeParquet(p, zone, "category", "eur_mwh", fetchImbalance(eic, from, to))
          Thread.sleep(SleepMs)
        case "flows" if !skipIfExists(p) =>
          val rows = Neighbours(zone).toSeq.flatMap { (nb, nbEic) =>
            val imp = fetchFlows(eic, nbEic, s"$nb>$zone", from, to)
            Thread.sleep(SleepMs)
            val exp = fetchFlows(nbEic, eic, s"$zone>$nb", from, to)
              .map(r => r.copy(value = -r.value))   // export negativt
            Thread.sleep(SleepMs)
            imp ++ exp
          }
          writeParquet(p, zone, "border", "mw", rows)
        case _ => ()   // fanns redan

def doTransform(): Unit =
  Files.createDirectories(Path.of("data", "marts"))
  runScript("elmix.duckdb", Path.of("transform.sql"))
  println("Klart. Marts i data/marts/.")

/** PCA på timvis produktionsmix (kraftslagsandelar) per zon. Läser fct_gen
  * ur elmix.duckdb (kräver att transform körts) och skriver två marts:
  * pca_explained (förklarad varians per komponent) och pca_loadings
  * (kraftslagens vikt i varje komponent). */
def doPca(): Unit =
  Files.createDirectories(Path.of("data", "marts"))
  val filters = Kraftslag.zipWithIndex
    .map((k, i) => s"sum(mwh) FILTER (WHERE kraftslag='${k.replace("'", "''")}') AS f$i")
    .mkString(",\n        ")
  val shares = Kraftslag.indices
    .map(i => s"COALESCE(f$i, 0) / tot").mkString(", ")
  val q =
    s"""
      WITH h AS (
        SELECT zone, date_trunc('hour', ts) AS hr, kraftslag, sum(mwh) AS mwh
        FROM fct_gen GROUP BY 1, 2, 3
      ),
      p AS (
        SELECT zone, hr,
        $filters,
        sum(mwh) AS tot
        FROM h GROUP BY 1, 2
      )
      SELECT zone, $shares
      FROM p WHERE tot > 0
      ORDER BY zone
    """
  // Effektiv kant: läs andelsmatrisen per zon ur DuckDB.
  val byZone = scala.collection.mutable.LinkedHashMap
    .empty[String, scala.collection.mutable.ArrayBuffer[Vector[Double]]]
  withConn("elmix.duckdb") { conn =>
    val rs = conn.createStatement().executeQuery(q)
    while rs.next() do
      val z = rs.getString(1)
      val row = Kraftslag.indices.map(i => rs.getDouble(i + 2)).toVector
      byZone.getOrElseUpdate(z, scala.collection.mutable.ArrayBuffer.empty) += row
  }
  // Ren kärna: PCA per zon.
  val results = byZone.toSeq.map((z, rows) => z -> pca(rows.toVector))
  val explainedRows = results.flatMap { (z, p) =>
    p.explained.zip(p.eigenvalues).zipWithIndex.map { case ((ratio, ev), k) =>
      Seq[Any](z, k + 1, ev, ratio)
    }
  }
  val loadingRows = results.flatMap { (z, p) =>
    p.loadings.zipWithIndex.flatMap { (vec, k) =>
      Kraftslag.zip(vec).map((slag, w) => Seq[Any](z, k + 1, slag, w))
    }
  }
  writeTable(Path.of("data", "marts", "pca_explained.parquet"),
    "zone VARCHAR, pc INTEGER, eigenvalue DOUBLE, explained DOUBLE", explainedRows)
  writeTable(Path.of("data", "marts", "pca_loadings.parquet"),
    "zone VARCHAR, pc INTEGER, kraftslag VARCHAR, loading DOUBLE", loadingRows)
  // Loggar förklarad varians (sanity: summerar till ~1 per zon).
  results.foreach { (z, p) =>
    val pcts = p.explained.take(3).map(r => f"${r * 100}%.0f%%").mkString(", ")
    println(s"  ${z.replace("_", "")}: PC1-3 = $pcts  (n_komponenter=${p.eigenvalues.size})")
  }
  println("Klart. PCA-marts i data/marts/.")

/** Skriver godtyckliga rader till en Parquet-fil via en temporär DuckDB-tabell. */
def writeTable(path: Path, ddl: String, rows: Seq[Seq[Any]]): Unit =
  withConn(":memory:") { conn =>
    val st = conn.createStatement()
    st.execute(s"CREATE TABLE t ($ddl)")
    val cols = ddl.split(",").length
    val ph = Vector.fill(cols)("?").mkString(", ")
    val ps = conn.prepareStatement(s"INSERT INTO t VALUES ($ph)")
    rows.foreach { r =>
      r.zipWithIndex.foreach((v, i) => ps.setObject(i + 1, v))
      ps.addBatch()
    }
    ps.executeBatch()
    st.execute(s"COPY t TO '${path.toString.replace("'", "''")}' (FORMAT parquet, COMPRESSION zstd, COMPRESSION_LEVEL 22)")
    println(s"  -> $path  (${rows.size} rader)")
  }

@main
def main(@mainargs.arg(positional = true) command: String,
         start: Int = 2016,
         end: Int = 2026,
         data: String = "all"): Unit =
  command match
    case "fetch"     => doFetch(start, end, data)
    case "transform" => doTransform()
    case "pca"       => doPca()
    case other =>
      System.err.println(s"Okant kommando: $other (fetch | transform | pca)")
      sys.exit(1)
