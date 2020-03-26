package com.twitter.util

/**
 * A mixin trait to describe resources that are an idempotent [[Closable]].
 *
 * The first call to `close(Time)` triggers closing the resource with the
 * provided deadline while subsequent calls will yield the same `Future`
 * as the first invocation irrespective of the deadline provided.
 *
 * @see [[CloseOnce]] if you are mixing in or extending an existing [[Closable]]
 *      to prevent any "inherits from conflicting members" compile-time issues.
 */
trait ClosableOnce extends Closable with CloseOnce

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
