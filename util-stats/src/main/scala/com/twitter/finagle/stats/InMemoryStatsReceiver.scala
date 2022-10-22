package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.exp.ExpressionSchema.ExpressionCollisionException
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.util.Throw
import com.twitter.util.Try
import java.io.PrintStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.varargs
import scala.collection.compat._
import scala.collection.SortedMap
import scala.collection.mutable
import scala.collection.{Map => scalaMap}
import scala.jdk.CollectionConverters._

object InMemoryStatsReceiver {
  private[stats] implicit class RichMap[K, V](val self: mutable.Map[K, V]) {
    def mapKeys[T](func: K => T): mutable.Map[T, V] = {
      for ((k, v) <- self) yield {
        func(k) -> v
      }
    }

    def toSortedMap(implicit ordering: Ordering[K]): SortedMap[K, V] = {
      SortedMap[K, V]() ++ self
    }
  }

  private[stats] def statValuesToStr(values: Seq[Float]): String = {
    if (values.length <= MaxStatsValues) {
      values.mkString("[", ",", "]")
    } else {
      val numOmitted = values.length - MaxStatsValues
      values.take(MaxStatsValues).mkString("[", ",", OmittedValuesStr.format(numOmitted))
    }
  }

  private[stats] val OmittedValuesStr = "... (omitted %s value(s))]"
  private[stats] val MaxStatsValues = 3
}

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
class InMemoryStatsReceiver extends StatsReceiver with WithHistogramDetails {
  import InMemoryStatsReceiver._

  def repr: InMemoryStatsReceiver = this

  val verbosity: mutable.Map[Seq[String], Verbosity] =
    new ConcurrentHashMap[Seq[String], Verbosity]().asScala

  val counters: mutable.Map[Seq[String], Long] =
    new ConcurrentHashMap[Seq[String], Long]().asScala

  val stats: mutable.Map[Seq[String], Seq[Float]] =
    new ConcurrentHashMap[Seq[String], Seq[Float]]().asScala

  val gauges: mutable.Map[Seq[String], () => Float] =
    new ConcurrentHashMap[Seq[String], () => Float]().asScala

  val schemas: mutable.Map[Seq[String], MetricBuilder] =
    new ConcurrentHashMap[Seq[String], MetricBuilder]().asScala

  val expressions: mutable.Map[ExpressionSchemaKey, ExpressionSchema] =
    new ConcurrentHashMap[ExpressionSchemaKey, ExpressionSchema]().asScala

  // duplicate metric name -> duplication times
  val duplicatedMetrics: mutable.Map[Seq[String], Long] =
    new ConcurrentHashMap[Seq[String], Long]().asScala

  private def recordDup(metricBuilder: MetricBuilder): Unit = {
    if (!duplicatedMetrics.contains(metricBuilder.name)) {
      duplicatedMetrics(metricBuilder.name) = 1
    } else {
      duplicatedMetrics(metricBuilder.name) = duplicatedMetrics(metricBuilder.name) + 1
    }
  }

  @varargs
  override def counter(name: String*): ReadableCounter =
    counter(MetricBuilder.forCounter.withName(name: _*))

  /**
   * Creates a [[ReadableCounter]] of the given `name`.
   */
  def counter(metricBuilder: MetricBuilder): ReadableCounter = {
    validateMetricType(metricBuilder, CounterType)
    new ReadableCounter {

      verbosity += metricBuilder.name -> metricBuilder.verbosity

      // eagerly initialize
      counters.synchronized {
        if (!counters.contains(metricBuilder.name)) {
          counters(metricBuilder.name) = 0
          schemas(metricBuilder.name) = metricBuilder
        } else {
          recordDup(metricBuilder)
        }
      }

      def incr(delta: Long): Unit = counters.synchronized {
        val oldValue = apply()
        counters(metricBuilder.name) = oldValue + delta
      }

      def apply(): Long = counters.getOrElse(metricBuilder.name, 0)

      def metadata: Metadata = metricBuilder

      override def toString: String =
        s"Counter(${metricBuilder.name.mkString("/")}=${apply()})"
    }
  }

  @varargs
  override def stat(name: String*): ReadableStat =
    stat(MetricBuilder.forStat.withName(name: _*))

  /**
   * Creates a [[ReadableStat]] of the given `name`.
   */
  def stat(metricBuilder: MetricBuilder): ReadableStat = {
    validateMetricType(metricBuilder, HistogramType)
    new ReadableStat {

      verbosity += metricBuilder.name -> metricBuilder.verbosity

      // eagerly initialize
      stats.synchronized {
        if (!stats.contains(metricBuilder.name)) {
          stats(metricBuilder.name) = Nil
          schemas(metricBuilder.name) = metricBuilder
        } else {
          recordDup(metricBuilder)
        }
      }

      def add(value: Float): Unit = stats.synchronized {
        val oldValue = apply()
        stats(metricBuilder.name) = oldValue :+ value
      }
      def apply(): Seq[Float] = stats.getOrElse(metricBuilder.name, Seq.empty)

      def metadata: Metadata = metricBuilder

      override def toString: String = {
        val vals = apply()
        s"Stat(${metricBuilder.name.mkString("/")}=${statValuesToStr(vals)})"
      }
    }
  }

  /**
   * Creates a [[Gauge]] of the given `name`.
   */
  def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    validateMetricType(metricBuilder, GaugeType)
    new Gauge {

      gauges += metricBuilder.name -> (() => f)
      schemas += metricBuilder.name -> metricBuilder
      verbosity += metricBuilder.name -> metricBuilder.verbosity

      def remove(): Unit = {
        gauges -= metricBuilder.name
        schemas -= metricBuilder.name
      }

      def metadata: Metadata = metricBuilder

      override def toString: String = {
        // avoid holding a reference to `f`
        val current = gauges.get(metricBuilder.name) match {
          case Some(fn) => fn()
          case None => -0.0f
        }
        s"Gauge(${metricBuilder.name.mkString("/")}=$current)"
      }
    }
  }

  override def toString: String = "InMemoryStatsReceiver"

  /**
   * Dumps this in-memory stats receiver to the given `PrintStream`.
   * @param p the `PrintStream` to which to write in-memory values.
   */
  def print(p: PrintStream): Unit = {
    print(p, includeHeaders = false)
  }

  /**
   * Dumps this in-memory stats receiver to the given `PrintStream`.
   * @param p the `PrintStream` to which to write in-memory values.
   * @param includeHeaders optionally include printing underlines headers for the different types
   *                       of stats printed, e.g., "Counters:", "Gauges:", "Stats;"
   */
  def print(p: PrintStream, includeHeaders: Boolean): Unit = {
    val sortedCounters = counters.mapKeys(_.mkString("/")).toSortedMap
    val sortedGauges = gauges.mapKeys(_.mkString("/")).toSortedMap
    val sortedStats = stats.mapKeys(_.mkString("/")).toSortedMap

    if (includeHeaders && sortedCounters.nonEmpty) {
      p.println("Counters:")
      p.println("---------")
    }
    for ((k, v) <- sortedCounters)
      p.println(f"$k%s $v%d")
    if (includeHeaders && sortedGauges.nonEmpty) {
      p.println("\nGauges:")
      p.println("-------")
    }
    for ((k, g) <- sortedGauges) {
      p.println("%s %f".formatLocal(Locale.US, k, g()))
    }
    if (includeHeaders && sortedStats.nonEmpty) {
      p.println("\nStats:")
      p.println("------")
    }
    for ((k, s) <- sortedStats if s.nonEmpty) {
      p.println("%s %f %s".formatLocal(Locale.US, k, s.sum / s.size, statValuesToStr(s)))
    }
  }

  /**
   * Dumps this in-memory stats receiver's MetricMetadata to the given `PrintStream`.
   * @param p the `PrintStream` to which to write in-memory metadata.
   */
  def printSchemas(p: PrintStream): Unit = {
    val sortedSchemas = schemas.mapKeys(_.mkString("/")).toSortedMap
    for ((k, schema) <- sortedSchemas) {
      p.println(s"$k $schema")
    }
  }

  /**
   * Clears all registered counters, gauges, stats, expressions, and their metadata.
   * @note this is not atomic. If new metrics are added while this method is executing, those metrics may remain.
   */
  def clear(): Unit = {
    counters.clear()
    stats.clear()
    gauges.clear()
    schemas.clear()
    expressions.clear()
  }

  private[this] def toHistogramDetail(addedValues: Seq[Float]): HistogramDetail = {
    def nearestPosInt(f: Float): Int = {
      if (f < 0) 0
      else if (f >= Int.MaxValue) Int.MaxValue - 1
      else f.toInt
    }

    new HistogramDetail {
      def counts: Seq[BucketAndCount] = {
        addedValues
          .groupBy(nearestPosInt)
          .map { case (k, vs) => BucketAndCount(k, k + 1, vs.size) }
          .toSeq
          .sortBy(_.lowerLimit)
      }
    }
  }

  def histogramDetails: Map[String, HistogramDetail] = stats.toMap.map {
    case (k, v) => (k.mkString("/"), toHistogramDetail(v))
  }

  /**
   * Designed to match the behavior of Metrics::registerExpression().
   */
  override def registerExpression(schema: ExpressionSchema): Try[Unit] = {
    if (expressions.contains(schema.schemaKey())) {
      Throw(
        ExpressionCollisionException(
          s"An expression with the key ${schema.schemaKey()} had already been defined."))
    } else Try { expressions.put(schema.schemaKey(), schema) }
  }

  /**
   * Retrieves all expressions with a given label key and value
   *
   * @return a Map of expressions ([[ExpressionSchemaKey]] -> [[ExpressionSchema]])
   */
  def getAllExpressionsWithLabel(
    key: String,
    value: String
  ): scalaMap[ExpressionSchemaKey, ExpressionSchema] =
    expressions.view.filterKeys(k => k.labels.get(key) == Some(value)).toMap

}

/**
 * A variation of [[Counter]] that also supports reading of the current value via the `apply` method.
 */
trait ReadableCounter extends Counter {
  def apply(): Long
}

/**
 * A variation of [[Stat]] that also supports reading of the current time series via the `apply` method.
 */
trait ReadableStat extends Stat {
  def apply(): Seq[Float]
}
