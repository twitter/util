package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.util.Try

/**
 * A proxy [[StatsReceiver]] that delegates all calls to the `self` stats receiver.
 */
trait StatsReceiverProxy extends StatsReceiver with DelegatingStatsReceiver {

  protected def self: StatsReceiver

  def repr: AnyRef = self.repr

  def counter(metricBuilder: MetricBuilder): Counter = self.counter(metricBuilder)
  def stat(metricBuilder: MetricBuilder): Stat = self.stat(metricBuilder)
  def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = self.addGauge(metricBuilder)(f)

  override private[finagle] def scopeTranslation: NameTranslatingStatsReceiver.Mode =
    self.scopeTranslation

  override def registerExpression(
    expressionSchema: ExpressionSchema
  ): Try[Unit] = self.registerExpression(expressionSchema)

  def underlying: Seq[StatsReceiver] = Seq(self)

  override def isNull: Boolean = self.isNull
  override def toString: String = self.toString
}
