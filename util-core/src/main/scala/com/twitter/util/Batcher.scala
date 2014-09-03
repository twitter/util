package com.twitter.util

import java.util.logging.{Level, Logger}

import scala.collection.mutable

class Batcher[In, Out](
  sizeThreshold: Int,
  timeThreshold: Duration = Duration.Top,
  sizePercentile: => Float = 1.0f,
  f: Seq[In] => Future[Seq[Out]]
)(
  implicit timer: Timer
) extends Function1[In, Future[Out]] { batcher =>
  import java.util.logging.Level.WARNING

  val executor = new BatchExecutor[In, Out](sizeThreshold, timeThreshold, sizePercentile, f)

  def apply(t: In): Future[Out] = executor.enqueue(t)

  def flushBatch() = {
    val doAfter = synchronized {
      executor.flushBatch()
    }

    doAfter()
  }
}
