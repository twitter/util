package com.twitter.io

import com.twitter.util.{Future, Return, Try, Throw, ConstFuture, Promise}

/**
 * Construct a [[Reader]] from a [[Buf]].
 */
private[io] final class BufReader(buf: Buf, chunkSize: Int) extends Reader[Buf] {
  private[this] var state: Try[Buf] = Return(buf)
  private[this] val closep = Promise[StreamTermination]()

  def read(): Future[Option[Buf]] = {
    val result: Future[Option[Buf]] = synchronized {
      state match {
        case Return(Buf.Empty) => Future.None
        case Return(b) =>
          state = Return(b.slice(chunkSize, Int.MaxValue))
          Future.value(Some(b.slice(0, chunkSize)))

        case t: Throw[_] =>
          new ConstFuture[Option[Buf]](t.cast[Option[Buf]])
      }
    }
    if (result eq Future.None) {
      closep.updateIfEmpty(StreamTermination.FullyRead.Return)
    }
    result
  }

  def discard(): Unit = {
    synchronized {
      state = Throw(new ReaderDiscardedException)
    }
    closep.updateIfEmpty(StreamTermination.Discarded.Return)
  }

  def onClose: Future[StreamTermination] = closep
}

object BufReader {

  /**
   * Creates a [[BufReader]] out of a given `buf`. The resulting [[Reader]] emits chunks of at
   * most `Int.MaxValue` bytes.
   */
  def apply(buf: Buf): Reader[Buf] =
    apply(buf, Int.MaxValue)

  /**
   * Creates a [[BufReader]] out of a given `buf`. The result [[Reader]] emits chunks of at most
   * `chunkSize`.
   */
  def apply(buf: Buf, chunkSize: Int): Reader[Buf] =
    if (buf.isEmpty) Reader.empty[Buf] else new BufReader(buf, chunkSize)
}
