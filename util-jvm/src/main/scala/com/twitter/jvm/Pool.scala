package com.twitter.jvm

import com.twitter.util.{Duration, Future, Stopwatch, Timer}

/**
 * A handle to a garbage collected memory pool.
 */
trait Pool {

  /** Get the current state of this memory pool. */
  def state(): PoolState

  /**
   * Sample the allocation rate of this pool. Note that this is merely
   * an estimation based on sampling the state of the pool initially
   * and then again when the period elapses.
   *
   * @return Future of the samples rate (in bps).
   */
  def estimateAllocRate(period: Duration, timer: Timer): Future[Long] = {
    val elapsed = Stopwatch.start()
    val begin = state()
    timer.doLater(period) {
      val end = state()
      val interval = elapsed()
      ((end - begin).used.inBytes * 1000) / interval.inMilliseconds
    }
  }
}
