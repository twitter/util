package com.twitter.io

import com.twitter.util.{Future, Promise, Return, Throw, Time, Timer}

/**
 * A synchronous in-memory pipe that connects [[Reader]] and [[Writer]] in the sense
 * that a reader's input is the output of a writer.
 *
 * A pipe is structured as a smash of both interfaces, a [[Reader]] and a [[Writer]] such that can
 * be passed directly to a consumer or a producer.
 *
 * {{{
 *   def consumer(r: Reader[Buf]): Future[Unit] = ???
 *   def producer(w: Writer[Buf]): Future[Unit] = ???
 *
 *   val p = new Pipe[Buf]
 *
 *   consumer(p)
 *   producer(p)
 * }}}
 *
 * Reads and writes on the pipe are matched one to one and only one outstanding `read`
 * or `write` is permitted in the current implementation (multiple pending writes or reads
 * resolve into `IllegalStateException` while leaving the pipe healthy). That is, the `write`
 * (its returned [[com.twitter.util.Future]]) is resolved when the `read` consumes the written data.
 *
 * Here is, for example, a very typical write-loop that writes into a pipe-backed [[Writer]]:
 *
 * {{{
 *   def writeLoop(w: Writer[Buf], data: List[Buf]): Future[Unit] = data match {
 *     case h :: t => p.write(h).before(writeLoop(w, t))
 *     case Nil => w.close()
 *   }
 * }}}
 *
 * Reading from a pipe-backed [[Reader]] is no different from working with any other reader:
 *
 *{{{
 *   def readLoop(r: Reader[Buf], process: Buf => Future[Unit]): Future[Unit] = r.read().flatMap {
 *     case Some(chunk) => process(chunk).before(readLoop(r, process))
 *     case None => Future.Done
 *   }
 * }}}
 *
 * == Thread Safety ==
 *
 * It is safe to call `read`, `write`, `fail`, `discard`, and `close` concurrently. The individual
 * calls are synchronized on the given [[Pipe]].
 *
 * == Closing or Failing Pipes ==
 *
 * Besides expecting a write or a read, a pipe can be closed or failed. A writer can do both `close`
 * and `fail` the pipe, while reader can only fail the pipe via `discard`.
 *
 * The following behavior is expected with regards to reading from or writing into a closed or a
 * failed pipe:
 *
 *  - Writing into a closed pipe yields `IllegalStateException`
 *  - Reading from a closed pipe yields EOF ([[com.twitter.util.Future.None]])
 *  - Reading from or writing into a failed pipe yields a failure it was failed with
 *
 * It's also worth discussing how pipes are being closed. As closure is always initiated by a
 * producer (writer), there is a machinery allowing it to be notified when said closure is observed
 * by a consumer (reader).
 *
 * The following rules should help reasoning about closure signals in pipes:
 *
 * - Closing a pipe with a pending read resolves said read into EOF and returns a
 *   [[com.twitter.util.Future.Unit]]
 * - Closing a pipe with a pending write by default fails said write with `IllegalStateException` and
 *   returns a future that will be satisfied when a consumer observes the closure (EOF) via read. If
 *   a timer is provided, the pipe will wait until the provided deadline for a successful read before
 *   failing the write.
 * - Closing an idle pipe returns a future that will be satisfied when a consumer observes the
 *   closure (EOF) via read when a timer is provided, otherwise the pipe will be closed immedidately.
 */
final class Pipe[A](timer: Timer) extends Reader[A] with Writer[A] {

  // Implementation Notes:
  //
  // - The the only mutable state of this pipe is `state` and the access to it is synchronized
  //   on `this`.
  //
  // - We do not run promises under a lock (`synchronized`) as it could lead to a deadlock if
  //   there are interleaved queue operations in the waiter closure.
  //
  // - Although promises are run without a lock, synchronized `state` transitions guarantee
  //   a promise that needs to be run is now owned exclusively by a caller, hence no concurrent
  //   updates will be racing with it.

  import Pipe._

  /**
   * For Java compatability
   */
  def this() = this(Timer.Nil)

  // thread-safety provided by synchronization on `this`
  private[this] var state: State[A] = State.Idle

  // satisfied when a `read` observes the EOF (after calling close())
  private[this] val closep: Promise[StreamTermination] = new Promise[StreamTermination]()

  def read(): Future[Option[A]] = {
    val (waiter, result) = synchronized {
      state match {
        case State.Failed(exc) =>
          (null, Future.exception(exc))

        case State.Closed =>
          (null, Future.None)

        case State.Idle =>
          val p = new Promise[Option[A]]
          state = State.Reading(p)
          (null, p)

        case State.Writing(buf, p) =>
          state = State.Idle
          (p, Future.value(Some(buf)))

        case State.Reading(_) =>
          (null, Future.exception(new IllegalStateException("read() while read is pending")))

        case State.Closing(buf, p) =>
          val result = buf match {
            case None =>
              state = State.Closed
              Future.None
            case _ =>
              state = State.Closing(None, null)
              Future.value(buf)
          }
          (p, result)
      }
    }

    if (waiter != null) waiter.setDone()
    if (result eq Future.None) closep.updateIfEmpty(StreamTermination.FullyRead.Return)
    result
  }

  def discard(): Unit = {
    fail(new ReaderDiscardedException(), discard = true)
  }

  def write(buf: A): Future[Unit] = {
    val (waiter, value, result) = synchronized {
      state match {
        case State.Failed(exc) =>
          (null, null, Future.exception(exc))

        case State.Closed | State.Closing(_, _) =>
          (null, null, Future.exception(new IllegalStateException("write() while closed")))

        case State.Idle =>
          val p = new Promise[Unit]
          state = State.Writing(buf, p)
          (null, null, p)

        case State.Reading(p: Promise[Option[A]]) =>
          // pending reader has enough space for the full write
          state = State.Idle
          // The Scala 3 compiler differentiates between the class type, `A` and the the defined
          // type of Promise in `Reading`, due to `State` being covariant in it's type parameter
          // but invariant in `Reading`. Let's explictly cast this type as we know it will be of
          // `Promise[Option[A]]`. See CSL-11210 or https://github.com/lampepfl/dotty/issues/13126
          (p.asInstanceOf[Promise[Option[A]]], Some(buf), Future.Done)

        case State.Writing(_, _) =>
          (
            null,
            null,
            Future.exception(new IllegalStateException("write() while write is pending"))
          )
      }
    }

    // The waiter and the value are mutually inclusive so just checking against waiter is adequate.
    if (waiter != null) waiter.setValue(value)
    result
  }

  def fail(cause: Throwable): Unit = fail(cause, discard = false)

  private[this] def fail(cause: Throwable, discard: Boolean): Unit = {
    val (closer, reader, writer) = synchronized {
      state match {
        case State.Closed | State.Failed(_) =>
          // do not update state to failing
          (null, null, null)
        case State.Idle =>
          state = State.Failed(cause)
          (closep, null, null)
        case State.Closing(_, p) =>
          state = State.Failed(cause)
          (closep, null, p)
        case State.Reading(p) =>
          state = State.Failed(cause)
          (closep, p, null)
        case State.Writing(_, p) =>
          state = State.Failed(cause)
          (closep, null, p)
      }
    }

    if (reader != null) reader.setException(cause)
    else if (writer != null) writer.setException(cause)

    if (closer != null) {
      if (discard) closer.updateIfEmpty(StreamTermination.Discarded.Return)
      else closer.updateIfEmpty(Throw(cause))
    }
  }

  private def closeLater(deadline: Time): Future[Unit] = {
    timer.doLater(Time.now.until(deadline)) {
      Pipe.this.synchronized {
        state match {
          case State.Failed(_) | State.Closed =>
          case _ => state = State.Closed
        }
      }
    }
  }

  def close(deadline: Time): Future[Unit] = {
    val (reader, writer) = synchronized {
      state match {
        case State.Failed(_) | State.Closed | State.Closing(_, _) =>
          (null, null)

        case State.Idle =>
          state = State.Closing(None, null)
          (null, null)

        case State.Reading(p) =>
          state = State.Closed
          (p, null)

        case State.Writing(buf, p) =>
          state = State.Closing(Some(buf), p)
          (null, p)
      }
    }

    if (reader != null) {
      reader.update(Return.None)
      closep.update(StreamTermination.FullyRead.Return)
    } else if (writer != null) {
      closeLater(deadline).respond { _ =>
        val exn = new IllegalStateException("close() while write is pending")
        writer.updateIfEmpty(Throw(exn))
        closep.updateIfEmpty(Throw(exn))
      }
    }

    onClose.unit
  }

  def onClose: Future[StreamTermination] = closep

  override def toString: String = synchronized(s"Pipe(state=$state)")
}

object Pipe {

  private sealed trait State[+A]

  private object State {

    /** Indicates no reads or writes are pending, and is not closed. */
    case object Idle extends State[Nothing]

    /**
     * Indicates a read is pending and is awaiting a `write`.
     *
     * @param p when satisfied it indicates that this read has completed.
     */
    final case class Reading[A](p: Promise[Option[A]]) extends State[A]

    /**
     * Indicates a write of `value` is pending to be `read`.
     *
     * @param value the value to write.
     * @param p when satisfied it indicates that this write has been fully read.
     */
    final case class Writing[A](value: A, p: Promise[Unit]) extends State[A]

    /** Indicates the pipe was failed. */
    final case class Failed(exc: Throwable) extends State[Nothing]

    /**
     * Indicates a close occurred while a write was pending.
     *
     * @param value the pending write
     * @param p when satisfied it indicates that this write has been fully read or the
     *          close deadline has expired before it has been fully read.
     * */
    final case class Closing[A](value: Option[A], p: Promise[Unit]) extends State[A]

    /** Indicates the reader has seen the EOF. No more reads or writes are allowed. */
    case object Closed extends State[Nothing]
  }

  /**
   * Copy elements from a Reader to a Writer. The Reader will be discarded if
   * `copy` is cancelled (discarding the writer). The Writer is unmanaged, the caller
   * is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Pipe.copy(r, w, n) ensure w.close()
   * }}}
   */
  def copy[A](r: Reader[A], w: Writer[A]): Future[Unit] = {
    def loop(): Future[Unit] =
      r.read().flatMap {
        case None => Future.Done
        case Some(elem) => w.write(elem) before loop()
      }

    w.onClose.respond {
      case Return(StreamTermination.Discarded) => r.discard()
      case _ => ()
    }
    val p = new Promise[Unit]
    // We have to do this because discarding the writer doesn't interrupt read
    // operations, it only fails the next write operation.
    loop().proxyTo(p)
    p.setInterruptHandler { case _ => r.discard() }
    p
  }
}
