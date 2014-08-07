package com.twitter.jvm

import scala.util.Random

/**
 * An estimator for values of type T.
 */
trait Estimator[T] {
  /** A scalar measurement `m` was taken */
  def measure(m: T)

  /** Estimate the current value */
  def estimate: T
}

/**
 * A simple Kalman filter to estimate a scalar value.
 */
class Kalman(N: Int) {
  private[this] val mbuf = new Array[Double](N)
  private[this] val ebuf = new Array[Double](N)
  private[this] var est: Double = _
  private[this] var n = 0L
  private[this] var weight: Double = 0.9

  /**
   * Update the filter with measurement `m`
   * and measurement error `e`.
   */
  def measure(m: Double, e: Double) {
    val i = (n%N).toInt
    mbuf(i) = m
    ebuf(i) = e

    if (n == 0)
      est = m

    est += weight*(m-est)
    val mv = mvar
    val ev = evar
    if (mv + ev == 0)
      weight = 1D
    else
      weight = mv / (mv+ev)
    n += 1
  }

  private[this] def mvar = variance(
    if (n < N) mbuf take n.toInt
    else mbuf
  )

  private[this] def evar = variance(
    if (n < N) ebuf take n.toInt
    else ebuf
  )

  def estimate = est

  private[this] def variance(samples: Array[Double]): Double = {
    if (samples.length == 1)
      return 0D

    val sum = samples.sum
    val mean = sum / samples.length
    val diff = (samples map { x => (x-mean)*(x-mean) }).sum
    diff/(samples.length-1)
  }

  override def toString =
    "Kalman<estimate=%f, weight=%f, mvar=%f, evar=%f>".format(estimate, weight, mvar, evar)
}

/**
 * A Kalman filter in which measurement errors are normally
 * distributed over the given range (as a fraction of the measured
 * value).
 */
class KalmanGaussianError(N: Int, range: Double) extends Kalman(N) with Estimator[Double] {
  require(range >= 0D && range <1D)
  private[this] val rng = new Random

  def measure(m: Double) {
    measure(m, rng.nextGaussian()*range*m)
  }
}


/**
 * An estimator for weighted windows of means.
 */
class WindowedMeans(N: Int, windows: Seq[(Int, Int)]) extends Estimator[Double] {
  require(windows forall { case (_, i) => i <= N })
  private[this] val normalized = {
    val sum = (windows map { case (w, _) => w }).sum
    windows map { case (w, i) => (w.toDouble / sum, i) }
  }
  private[this] val buf = new Array[Double](N)
  private[this] var n = 0L

  private[this] def mean(from: Long, count: Int): Double = {
    require(count <= N && count > 0)
    val i = {
      val x = ((from-count)%N).toInt
      if (x < 0) (x + N)
      else x
    }
    val j = (from%N).toInt
    val sum =
      if (i == j) buf.sum
      else if (i < j) buf.slice(i, j).sum
      else buf.slice(i, N).sum + buf.slice(0, j).sum
    sum/count
  }

  def measure(m: Double) {
    if (n == 0)
      java.util.Arrays.fill(buf, m)
    else
      buf((n%N).toInt) = m
    n += 1
  }

  def estimate = {
    require(n > 0)
    val weightedMeans = normalized map { case (w, i) => w*mean(n, i) }
    weightedMeans.sum
  }
}

/**
 * Unix-like load average, an exponentially weighted moving average,
 * smoothed to the given interval (counted in number of
 * measurements).
 * See: http://web.mit.edu/saltzer/www/publications/instrumentation.html
 */
class LoadAverage(interval: Double) extends Estimator[Double] {
  private[this] val a = math.exp(-1D/interval)
  private[this] var load = Double.NaN
  private[this] var first = true

  def measure(m: Double) {
    load =
      if (load.isNaN) m
      else load*a + m*(1-a)
  }

  def estimate = load
}

/**
 * Take a GC log produced by:
 *
 * {{{
 * $ jstat -gc \$PID 250 ...
 * }}}
 *
 * And report on GC prediction accuracy. Time is
 * indexed by the jstat output, and the columns are,
 * in order: current time, next gc, estimated next GC.
 */
object EstimatorTest extends App {
  import com.twitter.conversions.storage._

  val estimator = args match {
    case Array("kalman", n, error) =>
      new KalmanGaussianError(n.toInt, error.toDouble)
    case Array("windowed", n, windows) =>
      new WindowedMeans(n.toInt,
        windows.split(",") map { w =>
          w.split(":") match {
            case Array(w, i) => (w.toInt, i.toInt)
            case _ => throw new IllegalArgumentException("bad weight, count pair "+w)
          }
        }
      )
    case Array("load", interval) =>
      new LoadAverage(interval.toDouble)
    case _ => throw new IllegalArgumentException("bad args ")
  }

  val lines = scala.io.Source.stdin.getLines().drop(1)
  val states = lines.toArray map(_.split(" ") filter(_ != "") map(_.toDouble)) collect {
    case Array(s0c, s1c, s0u, s1u, ec, eu, oc, ou, pc, pu, ygc, ygct, fgc, fgct, gct) =>
      PoolState(ygc.toLong, ec.toLong.bytes, eu.toLong.bytes)
  }

  var elapsed = 1
  for (List(begin, end) <- states.toList.sliding(2)) {
    val allocated = (end - begin).used
    estimator.measure(allocated.inBytes)
    val r = end.capacity - end.used
    val i = (r.inBytes/estimator.estimate.toLong) + elapsed
    val j = states.indexWhere(_.numCollections > end.numCollections)

    if (j  > 0)
      println("%d %d %d".format(elapsed, j, i))

    elapsed += 1
  }
}

/*

The following script is useful for plotting
results from EstimatorTest:

	% scala ... com.twitter.jvm.EstimatorTest [ARGS] > /tmp/out

#!/usr/bin/env gnuplot

set terminal png size 800,600
set title "GC predictor"

set macros

set grid
set timestamp "Generated on %Y-%m-%d by `whoami`" font "Helvetica-Oblique, 8pt"
set noclip
set xrange [0:1100]

set key reverse left Left # box

set ylabel "Time of GC" textcolor lt 3
set yrange [0:1100]
set mytics 5
set y2tics

set xlabel "Time" textcolor lt 4
set mxtics 5

set boxwidth 0.5
set style fill transparent pattern 4 bo

plot "< awk '{print $1 \" \" $2}' /tmp/out" title "Actual", \
	"< awk '{print $1 \" \" $3}' /tmp/out" title "Predicted", \
	"< awk '{print $1 \" \" $1}' /tmp/out" title "time" with lines

*/
