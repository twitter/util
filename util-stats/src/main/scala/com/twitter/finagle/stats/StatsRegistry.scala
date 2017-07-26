package com.twitter.finagle.stats

/**
 * Interface used via the LoadService mechanism to obtain an
 * efficient mechanism to sample stats.
 */
private[twitter] trait StatsRegistry {
  def apply(): Map[String, StatEntry]
}

private[twitter] trait StatEntry {

  /**
   * The delta since the entry was last sampled.
   * Note, this field is identical to `value` for
   * instantaneous entries (ex. gauges).
   */
  val delta: Double

  /** The instantaneous value of the entry. */
  val value: Double
}
