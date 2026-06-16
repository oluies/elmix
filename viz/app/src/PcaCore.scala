package elmix.viz

// Klientsidig PCA: samma rena funktionella kärna som i Elmix.scala (Jacobi-
// rotation på en liten symmetrisk kovariansmatris), portad till Scala.js så
// att webbläsaren kan räkna om PCA för en vald delperiod. Inga beroenden.

type Mat = Vector[Vector[Double]]

final case class Pca(
    mean: Vector[Double],
    eigenvalues: Vector[Double],
    loadings: Mat,
    explained: Vector[Double]
)

object LinAlg:
  def identity(n: Int): Mat =
    Vector.tabulate(n, n)((i, j) => if i == j then 1.0 else 0.0)

  def transpose(m: Mat): Mat = m.transpose

  def matmul(x: Mat, y: Mat): Mat =
    val yt = y.transpose
    x.map(row => yt.map(col => row.lazyZip(col).map(_ * _).sum))

  def center(rows: Mat): Mat =
    val means = rows.transpose.map(c => c.sum / c.size)
    rows.map(_.lazyZip(means).map(_ - _))

  def covariance(centered: Mat): Mat =
    val n = centered.size
    val cols = centered.transpose
    Vector.tabulate(cols.size, cols.size) { (i, j) =>
      cols(i).lazyZip(cols(j)).map(_ * _).sum / (n - 1)
    }

  private def givens(n: Int, p: Int, q: Int, c: Double, s: Double): Mat =
    Vector.tabulate(n, n) { (i, j) =>
      if i == p && j == p then c
      else if i == q && j == q then c
      else if i == p && j == q then s
      else if i == q && j == p then -s
      else if i == j then 1.0
      else 0.0
    }

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
        val phi = 0.5 * math.atan2(2 * a(p)(q), a(q)(q) - a(p)(p))
        val g = givens(n, p, q, math.cos(phi), math.sin(phi))
        loop(matmul(matmul(transpose(g), a), g), matmul(v, g), iter + 1)
    val (a, v) = loop(a0, identity(n), 0)
    (Vector.tabulate(n)(i => a(i)(i)), v)

object PcaCore:
  /** PCA: centrera -> kovarians -> egendekomposition -> sortera fallande. */
  def pca(rows: Mat): Pca =
    val means = rows.transpose.map(c => c.sum / c.size)
    val centered = rows.map(_.lazyZip(means).map(_ - _))
    val (eig, vecs) = LinAlg.jacobi(LinAlg.covariance(centered))
    val order = eig.indices.sortBy(i => -eig(i)).toVector
    val sortedEig = order.map(eig)
    val total = sortedEig.map(math.max(_, 0.0)).sum
    val explained = sortedEig.map(e => if total > 0 then math.max(e, 0.0) / total else 0.0)
    val loadings = order.map(idx => vecs.map(_(idx)))
    Pca(means, sortedEig, loadings, explained)

  /** Projektion av observationer på de k första komponenterna (PC-scores). */
  def project(rows: Mat, p: Pca, k: Int): Mat =
    rows.map { r =>
      val c = r.lazyZip(p.mean).map(_ - _)
      Vector.tabulate(k)(j => c.lazyZip(p.loadings(j)).map(_ * _).sum)
    }

  /**
   * Pearson-korrelation. PC-scorer är ortogonala, så priset-regrerat-på-PC dekopplas: varje PC:s R²
   * \= corr(pris, score)².
   */
  def corr(a: Vector[Double], b: Vector[Double]): Double =
    val n = a.size
    if n < 2 then 0.0
    else
      val ma = a.sum / n
      val mb = b.sum / n
      var sab, saa, sbb = 0.0
      var i = 0
      while i < n do
        val da = a(i) - ma; val db = b(i) - mb
        sab += da * db; saa += da * da; sbb += db * db
        i += 1
      if saa <= 0 || sbb <= 0 then 0.0 else sab / math.sqrt(saa * sbb)
