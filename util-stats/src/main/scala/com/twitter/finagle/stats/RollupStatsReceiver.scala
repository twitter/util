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

  override def counter(schema: CounterSchema) = new Counter {
    private[this] val allCounters = BroadcastCounter(
      tails(schema.metricBuilder.name).map(n =>
        self.counter(CounterSchema(schema.metricBuilder.withName(n: _*))))
    )
    def incr(delta: Long): Unit = allCounters.incr(delta)
  }
  override def stat(schema: HistogramSchema) = new Stat {
    private[this] val allStats = BroadcastStat(
      tails(schema.metricBuilder.name).map(n =>
        self.stat(HistogramSchema(schema.metricBuilder.withName(n: _*))))
    )
    def add(value: Float): Unit = allStats.add(value)
  }

  override def addGauge(schema: GaugeSchema)(f: => Float) = new Gauge {
    private[this] val underlying =
      tails(schema.metricBuilder.name).map(n =>
        self.addGauge(GaugeSchema(schema.metricBuilder.withName(n: _*)))(f))
    def remove(): Unit = underlying.foreach(_.remove())
  }
}
