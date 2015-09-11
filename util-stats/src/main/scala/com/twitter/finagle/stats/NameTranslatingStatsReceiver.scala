package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 *
 * @param namespacePrefix the namespace used for translations
 */
abstract class NameTranslatingStatsReceiver(
    val self: StatsReceiver,
    namespacePrefix: String)
  extends StatsReceiver
{
  def this(self: StatsReceiver) = this(self, "<namespacePrefix>")

  override def toString: String =
    s"$self/$namespacePrefix"

  protected[this] def translate(name: Seq[String]): Seq[String]
  val repr = self.repr
  override def isNull: Boolean = self.isNull

  def counter(name: String*): Counter =
    self.counter(translate(name): _*)

  def stat(name: String*): Stat =
    self.stat(translate(name): _*)

  def addGauge(name: String*)(f: => Float): Gauge =
    self.addGauge(translate(name): _*)(f)
}
