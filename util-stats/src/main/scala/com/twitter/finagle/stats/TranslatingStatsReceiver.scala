package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.Identity

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
}

private object TranslatingStatsReceiver {

  private final class IdentityTranslatingStatsReceiver(sr: StatsReceiver, f: Identity => Identity)
      extends TranslatingStatsReceiver(sr) {
    protected def translate(builder: MetricBuilder): MetricBuilder =
      builder.withIdentity(f(builder.identity))
  }

  /**
   * A [[TranslatingStatsReceiver]] for working with both dimensional and hierarchical metrics.
   *
   * Translates the [[MetricBuilder]] to prepend the label value as a scope in addition to adding
   * it to the labels map.
   */
  final class LabelTranslatingStatsReceiver(
    sr: StatsReceiver,
    labelName: String,
    labelValue: String)
      extends TranslatingStatsReceiver(sr) {

    require(labelName.nonEmpty)
    require(labelValue.nonEmpty)

    private[this] val labelPair = labelName -> labelValue

    protected def translate(builder: MetricBuilder): MetricBuilder =
      builder.withIdentity(newIdentity(builder.identity))

    private[this] def newIdentity(identity: Identity): Identity = {
      identity.copy(labels = identity.labels + labelPair)
    }
  }

  /** Translate the identity of a metric */
  def translateIdentity(sr: StatsReceiver)(f: Identity => Identity): StatsReceiver =
    new IdentityTranslatingStatsReceiver(sr, f)
}
