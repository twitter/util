package com.twitter.finagle.stats

object NullStatsReceiver extends NullStatsReceiver {
  def get() = this
}

/**
 * A no-op StatsReceiver. Metrics are not recorded, making this receiver useful
 * in unit tests and as defaults in situations where metrics are not strictly
 * required.
 */
class NullStatsReceiver extends StatsReceiver {
  val repr = this
  override def isNull = true

  private[this] val NullCounter = new Counter { def incr(delta: Int) {} }
  private[this] val NullStat = new Stat { def add(value: Float) {}}
  private[this] val NullGauge = new Gauge { def remove() {} }

  def counter(name: String*) = NullCounter
  def stat(name: String*) = NullStat
  def addGauge(name: String*)(f: => Float) = NullGauge

  override def provideGauge(name: String*)(f: => Float): Unit = ()

  override def scope(namespace: String): StatsReceiver = this

  override def scopeSuffix(suffix: String): StatsReceiver = this

  override def toString = "NullStatsReceiver"
}
