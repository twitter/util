package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema

/**
 * A proxy [[StatsReceiver]] that delegates all calls to the `self` stats receiver.
 */
trait StatsReceiverProxy extends StatsReceiver with DelegatingStatsReceiver {

  protected def self: StatsReceiver

  def repr: AnyRef = self.repr

  def counter(schema: CounterSchema): Counter = self.counter(schema)
  def stat(schema: HistogramSchema): Stat = self.stat(schema)
  def addGauge(schema: GaugeSchema)(f: => Float): Gauge = self.addGauge(schema)(f)

  override protected[finagle] def registerExpression(expressionSchema: ExpressionSchema): Unit =
    self.registerExpression(expressionSchema)

  def underlying: Seq[StatsReceiver] = Seq(self)

  override def isNull: Boolean = self.isNull
  override def toString: String = self.toString
}
