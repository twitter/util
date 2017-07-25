package com.twitter.finagle.stats

/**
 * A blacklisting [[StatsReceiver]].  If the name for a metric is found to be
 * blacklisted, nothing is recorded.
 *
 * @param self a base [[StatsReceiver]], used for metrics that aren't
 *        blacklisted
 * @param blacklisted a predicate that reads a name and returns true to
 *        blacklist, and false to let it pass through
 */
class BlacklistStatsReceiver(
    protected val self: StatsReceiver,
    blacklisted: Seq[String] => Boolean)
  extends StatsReceiverProxy {

  override def counter(verbosity: Verbosity, name: String*): Counter =
    getStatsReceiver(name).counter(verbosity, name: _*)

  override def stat(verbosity: Verbosity, name: String*): Stat =
    getStatsReceiver(name).stat(verbosity, name: _*)

  override def addGauge(verbosity: Verbosity, name: String*)(f: => Float): Gauge =
    getStatsReceiver(name).addGauge(verbosity, name: _*)(f)

  private[this] def getStatsReceiver(name: Seq[String]): StatsReceiver =
    if (blacklisted(name)) NullStatsReceiver else self

  override def toString: String = s"BlacklistStatsReceiver($self)"
}
