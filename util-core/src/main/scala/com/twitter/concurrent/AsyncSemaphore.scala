package com.twitter.concurrent

import com.twitter.util._
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException}
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
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

  // if failedState (Int.MinValue), failed
  // else if >= 0, # of available permits
  // else if < 0, # of waiters
  private[this] final val state = new AtomicInteger(initialPermits)
  @volatile private[this] final var failure: Future[Nothing] = null

  private[this] final val failedState = Int.MinValue
  private[this] final val waiters = new ConcurrentLinkedQueue[Waiter]
  private[this] final val waitersLimit = maxWaiters.map(v => -v).getOrElse(failedState + 1)

  private[this] final val permit = new Permit {
    override final def release(): Unit = AsyncSemaphore.this.release()
  }
  private[this] final val permitFuture = Future.value(permit)
  private[this] final val permitReturn = Return(permit)
  private[this] final val releasePermit: Any => Unit = _ => permit.release()

  @tailrec private[this] final def release(): Unit = {
    val s = state.get()
    if (s != failedState) {
      if (!state.compareAndSet(s, s + 1) ||
        // If the waiter is already satisfied, it must
        // have been interrupted, so we can simply move on.
        (s < 0 && !pollWaiter().updateIfEmpty(permitReturn))) {
        release()
      }
    }
  }

  def numWaiters: Int = {
    val s = state.get()
    if (s < 0 && s != failedState) -s
    else 0
  }

  def numInitialPermits: Int = initialPermits

  def numPermitsAvailable: Int = {
    val s = state.get()
    if (s > 0) s
    else 0
  }

  /**
   * Fail the semaphore and stop it from distributing further permits. Subsequent
   * attempts to acquire a permit fail with `exc`. This semaphore's queued waiters
   * are also failed with `exc`.
   */
  def fail(exc: Throwable): Unit = {
    failure = Future.exception(exc)
    var s = state.getAndSet(failedState)
    // drain the wait queue
    while (s < 0 && s != failedState) {
      pollWaiter().raise(exc)
      s += 1
    }
  }

  /**
   * Polls from the wait queue until it returns an item. Must be called
   * after a state modification that indicates that a new waiter will be available
   * shortly. The retry accounts for the race when `acquire` updates the state to
   * indicate that a waiter is available but it hasn't been added to the queue yet.
   * @return
   */
  @tailrec private[this] final def pollWaiter(): Waiter = {
    val w = waiters.poll()
    if (w != null) {
      w
    } else {
      pollWaiter()
    }
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
  def acquire(): Future[Permit] = {
    @tailrec def loop(): Future[Permit] = {
      val s = state.get()
      if (s == failedState) {
        failure
      } else if (s == waitersLimit) {
        AsyncSemaphore.MaxWaitersExceededException
      } else if (state.compareAndSet(s, s - 1)) {
        if (s > 0) {
          permitFuture
        } else {
          val w = new Waiter
          waiters.add(w)
          w
        }
      } else {
        loop()
      }
    }
    loop()
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
      val f =
        try func
        catch {
          case NonFatal(e) =>
            Future.exception(e)
          case e: Throwable =>
            permit.release()
            throw e
        }
      f.respond(releasePermit)
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
    acquire().map { _ =>
      try {
        func
      } finally {
        permit.release()
      }
    }
}

object AsyncSemaphore {

  private final class Waiter extends Promise[Permit] with Promise.InterruptHandler {
    // the waiter will be removed from the queue later when a permit is released
    override protected def onInterrupt(t: Throwable): Unit = updateIfEmpty(Throw(t))
  }

  private val MaxWaitersExceededException =
    Future.exception(new RejectedExecutionException("Max waiters exceeded"))
}
