package com.twitter.finagle.stats

sealed trait MetricUsageHint {}

object MetricUsageHint {
  object HighContention extends MetricUsageHint
}
