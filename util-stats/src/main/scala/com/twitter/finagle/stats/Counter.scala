package com.twitter.finagle.stats

/**
 * A writeable Counter. Only sums are kept of Counters. An example
 * Counter is "number of requests served".
 */
trait Counter {
  def incr(delta: Int)
  def incr() { incr(1) }
}
