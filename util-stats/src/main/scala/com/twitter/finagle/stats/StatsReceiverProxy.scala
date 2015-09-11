package com.twitter.finagle.stats

trait StatsReceiverProxy extends StatsReceiver {
  def self: StatsReceiver

  val repr = self
  override def isNull: Boolean = self.isNull
  def counter(names: String*): Counter = self.counter(names:_*)
  def stat(names: String*): Stat = self.stat(names:_*)
  def addGauge(names: String*)(f: => Float): Gauge = self.addGauge(names:_*)(f)

  override def toString: String = self.toString
}
