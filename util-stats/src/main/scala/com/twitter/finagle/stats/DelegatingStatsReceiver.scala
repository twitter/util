package com.twitter.finagle.stats

/**
 * Should be mixed into [[StatsReceiver StatsReceivers]] that delegate to other
 * [[StatsReceiver StatsReceivers]].
 */
trait DelegatingStatsReceiver {

  /**
   * The underlying [[StatsReceiver StatsReceivers]] that the class delegates to.
   *
   * Must be nonempty.
   */
  def underlying: Seq[StatsReceiver]
}

object DelegatingStatsReceiver {

  /**
   * Collects all [[StatsReceiver StatsReceivers]] that are delegated to by the
   * [[DelegatingStatsReceiver]] passed as the argument, or if any of those is a
   * [[DelegatingStatsReceiver]], the [[StatsReceiver StatsReceivers]] they
   * delegate to, recursively.
   *
   * If you think of the [[StatsReceiver StatsReceivers]] as a tree, it collects
   * the leaves of the tree.
   */
  def all(sr: StatsReceiver): Seq[StatsReceiver] = sr match {
    case delegator: DelegatingStatsReceiver => delegator.underlying.flatMap(all)
    case leaf => Seq(leaf)
  }
}
