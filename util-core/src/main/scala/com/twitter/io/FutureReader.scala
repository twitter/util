package com.twitter.io

import com.twitter.io.FutureReader._
import com.twitter.util.Future
import com.twitter.util.Promise
import com.twitter.util.Return
import com.twitter.util.Throw

/**
 * We want to ensure that this reader always satisfies these invariants:
 * 1. Satisfied closep is always aligned with the state
 * 2. Reading from a discarded reader will always return ReaderDiscardedException
 * 3. Reading from a fully read reader will always return None
 * 4. Reading from a failed reader will always return the exception it has thrown
 *
 * We achieved this with a state machine where any access to the state is synchronized,
 * and by preventing changing the state when the reader is failed, fully read, or discarded
 */
private[io] final class FutureReader[A](fa: Future[A]) extends Reader[A] {
  private[this] val closep = Promise[StreamTermination]()
  private[this] var state: State = State.Idle

  def read(): Future[Option[A]] = {
    val (updatedState, result) = synchronized {
      val result = state match {
        case State.Idle =>
          fa.transform {
            case Throw(t) =>
              state = State.Failed(t)
              Future.exception(t)
            case Return(v) =>
              state = State.Read
              Future.value(Some(v))
          }
        case State.Read =>
          state = State.FullyRead
          Future.None
        case State.Failed(_) =>
          // closep is guaranteed to be an exception, flatMap
          // should never be triggered but return the exception
          closep.flatMap(_ => Future.None)
        case State.FullyRead =>
          Future.None
        case State.Discarded =>
          Future.exception(new ReaderDiscardedException)
      }
      (state, result)
    }

    updatedState match {
      case State.Failed(t) =>
        // use `updateIfEmpty` instead of `update` here because we don't want to throw
        // `ImmutableResult` exception when reading from a failed `FutureReader` multiple times
        closep.updateIfEmpty(Throw(t))
      case State.FullyRead =>
        closep.updateIfEmpty(StreamTermination.FullyRead.Return)
      case _ =>
    }

    result
  }

  def discard(): Unit = {
    val discarded = synchronized {
      state match {
        case State.Idle | State.Read =>
          state = State.Discarded
          true
        case _ => false
      }
    }
    if (discarded) {
      closep.updateIfEmpty(StreamTermination.Discarded.Return)
      fa.raise(new ReaderDiscardedException)
    }
  }

  def onClose: Future[StreamTermination] = closep
}

object FutureReader {

  /**
   * Indicates reader state when the reader is created via FutureReader
   */
  private sealed trait State
  private object State {

    /** Indicates the reader is ready to be read. */
    case object Idle extends State

    /** Indicates the reader has been read. */
    case object Read extends State

    /** Indicates an exception occurred during reading */
    case class Failed(t: Throwable) extends State

    /** Indicates the EOS has been observed. */
    case object FullyRead extends State

    /** Indicates the reader has been discarded. */
    case object Discarded extends State
  }
}
