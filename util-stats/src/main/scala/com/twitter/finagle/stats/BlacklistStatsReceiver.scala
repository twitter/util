package com.twitter.finagle.stats

/**
 * A blacklisting [[StatsReceiver]].  If the name for a metric is found to be
 * blacklisted, nothing is recorded.
 *
 * @param underlying: a base [[StatsReceiver]], used for metrics that aren't
 *        blacklisted
 * @param blacklisted: a predicate that reads a name and returns true to
 *        blacklist, and false to let it pass through
 */
class BlacklistStatsReceiver(
    underlying: StatsReceiver,
    blacklisted: Seq[String] => Boolean)
  extends StatsReceiver {
  val repr = underlying.repr
  override def isNull = underlying.isNull

  def counter(name: String*): Counter = getStatsReceiver(name).counter(name: _*)

  def stat(name: String*): Stat = getStatsReceiver(name).stat(name: _*)

  def addGauge(name: String*)(f: => Float): Gauge = getStatsReceiver(name).addGauge(name: _*)(f)

  private[this] def getStatsReceiver(name: Seq[String]): StatsReceiver =
    if (blacklisted(name)) NullStatsReceiver else underlying

  override def toString: String =
    s"BlacklistStatsReceiver($underlying)"

}
