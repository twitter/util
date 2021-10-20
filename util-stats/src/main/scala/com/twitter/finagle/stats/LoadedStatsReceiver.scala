package com.twitter.finagle.stats

import com.twitter.app.LoadService

/**
 * A [[com.twitter.finagle.stats.StatsReceiver]] that loads
 * all service-loadable receivers and broadcasts stats to them.
 */
object LoadedStatsReceiver extends StatsReceiverProxy {

  /**
   * Mutating this value at runtime after it has been initialized should be done
   * with great care. If metrics have been created using the prior
   * [[StatsReceiver]], updates to those metrics may not be reflected in the
   * [[StatsReceiver]] that replaces it. In addition, histograms created with
   * the prior [[StatsReceiver]] will not be available.
   */
  @volatile var self: StatsReceiver = BroadcastStatsReceiver(LoadService[StatsReceiver]())
}

/**
 * A "default" StatsReceiver loaded by the
 * [[com.twitter.finagle.util.LoadService]] mechanism.
 */
object DefaultStatsReceiver extends StatsReceiverProxy {
  def self: StatsReceiver = LoadedStatsReceiver
  override def repr: DefaultStatsReceiver.type = this

  def get: StatsReceiver = this
}
