package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 *
 * @param namespacePrefix the namespace used for translations
 */
abstract class NameTranslatingStatsReceiver(
  protected val self: StatsReceiver,
  namespacePrefix: String)
    extends StatsReceiverProxy {

  def this(self: StatsReceiver) = this(self, "<namespacePrefix>")

  protected def translate(name: Seq[String]): Seq[String]

  override def counter(metricBuilder: MetricBuilder): Counter = {
    self.counter(metricBuilder.withName(translate(metricBuilder.name): _*))
  }

  override def stat(metricBuilder: MetricBuilder): Stat = {
    self.stat(metricBuilder.withName(translate(metricBuilder.name): _*))
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    self.addGauge(metricBuilder.withName(translate(metricBuilder.name): _*))(f)
  }

  override def toString: String = s"$self/$namespacePrefix"
}
