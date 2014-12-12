package com.twitter.finagle.stats

import com.twitter.util.{Future, JavaSingleton, Time}
import java.lang.{Float => JFloat}
import java.util.concurrent.{Callable, TimeUnit}
import scala.annotation.varargs

/**
 * A writeable Counter. Only sums are kept of Counters. An example
 * Counter is "number of requests served".
 */
trait Counter {
  def incr(delta: Int)
  def incr() { incr(1) }
}

/**
 * An append-only collection of time-series data. Example Stats are
 * "queue depth" or "query width in a stream of requests".
 */
trait Stat {
  def add(value: Float): Unit
}

/**
 * Helpers for working with histograms.  Java-friendly versions can be found in
 * [[com.twitter.finagle.stats.JStats]].
 */
object Stat {
  /**
   * Time a given `fn` using the given `unit`.
   */
  def time[A](stat: Stat, unit: TimeUnit)(f: => A): A = {
    val start = Time.now
    val result = f
    stat.add((Time.now - start).inUnit(unit))
    result
  }

  /**
   * Time a given `fn` using milliseconds.
   */
  def time[A](stat: Stat)(f: => A): A = time(stat, TimeUnit.MILLISECONDS)(f)

  /**
   * Time a given asynchronous `fn` using the given `unit`.
   */
  def timeFuture[A](stat: Stat, unit: TimeUnit)(f: => Future[A]): Future[A] = {
    val start = Time.now
    f ensure {
      stat.add((Time.now - start).inUnit(unit))
    }
  }

  /**
   * Time a given asynchronous `fn` using milliseconds.
   */
  def timeFuture[A](stat: Stat)(f: => Future[A]): Future[A] =
    timeFuture(stat, TimeUnit.MILLISECONDS)(f)
}

/**
 * Stat utility methods for ease of use from java.
 */
object JStats {
  /**
   * Time a given `fn` using the given `unit`.
   */
  def time[A](stat: Stat, fn: Callable[A], unit: TimeUnit): A = Stat.time(stat, unit)(fn.call())

  /**
   * Time a given `fn` using milliseconds.
   */
  def time[A](stat: Stat, fn: Callable[A]): A = Stat.time(stat)(fn.call())

  /**
   * Time a given asynchronous `fn` using the given `unit`.
   */
  def timeFuture[A](stat: Stat, fn: Callable[Future[A]], unit: TimeUnit): Future[A] =
    Stat.timeFuture(stat, unit)(fn.call())

  /**
   * Time a given asynchronous `fn` using milliseconds.
   */
  def timeFuture[A](stat: Stat, fn: Callable[Future[A]]): Future[A] =
    Stat.timeFuture(stat)(fn.call())
}

/**
 * Exposes the value of a function. For example, one could add a gauge for a
 * computed health metric.
 */
trait Gauge {
  def remove()
}

object StatsReceiver {
  private[StatsReceiver] var immortalGauges: List[Gauge] = Nil
}

/**
 * [[com.twitter.finagle.stats.StatsReceiver]] utility methods for ease of use from java.
 */
object StatsReceivers {
  /**
   * Java compatible version of `StatsReceiver#counter`.
   */
  @varargs
  def counter(statsReceiver: StatsReceiver, name: String*): Counter = statsReceiver.counter(name: _*)

  /**
   * Java compatible version of `StatsReceiver#addGauge`.
   */
  @varargs
  def addGauge(statsReceiver: StatsReceiver, callable: Callable[JFloat], name: String*): Gauge =
    statsReceiver.addGauge(name: _*)(callable.call())

  /**
   * Java compatible version of `StatsReceiver#provideGauge`.
   */
  @varargs
  def provideGauge(statsReceiver: StatsReceiver, callable: Callable[JFloat], name: String*): Unit =
    statsReceiver.provideGauge(name: _*)(callable.call())

  /**
   * Java compatible version of `StatsReceiver#stat`.
   */
  @varargs
  def stat(statsReceiver: StatsReceiver, name: String*): Stat = statsReceiver.stat(name: _*)
}

/**
 * An interface for recording metrics. Named
 * [[com.twitter.finagle.stats.Counter Counters]],
 * [[com.twitter.finagle.stats.Stat Stats]], and
 * [[com.twitter.finagle.stats.Gauge Gauges]] can be accessed through the
 * corresponding methods of this class.
 */
trait StatsReceiver {
  /**
   * Specifies the representative receiver.  This is in order to
   * expose an object we can use for comparison so that global stats
   * are only reported once per receiver.
   */
  val repr: AnyRef

  /**
   * Accurately indicates if this is a NullStatsReceiver.
   * Because equality is not forwarded via scala.Proxy, this
   * is helpful to check for a NullStatsReceiver.
   */
  def isNull: Boolean = false

  /**
   * Time a given function using the given TimeUnit
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.time]].
   */
  def time[T](unit: TimeUnit, stat: Stat)(f: => T): T = Stat.time(stat, unit)(f)

  /**
   * Time a given function using the given TimeUnit
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.time]].
   */
  def time[T](unit: TimeUnit, name: String*)(f: => T): T = time(unit, stat(name: _*))(f)

  /**
   * Time a given function in milliseconds
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.time]].
   */
  def time[T](name: String*)(f: => T): T = time(TimeUnit.MILLISECONDS, name: _*)(f)

  /**
   * Time a given future using the given TimeUnit
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.timeFuture]].
   */
  def timeFuture[T](unit: TimeUnit, stat: Stat)(f: => Future[T]): Future[T] =
    Stat.timeFuture(stat, unit)(f)

  /**
   * Time a given future using the given TimeUnit
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.timeFuture]].
   */
  def timeFuture[T](unit: TimeUnit, name: String*)(f: => Future[T]): Future[T] =
    timeFuture(unit, stat(name: _*))(f)

  /**
   * Time a given future in milliseconds.
   * This method will soon be deprecated in favor of [[com.twitter.finagle.stats.Stat.timeFuture]].
   */
  def timeFuture[T](name: String*)(f: => Future[T]): Future[T] =
    timeFuture(TimeUnit.MILLISECONDS, name: _*)(f)

  /**
   * Get a Counter with the prefix `name`.
   */
  def counter(name: String*): Counter

  /**
   * Get a Counter with the prefix `name`. This method is a convenience for Java
   * programs, but is no longer needed because StatsReceiver#counter is usable
   * from java.
   */
  def counter0(name: String): Counter = counter(name)

  /**
   * Get a Stat with the description
   */
  def stat(name: String*): Stat

  /**
   * Get a Stat with the description. This method is a convenience for Java
   * programs, but is no longer needed because StatsReceiver#counter is usable
   * from java.
   */
  def stat0(name: String): Stat = stat(name)

  /**
   * Register a function to be periodically measured. This measurement
   * exists in perpetuity. Measurements under the same name are added
   * together.
   */
  def provideGauge(name: String*)(f: => Float): Unit = {
    val gauge = addGauge(name: _*)(f)
    StatsReceiver.synchronized {
      StatsReceiver.immortalGauges ::= gauge
    }
  }

  /**
   * Add the function `f` as a gauge with the given name. The
   * returned gauge value is only weakly referenced by the
   * StatsReceiver, and if garbage collected will cease to be a part
   * of this measurement: thus, it needs to be retained by the
   * caller. Immortal measurements are made with `provideGauge`. As
   * with `provideGauge`, gauges with equal names are added
   * together.
   */
  def addGauge(name: String*)(f: => Float): Gauge

  /**
   * Prepend `namespace` to the names of this receiver.
   */
  def scope(namespace: String): StatsReceiver = {
    if (namespace == "") this
    else {
      val seqPrefix = Seq(namespace)
      new NameTranslatingStatsReceiver(this) {
        protected[this] def translate(name: Seq[String]) = seqPrefix ++ name
      }
    }
  }

  /**
   * Prepend a suffix value to the next scope.
   * stats.scopeSuffix("toto").scope("client").counter("adds") will generate
   * /client/toto/adds
   */
  def scopeSuffix(suffix: String): StatsReceiver = {
    if (suffix == "") this
    else {
      val self = this
      new StatsReceiver {
        val repr = self.repr

        def counter(names: String*) = self.counter(names: _*)
        def stat(names: String*)    = self.stat(names: _*)
        def addGauge(names: String*)(f: => Float) = self.addGauge(names: _*)(f)

        override def scope(namespace: String) = self.scope(namespace).scope(suffix)
      }
    }
  }
}

trait StatsReceiverProxy extends StatsReceiver {
  def self: StatsReceiver

  val repr = self
  override def isNull = self.isNull
  def counter(names: String*) = self.counter(names:_*)
  def stat(names: String*) = self.stat(names:_*)
  def addGauge(names: String*)(f: => Float) = self.addGauge(names:_*)(f)
}

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 */
abstract class NameTranslatingStatsReceiver(val self: StatsReceiver)
  extends StatsReceiver
{
  protected[this] def translate(name: Seq[String]): Seq[String]
  val repr = self.repr
  override def isNull = self.isNull

  def counter(name: String*) = self.counter(translate(name): _*)
  def stat(name: String*)    = self.stat(translate(name): _*)
  def addGauge(name: String*)(f: => Float) = self.addGauge(translate(name): _*)(f)
}

/**
 * A noop StatsReceiver. Metrics are not recorded, making this receiver useful
 * in unit tests and as defaults in situations where metrics are not strictly
 * required.
 */
class NullStatsReceiver extends StatsReceiver {
  val repr = this
  override def isNull = true

  private[this] val NullCounter = new Counter { def incr(delta: Int) {} }
  private[this] val NullStat = new Stat { def add(value: Float) {}}
  private[this] val NullGauge = new Gauge { def remove() {} }

  def counter(name: String*) = NullCounter
  def stat(name: String*) = NullStat
  def addGauge(name: String*)(f: => Float) = NullGauge

  override def toString = "NullStatsReceiver"
}

object NullStatsReceiver extends NullStatsReceiver {
  def get() = this
}
