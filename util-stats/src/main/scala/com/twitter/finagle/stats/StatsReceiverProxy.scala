package com.twitter.finagle.stats

trait StatsReceiverProxy extends StatsReceiver {
  def self: StatsReceiver

  val repr = self
  override def isNull = self.isNull
  def counter(names: String*) = self.counter(names:_*)
  def stat(names: String*) = self.stat(names:_*)
  def addGauge(names: String*)(f: => Float) = self.addGauge(names:_*)(f)
}
