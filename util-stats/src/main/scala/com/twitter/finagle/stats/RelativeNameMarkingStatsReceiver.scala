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

  override def counter(counterSchema: CounterSchema): Counter = {
    val schema = CounterSchema(
      counterSchema.metricBuilder
        .withRelativeName(counterSchema.metricBuilder.name: _*))
    self.counter(schema)
  }

  override def stat(histogramSchema: HistogramSchema): Stat = {
    val schema =
      HistogramSchema(
        histogramSchema.metricBuilder
          .withRelativeName(histogramSchema.metricBuilder.name: _*))
    self.stat(schema)
  }

  override def addGauge(gaugeSchema: GaugeSchema)(f: => Float): Gauge = {
    val schema =
      GaugeSchema(
        gaugeSchema.metricBuilder
          .withRelativeName(gaugeSchema.metricBuilder.name: _*))
    self.addGauge(schema)(f)
  }
}
