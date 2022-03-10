package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate` function.
 *
 * @param self The underlying StatsReceiver to which translated `MetricBuilder`s are passed
 */
abstract class TranslatingStatsReceiver(
  protected val self: StatsReceiver)
    extends StatsReceiverProxy {

  protected def translate(builder: MetricBuilder): MetricBuilder

  override def counter(metricBuilder: MetricBuilder): Counter =
    self.counter(translate(metricBuilder))

  override def stat(metricBuilder: MetricBuilder): Stat =
    self.stat(translate(metricBuilder))

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    self.addGauge(translate(metricBuilder))(f)
  }

  override def toString: String = s"Translating($self)"
}
