package com.twitter.util

/** Provides an interface for working with a batched [[com.twitter.util.Future]] */
class Batcher[In, Out] private[util] (
  executor: BatchExecutor[In, Out]
)(
  implicit timer: Timer
) extends (In => Future[Out]) { batcher =>

  /**
   * Enqueues requests for a batched [[com.twitter.util.Future]]
   *
   * Processes all enqueued requests after the batch size is reached
   */
  def apply(t: In): Future[Out] = executor.enqueue(t)

  /** Immediately processes all unprocessed requests */
  def flushBatch(): Unit = executor.flushNow()
}
