package com.twitter.finagle.stats

/**
 * A [[StatsReceiver]] that adjusts the passed [[Verbosity]] of an underlying stats receiver to
 * a given `defaultVerbosity`.
 */
class VerbosityAdjustingStatsReceiver(
  protected val self: StatsReceiver,
  defaultVerbosity: Verbosity
) extends StatsReceiverProxy {

  override def counter(verbosity: Verbosity, names: String*): Counter =
    self.counter(defaultVerbosity, names: _*)

  override def stat(verbosity: Verbosity, names: String*): Stat =
    self.stat(defaultVerbosity, names: _*)

  override def addGauge(
    verbosity: Verbosity,
    names: String*
  )(f: => Float): Gauge = self.addGauge(defaultVerbosity, names: _*)(f)
}
