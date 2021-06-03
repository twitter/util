package com.twitter.finagle.stats

object NullStatsReceiver extends NullStatsReceiver {
  def get(): NullStatsReceiver.type = this

  val NullCounter: Counter = new Counter {
    def incr(delta: Long): Unit = ()
    def metadata: Metadata = NoMetadata
  }

  val NullStat: Stat = new Stat {
    def add(value: Float): Unit = ()
    def metadata: Metadata = NoMetadata
  }

  val NullGauge: Gauge = new Gauge {
    def remove(): Unit = ()
    def metadata: Metadata = NoMetadata
  }
}

/**
 * A no-op [[StatsReceiver]]. Metrics are not recorded, making this receiver useful
 * in unit tests and as defaults in situations where metrics are not strictly
 * required.
 */
class NullStatsReceiver extends StatsReceiver {
  import NullStatsReceiver._

  def repr: NullStatsReceiver = this

  def counter(metricBuilder: MetricBuilder) = NullCounter
  def stat(metricBuilder: MetricBuilder) = NullStat
  def addGauge(metricBuilder: MetricBuilder)(f: => Float) = NullGauge

  override def scope(namespace: String): StatsReceiver = this

  override def scopeSuffix(suffix: String): StatsReceiver = this

  override def isNull: Boolean = true

  override def toString: String = "NullStatsReceiver"

}
