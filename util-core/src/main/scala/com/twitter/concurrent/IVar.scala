package com.twitter.concurrent

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


/**
 * I-structured variables provide write-once semantics with
 * synchronization on read.  Additionally, gets preserve a
 * happens-before relationship vis-a-vis the IVar itself: that is, an
 * ordering of gets that is visible to the consumer is preserved.
 * IVars may be thought of as a skeletal Future: it provides read
 * synchronization, but no error handling, composition etc.
 */

class IVar[A](expectedWaiters: Int = 1) {
  private[this] var waitq = new ArrayBuffer[A => Unit](expectedWaiters)
  @volatile private[this] var result: Option[A] = None
  private[this] val didPut = new AtomicBoolean(false)

  /**
   * A blocking get.
   */
  def apply(): A = {
    val latch = new CountDownLatch(1)
    get { _ => latch.countDown() }
    latch.await()
    result.get
  }

  /**
   * @return true if the value has been set.
   */
  def isDefined = result.isDefined

  /**
   * Set the value - only the first call will be successful.
   *
   * @return true if the value was set succesfully.
   */
  def set(value: A): Boolean = {
    if (!didPut.compareAndSet(false, true)) false else {
      var continue = true
      do {
        val waiters = synchronized {
          val waiters = waitq
          waitq = new ArrayBuffer[A => Unit](0)
          waiters
        }

        waiters foreach { _(value) }

        continue = synchronized {
          if (waitq.isEmpty) {
            result = Some(value)
            false
          } else {
            true
          }
        }
      } while (continue)

      true
    }
  }

  /**
   * Pass a block, 'k' which will be invoked when the value is
   * available.  Blocks will be invoked in order of addition.
   */
  def get(k: A => Unit) {
    val isPut = if (result.isDefined) true else synchronized {
      if (result.isDefined) true else {
        waitq += k
        false
      }
    }

    if (isPut)
      result foreach(k)
  }
}
