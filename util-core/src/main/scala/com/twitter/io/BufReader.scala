package com.twitter.io

import com.twitter.util.Future
import java.util.NoSuchElementException
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

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
    // Returns an iterator that iterates over the given [[Buf]] by `chunkSize`.
    def iterator(buf: Buf, chunkSize: Int): Iterator[Buf] = new Iterator[Buf] {
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

    if (buf.isEmpty) Reader.empty[Buf]
    else new IteratorReader[Buf](iterator(buf, chunkSize))
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

  // see BufReader.chunked
  private final class ChunkedFramer(chunkSize: Int) extends (Buf => Seq[Buf]) {
    require(chunkSize > 0, s"chunkSize should be > 0 but was $chunkSize")

    def apply(input: Buf): Seq[Buf] = {
      val acc = ListBuffer[Buf]()

      @tailrec
      def loop(buf: Buf): Seq[Buf] = {
        if (buf.length < chunkSize) (acc += buf).result()
        else {
          acc += buf.slice(0, chunkSize)
          loop(buf.slice(chunkSize, buf.length))
        }
      }

      loop(input)
    }
  }

  // see BufReader.framed
  private final class Framed(r: Reader[Buf], framer: Buf => Seq[Buf])
      extends Reader[Buf]
      with (Option[Buf] => Future[Option[Buf]]) {

    private[this] var frames: Seq[Buf] = Nil

    // we only enter here when `frames` is empty.
    def apply(in: Option[Buf]): Future[Option[Buf]] = synchronized {
      in match {
        case Some(data) =>
          frames = framer(data)
          read()
        case None =>
          Future.None
      }
    }

    def read(): Future[Option[Buf]] = synchronized {
      if (frames.isEmpty) {
        // flatMap to `this` to prevent allocating
        r.read().flatMap(this)
      } else {
        val nextFrame = frames.head
        frames = frames.tail
        Future.value(Some(nextFrame))
      }
    }

    def discard(): Unit = synchronized {
      frames = Seq.empty
      r.discard()
    }

    def onClose: Future[StreamTermination] = r.onClose
  }

  /**
   * Chunk the output of a given [[Reader]] by at most `chunkSize` (bytes). This consumes the
   * reader.
   */
  def chunked(r: Reader[Buf], chunkSize: Int): Reader[Buf] =
    new Framed(r, new ChunkedFramer(chunkSize))

  /**
   * Wraps a [[Reader]] and emits frames as decided by `framer`.
   *
   * @note The returned `Reader` may not be thread safe depending on the behavior
   *       of the framer.
   */
  def framed(r: Reader[Buf], framer: Buf => Seq[Buf]): Reader[Buf] = new Framed(r, framer)
}
