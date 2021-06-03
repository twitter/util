package com.twitter.finagle.stats

/**
 * A [[StatsReceiver]] that adjusts the passed [[Verbosity]] of an underlying stats receiver to
 * a given `defaultVerbosity`.
 */
class VerbosityAdjustingStatsReceiver(
  protected val self: StatsReceiver,
  defaultVerbosity: Verbosity)
    extends StatsReceiverProxy {

  override def stat(metricBuilder: MetricBuilder): Stat = {
    self.stat(metricBuilder.withVerbosity(defaultVerbosity))
  }

  override def counter(metricBuilder: MetricBuilder): Counter = {
    self.counter(metricBuilder.withVerbosity(defaultVerbosity))
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    self.addGauge(metricBuilder.withVerbosity(defaultVerbosity))(f)
  }

}
