package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey

/**
 * Interface used via the LoadService mechanism to obtain an
 * efficient mechanism to sample stats.
 */
private[twitter] trait SchemaRegistry {

  /** Whether or not the counters are latched. */
  def hasLatchedCounters: Boolean

  def schemas(): Map[String, MetricBuilder]

  def expressions(): Map[ExpressionSchemaKey, ExpressionSchema]

}
