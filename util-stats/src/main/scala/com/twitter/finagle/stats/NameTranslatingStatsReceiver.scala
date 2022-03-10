package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate(name: Seq[String])` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 *
 * @param namespacePrefix the namespace used for translations
 */
abstract class NameTranslatingStatsReceiver(
  self: StatsReceiver,
  namespacePrefix: String)
    extends TranslatingStatsReceiver(self) {

  def this(self: StatsReceiver) = this(self, "<namespacePrefix>")

  protected def translate(name: Seq[String]): Seq[String]

  final protected def translate(builder: MetricBuilder): MetricBuilder =
    builder.withName(translate(builder.name): _*)

  override def toString: String = s"$self/$namespacePrefix"
}
