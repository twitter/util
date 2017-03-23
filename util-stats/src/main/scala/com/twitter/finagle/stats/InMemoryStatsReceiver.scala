package com.twitter.finagle.stats

import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.collection.JavaConverters._

/**
 * An in-memory implementation of [[StatsReceiver]], which is mostly used for testing.
 *
 * Note that an [[InMemoryStatsReceiver]] does not conflate `Seq("a", "b")` and `Seq("a/b")`
 * names no matter how they look when printed.
 *
 * {{{
 * val isr = new InMemoryStatsReceiver
 * isr.counter("a", "b", "foo")
 * isr.counter("a/b", "bar")
 *
 * isr.print(Console.out) // will print two lines "a/b/foo 0" and "a/b/bar 0"
 *
 * assert(isr.counters(Seq("a", "b", "foo") == 0)) // ok
 * assert(isr.counters(Seq("a", "b", "bar") == 0)) // fail
 * }}}
 **/
class InMemoryStatsReceiver
  extends WithHistogramDetails
  with StatsReceiver {
  val repr = this

  val counters: mutable.Map[Seq[String], Int] =
    new ConcurrentHashMap[Seq[String], Int]().asScala

  val stats: mutable.Map[Seq[String], Seq[Float]] =
    new ConcurrentHashMap[Seq[String], Seq[Float]]().asScala

  val gauges: mutable.Map[Seq[String], () => Float] =
    new ConcurrentHashMap[Seq[String], () => Float]().asScala

  /**
   * Creates a [[ReadableCounter]] of the given `name`.
   */
  def counter(name: String*): ReadableCounter =
    new ReadableCounter {

      def incr(delta: Int): Unit = counters.synchronized {
        val oldValue = apply()
        counters(name) = oldValue + delta
      }
      def apply(): Int = counters.getOrElse(name, 0)

      override def toString: String =
        s"Counter(${name.mkString("/")}=${apply()})"
    }

  /**
   * Creates a [[ReadableStat]] of the given `name`.
   */
  def stat(name: String*): ReadableStat =
    new ReadableStat {
      def add(value: Float): Unit = stats.synchronized {
        val oldValue = apply()
        stats(name) = oldValue :+ value
      }
      def apply(): Seq[Float] = stats.getOrElse(name, Seq.empty)

      override def toString: String = {
        val vals = apply()
        val valStr = if (vals.length <= 3) {
          vals.mkString("[", ",", "]")
        } else {
          val numOmitted = vals.length - 3
          vals.take(3).mkString("[", ",", s"... (omitted $numOmitted value(s))]")
        }
        s"Stat(${name.mkString("/")}=$valStr)"
      }
    }

  /**
   * Creates a [[Gauge]] of the given `name`.
   */
  def addGauge(name: String*)(f: => Float): Gauge = {
    val gauge = new Gauge {
      def remove(): Unit = {
        gauges -= name
      }

      override def toString: String = {
        // avoid holding a reference to `f`
        val current = gauges.get(name) match {
          case Some(fn) => fn()
          case None => -0.0f
        }
        s"Gauge(${name.mkString("/")}=$current)"
      }
    }
    gauges += name -> (() => f)
    gauge
  }

  override def toString: String = "InMemoryStatsReceiver"

  /**
   * Dumps this in-memory stats receiver to the given [[PrintStream]].
   */
  def print(p: PrintStream): Unit = {
    for ((k, v) <- counters)
      p.printf("%s %d\n", k.mkString("/"), v: java.lang.Integer)
    for ((k, g) <- gauges)
      p.printf("%s %f\n", k.mkString("/"), g(): java.lang.Float)
    for ((k, s) <- stats if s.size > 0)
      p.printf("%s %f\n", k.mkString("/"), (s.sum / s.size): java.lang.Float)
  }

  /**
   * Clears all registered counters, gauges and stats.
   * @note this is not atomic. If new metrics are added while this method is executing, those metrics may remain.
   */
  def clear(): Unit = {
    counters.clear()
    stats.clear()
    gauges.clear()
  }

  private[this] def toHistogramDetail(addedValues: Seq[Float]): HistogramDetail = {
    def nearestPosInt(f: Float): Int = {
      if (f < 0) 0
      else if (f >= Int.MaxValue) Int.MaxValue - 1
      else f.toInt
    }
    
    new HistogramDetail {
      def counts = { 
        addedValues
          .map { x => nearestPosInt(x) }
          .groupBy(identity)
          .mapValues(_.size)
          .toSeq.sortWith(_._1 < _._1)
          .map { case (k, v) => BucketAndCount(k, k + 1, v) }
      }
    }
  }

  def histogramDetails: Map[String, HistogramDetail] = stats.toMap.map { 
    case (k, v) => (k.mkString("/"), toHistogramDetail(v))
  }
}

/**
 * A variation of [[Counter]] that also supports reading of the current value via the `apply` method.
 */
trait ReadableCounter extends Counter {
  def apply(): Int
}

/**
 * A variation of [[Stat]] that also supports reading of the current time series via the `apply` method.
 */
trait ReadableStat extends Stat {
  def apply(): Seq[Float]
}