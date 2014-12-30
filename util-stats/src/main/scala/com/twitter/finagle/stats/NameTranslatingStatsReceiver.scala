package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 */
abstract class NameTranslatingStatsReceiver(val self: StatsReceiver)
  extends StatsReceiver
{
  protected[this] def translate(name: Seq[String]): Seq[String]
  val repr = self.repr
  override def isNull = self.isNull

  def counter(name: String*) = self.counter(translate(name): _*)
  def stat(name: String*)    = self.stat(translate(name): _*)
  def addGauge(name: String*)(f: => Float) = self.addGauge(translate(name): _*)(f)
}
