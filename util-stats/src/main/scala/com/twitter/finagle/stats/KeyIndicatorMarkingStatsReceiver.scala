package com.twitter.finagle.stats

/**
 * A StatsReceiver proxy that configures all counters, stats, and gauges marked as KeyIndicators.
 * This is used with [[ReadableIndicator]].
 *
 * @param self The underlying StatsReceiver to which modified schemas are passed
 */
private[finagle] class KeyIndicatorMarkingStatsReceiver(protected val self: StatsReceiver)
    extends StatsReceiverProxy {

  override def counter(counterSchema: CounterSchema): Counter = {
    self.counter(CounterSchema(counterSchema.metricBuilder.withKeyIndicator()))
  }

  override def stat(histogramSchema: HistogramSchema): Stat = {
    self.stat(HistogramSchema(histogramSchema.metricBuilder.withKeyIndicator()))
  }

  override def addGauge(gaugeSchema: GaugeSchema)(f: => Float): Gauge = {
    self.addGauge(GaugeSchema(gaugeSchema.metricBuilder.withKeyIndicator()))(f)
  }
}
