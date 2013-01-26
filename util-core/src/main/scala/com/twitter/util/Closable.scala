package com.twitter.util

/**
 * Closable is a mixin trait to describe a closable ``resource``.
 */
trait Closable { self =>
  /**
   * Close the resource. The returned Future is completed when
   * the resource has been fully relinquished.
   */
  final def close(): Future[Unit] = close(Time.now)

  /**
   * Close the resource with the given deadline. This deadline is advisory,
   * giving the callee some leeway, for example to drain clients or finish
   * up other tasks.
   */
  def close(deadline: Time): Future[Unit]
}

object Closable {
  /**
   * Concurrent composition: creates a new closable which, when
   * closed, closes all of the underlying resources simultaneously.
   */
  def all(closables: Closable*): Closable = new Closable {
    def close(deadline: Time) = Future.join(closables map(_.close(deadline)))
  }

  /**
   * Sequential composition: create a new Closable which, when
   * closed, closes all of the underlying ones in sequence: that is,
   * resource ''n+1'' is not closed until resource ''n'' is.
   */
  def sequence(closables: Closable*): Closable = new Closable {
    private final def closeSeq(deadline: Time, closables: Seq[Closable]): Future[Unit] = 
      closables match {
        case Seq() => Future.Done
        case Seq(hd, tl@_*) => hd.close(deadline) flatMap { _ => closeSeq(deadline, tl) }
      }

    def close(deadline: Time) = closeSeq(deadline, closables)
  }
}
