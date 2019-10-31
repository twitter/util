package com.twitter.io

import com.twitter.util.Future
import java.util.NoSuchElementException

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
  def apply(buf: Buf, chunkSize: Int): Reader[Buf] = {
    if (buf.isEmpty) Reader.empty[Buf]
    else new IteratorReader[Buf](iterator(buf, chunkSize))
  }

  /**
   * Returns an iterator that iterates over the given [[Buf]] by `chunkSize`.
   */
  private[io] def iterator(buf: Buf, chunkSize: Int): Iterator[Buf] = new Iterator[Buf] {
    require(chunkSize > 0, s"Then chunkSize should be greater than 0, but received: $chunkSize")
    private[this] var remainingBuf: Buf = buf

    def hasNext: Boolean = !remainingBuf.isEmpty
    def next(): Buf = {
      if (!hasNext) throw new NoSuchElementException("next() on empty iterator")
      val nextChuck = remainingBuf.slice(0, chunkSize)
      remainingBuf = remainingBuf.slice(chunkSize, Int.MaxValue)
      nextChuck
    }
  }

  /**
   * Read the entire bytestream presented by `r`.
   */
  def readAll(r: Reader[Buf]): Future[Buf] = {
    def loop(left: Buf): Future[Buf] =
      r.read().flatMap {
        case Some(right) => loop(left.concat(right))
        case _ => Future.value(left)
      }

    loop(Buf.Empty)
  }
}
