package com.twitter.io

import com.twitter.io.SeqReader._
import com.twitter.util.{Future, Promise}
import scala.collection.Iterable

/**
 * We want to ensure that this reader always satisfies these invariants:
 * 1. Reading from a discarded reader will always return ReaderDiscardedException
 * 2. Reading from a fully read reader will always return None
 *
 * We achieved this with a state machine where any access to the state is synchronized,
 * and by preventing changing the state when the reader is fully read or discarded.
 */
private[io] final class SeqReader[A](it: Iterable[A]) extends Reader[A] {
  import SeqReader._

  private[this] val closep = Promise[StreamTermination]()
  private[this] var value: Iterator[A] = it.iterator
  private[this] var state: State = State.Idle

  def read(): Future[Option[A]] = {
    val result = synchronized {
      state match {
        case State.Idle =>
          if(value.hasNext) {
            val n = value.next
            Future.value(Some(n))
          }
          else {
            state = State.FullyRead
            Future.None
          }
        case State.FullyRead =>
          Future.None
        case State.Discarded =>
          Future.exception(new ReaderDiscardedException)
      }
    }

    if (result.eq(Future.None))
      closep.updateIfEmpty(StreamTermination.FullyRead.Return)

    result
  }

  def discard(): Unit = {
    val discarded = synchronized {
      state match {
        case State.Idle =>
          state = State.Discarded
          true
        case _ => false
      }
    }
    if (discarded) closep.updateIfEmpty(StreamTermination.Discarded.Return)

  }

  def onClose: Future[StreamTermination] = closep
}

object SeqReader {

  /**
   * Indicates reader state when the reader is created via SeqReader
   */
  private sealed trait State
  private object State {

    /** Indicates the reader is ready to be read. */
    case object Idle extends State

    /** Indicates the reader is fully read. */
    case object FullyRead extends State

    /** Indicates the reader has been discarded. */
    case object Discarded extends State
  }
}
