package com.twitter.concurrent

import com.twitter.util._
import java.util.ArrayDeque
import java.util.concurrent.RejectedExecutionException
import scala.collection.JavaConverters._

/**
 * An AsyncSemaphore is a traditional semaphore but with asynchronous
 * execution.
 *
 * Grabbing a permit returns a `Future[Permit]`.
 *
 * Basic usage:
 * {{{
 *   val semaphore = new AsyncSemaphore(n)
 *   ...
 *   semaphore.acquireAndRun() {
 *     somethingThatReturnsFutureT()
 *   }
 * }}}
 *
 * Calls to acquire() and acquireAndRun are serialized, and tickets are
 * given out fairly (in order of arrival).
 *
 * @see [[AsyncMutex]] for a mutex version.
 */
class AsyncSemaphore protected (initialPermits: Int, maxWaiters: Option[Int]) { self =>
  import AsyncSemaphore._

  /**
   * Constructs a semaphore with no limit on the max number
   * of waiters for permits.
   *
   * @param initialPermits must be positive
   */
  def this(initialPermits: Int) = this(initialPermits, None)

  /**
   * Constructs a semaphore with `maxWaiters` as the limit on the
   * number of waiters for permits.
   *
   * @param initialPermits must be positive
   * @param maxWaiters must be non-negative
   */
  def this(initialPermits: Int, maxWaiters: Int) = this(initialPermits, Some(maxWaiters))

  require(maxWaiters.getOrElse(0) >= 0, s"maxWaiters must be non-negative: $maxWaiters")
  require(initialPermits > 0, s"initialPermits must be positive: $initialPermits")

  // access to `closed`, `waitq`, and `availablePermits` is synchronized
  // by locking on `self`
  private[this] var closed: Option[Throwable] = None
  private[this] val waitq = new ArrayDeque[Promise[Permit]]
  private[this] var availablePermits = initialPermits

  private[this] class SemaphorePermit extends Permit {
    /**
     * Indicate that you are done with your Permit.
     */
    override def release() {
      self.synchronized {
        val next = waitq.pollFirst()
        if (next != null)
          next.setValue(new SemaphorePermit)
        else
          availablePermits += 1
      }
    }
  }

  def numWaiters: Int = self.synchronized(waitq.size)
  def numInitialPermits: Int = initialPermits
  def numPermitsAvailable: Int = self.synchronized(availablePermits)

  /**
   * Fail the semaphore and stop it from distributing further permits. Subsequent
   * attempts to acquire a permit fail with `exc`. This semaphore's queued waiters
   * are also failed with `exc`.
   */
  def fail(exc: Throwable): Unit = self.synchronized {
    closed = Some(exc)
    val drained = waitq.asScala.toList
    // delegate dequeuing to the interrupt handler defined in #acquire
    drained.foreach(_.raise(exc))
  }

  /**
   * Acquire a Permit, asynchronously. Be sure to permit.release() in a 'finally'
   * block of your onSuccess() callback.
   *
   * Interrupting this future is only advisory, and will not release the permit
   * if the future has already been satisfied.
   *
   * @return a Future[Permit] when the Future is satisfied, computation can proceed,
   * or a Future.Exception[RejectedExecutionException] if the configured maximum number of waitq
   * would be exceeded.
   */
  def acquire(): Future[Permit] = {
    self.synchronized {
      if (closed.isDefined)
        return Future.exception(closed.get)

      if (availablePermits > 0) {
        availablePermits -= 1
        Future.value(new SemaphorePermit)
      } else {
        maxWaiters match {
          case Some(max) if (waitq.size >= max) =>
            MaxWaitersExceededException
          case _ =>
            val promise = new Promise[Permit]
            promise.setInterruptHandler { case t: Throwable =>
              self.synchronized {
                if (promise.updateIfEmpty(Throw(t)))
                  waitq.remove(promise)
              }
            }
            waitq.addLast(promise)
            promise
        }
      }
    }
  }

  /**
   * Execute the function asynchronously when a permit becomes available.
   *
   * If the function throws a non-fatal exception, the exception is returned as part of the Future.
   * For all exceptions, the permit would be released before returning.
   *
   * @return a Future[T] equivalent to the return value of the input function. If the configured
   *         maximum value of waitq is reached, Future.Exception[RejectedExecutionException] is
   *         returned.
   */
  def acquireAndRun[T](func: => Future[T]): Future[T] = {
    acquire() flatMap { permit =>
      val f = try func catch {
        case NonFatal(e) =>
          Future.exception(e)
        case e: Throwable =>
          permit.release()
          throw e
      }
      f ensure {
        permit.release()
      }
    }
  }

  /**
   * Execute the function when a permit becomes available.
   *
   * If the function throws an exception, the exception is returned as part of the Future.
   * For all exceptions, the permit would be released before returning.
   *
   * @return a Future[T] equivalent to the return value of the input function. If the configured
   *         maximum value of waitq is reached, Future.Exception[RejectedExecutionException] is
   *         returned.
   */
  def acquireAndRunSync[T](func: => T): Future[T] = {
    acquire() flatMap { permit =>
      Future(func) ensure {
        permit.release()
      }
    }
  }
}

object AsyncSemaphore {
  private val MaxWaitersExceededException =
    Future.exception(new RejectedExecutionException("Max waiters exceeded"))
}
