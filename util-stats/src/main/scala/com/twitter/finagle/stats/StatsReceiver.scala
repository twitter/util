package com.twitter.finagle.stats

import com.twitter.util.Future
import java.lang.{Float => JFloat}
import java.util.concurrent.{Callable, TimeUnit}
import scala.annotation.varargs

object StatsReceiver {
  private[StatsReceiver] var immortalGauges: List[Gauge] = Nil
}

/**
 * [[StatsReceiver]] utility methods for ease of use from java.
 */
object StatsReceivers {
  /**
   * Java compatible version of [[StatsReceiver.counter]].
   */
  @varargs
  def counter(statsReceiver: StatsReceiver, name: String*): Counter = statsReceiver.counter(name: _*)

  /**
   * Java compatible version of [[StatsReceiver.addGauge]].
   */
  @varargs
  def addGauge(statsReceiver: StatsReceiver, callable: Callable[JFloat], name: String*): Gauge =
    statsReceiver.addGauge(name: _*)(callable.call())

  /**
   * Java compatible version of [[StatsReceiver.provideGauge]].
   */
  @varargs
  def provideGauge(statsReceiver: StatsReceiver, callable: Callable[JFloat], name: String*): Unit =
    statsReceiver.provideGauge(name: _*)(callable.call())

  /**
   * Java compatible version of [[StatsReceiver.stat]].
   */
  @varargs
  def stat(statsReceiver: StatsReceiver, name: String*): Stat = statsReceiver.stat(name: _*)
}

/**
 * An interface for recording metrics. Named
 * [[Counter Counters]], [[Stat Stats]], and [[Gauge Gauges]] can be accessed
 * through the corresponding methods of this class.
 *
 * @see [[StatsReceivers]] for a Java-friendly API.
 */
trait StatsReceiver {
  /**
   * Specifies the representative receiver.  This is in order to
   * expose an object we can use for comparison so that global stats
   * are only reported once per receiver.
   */
  val repr: AnyRef

  /**
   * Accurately indicates if this is a [[NullStatsReceiver]].
   * Because equality is not forwarded via scala.Proxy, this
   * is helpful to check for a [[NullStatsReceiver]].
   */
  def isNull: Boolean = false

  /**
   * Time a given function using the given TimeUnit.
   */
  @deprecated("Use Stat.time instead", "2015-2-22")
  def time[T](unit: TimeUnit, stat: Stat)(f: => T): T = Stat.time(stat, unit)(f)

  /**
   * Time a given function using the given TimeUnit.
   */
  @deprecated("Use Stat.time instead", "2015-2-22")
  def time[T](unit: TimeUnit, name: String*)(f: => T): T = time(unit, stat(name: _*))(f)

  /**
   * Time a given function in milliseconds.
   */
  @deprecated("Use Stat.time instead", "2015-2-22")
  def time[T](name: String*)(f: => T): T = time(TimeUnit.MILLISECONDS, name: _*)(f)

  /**
   * Time a given future using the given TimeUnit.
   */
  @deprecated("Use Stat.timeFuture instead", "2015-2-22")
  def timeFuture[T](unit: TimeUnit, stat: Stat)(f: => Future[T]): Future[T] =
    Stat.timeFuture(stat, unit)(f)

  /**
   * Time a given future using the given TimeUnit.
   */
  @deprecated("Use Stat.timeFuture instead", "2015-2-22")
  def timeFuture[T](unit: TimeUnit, name: String*)(f: => Future[T]): Future[T] =
    timeFuture(unit, stat(name: _*))(f)

  /**
   * Time a given future in milliseconds.
   */
  @deprecated("Use Stat.timeFuture instead", "2015-2-22")
  def timeFuture[T](name: String*)(f: => Future[T]): Future[T] =
    timeFuture(TimeUnit.MILLISECONDS, name: _*)(f)

  /**
   * Get a [[Counter counter]] with the given `name`.
   */
  def counter(name: String*): Counter

  /**
   * Get a [[Counter counter]] with the given `name`.
   *
   * This method is a convenience for Java programs, but is no longer needed because
   * [[StatsReceivers.counter]] is usable from java.
   */
  def counter0(name: String): Counter = counter(name)

  /**
   * Get a [[Stat stat]] with the given name.
   */
  def stat(name: String*): Stat

  /**
   * Get a [[Stat stat]] with the given name. This method is a convenience for Java
   * programs, but is no longer needed because [[StatsReceivers.counter]] is
   * usable from java.
   */
  def stat0(name: String): Stat = stat(name)

  /**
   * Register a function `f` as a [[Gauge gauge]] with the given name that has
   * a lifecycle with no end.
   *
   * This measurement exists in perpetuity.
   *
   * Measurements under the same name are added together.
   *
   * @see [[StatsReceiver.addGauge]] if you can properly control the lifecycle
   *     of the returned [[Gauge gauge]].
   */
  def provideGauge(name: String*)(f: => Float): Unit = {
    val gauge = addGauge(name: _*)(f)
    StatsReceiver.synchronized {
      StatsReceiver.immortalGauges ::= gauge
    }
  }

  /**
   * Add the function `f` as a [[Gauge gauge]] with the given name.
   *
   * The returned [[Gauge gauge]] value is only weakly referenced by the
   * [[StatsReceiver]], and if garbage collected will eventually cease to
   * be a part of this measurement: thus, it needs to be retained by the
   * caller. Or put another way, the measurement is only guaranteed to exist
   * as long as there exists a strong reference to the returned
   * [[Gauge gauge]] and typically should be stored in a member variable.
   *
   * Measurements under the same name are added together.
   *
   * @see [[StatsReceiver.provideGauge]] when there is not a good location
   *     to store the returned [[Gauge gauge]] that can give the desired lifecycle.
   *
   * @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/ref/WeakReference.html java.lang.ref.WeakReference]]
   */
  def addGauge(name: String*)(f: => Float): Gauge

  /**
   * Prepend `namespace` to the names of the returned [[StatsReceiver]].
   *
   * For example:
   * {{{
   * statsReceiver.scope("client").counter("adds")
   * statsReceiver.scope("client").scope("backend").counter("adds")
   * }}}
   * will generate [[Counter counters]] named `/client/adds`
   * and `/client/backend/adds`.
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
   *
   * For example:
   * {{{
   * statsReceiver.scopeSuffix("toto").scope("client").counter("adds")
   * }}}
   * will generate a [[Counter counter]] named `/client/toto/adds`.
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

abstract class AbstractStatsReceiver extends StatsReceiver
