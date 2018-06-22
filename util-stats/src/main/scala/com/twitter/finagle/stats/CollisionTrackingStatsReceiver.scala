package com.twitter.finagle.stats

import com.twitter.util.lint.Rule

/**
 * A [[StatsReceiver]] that provides a linting rule to track metrics that have different underlying
 * names, yet are exported under the same name.
 */
trait CollisionTrackingStatsReceiver {
  def metricsCollisionsLinterRule: Rule
}
