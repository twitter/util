package com.twitter.finagle.stats

/**
 * A denylisting [[StatsReceiver]].  If the name for a metric is found to be
 * denylisted, nothing is recorded.
 *
 * @param self a base [[StatsReceiver]], used for metrics that aren't
 *        denylisted
 * @param denylisted a predicate that reads a name and returns true to
 *        denylist, and false to let it pass through
 */
class DenylistStatsReceiver(protected val self: StatsReceiver, denylisted: Seq[String] => Boolean)
    extends StatsReceiverProxy {

  override def counter(verbosity: Verbosity, name: String*): Counter =
    getStatsReceiver(name).counter(verbosity, name: _*)

  override def stat(verbosity: Verbosity, name: String*): Stat =
    getStatsReceiver(name).stat(verbosity, name: _*)

  override def addGauge(verbosity: Verbosity, name: String*)(f: => Float): Gauge =
    getStatsReceiver(name).addGauge(verbosity, name: _*)(f)

  private[this] def getStatsReceiver(name: Seq[String]): StatsReceiver =
    if (denylisted(name)) NullStatsReceiver else self

  override def toString: String = s"DenylistStatsReceiver($self)"
}
