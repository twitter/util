package com.twitter.concurrent

import com.twitter.util._
import java.util.ArrayDeque
import java.util.concurrent.RejectedExecutionException
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

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
 *   semaphore.acquireAndRun {
 *     somethingThatReturnsFutureT()
 *   }
 * }}}
 *
 * Calls to acquire() and acquireAndRun are serialized, and tickets are
 * given out fairly (in order of arrival).
 *
 * @see [[AsyncMutex]] for a mutex version.
 */
class AsyncSemaphore protected (initialPermits: Int, maxWaiters: Option[Int]) {
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
  // by locking on `lock`
  private[this] var closed: Option[Throwable] = None
  private[this] val waitq = new ArrayDeque[Promise[Permit]]
  private[this] var availablePermits = initialPermits

  // Serves as our intrinsic lock.
  private[this] final def lock: Object = waitq

  private[this] val semaphorePermit = new Permit {
    private[this] val ReturnThis = Return(this)

    @tailrec override def release(): Unit = {
      val waiter = lock.synchronized {
        val next = waitq.pollFirst()
        if (next == null) {
          availablePermits += 1
        }
        next
      }

      if (waiter != null) {
        // If the waiter is already satisfied, it must
        // have been interrupted, so we can simply move on.
        if (!waiter.updateIfEmpty(ReturnThis)) {
          release()
        }
      }
    }
  }

  private[this] val futurePermit = Future.value(semaphorePermit)

  def numWaiters: Int = lock.synchronized(waitq.size)
  def numInitialPermits: Int = initialPermits
  def numPermitsAvailable: Int = lock.synchronized(availablePermits)

  /**
   * Fail the semaphore and stop it from distributing further permits. Subsequent
   * attempts to acquire a permit fail with `exc`. This semaphore's queued waiters
   * are also failed with `exc`.
   */
  def fail(exc: Throwable): Unit = {
    val drained = lock.synchronized {
      closed = Some(exc)
      waitq.asScala.toList
    }
    // delegate dequeuing to the interrupt handler defined in #acquire
    drained.foreach(_.raise(exc))
  }

  /**
   * Acquire a [[Permit]], asynchronously. Be sure to `permit.release()` in a
   *
   * - `finally` block of your `onSuccess` callback
   * - `ensure` block of your future chain
   *
   * Interrupting this future is only advisory, and will not release the permit
   * if the future has already been satisfied.
   *
   * @note This method always return the same instance of [[Permit]].
   *
   * @return a `Future[Permit]` when the `Future` is satisfied, computation can proceed,
   *         or a Future.Exception[RejectedExecutionException]` if the configured maximum
   *         number of waiters would be exceeded.
   */
  def acquire(): Future[Permit] = lock.synchronized {
    if (closed.isDefined)
      return Future.exception(closed.get)

    if (availablePermits > 0) {
      availablePermits -= 1
      futurePermit
    } else {
      maxWaiters match {
        case Some(max) if waitq.size >= max =>
          MaxWaitersExceededException
        case _ =>
          val promise = new Promise[Permit]
          promise.setInterruptHandler {
            case t: Throwable =>
              if (promise.updateIfEmpty(Throw(t))) lock.synchronized {
                waitq.remove(promise)
              }
          }
          waitq.addLast(promise)
          promise
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
  def acquireAndRun[T](func: => Future[T]): Future[T] =
    acquire().flatMap { permit =>
      val f = try func
      catch {
        case NonFatal(e) =>
          Future.exception(e)
        case e: Throwable =>
          permit.release()
          throw e
      }
      f.ensure {
        permit.release()
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
  def acquireAndRunSync[T](func: => T): Future[T] =
    acquire().flatMap { permit =>
      Future(func).ensure {
        permit.release()
      }
    }
}

object AsyncSemaphore {
  private val MaxWaitersExceededException =
    Future.exception(new RejectedExecutionException("Max waiters exceeded"))
}
