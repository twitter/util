package com.twitter.io

import com.twitter.util.{Future, Promise, Return, Time}

/**
 * A synchronous in-memory pipe that connects [[Reader]] and [[Writer]] in the sense
 * that a reader's input is the output of a writer.
 *
 * Reads and writes on the pipe are matched one to one and only one outstanding `read`
 * or `write` is permitted in the current implementation. That is, the `write` (its
 * returned [[Future]]) is resolved when the `read` consumes the written data.
 *
 * It is safe to call `read`, `write`, and `close` concurrently. The individual calls
 * are synchronized on the given [[Pipe]].
 */
final class Pipe[A <: Buf] extends Reader[A] with Writer[A] {

  import Pipe._

  // thread-safety provided by synchronization on `this`
  private[this] var state: State[A] = State.Idle

  def read(n: Int): Future[Option[A]] = synchronized {
    state match {
      case State.Failing(exc) =>
        Future.exception(exc)

      case State.Closing(reof) =>
        state = State.Eof
        reof.setDone()
        Future.None

      case State.Eof =>
        Future.None

      case State.Idle =>
        val p = new Promise[Option[A]]
        state = State.Reading(n, p)
        p

      case State.Writing(buf, p) if buf.length <= n =>
        // pending write can fully fit into this requested read
        state = State.Idle
        p.setDone()
        Future.value(Some(buf))

      case State.Writing(buf, p) =>
        // pending write is larger than the requested read
        state = State.Writing(buf.slice(n, buf.length).asInstanceOf[A], p)
        Future.value(Some(buf.slice(0, n).asInstanceOf[A]))

      case State.Reading(_, _) =>
        Future.exception(new IllegalStateException("read() while Reading"))
    }
  }

  def discard(): Unit = synchronized {
    val cause = new Reader.ReaderDiscarded()
    fail(cause)
  }

  def write(buf: A): Future[Unit] = synchronized {
    state match {
      case State.Failing(exc) =>
        Future.exception(exc)

      case State.Eof | State.Closing(_) =>
        Future.exception(new IllegalStateException("write after close"))

      case State.Idle =>
        val p = new Promise[Unit]()
        state = State.Writing(buf, p)
        p

      case State.Reading(n, p) if n < buf.length =>
        // pending reader doesn't have enough space for this write
        val nextp = new Promise[Unit]()
        state = State.Writing(buf.slice(n, buf.length).asInstanceOf[A], nextp)
        p.setValue(Some(buf.slice(0, n).asInstanceOf[A]))
        nextp

      case State.Reading(n, p) =>
        // pending reader has enough space for the full write
        state = State.Idle
        p.setValue(Some(buf))
        Future.Done

      case State.Writing(_, _) =>
        Future.exception(new IllegalStateException("write while Writing"))
    }
  }

  def fail(cause: Throwable): Unit = synchronized {
    // We fix the state before inspecting it so we enter in a
    // good state if setting promises recurses.
    val oldState = state
    oldState match {
      case State.Eof | State.Failing(_) =>
      // do not update state to failing
      case State.Idle =>
        state = State.Failing(cause)
      case State.Closing(reof) =>
        state = State.Failing(cause)
        reof.setException(cause)
      case State.Reading(_, p) =>
        state = State.Failing(cause)
        p.setException(cause)
      case State.Writing(_, p) =>
        state = State.Failing(cause)
        p.setException(cause)
    }
  }

  def close(deadline: Time): Future[Unit] = synchronized {
    state match {
      case State.Failing(t) =>
        Future.exception(t)

      case State.Eof =>
        Future.Done

      case State.Closing(p) =>
        p

      case State.Idle =>
        val reof = new Promise[Unit]()
        state = State.Closing(reof)
        reof

      case State.Reading(_, p) =>
        state = State.Eof
        p.update(Return.None)
        Future.Done

      case State.Writing(_, p) =>
        val reof = new Promise[Unit]()
        state = State.Closing(reof)
        p.setException(new IllegalStateException("close while write is pending"))
        reof
    }
  }

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

    /** Indicates this was `fail`-ed or `discard`-ed. */
    final case class Failing(exc: Throwable) extends State[Nothing]

    /**
     * Indicates a close occurred while `Idle` — no reads or writes were pending.
     *
     * @param reof satisfied when a `read` sees the EOF.
     */
    final case class Closing(reof: Promise[Unit]) extends State[Nothing]

    /** Indicates the reader has seen the EOF. No more reads or writes are allowed. */
    case object Eof extends State[Nothing]
  }
}
