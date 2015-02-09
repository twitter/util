package com.twitter.util

import java.util.logging.{Level, Logger}

import scala.collection.mutable

/** Provides a clean, lightweight interface for controlling a BatchExecutor
  *
  * @constructor create a new Batcher for a BatchExecutor
  * @param executor the BatchExector to be used
  */
class Batcher[In, Out](
  executor: BatchExecutor[In, Out]
)(
  implicit timer: Timer
) extends Function1[In, Future[Out]] { batcher =>
  import java.util.logging.Level.WARNING

  /** Enqueues requests for the BatchExecutor */
  def apply(t: In): Future[Out] = executor.enqueue(t)

  /** Immediately processes all unprocessed requests */
  def flushBatch(): Unit = {
    val doAfter = synchronized {
      executor.flushBatch()
    }

    doAfter()
  }
}
