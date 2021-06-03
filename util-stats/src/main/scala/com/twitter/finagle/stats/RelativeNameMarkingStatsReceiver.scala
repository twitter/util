package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that populates relativeName for all counter, stat, and gauge
 * schemas/metadata.
 *
 * @param self The underlying StatsReceiver to which modified schemas are passed
 */
class RelativeNameMarkingStatsReceiver(
  protected val self: StatsReceiver)
    extends StatsReceiverProxy {

  override def counter(metricBuilder: MetricBuilder): Counter = {
    val schema = metricBuilder.withRelativeName(metricBuilder.name: _*)
    self.counter(schema)
  }

  override def stat(metricBuilder: MetricBuilder): Stat = {
    val schema = metricBuilder.withRelativeName(metricBuilder.name: _*)
    self.stat(schema)
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    val schema = metricBuilder
      .withRelativeName(metricBuilder.name: _*)
    self.addGauge(schema)(f)
  }
}
