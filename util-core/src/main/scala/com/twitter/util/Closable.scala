package com.twitter.util

import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.{util => ju}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger
import scala.annotation.tailrec
import scala.annotation.varargs
import scala.util.control.{NonFatal => NF}

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
 * Note: There is a Java-friendly API for this object: `com.twitter.util.Closables`.
 */
object Closable {
  private[this] val logger = Logger.getLogger("")
  private[this] val collectorThreadEnabled = new AtomicBoolean(true)

  /** Stop CollectClosables thread. */
  def stopCollectClosablesThread(): Unit = {
    if (collectorThreadEnabled.compareAndSet(true, false))
      Try(collectorThread.interrupt).onFailure(fatal =>
        logger
          .log(Level.SEVERE, "Current thread cannot interrupt CollectClosables thread", fatal))()
  }

  /** Provide Java access to the [[com.twitter.util.Closable]] mixin. */
  def close(o: AnyRef): Future[Unit] = o match {
    case c: Closable => c.close()
    case _ => Future.Done
  }

  /** Provide Java access to the [[com.twitter.util.Closable]] mixin. */
  def close(o: AnyRef, deadline: Time): Future[Unit] = o match {
    case c: Closable => c.close(deadline)
    case _ => Future.Done
  }

  /** Provide Java access to the [[com.twitter.util.Closable]] mixin. */
  def close(o: AnyRef, after: Duration): Future[Unit] = o match {
    case c: Closable => c.close(after)
    case _ => Future.Done
  }

  private[this] def safeClose(closable: Closable, deadline: Time): Future[Unit] =
    try closable.close(deadline)
    catch { case NF(ex) => Future.exception(ex) }

  /**
   * Concurrent composition: creates a new closable which, when
   * closed, closes all of the underlying resources simultaneously.
   */
  @varargs def all(closables: Closable*): Closable = new Closable {
    def close(deadline: Time): Future[Unit] = {
      val iter = closables.iterator
      @tailrec
      def checkNext(async: List[Future[Unit]]): List[Future[Unit]] = {
        if (!iter.hasNext) async
        else {
          safeClose(iter.next(), deadline) match {
            case Future.Done => checkNext(async)
            case f => checkNext(f :: async)
          }
        }
      }

      Future.join(checkNext(Nil))
    }
  }

  /**
   * Sequential composition: create a new Closable which, when
   * closed, closes all of the underlying ones in sequence: that is,
   * resource ''n+1'' is not closed until resource ''n'' is.
   *
   * @return the first failed [[Future]] should any of the `Closables`
   *         result in a failed [[Future]]. Any subsequent failures are
   *         included as suppressed exceptions.
   *
   * @note as with all `Closables`, the `deadline` passed to `close`
   *       is advisory.
   */
  def sequence(a: Closable, b: Closable): Closable = new Closable {
    def close(deadline: Time): Future[Unit] = {
      val first = safeClose(a, deadline)

      def onFirstComplete(t: Try[Unit]): Future[Unit] = {
        val second = safeClose(b, deadline)
        if (t.isThrow) {
          if (second eq Future.Done) first
          else {
            second.transform {
              case Return(_) => first
              case Throw(secondThrow) =>
                t.throwable.addSuppressed(secondThrow)
                first
            }
          }
        } else second
      }

      if (first eq Future.Done) onFirstComplete(Try.Unit)
      else first.transform(onFirstComplete)
    }
  }

  /**
   * Sequential composition: create a new Closable which, when
   * closed, closes all of the underlying ones in sequence: that is,
   * resource ''n+1'' is not closed until resource ''n'' is.
   *
   * @return the first failed [[Future]] should any of the `Closables`
   *         result in a failed [[Future]]. Any subsequent failures are
   *         included as suppressed exceptions.
   *
   * @note as with all `Closables`, the `deadline` passed to `close`
   *       is advisory.
   */
  @varargs
  def sequence(closables: Closable*): Closable = new Closable {
    private final def closeSeq(
      deadline: Time,
      closables: Seq[Closable],
      firstFailure: Option[Throw[Unit]]
    ): Future[Unit] = closables match {
      case Seq() =>
        firstFailure match {
          case Some(t) => Future.const(t)
          case None => Future.Done
        }
      case Seq(hd, tl @ _*) =>
        def onTry(_try: Try[Unit]): Future[Unit] = {
          val failure = _try match {
            case Return(_) => firstFailure
            case t @ Throw(_) =>
              firstFailure match {
                case Some(firstThrow) =>
                  firstThrow.throwable.addSuppressed(t.throwable)
                  firstFailure
                case None => Some(t)
              }
          }
          closeSeq(deadline, tl, failure)
        }

        val firstClose = safeClose(hd, deadline)
        // eagerness is needed by `Var`, see RB_ID=557464
        if (firstClose eq Future.Done) onTry(Try.Unit)
        else firstClose.transform(onTry)
    }

    def close(deadline: Time): Future[Unit] =
      closeSeq(deadline, closables, None)
  }

  /** A [[Closable]] that does nothing — `close` returns `Future.Done` */
  val nop: Closable = new Closable {
    def close(deadline: Time): Future[Unit] = Future.Done
  }

  /** Make a new [[Closable]] whose `close` method invokes f. */
  def make(f: Time => Future[Unit]): Closable = new Closable {
    def close(deadline: Time): Future[Unit] =
      try f(deadline)
      catch { case NF(ex) => Future.exception(ex) }
  }

  def ref(r: AtomicReference[Closable]): Closable = new Closable {
    def close(deadline: Time): Future[Unit] = r.getAndSet(nop).close(deadline)
  }

  private val refs = new ju.HashMap[Reference[Object], Closable]
  private val refq = new ReferenceQueue[Object]

  private val collectorThread = new Thread("CollectClosables") {
    override def run(): Unit = {
      while (collectorThreadEnabled.get()) {
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
            logger.log(
              Level.FINE,
              "com.twitter.util.Closable collector thread caught InterruptedException"
            )

          case NF(exc) =>
            logger.log(
              Level.SEVERE,
              "com.twitter.util.Closable collector thread caught exception",
              exc
            )

          case fatal: Throwable =>
            logger.log(
              Level.SEVERE,
              "com.twitter.util.Closable collector thread threw fatal exception",
              fatal
            )
            throw fatal
        }
      }
    }

    setDaemon(true)
    start()
  }

  /**
   * Close the given closable when `obj` is collected.
   *
   * Care should be taken to ensure that `closable` has no references
   * back to `obj` or it will prevent the close from taking place.
   */
  def closeOnCollect(closable: Closable, obj: Object): Unit = refs.synchronized {
    refs.put(new PhantomReference(obj, refq), closable)
  }
}
