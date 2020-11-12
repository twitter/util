package com.twitter.finagle.stats

object DenylistStatsReceiver {

  /**
   * Creates a DenyListStatsReceiver based on a PartialFunction[Seq[String], Boolean].
   *
   * @param underlying a base [[StatsReceiver]].
   * @param pf a PartialFunction that returns true for metrics which should be denylisted,
   *           and false for metrics which should be recorded.
   *           If pf is undefined for a given metric name,
   *           then the metric will NOT be recorded.
   */
  def orElseDenied(
    underlying: StatsReceiver,
    pf: PartialFunction[Seq[String], Boolean]
  ): StatsReceiver =
    new DenylistStatsReceiver(underlying, pf.orElse { case _ => true })

  /**
   * Creates a DenyListStatsReceiver based on a PartialFunction[Seq[String], Boolean].
   *
   * @param underlying a base [[StatsReceiver]].
   * @param pf a PartialFunction that returns true for metrics which should be denylisted,
   *           and false for metrics which should be recorded.
   *           If pf is undefined for a given metric name,
   *           then the metric WILL be recorded.
   */
  def orElseAdmitted(
    underlying: StatsReceiver,
    pf: PartialFunction[Seq[String], Boolean]
  ): StatsReceiver =
    new DenylistStatsReceiver(underlying, pf.orElse { case _ => false })
}

/**
 * A denylisting [[StatsReceiver]].  If the name for a metric is found to be
 * denylisted, nothing is recorded.
 *
 * @param self a base [[StatsReceiver]], used for metrics that aren't
 *        denylisted
 * @param denylisted a predicate that reads a name and returns true to
 *        denylist, and false to let it pass through
 */
class DenylistStatsReceiver(protected val self: StatsReceiver, denylisted: Seq[String] => Boolean)
    extends StatsReceiverProxy {

  override def counter(schema: CounterSchema) =
    getStatsReceiver(schema.metricBuilder.name).counter(schema)

  override def stat(schema: HistogramSchema) =
    getStatsReceiver(schema.metricBuilder.name).stat(schema)

  override def addGauge(schema: GaugeSchema)(f: => Float) =
    getStatsReceiver(schema.metricBuilder.name).addGauge(schema)(f)

  private[this] def getStatsReceiver(name: Seq[String]): StatsReceiver =
    if (denylisted(name)) NullStatsReceiver else self

  override def toString: String = s"DenylistStatsReceiver($self)"
}
