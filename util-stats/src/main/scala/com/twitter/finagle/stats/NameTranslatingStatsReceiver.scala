package com.twitter.finagle.stats

/**
 * A StatsReceiver receiver proxy that translates all counter, stat, and gauge
 * names according to a `translate(name: Seq[String])` function.
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 * @param namespacePrefix the namespace used for translations
 * @param mode determines which names to translate: hierarchical, dimensional, or both.
 */
abstract class NameTranslatingStatsReceiver(
  self: StatsReceiver,
  namespacePrefix: String,
  mode: NameTranslatingStatsReceiver.Mode)
    extends TranslatingStatsReceiver(self) {

  import NameTranslatingStatsReceiver._

  def this(self: StatsReceiver, namespacePrefix: String) =
    this(self, namespacePrefix, NameTranslatingStatsReceiver.FullTranslation)

  def this(self: StatsReceiver) = this(self, "<namespacePrefix>")

  protected def translate(name: Seq[String]): Seq[String]

  final protected def translate(builder: MetricBuilder): MetricBuilder = {
    val nextIdentity = mode match {
      case FullTranslation =>
        builder.identity.copy(
          hierarchicalName = translate(builder.identity.hierarchicalName),
          dimensionalName = translate(builder.identity.dimensionalName))

      case HierarchicalOnly =>
        builder.identity.copy(hierarchicalName = translate(builder.identity.hierarchicalName))

      case DimensionalOnly =>
        builder.identity.copy(dimensionalName = translate(builder.identity.dimensionalName))
    }

    builder.withIdentity(nextIdentity)
  }

  override def toString: String = mode match {
    case FullTranslation | HierarchicalOnly => s"$self/$namespacePrefix"
    case DimensionalOnly => self.toString
  }
}

private object NameTranslatingStatsReceiver {

  /** The mode with which to translate stats, either hierarchical, dimensional, or both. */
  sealed trait Mode

  /** Translate metric names for both hierarchical and dimensional representations */
  case object FullTranslation extends Mode

  /** Translate metric names only for hierarchical representations */
  case object HierarchicalOnly extends Mode

  /** Translate metric names only for dimensional representations */
  case object DimensionalOnly extends Mode

  /**
   * Translate the scope of a [[StatsReceiver]]
   *
   * @param parent the [[StatsReceiver]] to delegate the translated name to.
   * @param namespace the segment to prepend to the name using the appropriate scope delimiter.
   * @param mode select which representations to translate.
   * */
  final class ScopeTranslatingStatsReceiver(
    parent: StatsReceiver,
    namespace: String,
    mode: NameTranslatingStatsReceiver.Mode)
      extends NameTranslatingStatsReceiver(parent, namespace, mode) {
    protected def translate(name: Seq[String]): Seq[String] = namespace +: name
  }
}
