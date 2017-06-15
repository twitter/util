package com.twitter.finagle.stats

/**
 * A proxy [[StatsReceiver]] that delegates all calls to the `self` stats receiver.
 */
trait StatsReceiverProxy extends StatsReceiver with DelegatingStatsReceiver {

  protected def self: StatsReceiver

  def repr: AnyRef = self.repr

  def counter(names: String*): Counter = self.counter(names:_*)
  def stat(names: String*): Stat = self.stat(names:_*)
  def addGauge(names: String*)(f: => Float): Gauge = self.addGauge(names:_*)(f)

  def underlying: Seq[StatsReceiver] = Seq(self)

  override def isNull: Boolean = self.isNull
  override def toString: String = self.toString
}
