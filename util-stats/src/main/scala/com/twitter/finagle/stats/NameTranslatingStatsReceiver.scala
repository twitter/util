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
  protected val self: StatsReceiver,
  namespacePrefix: String
) extends StatsReceiverProxy {

  def this(self: StatsReceiver) = this(self, "<namespacePrefix>")

  protected def translate(name: Seq[String]): Seq[String]

  override def counter(verbosity: Verbosity, name: String*): Counter =
    self.counter(verbosity, translate(name): _*)

  override def stat(verbosity: Verbosity, name: String*): Stat =
    self.stat(verbosity, translate(name): _*)

  override def addGauge(verbosity: Verbosity, name: String*)(f: => Float): Gauge =
    self.addGauge(verbosity, translate(name): _*)(f)

  override def toString: String = s"$self/$namespacePrefix"
}
