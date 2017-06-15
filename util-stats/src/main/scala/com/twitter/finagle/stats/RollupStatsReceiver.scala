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
      case s@Seq(_) =>
        Seq(s)

      case Seq(hd, tl@_*) =>
        Seq(Seq(hd)) ++ (tails(tl) map { t => Seq(hd) ++ t })
    }
  }

  override def counter(names: String*): Counter = new Counter {
    private[this] val allCounters = BroadcastCounter(
      tails(names) map (self.counter(_: _*))
    )
    def incr(delta: Int): Unit = allCounters.incr(delta)
  }

  override def stat(names: String*): Stat = new Stat {
    private[this] val allStats = BroadcastStat(
      tails(names) map (self.stat(_: _*))
    )
    def add(value: Float): Unit = allStats.add(value)
  }

  override def addGauge(names: String*)(f: => Float): Gauge = new Gauge {
    private[this] val underlying = tails(names) map { self.addGauge(_: _*)(f) }
    def remove(): Unit = underlying foreach { _.remove() }
  }
}
