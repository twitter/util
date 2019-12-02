package com.twitter.finagle.stats

/**
 * A [[StatsReceiver]] that adjusts the passed [[Verbosity]] of an underlying stats receiver to
 * a given `defaultVerbosity`.
 */
class VerbosityAdjustingStatsReceiver(
  protected val self: StatsReceiver,
  defaultVerbosity: Verbosity)
    extends StatsReceiverProxy {

  override def stat(histogramSchema: HistogramSchema): Stat = {
    self.stat(HistogramSchema(histogramSchema.metricBuilder.withVerbosity(defaultVerbosity)))
  }

  override def counter(counterSchema: CounterSchema): Counter = {
    self.counter(CounterSchema(counterSchema.metricBuilder.withVerbosity(defaultVerbosity)))
  }

  override def addGauge(gaugeSchema: GaugeSchema)(f: => Float): Gauge = {
    self.addGauge(GaugeSchema(gaugeSchema.metricBuilder.withVerbosity(defaultVerbosity)))(f)
  }

}
