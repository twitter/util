package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.CounterishGaugeType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.MetricBuilder.MetricType
import com.twitter.finagle.stats.MetricBuilder.UnlatchedCounter
import java.util.function.Supplier
import scala.annotation.varargs

/**
 * Represents the "role" this service plays with respect to this metric.
 *
 * Usually either Server (the service is processing a request) or Client (the server has sent a
 * request to another service). In many cases, there is no relevant role for a metric, in which
 * case NoRole should be used.
 */
sealed trait SourceRole {

  /**
   * Java-friendly helper for accessing the object itself.
   */
  def getInstance(): SourceRole = this
}
case object NoRoleSpecified extends SourceRole
case object Client extends SourceRole
case object Server extends SourceRole

/**
 * finagle-stats has configurable scope separators. As this package is wrapped by finagle-stats, we
 * cannot retrieve it from finagle-stats. Consequently, we created this object so that the
 * scope-separator can be passed in for stringification of the MetricBuilder objects.
 */
object metadataScopeSeparator {
  @volatile private var separator: String = "/"

  def apply(): String = separator

  private[finagle] def setSeparator(separator: String): Unit = {
    this.separator = separator
  }
}

object MetricBuilder {

  /**
   * Construct a MethodBuilder.
   *
   * @param keyIndicator indicates whether this metric is crucial to this service (ie, an SLO metric)
   * @param description human-readable description of a metric's significance
   * @param units the unit associated with the metrics value (milliseconds, megabytes, requests, etc)
   * @param role whether the service is playing the part of client or server regarding this metric
   * @param verbosity see StatsReceiver for details
   * @param sourceClass the name of the class which generated this metric (ie, com.twitter.finagle.StatsFilter)
   * @param name the full metric name
   * @param relativeName the relative metric name which will be appended to the scope of the StatsReceiver prior to long term storage
   * @param processPath a universal coordinate for the resource
   * @param percentiles used to indicate buckets for histograms, to be set by the StatsReceiver
   * @param metricType the type of the metric being constructed
   * @param isStandard whether the metric should the standard path separator or defer to the metrics store for the separator
   * @param statsReceiver used for the actual metric creation, set by the StatsReceiver when creating a MetricBuilder
   */
  def apply(
    keyIndicator: Boolean = false,
    description: String = "No description provided",
    units: MetricUnit = Unspecified,
    role: SourceRole = NoRoleSpecified,
    verbosity: Verbosity = Verbosity.Default,
    sourceClass: Option[String] = None,
    name: Seq[String] = Seq.empty,
    labels: Map[String, String] = Map.empty,
    relativeName: Seq[String] = Seq.empty,
    processPath: Option[String] = None,
    percentiles: IndexedSeq[Double] = IndexedSeq.empty,
    isStandard: Boolean = false,
    metricType: MetricType,
    statsReceiver: StatsReceiver
  ): MetricBuilder = {
    new MetricBuilder(
      keyIndicator,
      description,
      units,
      role,
      verbosity,
      sourceClass,
      name,
      labels,
      relativeName,
      processPath,
      percentiles,
      isStandard,
      metricType,
      statsReceiver
    )
  }

  /**
   * Construct a new `MetricBuilder`
   *
   * Simpler method for constructing a `MetricBuilder`. This API is a little more friendly for java
   * users as they can bootstrap a `MetricBuilder` and then proceed to use with `.with` API's rather
   * than being forced to use the mega apply method.
   *
   * @param metricType the type of the metric being constructed.
   * @param statsReceiver used for the actual metric creation, set by the StatsReceiver when creating a MetricBuilder.
   */
  def newBuilder(metricType: MetricType, statsReceiver: StatsReceiver): MetricBuilder =
    apply(metricType = metricType, statsReceiver = statsReceiver)

  /**
   * Indicate the Metric type, [[CounterType]] will create a [[Counter]],
   * [[GaugeType]] will create a standard [[Gauge]], and [[HistogramType]] will create a [[Stat]].
   *
   * @note [[CounterishGaugeType]] will also create a [[Gauge]], but specifically one that
   *       models a [[Counter]].
   */
  sealed trait MetricType {
    def toPrometheusString: String
    def toJsonString: String
  }
  case object CounterType extends MetricType {
    def toJsonString: String = "counter"
    def toPrometheusString: String = toJsonString
  }
  case object CounterishGaugeType extends MetricType {
    def toJsonString: String = "counterish_gauge"
    def toPrometheusString: String = "gauge"
  }
  case object GaugeType extends MetricType {
    def toJsonString: String = "gauge"
    def toPrometheusString: String = toJsonString
  }
  case object HistogramType extends MetricType {
    def toJsonString: String = "histogram"
    def toPrometheusString: String = "summary"
  }

  /** Counter type that will be always unlatched */
  case object UnlatchedCounter extends MetricType {
    def toJsonString: String = "unlatched_counter"
    def toPrometheusString: String = "counter"
  }
}

sealed trait Metadata {

  /**
   * Extract the MetricBuilder from Metadata
   *
   * Will return `None` if it's `NoMetadata`
   */
  def toMetricBuilder: Option[MetricBuilder] = this match {
    case metricBuilder: MetricBuilder => Some(metricBuilder)
    case NoMetadata => None
    case MultiMetadata(schemas) =>
      schemas.find(_ != NoMetadata).flatMap(_.toMetricBuilder)
  }
}

case object NoMetadata extends Metadata

case class MultiMetadata(schemas: Seq[Metadata]) extends Metadata

/**
 * A builder class used to configure settings and metadata for metrics prior to instantiating them.
 * Calling any of the three build methods (counter, gauge, or histogram) will cause the metric to be
 * instantiated in the underlying StatsReceiver.
 */
class MetricBuilder private (
  val keyIndicator: Boolean,
  val description: String,
  val units: MetricUnit,
  val role: SourceRole,
  val verbosity: Verbosity,
  val sourceClass: Option[String],
  val name: Seq[String],
  private[finagle] val labels: Map[String, String],
  val relativeName: Seq[String],
  val processPath: Option[String],
  // Only persisted and relevant when building histograms.
  val percentiles: IndexedSeq[Double],
  val isStandard: Boolean,
  val metricType: MetricType,
  val statsReceiver: StatsReceiver)
    extends Metadata {

  /**
   * This copy method omits statsReceiver and percentiles as arguments because they should be
   * set only during the initial creation of a MetricBuilder by a StatsReceiver, which should use
   * itself as the value for statsReceiver.
   */
  private[this] def copy(
    keyIndicator: Boolean = this.keyIndicator,
    description: String = this.description,
    units: MetricUnit = this.units,
    role: SourceRole = this.role,
    verbosity: Verbosity = this.verbosity,
    sourceClass: Option[String] = this.sourceClass,
    name: Seq[String] = this.name,
    labels: Map[String, String] = this.labels,
    relativeName: Seq[String] = this.relativeName,
    processPath: Option[String] = this.processPath,
    isStandard: Boolean = this.isStandard,
    percentiles: IndexedSeq[Double] = this.percentiles,
    metricType: MetricType = this.metricType
  ): MetricBuilder = {
    new MetricBuilder(
      keyIndicator = keyIndicator,
      description = description,
      units = units,
      role = role,
      verbosity = verbosity,
      sourceClass = sourceClass,
      name = name,
      labels = labels,
      relativeName = relativeName,
      processPath = processPath,
      percentiles = percentiles,
      isStandard = isStandard,
      statsReceiver = this.statsReceiver,
      metricType = metricType
    )
  }

  private[finagle] def withStandard: MetricBuilder = {
    this.copy(isStandard = true)
  }

  private[finagle] def withUnlatchedCounter: MetricBuilder = {
    require(this.metricType == CounterType, "unable to set a non counter to unlatched counter")
    this.copy(metricType = UnlatchedCounter)
  }

  def withKeyIndicator(isKeyIndicator: Boolean = true): MetricBuilder =
    this.copy(keyIndicator = isKeyIndicator)

  def withDescription(desc: String): MetricBuilder = this.copy(description = desc)

  def withVerbosity(verbosity: Verbosity): MetricBuilder = this.copy(verbosity = verbosity)

  def withSourceClass(sourceClass: Option[String]): MetricBuilder =
    this.copy(sourceClass = sourceClass)

  def withIdentifier(processPath: Option[String]): MetricBuilder =
    this.copy(processPath = processPath)

  def withUnits(units: MetricUnit): MetricBuilder = this.copy(units = units)

  def withRole(role: SourceRole): MetricBuilder = this.copy(role = role)

  @varargs
  def withName(name: String*): MetricBuilder = this.copy(name = name)

  // Temporarily private while API's are under development
  private[finagle] def withLabels(labels: Map[String, String]): MetricBuilder =
    this.copy(labels = labels)

  @varargs
  def withRelativeName(relativeName: String*): MetricBuilder =
    if (this.relativeName == Seq.empty) this.copy(relativeName = relativeName) else this

  @varargs
  def withPercentiles(percentiles: Double*): MetricBuilder =
    this.copy(percentiles = percentiles.toIndexedSeq)

  def withCounterishGauge: MetricBuilder = {
    require(
      this.metricType == GaugeType,
      "Cannot create a CounterishGauge from a Counter or Histogram")
    this.copy(metricType = CounterishGaugeType)
  }

  /**
   * Generates a CounterType MetricBuilder which can be used to create a counter in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a Countertype MetricBuilder.
   */
  private[MetricBuilder] final def counterSchema: MetricBuilder =
    this.copy(metricType = CounterType)

  /**
   * Generates a GaugeType MetricBuilder which can be used to create a gauge in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a GaugeType MetricBuilder.
   */
  private[MetricBuilder] final def gaugeSchema: MetricBuilder = this.copy(metricType = GaugeType)

  /**
   * Generates a HistogramType MetricBuilder which can be used to create a histogram in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a HistogramType MetricBuilder.
   */
  private[MetricBuilder] final def histogramSchema: MetricBuilder =
    this.copy(metricType = HistogramType)

  /**
   * Generates a CounterishGaugeType MetricBuilder which can be used to create a counter-ish gauge
   * in a StatsReceiver. Used to test that builder class correctly propagates configured metadata.
   * @return a CounterishGaugeType MetricBuilder.
   */
  private[MetricBuilder] final def counterishGaugeSchema: MetricBuilder =
    this.copy(metricType = CounterishGaugeType)

  /**
   * Produce a counter as described by the builder inside the underlying StatsReceiver.
   * @return the counter created.
   */
  @varargs
  final def counter(name: String*): Counter = {
    this.statsReceiver.validateMetricType(this, CounterType)
    this.statsReceiver.counter(this.withName(name: _*))
  }

  /**
   * Produce a gauge as described by the builder inside the underlying StatsReceiver.
   * @return the gauge created.
   */
  final def gauge(name: String*)(f: => Float): Gauge = {
    this.statsReceiver.validateMetricType(this, GaugeType)
    this.statsReceiver.addGauge(this.withName(name: _*))(f)
  }

  /**
   * Produce a gauge as described by the builder inside the underlying StatsReceiver.
   * This API is for Java compatibility
   * @return the gauge created.
   */
  @varargs
  final def gauge(f: Supplier[Float], name: String*): Gauge = {
    this.statsReceiver.validateMetricType(this, GaugeType)
    this.statsReceiver.addGauge(this.withName(name: _*))(f.get())
  }

  /**
   * Produce a histogram as described by the builder inside the underlying StatsReceiver.
   * @return the histogram created.
   */
  @varargs
  final def histogram(name: String*): Stat = {
    this.statsReceiver.validateMetricType(this, HistogramType)
    this.statsReceiver.stat(this.withName(name: _*))
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[MetricBuilder]

  override def equals(other: Any): Boolean = other match {
    case that: MetricBuilder =>
      that.canEqual(this) &&
        keyIndicator == that.keyIndicator &&
        description == that.description &&
        units == that.units &&
        role == that.role &&
        verbosity == that.verbosity &&
        sourceClass == that.sourceClass &&
        name == that.name &&
        labels == that.labels &&
        relativeName == that.relativeName &&
        processPath == that.processPath &&
        percentiles == that.percentiles &&
        metricType == that.metricType
    case _ => false
  }

  override def hashCode(): Int = {
    val state =
      Array[AnyRef](
        description,
        units,
        role,
        verbosity,
        sourceClass,
        name,
        labels,
        relativeName,
        processPath,
        percentiles,
        metricType)

    state.foldLeft(keyIndicator.hashCode())((a, b) => 31 * a + b.hashCode())
  }

  override def toString(): String = {
    val nameString = name.mkString(metadataScopeSeparator())
    s"MetricBuilder($keyIndicator, $description, $units, $role, $verbosity, $sourceClass, $nameString, $relativeName, $processPath, $percentiles, $metricType, $statsReceiver)"
  }
}
