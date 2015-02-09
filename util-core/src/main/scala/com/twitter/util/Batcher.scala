package com.twitter.util

/** Provides a clean, lightweight interface for working with batched Futures */
private[util] class Batcher[In, Out](
  executor: BatchExecutor[In, Out]
)(
  implicit timer: Timer
) extends Function1[In, Future[Out]] { batcher =>
  /** Enqueues requests for a batched Future */
  def apply(t: In): Future[Out] = executor.enqueue(t)

  /** Immediately processes all unprocessed requests */
  def flushBatch(): Unit = {
    val doAfter = executor.synchronized {
      executor.flushBatch()
    }

    doAfter()
  }
}
