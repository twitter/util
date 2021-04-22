package com.twitter.finagle.stats

/**
 * Exposes the value of a function. For example, one could add a gauge for a
 * computed health metric.
 */
trait Gauge {
  def remove(): Unit
  def metadata: Metadata
}
