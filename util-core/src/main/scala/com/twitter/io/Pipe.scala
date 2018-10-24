package com.twitter.io

import com.twitter.util.{Future, Promise, Return, Time}

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
 * resolve into [[IllegalStateException]] while leaving the pipe healthy). That is, the `write`
 * (its returned [[Future]]) is resolved when the `read` consumes the written data.
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
 *  - Writing into a closed pipe yields [[IllegalStateException]]
 *  - Reading from a closed pipe yields EOF ([[Future.None]])
 *  - Reading from or writing into a failed pipe yields a failure it was failed with
 *
 * It's also worth discussing how pipes are being closed. As closure is always initiated by a
 * producer (writer), there is a machinery allowing it to be notified when said closure is observed
 * by a consumer (reader).
 *
 * The following rules should help reasoning about closure signals in pipes:
 *
 * - Closing a pipe with a pending read resolves said read into EOF and returns a [[Future.Unit]]
 * - Closing a pipe with a pending write fails said write with [[IllegalStateException]] and
 *   returns a future that will be satisfied when a consumer observes the closure (EOF) via read
 * - Closing an idle pipe returns a future that will be satisfied when a consumer observes the
 *   closure (EOF) via read
 */
final class Pipe[A <: Buf] extends Reader[A] with Writer[A] {

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

  // thread-safety provided by synchronization on `this`
  private[this] var state: State[A] = State.Idle

  // satisfied when a `read` observes the EOF (after calling close())
  private[this] val closep: Promise[Unit] = new Promise[Unit]()

  def read(n: Int): Future[Option[A]] = {
    val (waiter, result) = synchronized {
      state match {
        case State.Failed(exc) =>
          (null, Future.exception(exc))

        case State.Closing =>
          state = State.Closed
          (closep, Future.None)

        case State.Closed =>
          (null, Future.None)

        case State.Idle =>
          val p = new Promise[Option[A]]
          state = State.Reading(n, p)
          (null, p)

        case State.Writing(buf, p) if buf.length <= n =>
          // pending write can fully fit into this requested read
          state = State.Idle
          (p, Future.value(Some(buf)))

        case State.Writing(buf, p) =>
          // pending write is larger than the requested read
          state = State.Writing(buf.slice(n, buf.length).asInstanceOf[A], p)
          (null, Future.value(Some(buf.slice(0, n).asInstanceOf[A])))

        case State.Reading(_, _) =>
          (null, Future.exception(new IllegalStateException("read() while read is pending")))
      }
    }

    if (waiter != null) waiter.setDone()
    result
  }

  def discard(): Unit = fail(new ReaderDiscardedException())

  def write(buf: A): Future[Unit] = {
    val (waiter, value, result) = synchronized {
      state match {
        case State.Failed(exc) =>
          (null, null, Future.exception(exc))

        case State.Closed | State.Closing =>
          (null, null, Future.exception(new IllegalStateException("write() while closed")))

        case State.Idle =>
          val p = new Promise[Unit]
          state = State.Writing(buf, p)
          (null, null, p)

        case State.Reading(n, p) if n < buf.length =>
          // pending reader doesn't have enough space for this write
          val nextp = new Promise[Unit]
          state = State.Writing(buf.slice(n, buf.length).asInstanceOf[A], nextp)
          (p, Some(buf.slice(0, n).asInstanceOf[A]), nextp)

        case State.Reading(n, p) =>
          // pending reader has enough space for the full write
          state = State.Idle
          (p, Some(buf), Future.Done)

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

  def fail(cause: Throwable): Unit = {
    val (closer, reader, writer) = synchronized {
      state match {
        case State.Closed | State.Failed(_) =>
          // do not update state to failing
          (null, null, null)
        case State.Idle | State.Closing =>
          state = State.Failed(cause)
          (closep, null, null)
        case State.Reading(_, p) =>
          state = State.Failed(cause)
          (closep, p, null)
        case State.Writing(_, p) =>
          state = State.Failed(cause)
          (closep, null, p)
      }
    }

    if (reader != null) reader.setException(cause)
    else if (writer != null) writer.setException(cause)

    if (closer != null) closer.setException(cause)
  }

  def close(deadline: Time): Future[Unit] = {
    val (closer, reader, writer) = synchronized {
      state match {
        case State.Failed(_) | State.Closed | State.Closing =>
          (null, null, null)

        case State.Idle =>
          state = State.Closing
          (null, null, null)

        case State.Reading(_, p) =>
          state = State.Closed
          (closep, p, null)

        case State.Writing(_, p) =>
          state = State.Closing
          (null, null, p)
      }
    }

    if (reader != null)
      reader.update(Return.None)
    else if (writer != null)
      writer.setException(new IllegalStateException("close() while write is pending"))

    if (closer != null) closer.setDone()

    onClose
  }

  def onClose: Future[Unit] = closep

  override def toString: String = synchronized(s"Pipe(state=$state)")
}

object Pipe {

  private sealed trait State[+A <: Buf]

  private object State {

    /** Indicates no reads or writes are pending, and is not closed. */
    case object Idle extends State[Nothing]

    /**
     * Indicates a read is pending and is awaiting a `write`.
     *
     * @param n number of bytes to read.
     * @param p when satisfied it indicates that this read has completed.
     */
    final case class Reading[A <: Buf](n: Int, p: Promise[Option[A]]) extends State[A]

    /**
     * Indicates a write of `buf` is pending to be `read`.
     *
     * @param buf the [[Buf]] to write.
     * @param p when satisfied it indicates that this write has been fully read.
     */
    final case class Writing[A <: Buf](buf: A, p: Promise[Unit]) extends State[A]

    /** Indicates the pipe was failed. */
    final case class Failed(exc: Throwable) extends State[Nothing]

    /** Indicates a close occurred while `Idle` — no reads or writes were pending.*/
    case object Closing extends State[Nothing]

    /** Indicates the reader has seen the EOF. No more reads or writes are allowed. */
    case object Closed extends State[Nothing]
  }
}
