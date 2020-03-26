package com.twitter.util

import scala.util.control.NonFatal

/**
 * A mixin trait to describe resources that are an idempotent [[Closable]].
 *
 * The first call to `close(Time)` triggers closing the resource with the
 * provided deadline while subsequent calls will yield the same `Future`
 * as the first invocation irrespective of the deadline provided.
 *
 * @see [[ClosableOnce]] if you are not mixing in or extending an existing [[Closable]]
 * @see [[ClosableOnce.of(closable: Closable)]] for creating a proxy to a [[Closable]]
 *       that has already been instantiated.
 */
trait CloseOnce { self: Closable =>
  // Our intrinsic lock for mutating the `closed` field
  private[this] val closePromise: Promise[Unit] = Promise[Unit]()
  @volatile private[this] var closed: Boolean = false

  // optimized to limit synchronized locking scope
  private[this] def firstCloseInvoked(): Boolean = closePromise.synchronized {
    if (closed) {
      false
    } else {
      closed = true
      true
    }
  }

  /**
   * Close the resource with the given deadline exactly once. This deadline is
   * advisory, giving the callee some leeway, for example to drain clients or
   * finish up other tasks.
   *
   * @note if this method throws a synchronous exception, that exception will
   *       be wrapped in a failed future.
   */
  protected def closeOnce(deadline: Time): Future[Unit]

  /**
   * Signals whether or not this [[Closable]] has been closed.
   *
   * @return return true if [[close]] has been initiated, false otherwise
   */
  final def isClosed: Boolean = closed

  override final def close(deadline: Time): Future[Unit] = {
    // only call `closeOnce()` and assign `closePromise` if this is the first `close()` invocation
    if (firstCloseInvoked()) {
      val closeF =
        try {
          closeOnce(deadline)
        } catch {
          case NonFatal(ex) => Future.exception(ex)
        }
      closePromise.become(closeF)
    }

    closePromise
  }

}
