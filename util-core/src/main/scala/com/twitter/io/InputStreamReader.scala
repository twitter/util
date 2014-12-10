package com.twitter.io

import java.io.InputStream

import com.twitter.concurrent.AsyncMutex
import com.twitter.util.{Closable, CloseAwaitably, Future, FuturePool, Time}

/**
 * Provides the Reader API for an InputStream
 */
class InputStreamReader private[io] (inputStream: InputStream, maxBufferSize: Int, pool: FuturePool)
    extends Reader with Closable with CloseAwaitably {
  private[this] val mutex = new AsyncMutex()
  @volatile private[this] var discarded = false

  def this(inputStream: InputStream, maxBufferSize: Int) =
    this(inputStream, maxBufferSize, FuturePool.interruptibleUnboundedPool)

  /**
   * Asynchronously read at most min(`n`, `maxBufferSize`) bytes from
   * the InputStream. The returned future represents the results of
   * the read operation.  Any failure indicates an error; an empty buffer
   * indicates that the stream has completed.
   */
  def read(n: Int): Future[Option[Buf]] = {
    if (discarded)
      return Future.exception(new Reader.ReaderDiscarded())
    if (n == 0)
      return Future.value(Some(Buf.Empty))

    mutex.acquire() flatMap { permit =>
      pool {
        try {
          if (discarded)
            throw new Reader.ReaderDiscarded()
          val size = n min maxBufferSize
          val buffer = new Array[Byte](size)
          val c = inputStream.read(buffer, 0, size)
          if (c == -1)
            None
          else
            Some(Buf.ByteArray.Owned(buffer, 0, c))
        } catch { case exc: InterruptedException =>
            discarded = true
            throw exc
        }
      } ensure {
        permit.release()
      }
    }
  }

  /**
   * Discard this reader: its output is no longer required.
   */
  def discard() { discarded = true }

  /**
   * Discards this Reader and closes the underlying InputStream
   */
  def close(deadline: Time) = closeAwaitably {
    discard()
    pool { inputStream.close() }
  }
}

object InputStreamReader {
  val DefaultMaxBufferSize = 4096
  def apply(inputStream: InputStream, maxBufferSize: Int = DefaultMaxBufferSize) =
    new InputStreamReader(inputStream, maxBufferSize)
}
