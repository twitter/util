package com.twitter.io.exp

import com.twitter.util.{Duration, TimeoutException}

/**
 * Indicates a [[com.twitter.io.Reader reader]] or
 * [[com.twitter.io.Writer writer]] dropped below
 * the minimum required bytes per second threshold.
 *
 * @param elapsed total time spent reading or writing.
 *
 * @see [[MinimumThroughput]]
 */
case class BelowThroughputException(
    elapsed: Duration,
    currentBps: Double,
    expectedBps: Double)
  extends TimeoutException(
    s"current bps $currentBps below minimum $expectedBps")
