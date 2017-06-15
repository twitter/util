package com.twitter.finagle.stats

object NullStatsReceiver extends NullStatsReceiver {
  def get() = this
}

/**
 * A no-op [[StatsReceiver]]. Metrics are not recorded, making this receiver useful
 * in unit tests and as defaults in situations where metrics are not strictly
 * required.
 */
class NullStatsReceiver extends StatsReceiver {

  def repr: NullStatsReceiver = this

  private[this] val NullCounter = new Counter { def incr(delta: Int): Unit = () }
  private[this] val NullStat = new Stat { def add(value: Float): Unit = () }
  private[this] val NullGauge = new Gauge { def remove(): Unit = () }

  def counter(name: String*): Counter = NullCounter
  def stat(name: String*): Stat = NullStat
  def addGauge(name: String*)(f: => Float): Gauge = NullGauge

  override def provideGauge(name: String*)(f: => Float): Unit = ()

  override def scope(namespace: String): StatsReceiver = this

  override def scopeSuffix(suffix: String): StatsReceiver = this

  override def isNull: Boolean = true

  override def toString: String = "NullStatsReceiver"
}
