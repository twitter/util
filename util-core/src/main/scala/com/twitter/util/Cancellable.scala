package com.twitter.util

import scala.collection.mutable.ArrayBuffer

import com.twitter.concurrent.IVar

/**
 * Provides a mixin to make the underlying object *cancellable*.
 * Cancellable objects may be linked to each other (one way) in order
 * to propagate cancellation.
 *
 * Note that the underlying object may or may not _respond_ to the
 * cancellation request.  That is, calling 'cancel()' does not
 * guarantee the cancellation of the computation; rather it is a hint
 * that the provider of the computation may choose to ignore.
 */

trait Cancellable {
  private[this] val linked = new ArrayBuffer[Cancellable](0)
  private[this] val cancelled = new IVar[Unit]

  def isCancelled = cancelled.isDefined

  /**
   * Cancel the computation.  The cancellation is propagated to linked
   * cancellable objects.
   */
  def cancel() {
    cancelled.set(())
  }

  /**
   * Link this cancellable computation to 'other'.  This means
   * cancellation of 'this' computation will propagate to 'other'.
   */
  def linkTo(other: Cancellable) {
    cancelled.get { _ => other.cancel() }
  }
}
