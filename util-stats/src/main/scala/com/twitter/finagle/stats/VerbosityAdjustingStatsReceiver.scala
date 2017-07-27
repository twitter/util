package com.twitter.finagle.stats

/**
 * A [[StatsReceiver]] that adjusts the default [[Verbosity]] of an underlying stats receiver to
 * a given `verbosity`.
 *
 * @note An explicitly passed [[Verbosity]] will always take precedence over the `verbosity` this
 *       stats receiver adjusts to.
 */
class VerbosityAdjustingStatsReceiver(
    protected val self: StatsReceiver,
    verbosity: Verbosity)
  extends StatsReceiverProxy {

  override def counter(names: String*): Counter = self.counter(verbosity, names: _*)
  override def stat(names: String*): Stat = self.stat(verbosity, names: _*)
  override def addGauge(names: String*)(f: => Float): Gauge = self.addGauge(verbosity, names: _*)(f)
}
