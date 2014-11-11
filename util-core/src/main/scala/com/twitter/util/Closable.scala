package com.twitter.util

import java.lang.ref.{PhantomReference, Reference, ReferenceQueue}
import java.util.HashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.{Level, Logger}

/**
 * Closable is a mixin trait to describe a closable ``resource``.
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.util.AbstractClosable]].
 */
trait Closable { self =>

  /**
   * Close the resource. The returned Future is completed when
   * the resource has been fully relinquished.
   */
  final def close(): Future[Unit] = close(Time.Bottom)

  /**
   * Close the resource with the given deadline. This deadline is advisory,
   * giving the callee some leeway, for example to drain clients or finish
   * up other tasks.
   */
  def close(deadline: Time): Future[Unit]

  /**
   * Close the resource with the given timeout. This timeout is advisory,
   * giving the callee some leeway, for example to drain clients or finish
   * up other tasks.
   */
  def close(after: Duration): Future[Unit] = close(after.fromNow)
}

/**
 * Abstract `Closable` class for Java compatibility.
 */
abstract class AbstractClosable extends Closable

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.util.Closables]].
 */
object Closable {
  private[this] val logger = Logger.getLogger("")

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

  /** A Closable that does nothing immediately. */
  val nop: Closable = new Closable {
    def close(deadline: Time) = Future.Done
  }

  /** Make a new Closable whose close method invokes f. */
  def make(f: Time => Future[Unit]): Closable = new Closable {
    def close(deadline: Time) = f(deadline)
  }

  def ref(r: AtomicReference[Closable]): Closable = new Closable {
    def close(deadline: Time) = r.getAndSet(nop).close(deadline)
  }

  private val refs = new HashMap[Reference[Object], Closable]
  private val refq = new ReferenceQueue[Object]

  private val collectorThread = new Thread("CollectClosables") {
    override def run() {
      while(true) {
        try {
          val ref = refq.remove()
          val closable = refs.synchronized(refs.remove(ref))
          if (closable != null)
            closable.close()
          ref.clear()
        } catch {
          case _: InterruptedException =>
            // Thread interrupted while blocked on `refq.remove()`. Daemon
            // threads shouldn't be interrupted explicitly on `System.exit`, but
            // SBT does it anyway.
            logger.log(Level.FINE,
              "com.twitter.util.Closable collector thread caught InterruptedException")

          case NonFatal(exc) =>
            logger.log(Level.SEVERE,
              "com.twitter.util.Closable collector thread caught exception", exc)

          case fatal: Throwable =>
            logger.log(Level.SEVERE,
              "com.twitter.util.Closable collector thread threw fatal exception", fatal)
            throw fatal
        }
      }
    }

    setDaemon(true)
    start()
  }

  /**
   * Close the given closable when `obj` is collected.
   */
  def closeOnCollect(closable: Closable, obj: Object): Unit = refs.synchronized {
    refs.put(new PhantomReference(obj, refq), closable)
  }
}
