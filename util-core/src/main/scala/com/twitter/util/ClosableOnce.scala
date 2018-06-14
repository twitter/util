package com.twitter.util

import scala.util.control.NonFatal

/**
 * A mixin trait to describe resources that are an idempotent [[Closable]].
 *
 * The first call to `close(Time)` triggers closing the resource with the
 * provided deadline while subsequent calls will yield the same `Future`
 * as the first invocation irrespective of the deadline provided.
 */
trait ClosableOnce extends Closable {

  // Our intrinsic lock for mutating the `closed` field
  private[this] val closePromise = Promise[Unit]()
  private[this] var closed = false

  private[this] def once(): Boolean = closePromise.synchronized {
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

  final def close(deadline: Time): Future[Unit] = {
    if (once()) {
      val closeF =
        try closeOnce(deadline)
        catch { case NonFatal(ex) => Future.exception(ex) }
      closePromise.become(closeF)
    }
    closePromise
  }
}

object ClosableOnce {

  private final class ClosableOnceWrapper(underlying: Closable) extends ClosableOnce {
    protected def closeOnce(deadline: Time): Future[Unit] = underlying.close(deadline)
  }

  /**
   * Convert the provided [[Closable]] to a [[ClosableOnce]].
   */
  def of(closable: Closable): ClosableOnce = closable match {
    case once: ClosableOnce => once
    case closable => new ClosableOnceWrapper(closable)
  }
}

/**
 * A Java-friendly API for the [[ClosableOnce]] trait.
 */
abstract class AbstractClosableOnce extends ClosableOnce
