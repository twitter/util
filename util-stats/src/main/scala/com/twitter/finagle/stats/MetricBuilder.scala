package com.twitter.finagle.stats

/**
 * Represents the "role" this service plays with respect to this metric.
 *
 * Usually either Server (the service is processing a request) or Client (the server has sent a
 * request to another service). In many cases, there is no relevant role for a metric, in which
 * case NoRole should be used.
 */
sealed trait SourceRole
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

/**
 * A builder class used to configure settings and metadata for metrics prior to instantiating them.
 * Calling any of the three build methods (counter, gauge, or histogram) will cause the metric to be
 * instantiated in the underlying StatsReceiver.
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
 * @param statsReceiver used for the actual metric creation, set by the StatsReceiver when creating a MetricBuilder
 */
class MetricBuilder(
  val keyIndicator: Boolean = false,
  val description: String = "No description provided",
  val units: MetricUnit = Unspecified,
  val role: SourceRole = NoRoleSpecified,
  val verbosity: Verbosity = Verbosity.Default,
  val sourceClass: Option[String] = None,
  val name: Seq[String] = Seq.empty,
  val relativeName: Seq[String] = Seq.empty,
  val processPath: Option[String] = None,
  // Only persisted and relevant when building histograms.
  val percentiles: IndexedSeq[Double] = IndexedSeq.empty,
  val statsReceiver: StatsReceiver) {

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
    relativeName: Seq[String] = this.relativeName,
    processPath: Option[String] = this.processPath,
    percentiles: IndexedSeq[Double] = this.percentiles
  ): MetricBuilder = {
    new MetricBuilder(
      keyIndicator = keyIndicator,
      description = description,
      units = units,
      role = role,
      verbosity = verbosity,
      sourceClass = sourceClass,
      name = name,
      relativeName = relativeName,
      processPath = processPath,
      percentiles = percentiles,
      statsReceiver = this.statsReceiver
    )
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

  def withName(name: Seq[String]): MetricBuilder = this.copy(name = name)

  def withRelativeName(relativeName: Seq[String]): MetricBuilder =
    if (this.relativeName == Seq.empty) this.copy(relativeName = relativeName) else this

  def withPercentiles(percentiles: IndexedSeq[Double]) = this.copy(percentiles = percentiles)

  /**
   * Generates a CounterSchema which can be used to create a counter in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a CounterSchema describing a counter.
   */
  private[MetricBuilder] def counterSchema: CounterSchema = CounterSchema(this)

  /**
   * Generates a GaugeSchema which can be used to create a gauge in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a GaugeSchema describing a gauge.
   */
  private[MetricBuilder] def gaugeSchema: GaugeSchema = GaugeSchema(this)

  /**
   * Generates a HistogramSchema which can be used to create a histogram in a StatsReceiver.
   * Used to test that builder class correctly propagates configured metadata.
   * @return a HistogramSchema describing a histogram.
   */
  private[MetricBuilder] def histogramSchema: HistogramSchema = HistogramSchema(this)

  /**
   * Produce a counter as described by the builder inside the underlying StatsReceiver.
   * @return the counter created.
   */
  def counter(name: String*): Counter = {
    val schema = this.copy(name = name).counterSchema
    this.statsReceiver.counter(schema)
  }

  /**
   * Produce a gauge as described by the builder inside the underlying StatsReceiver.
   * @return the gauge created.
   */
  def gauge(name: String*)(f: => Float): Gauge = {
    val schema = this.copy(name = name).gaugeSchema
    this.statsReceiver.addGauge(schema)(f)
  }

  /**
   * Produce a histogram as described by the builder inside the underlying StatsReceiver.
   * @return the histogram created.
   */
  def histogram(name: String*): Stat = {
    val schema = this.copy(name = name).histogramSchema
    this.statsReceiver.stat(schema)
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[MetricBuilder]

  override def equals(other: Any): Boolean = other match {
    case that: MetricBuilder =>
      (that.canEqual(this)) &&
        keyIndicator == that.keyIndicator &&
        description == that.description &&
        units == that.units &&
        role == that.role &&
        verbosity == that.verbosity &&
        sourceClass == that.sourceClass &&
        name == that.name &&
        relativeName == that.relativeName &&
        processPath == that.processPath &&
        percentiles == that.percentiles
    case _ => false
  }

  override def hashCode(): Int = {
    val state =
      Seq[AnyRef](
        description,
        units,
        role,
        verbosity,
        sourceClass,
        name,
        relativeName,
        processPath,
        percentiles)
    val hashCodes = keyIndicator.hashCode() +: state.map(_.hashCode())
    hashCodes.foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString(): String = {
    val nameString = name.mkString(metadataScopeSeparator())
    s"MetricBuilder($keyIndicator, $description, $units, $role, $verbosity, $sourceClass, $nameString, $relativeName, $processPath, $percentiles, $statsReceiver)"
  }
}
