package com.twitter.util

import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent.{CancellationException, ExecutorService, Executors, Future => JFuture}
import java.util.concurrent.atomic.AtomicBoolean

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
   * A default shared FuturePool, configured at 2 times the number of cores, similar to Netty.
   * Convenient, but higher demand systems should configure their own.
   */
  lazy val defaultPool = new ExecutorServiceFuturePool(
    Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors() * 2,
      new NamedPoolThreadFactory("DefaultFuturePool")
    )
  )
}

/**
 * A FuturePool implementation backed by an ExecutorService.
 *
 * If a piece of work has started, it cannot be cancelled and will not propagate
 * cancellation.
 */
class ExecutorServiceFuturePool(val executor: ExecutorService) extends FuturePool {
  def apply[T](f: => T): Future[T] = {
    val saved = Local.save()

    val stoppable = new AtomicBoolean(true)

    def makePromiseTask(out: Promise[T], f: => T): Runnable = new Runnable {
      def run = {
        // At this point, nothing should be cancelling us
        // sets running=true and returns whether we were inProgress or not.
        val runnable = stoppable.compareAndSet(true, false)
        // Make an effort to skip work in the case the promise
        // has been cancelled or already defined.
        if (runnable) {
          val current = Local.save()
          Local.restore(saved)

          try {
            out.update(Try(f))
          } finally {
            Local.restore(current)
          }
        }
      }
    }

    /**
     * link the cancellation of the Promise to the JFuture
     */
    def linkCancellation(promise: Promise[T], javaFuture: JFuture[_]) {
      promise onCancellation {
        val cancelled = stoppable.compareAndSet(true, false)

        if (cancelled) {
          javaFuture.cancel(true)
          promise.update(Throw(new CancellationException))
        }
      }
    }

    Future {
      val out = new Promise[T]
      linkCancellation(out, executor.submit(makePromiseTask(out, f)))

      out
    } flatten
  }
}
