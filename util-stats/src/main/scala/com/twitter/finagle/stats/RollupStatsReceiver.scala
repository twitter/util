package com.twitter.finagle.stats

/**
 * A RollupStatsReceiver reports stats on multiple Counter/Stat/Gauge based on the sequence of
 * names you pass.
 * e.g.
 * counter("errors", "clientErrors", "java_net_ConnectException").incr()
 * will actually increment those three counters:
 * - "/errors"
 * - "/errors/clientErrors"
 * - "/errors/clientErrors/java_net_ConnectException"
 */
class RollupStatsReceiver(protected val self: StatsReceiver) extends StatsReceiverProxy {

  private[this] def tails[A](s: Seq[A]): Seq[Seq[A]] = {
    s match {
      case s @ Seq(_) =>
        Seq(s)

      case Seq(hd, tl @ _*) =>
        Seq(Seq(hd)) ++ (tails(tl) map { t => Seq(hd) ++ t })
    }
  }

  override def counter(metricBuilder: MetricBuilder) = new Counter {
    private[this] val allCounters = BroadcastCounter(
      tails(metricBuilder.name).map(n => self.counter(metricBuilder.withName(n: _*)))
    )
    def incr(delta: Long): Unit = allCounters.incr(delta)
    def metadata: Metadata = allCounters.metadata
  }
  override def stat(metricBuilder: MetricBuilder) = new Stat {
    private[this] val allStats = BroadcastStat(
      tails(metricBuilder.name).map(n => self.stat(metricBuilder.withName(n: _*)))
    )
    def add(value: Float): Unit = allStats.add(value)
    def metadata: Metadata = allStats.metadata
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float) = new Gauge {
    private[this] val underlying =
      tails(metricBuilder.name).map(n => self.addGauge(metricBuilder.withName(n: _*))(f))
    def remove(): Unit = underlying.foreach(_.remove())
    def metadata: Metadata = MultiMetadata(underlying.map(_.metadata))
  }
}
