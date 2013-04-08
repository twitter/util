package com.twitter.concurrent

import java.util.concurrent.RejectedExecutionException
import java.util.ArrayDeque
import com.twitter.util.{Promise, Future}

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
            waitq.addLast(promise)
            promise
        }
      }
    }
  }

}

object AsyncSemaphore {
  private val MaxWaitersExceededException =
    Future.exception(new RejectedExecutionException("Max waiters exceeded"))
}
