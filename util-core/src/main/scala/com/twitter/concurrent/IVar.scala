package com.twitter.concurrent

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


/**
 * I-structured variables provide write-once semantics with
 * synchronization on read.  IVars may be thought of as a skeletal
 * Future: it provides read synchronization, but no error handling,
 * composition etc.
 */

class IVar[A] {
  private[this] var waitq: List[A => Unit] = Nil
  private[this] var chainq: List[IVar[A]] = Nil
  @volatile private[this] var result: Option[A] = None

  override def toString = "Ivar@%s(waitq=%s, chainq=%s, result=%s)".format(hashCode, waitq, chainq, result)

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
   * @return true if the value was set successfully.
   */
  def set(value: A): Boolean = {
    val didSet = synchronized {
      if (result.isDefined) false else {
        result = Some(value)
        true
      }
    }

    if (didSet) {
      while (waitq != Nil) {
        waitq.head(value)
        waitq = waitq.tail
      }

      while (chainq != Nil) {
        chainq.head.set(value)
        chainq = chainq.tail
      }
    }

    didSet
  }

  /**
   * Pass a block, 'k' which will be invoked when the value is
   * available.  Blocks will be invoked in order of addition.
   */
  def get(k: A => Unit) {
    val isSet = synchronized {
      if (result.isDefined) true else {
        waitq ::= k
        false
      }
    }

    if (isSet) result foreach(k)
  }

  /**
   * Provide an IVar that is chained to this one.  This provides a
   * happens-before relationship vis-a-vis *this* IVar.  That is: an
   * ordering that is visible to the consumer of this IVar (wrt.
   * gets) is preserved via chaining.
   */
  def chained: IVar[A] = synchronized {
    if (result.isDefined) this else {
      val iv = new IVar[A]
      chainq ::= iv
      iv
    }
  }
}
