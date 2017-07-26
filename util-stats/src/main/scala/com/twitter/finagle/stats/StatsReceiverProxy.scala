package com.twitter.finagle.stats

/**
 * A proxy [[StatsReceiver]] that delegates all calls to the `self` stats receiver.
 */
trait StatsReceiverProxy extends StatsReceiver with DelegatingStatsReceiver {

  protected def self: StatsReceiver

  def repr: AnyRef = self.repr

  def counter(verbosity: Verbosity, names: String*): Counter = self.counter(verbosity, names: _*)
  def stat(verbosity: Verbosity, names: String*): Stat = self.stat(verbosity, names: _*)
  def addGauge(verbosity: Verbosity, names: String*)(f: => Float): Gauge =
    self.addGauge(verbosity, names: _*)(f)

  def underlying: Seq[StatsReceiver] = Seq(self)

  override def isNull: Boolean = self.isNull
  override def toString: String = self.toString
}
