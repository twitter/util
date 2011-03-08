package com.twitter.concurrent

/**
 * An AsyncSemaphore is a traditional semaphore but with asynchronous
 * execution. Grabbing a permit returns a Future[Permit]
 */

import collection.mutable.Queue
import com.twitter.util.{Promise, Future}

class AsyncSemaphore(initialPermits: Int = 0) {
  private[this] var waiters = new Queue[() => Unit]
  private[this] var availablePermits = initialPermits

  private[this] class SemaphorePermit extends Permit {
    /**
     * Indicate that you are done with your Permit.
     */
    def release() = {
      val run = AsyncSemaphore.this.synchronized {
        availablePermits += 1
        if (availablePermits > 0 && !waiters.isEmpty) {
          availablePermits -= 1
          Some(waiters.dequeue())
        } else {
          None
        }
      }

      run foreach { _() }
    }
  }

  def numWaiters = synchronized { waiters.size }
  def numPermitsAvailable = availablePermits

  /**
   * Acquire a Permit, asynchronously. Be sure to permit.release() in a 'finally'
   * block of your onSuccess() callback.
   *
   * @return a Future[Permit] when the Future is satisfied, computation can proceed.
   */
  def acquire(): Future[Permit] = {
    val result = new Promise[Permit]

    def setAcquired() {
      result.setValue(new SemaphorePermit)
    }

    val runNow = synchronized {
      if (availablePermits > 0) {
        availablePermits -= 1
        true
      } else {
        waiters enqueue(setAcquired)
        false
      }
    }

    if (runNow) setAcquired()
    result
  }
}
