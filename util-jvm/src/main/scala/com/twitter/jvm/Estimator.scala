package com.twitter.jvm

import scala.util.Random

/**
 * An estimator for values of type T.
 */
trait Estimator[T] {

  /** A scalar measurement `m` was taken */
  def measure(m: T): Unit

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
  def measure(m: Double, e: Double): Unit = {
    val i = (n % N).toInt
    mbuf(i) = m
    ebuf(i) = e

    if (n == 0)
      est = m

    est += weight * (m - est)
    val mv = mvar
    val ev = evar
    if (mv + ev == 0)
      weight = 1d
    else
      weight = mv / (mv + ev)
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

  def estimate: Double = est

  private[this] def variance(samples: Array[Double]): Double = {
    if (samples.length == 1)
      return 0d

    val sum = samples.sum
    val mean = sum / samples.length
    val diff = (samples map { x => (x - mean) * (x - mean) }).sum
    diff / (samples.length - 1)
  }

  override def toString: String =
    "Kalman<estimate=%f, weight=%f, mvar=%f, evar=%f>".format(estimate, weight, mvar, evar)
}

/**
 * A Kalman filter in which measurement errors are normally
 * distributed over the given range (as a fraction of the measured
 * value).
 */
class KalmanGaussianError(N: Int, range: Double) extends Kalman(N) with Estimator[Double] {
  require(range >= 0d && range < 1d)
  private[this] val rng = new Random

  def measure(m: Double): Unit = {
    measure(m, rng.nextGaussian() * range * m)
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
      val x = ((from - count) % N).toInt
      if (x < 0) (x + N)
      else x
    }
    val j = (from % N).toInt
    val sum =
      if (i == j) buf.sum
      else if (i < j) buf.slice(i, j).sum
      else buf.slice(i, N).sum + buf.slice(0, j).sum
    sum / count
  }

  def measure(m: Double): Unit = {
    if (n == 0)
      java.util.Arrays.fill(buf, m)
    else
      buf((n % N).toInt) = m
    n += 1
  }

  def estimate: Double = {
    require(n > 0)
    val weightedMeans = normalized map { case (w, i) => w * mean(n, i) }
    weightedMeans.sum
  }
}

/**
 * Unix-like load average, an exponentially weighted moving average,
 * smoothed to the given interval (counted in number of
 * measurements).
 * See: https://web.mit.edu/saltzer/www/publications/instrumentation.html
 */
class LoadAverage(interval: Double) extends Estimator[Double] {
  private[this] val a = math.exp(-1d / interval)
  private[this] var load = Double.NaN

  def measure(m: Double): Unit = {
    load =
      if (load.isNaN) m
      else load * a + m * (1 - a)
  }

  def estimate: Double = load
}
