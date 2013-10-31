package com.twitter.util

import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CancellationException, RejectedExecutionException, ExecutorService, Executors,
  Future => JFuture}

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
  def apply(executor: ExecutorService, interruptible: Boolean = false) =
    new ExecutorServiceFuturePool(executor, interruptible)

  /**
   * A FuturePool that really isn't; it executes tasks immediately
   * without waiting.  This can be useful in unit tests.
   */
  val immediatePool = new FuturePool {
    def apply[T](f: => T) = Future(f)
  }

  private lazy val defaultExecutor = Executors.newCachedThreadPool(
    new NamedPoolThreadFactory("UnboundedFuturePool")
  )

  /**
   * The default future pool, using a cached threadpool, provided by
   * [[java.util.concurrent.Executors.newCachedThreadPool]]. Note
   * that this is intended for IO concurrency; computational
   * parallelism typically requires special treatment.
   */
  @deprecated("use unboundedPool instead", "5.3.11")
  lazy val defaultPool = unboundedPool

  /**
   * The default future pool, using a cached threadpool, provided by
   * [[java.util.concurrent.Executors.newCachedThreadPool]]. Note
   * that this is intended for IO concurrency; computational
   * parallelism typically requires special treatment. If an interrupt
   * is raised on a returned Future and the work has started, the worker
   * thread will not be interrupted.
   */
  lazy val unboundedPool = new ExecutorServiceFuturePool(defaultExecutor, false)

  /**
   * The default future pool, using a cached threadpool, provided by
   * [[java.util.concurrent.Executors.newCachedThreadPool]]. Note
   * that this is intended for IO concurrency; computational
   * parallelism typically requires special treatment.  If an interrupt
   * is raised on a returned Future and the work has started, an attempt
   * will will be made to interrupt the worker thread.
   */
  lazy val interruptibleUnboundedPool = new ExecutorServiceFuturePool(defaultExecutor, true)
}

/**
 * A FuturePool implementation backed by an ExecutorService.
 *
 * If a piece of work has started, it cannot be cancelled and will not propagate
 * cancellation unless interruptible is true.
 */
class ExecutorServiceFuturePool(
  val executor: ExecutorService,
  val interruptible: Boolean = false
) extends FuturePool
{
  def apply[T](f: => T): Future[T] = {
    val runOk = new AtomicBoolean(true)
    val p = new Promise[T]
    val task = new Runnable {
      val saved = Local.save()

      def run() {
        // Make an effort to skip work in the case the promise
        // has been cancelled or already defined.
        if (!runOk.compareAndSet(true, false))
          return

        val current = Local.save()
        Local.restore(saved)

        try
          p.updateIfEmpty(Try(f))
        finally
          Local.restore(current)
      }
    }


    // This is safe: the only thing that can call task.run() is
    // executor, the only thing that can raise an interrupt is the
    // receiver of this value, which will then be fully initialized.
    val javaFuture = try executor.submit(task) catch {
      case e: RejectedExecutionException =>
        runOk.set(false)
        p.setException(e)
        null
    }

    p.setInterruptHandler {
      case cause =>
        if (interruptible || runOk.compareAndSet(true, false)) {
          javaFuture.cancel(true)
          val exc = new CancellationException
          exc.initCause(cause)
          p.updateIfEmpty(Throw(exc))
        }
    }

    p
  }
}
