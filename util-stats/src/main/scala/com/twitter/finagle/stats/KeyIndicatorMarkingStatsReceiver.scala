package com.twitter.finagle.stats

/**
 * A StatsReceiver proxy that configures all counters, stats, and gauges marked as KeyIndicators.
 * This is used with [[ReadableIndicator]].
 *
 * @param self The underlying StatsReceiver to which modified schemas are passed
 */
private[finagle] class KeyIndicatorMarkingStatsReceiver(protected val self: StatsReceiver)
    extends StatsReceiverProxy {

  override def counter(metricBuilder: MetricBuilder): Counter = {
    self.counter(metricBuilder.withKeyIndicator())
  }

  override def stat(metricBuilder: MetricBuilder): Stat = {
    self.stat(metricBuilder.withKeyIndicator())
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    self.addGauge(metricBuilder.withKeyIndicator())(f)
  }
}
