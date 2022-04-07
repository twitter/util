package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.Identity

/**
 * A [[TranslatingStatsReceiver]] for working with both dimensional and hierarchical metrics.
 *
 * Translates the [[MetricBuilder]] to prepend the label value as a scope in addition to adding
 * it to the labels map.
 */
private class ScopeTranslatingStatsReceiver(
  sr: StatsReceiver,
  labelName: String,
  labelValue: String)
    extends TranslatingStatsReceiver(sr) {

  require(labelName.nonEmpty)
  require(labelValue.nonEmpty)

  private[this] val labelPair = labelName -> labelValue

  protected def translate(builder: MetricBuilder): MetricBuilder =
    builder.withIdentity(newIdentity(builder.identity))

  private[this] def newIdentity(identity: Identity): Identity = identity match {
    case Identity.Full(name, labels) =>
      Identity.Full(labelValue +: name, labels + labelPair)
    case Identity.Hierarchical(name, labels) =>
      Identity.Hierarchical(labelValue +: name, labels + labelPair)
  }

  // We preserve this because unfortunately it is sometimes parsed
  override def toString: String = s"$self/$labelValue"
}
