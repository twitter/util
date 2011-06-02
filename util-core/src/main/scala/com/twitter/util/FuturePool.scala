package com.twitter.util

import java.util.concurrent.ExecutorService

/**
 * A FuturePool executes tasks asynchronously, typically using a pool
 * of worker threads.
 */
trait FuturePool {
  def apply[T](f: => T): Future[T]
}

object FuturePool {
  /**
   * Creates a FuturePool backed by an ExecutorService.
   */
  def apply(executor: ExecutorService) = new ExecutorServiceFuturePool(executor)

  /**
   * A FuturePool that really isn't; it executes tasks immediately
   * without waiting.  This can be useful in unit tests.
   */
  val immediatePool = new FuturePool {
    def apply[T](f: => T) = Future(f)
  }
}

/**
 * A FuturePool implementation backed by an ExecutorService.
 */
class ExecutorServiceFuturePool(val executor: ExecutorService) extends FuturePool {
  def apply[T](f: => T): Future[T] = {
    val out = new Promise[T]
    executor.submit(new Runnable {
      def run = out.update(Try(f))
    })
    out
  }
}
