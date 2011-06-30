package com.twitter.util

import scala.collection.mutable.ArrayBuffer

/**
 * Defines a trait that makes the underlying object *cancellable*.
 * Cancellable objects may be linked to each other (one way) in order
 * to propagate cancellation.
 *
 * Note that the underlying object may or may not _respond_ to the
 * cancellation request.  That is, calling 'cancel()' does not
 * guarantee the cancellation of the computation; rather it is a hint
 * that the provider of the computation may choose to ignore.
 */

trait Cancellable {
  def isCancelled: Boolean

  /**
   * Cancel the computation.  The cancellation is propagated to linked
   * cancellable objects.
   */
  def cancel(): Unit

  /**
   * Link this cancellable computation to 'other'.  This means
   * cancellation of 'this' computation will propagate to 'other'.
   */
  def linkTo(other: Cancellable): Unit
}

class CancellableSink(f: => Unit) extends Cancellable {
  @volatile var wasCancelled = false
  def isCancelled = wasCancelled
  def cancel() { f; wasCancelled = true }
  def linkTo(other: Cancellable) {
    throw new Exception("linking not supported in CancellableSink")
  }
}
