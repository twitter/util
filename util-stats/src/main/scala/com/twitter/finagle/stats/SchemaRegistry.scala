package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema

/**
 * Interface used via the LoadService mechanism to obtain an
 * efficient mechanism to sample stats.
 */
private[twitter] trait SchemaRegistry {

  /** Whether or not the counters are latched. */
  def hasLatchedCounters: Boolean

  def schemas(): Map[String, MetricSchema]

  def expressions(): Map[String, ExpressionSchema]

}
