package com.twitter.io

import com.twitter.util.{Future, Promise, Return, Throw}
import java.util.concurrent.atomic.AtomicReference

/**
 * We want to ensure that this reader always satisfies these invariants:
 * 1. Satisfied closep is always aligned with the state
 * 2. Reading from a discarded reader will always return ReaderDiscardedException
 * 3. Reading from a fully read reader will always return None
 * 4. Reading from a failed reader will always return the exception it has thrown
 *
 * We achieved this with a state machine where any access to the state is synchronized,
 * and by ensuring that we always verify the update of state succeeded before setting closep,
 * and by preventing changing the state when the reader is failed, fully read, or discarded
 */
private[io] final class FutureReader[A](fa: Future[A]) extends Reader[A] {
  import FutureReader._

  private[this] val closep = Promise[StreamTermination]()
  private[this] val state = new AtomicReference[State](State.Idle)

  def read(): Future[Option[A]] = {
    state.get() match {
      case State.Idle =>
        if (state.compareAndSet(State.Idle, State.Reading)) {
          fa.map(Some.apply).respond {
            case t: Throw[_] =>
              if (state.compareAndSet(State.Reading, State.Failed)) {
                closep.update(t.cast[StreamTermination])
              }
            case Return(_) =>
              // it is safe to return the value even when the `state` is not updated, which could happen
              // when the `state` is updated to `Discarded` in `discard()`, in that case, we still retain
              // the execution order because `read()` happens before `discard()`
              state.compareAndSet(State.Reading, State.Read)
          }
        } else {
          // when `state` is updated to `Reading` or `Discarded`
          read()
        }
      case State.Reading =>
        // multiple outstanding reads are not allowed
        Future.exception(new IllegalStateException("read() while read is pending"))
      case State.Read =>
        // update would fail if `state` is already updated to `Discarded` in `discard()`
        if (state.compareAndSet(State.Read, State.FullyRead)) {
          closep.update(StreamTermination.FullyRead.Return)
        }
        closep.flatMap {
          case StreamTermination.FullyRead => Future.None
          case StreamTermination.Discarded => Future.exception(new ReaderDiscardedException)
        }
      case State.Failed =>
        // closep is guaranteed to be an exception, flatMap should never be triggered but return the exception
        closep.flatMap(_ => Future.None)
      case State.FullyRead =>
        Future.None
      case State.Discarded =>
        Future.exception(new ReaderDiscardedException)
    }
  }

  def discard(): Unit = {
    if (state.compareAndSet(State.Idle, State.Discarded) || state
        .compareAndSet(State.Read, State.Discarded) || state.compareAndSet(
        State.Reading,
        State.Discarded)) {
      closep.update(StreamTermination.Discarded.Return)
      fa.raise(new ReaderDiscardedException)
    }
  }

  def onClose: Future[StreamTermination] = closep
}

object FutureReader {

  /**
   * Indicates reader state when the reader is created via FutureReader
   */
  sealed trait State
  object State {

    /** Indicates the reader is ready to be read. */
    case object Idle extends State

    /** Indicates the reader has been read. */
    case object Read extends State

    /** Indicates a reading is in progress. */
    case object Reading extends State

    /** Indicates an exception occurred during reading */
    case object Failed extends State

    /** Indicates the EOS has been observed. */
    case object FullyRead extends State

    /** Indicates the reader has been discarded. */
    case object Discarded extends State

  }
}
