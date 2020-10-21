package com.twitter.io

import com.twitter.concurrent.AsyncMutex
import com.twitter.util._
import java.io.InputStream

/**
 * Provides the [[Reader]] API for an `InputStream`.
 *
 * The given `InputStream` will be closed when [[Reader.read]]
 * reaches the EOF or a call to [[discard]] or `close`.
 */
class InputStreamReader private[io] (inputStream: InputStream, chunkSize: Int, pool: FuturePool)
    extends Reader[Buf]
    with Closable
    with CloseAwaitably {
  private[this] val mutex = new AsyncMutex()
  @volatile private[this] var discarded = false
  private[this] val closep = Promise[StreamTermination]()

  /**
   * Constructs an [[InputStreamReader]] out of a given `inputStream`. The resulting [[Reader]]
   * emits chunks of at most `chunkSize`.
   */
  def this(inputStream: InputStream, chunkSize: Int) =
    this(inputStream, chunkSize, FuturePool.interruptibleUnboundedPool)

  /**
   * Asynchronously read at most min(`n`, `maxBufferSize`) bytes from
   * the `InputStream`. The returned [[com.twitter.util.Future]] represents the results of
   * the read operation.  Any failure indicates an error; an empty buffer
   * indicates that the stream has completed.
   *
   * @note the underlying `InputStream` is closed on read of EOF.
   */
  def read(): Future[Option[Buf]] = {
    if (discarded)
      return Future.exception(new ReaderDiscardedException())

    mutex.acquire().flatMap { permit =>
      pool {
        try {
          if (discarded)
            throw new ReaderDiscardedException()
          val buffer = new Array[Byte](chunkSize)
          val c = inputStream.read(buffer, 0, chunkSize)
          if (c == -1) {
            pool { inputStream.close() }
            closep.updateIfEmpty(StreamTermination.FullyRead.Return)
            None
          } else {
            Some(Buf.ByteArray.Owned(buffer, 0, c))
          }
        } catch {
          case exc: InterruptedException =>
            // we use updateIfEmpty because this is potentially racy, if someone
            // called close and then we were interrupted.
            if (closep.updateIfEmpty(Throw(exc))) {
              discard()
            }
            throw exc
        }
      }.ensure {
        permit.release()
      }
    }
  }

  /**
   * Discard this reader: its output is no longer required.
   *
   * This asynchronously closes the underlying `InputStream`.
   */
  def discard(): Unit = close()

  /**
   * Discards this [[Reader]] and closes the underlying `InputStream`
   */
  def close(deadline: Time): Future[Unit] = closeAwaitably {
    discarded = true
    pool { inputStream.close() }.ensure {
      closep.updateIfEmpty(StreamTermination.Discarded.Return)
    }
  }

  def onClose: Future[StreamTermination] = closep
}

object InputStreamReader {
  val DefaultMaxBufferSize: Int = 4096

  /**
   * Create an [[InputStreamReader]] from the given `InputStream`
   * using [[com.twitter.util.FuturePool.interruptibleUnboundedPool]]
   * for executing all I/O.
   */
  def apply(inputStream: InputStream, chunkSize: Int = DefaultMaxBufferSize): InputStreamReader =
    new InputStreamReader(inputStream, chunkSize)

  /**
   * Create an [[InputStreamReader]] from the given `InputStream`
   * using the provided [[com.twitter.util.FuturePool]] for executing all I/O. The resulting
   * [[InputStreamReader]] emits chunks of at most `DefaultMaxBufferSize`.
   */
  def apply(inputStream: InputStream, pool: FuturePool): InputStreamReader =
    new InputStreamReader(inputStream, chunkSize = DefaultMaxBufferSize, pool)

}
