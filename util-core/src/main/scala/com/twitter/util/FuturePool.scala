package com.twitter.util

import java.util.concurrent.{ExecutorService, Executors}

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

  /**
   * A default shared FuturePool, configured at 2 times the number of cores, similar to Netty. Convenient, but higher demand systems should configure their own.
   */
  lazy val defaultPool = new ExecutorServiceFuturePool(
    Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
  )
}

/**
 * A FuturePool implementation backed by an ExecutorService.
 */
class ExecutorServiceFuturePool(val executor: ExecutorService) extends FuturePool {
  def apply[T](f: => T): Future[T] = {
    val out = new Promise[T]
    executor.submit(new Runnable {
      def run = {

        // Make an effort to skip work in the case the promise
        // has been cancelled or already defined.
        if (!out.isDefined && !out.isCancelled) {
          out.updateIfEmpty(Try(f))
        }
      }
    })
    out
  }
}
