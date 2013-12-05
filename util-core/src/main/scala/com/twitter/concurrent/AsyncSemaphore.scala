package com.twitter.concurrent

import java.util.concurrent.RejectedExecutionException
import java.util.ArrayDeque
import com.twitter.util.{Future, Promise, Throw, NonFatal}

/**
 * An AsyncSemaphore is a traditional semaphore but with asynchronous
 * execution. Grabbing a permit returns a Future[Permit]
 */
class AsyncSemaphore protected (initialPermits: Int, maxWaiters: Option[Int]) {
  import AsyncSemaphore._

  def this(initialPermits: Int = 0) = this(initialPermits, None)
  def this(initialPermits: Int, maxWaiters: Int) = this(initialPermits, Some(maxWaiters))
  require(maxWaiters.getOrElse(0) >= 0)
  private[this] val waitq = new ArrayDeque[Promise[Permit]]
  private[this] var availablePermits = initialPermits

  private[this] class SemaphorePermit extends Permit {
    /**
     * Indicate that you are done with your Permit.
     */
    override def release() {
      val run = AsyncSemaphore.this.synchronized {
        val next = waitq.pollFirst()
        if (next == null) availablePermits += 1
        next
      }

      if (run != null) run.setValue(new SemaphorePermit)
    }
  }

  def numWaiters: Int = synchronized(waitq.size)
  def numPermitsAvailable: Int = synchronized(availablePermits)

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
    synchronized {
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
                AsyncSemaphore.this.synchronized {
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
        case e =>
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
