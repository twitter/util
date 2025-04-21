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
 *
 * @param self the [[StatsReceiver]] to proxy metrics creation to
 * @param hierarchicalOnly whether non-root scopes should be created with the HierarchicalOnly metrics identity
 */
class RollupStatsReceiver(protected val self: StatsReceiver, hierarchicalOnly: Boolean = false)
    extends StatsReceiverProxy {

  /**
   * @return Seq(metrics namespace -> whether the namespace is the "parent" scope)
   */
  private[this] def tails[A](s: Seq[A]): Seq[(Seq[A], Boolean)] = {
    s match {
      case s @ Seq(_) =>
        Seq(s -> true)

      case Seq(hd, tl @ _*) =>
        Seq(Seq(hd) -> true) ++ (tails(tl) map { case (t, _) => (Seq(hd) ++ t) -> false })
    }
  }

  private[this] def metrics(parent: MetricBuilder): Seq[MetricBuilder] = tails(parent.name).map {
    case (name, isRoot) =>
      val builder = parent.withName(name: _*)
      if (isRoot || !hierarchicalOnly) builder else builder.withHierarchicalOnly
  }

  override def counter(metricBuilder: MetricBuilder): Counter = new Counter {
    private[this] val allCounters = BroadcastCounter(metrics(metricBuilder).map(self.counter))
    def incr(delta: Long): Unit = allCounters.incr(delta)
    def metadata: Metadata = allCounters.metadata
  }
  override def stat(metricBuilder: MetricBuilder): Stat = new Stat {
    private[this] val allStats = BroadcastStat(metrics(metricBuilder).map(self.stat))
    def add(value: Float): Unit = allStats.add(value)
    def metadata: Metadata = allStats.metadata
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = new Gauge {
    private[this] val underlying = metrics(metricBuilder).map { self.addGauge(_)(f) }
    def remove(): Unit = underlying.foreach(_.remove())
    def metadata: Metadata = MultiMetadata(underlying.map(_.metadata))
  }
}
