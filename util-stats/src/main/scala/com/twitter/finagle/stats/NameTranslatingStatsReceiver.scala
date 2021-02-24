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

  override def counter(counterSchema: CounterSchema): Counter = {
    self.counter(
      CounterSchema(
        counterSchema.metricBuilder.withName(translate(counterSchema.metricBuilder.name): _*)))
  }

  override def stat(histogramSchema: HistogramSchema): Stat = {
    self.stat(
      HistogramSchema(
        histogramSchema.metricBuilder.withName(translate(histogramSchema.metricBuilder.name): _*)))
  }

  override def addGauge(gaugeSchema: GaugeSchema)(f: => Float): Gauge = {
    self.addGauge(
      GaugeSchema(
        gaugeSchema.metricBuilder.withName(translate(gaugeSchema.metricBuilder.name): _*)))(f)
  }

  override def toString: String = s"$self/$namespacePrefix"
}
