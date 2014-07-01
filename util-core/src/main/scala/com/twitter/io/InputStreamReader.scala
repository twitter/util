package com.twitter.io

import java.io.InputStream

import com.twitter.concurrent.AsyncMutex
import com.twitter.util.{Closable, CloseAwaitably, Future, FuturePool, Time}

/**
 * Provides the Reader API for an InputStream
 */
class InputStreamReader(inputStream: InputStream, maxBufferSize: Int)
    extends Reader with Closable with CloseAwaitably {
  private[this] val mutex = new AsyncMutex()
  @volatile private[this] var discarded = false

  /**
   * Asynchronously read at most min(`n`, `maxBufferSize`) bytes from
   * the InputStream. The returned future represents the results of
   * the read operation.  Any failure indicates an error; a buffer
   * value of [[com.twitter.io.Buf.Eof]] indicates that the stream has
   * completed.
   */
  def read(n: Int): Future[Buf] = {
    if (discarded)
      return Future.exception(new Reader.ReaderDiscarded())
    if (n == 0)
      return Future.value(Buf.Empty)

    mutex.acquire() flatMap { permit =>
      FuturePool.interruptibleUnboundedPool {
        try {
          if (discarded)
            throw new Reader.ReaderDiscarded()
          val size = n min maxBufferSize
          val buffer = new Array[Byte](size)
          val c = inputStream.read(buffer, 0, size)
          if (c == -1)
            Buf.Eof
          else
            Buf.ByteArray(buffer, 0, c)
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
    FuturePool.unboundedPool { inputStream.close() }
  }
}

object InputStreamReader {
  val DefaultMaxBufferSize = 4096
  def apply(inputStream: InputStream, maxBufferSize: Int = DefaultMaxBufferSize) =
    new InputStreamReader(inputStream, maxBufferSize)
}
