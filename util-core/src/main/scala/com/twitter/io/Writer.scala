package com.twitter.io

import com.twitter.util.{Closable, Future, Time}
import java.io.OutputStream

/**
 * A Writer represents a sink for a stream of `A`s, providing a convenient interface
 * for the producer of such streams.
 */
trait Writer[-A] extends Closable {

  /**
   * Write a chunk. The returned future is completed when the chunk has been
   * fully read by the sink.
   *
   * Only one outstanding `write` is permitted, thus backpressure is asserted.
   */
  def write(buf: A): Future[Unit]

  /**
   * Indicate that the producer of the bytestream has failed. No further writes are allowed.
   */
  def fail(cause: Throwable): Unit
}

/**
 * @see Writers for Java friendly APIs.
 */
object Writer {

  /**
   * A [[Writer]] instance that will always fail. Useful for situations
   * where writing to the [[Writer]] is nonsensical such as the [[Writer]] on the
   * `Response` returned by the Finagle HTTP client.
   */
  val FailingWriter: Writer[Buf] = fail[Buf]

  def fail[A]: Writer[A] = new Writer[A] {
    def write(buf: A): Future[Unit] = Future.exception(new IllegalStateException("NullWriter"))
    def fail(cause: Throwable): Unit = ()
    def close(deadline: Time): Future[Unit] = Future.Done
  }

  val BufferSize: Int = 4096

  /**
   * Construct a [[Writer]] from a given OutputStream.
   *
   * This [[Writer]] is not thread safe. If multiple threads attempt to `write`, the
   * behavior is identical to multiple threads calling `write` on the underlying
   * OutputStream.
   *
   * @param bufsize Size of the copy buffer between Writer and OutputStream.
   */
  def fromOutputStream(out: OutputStream, bufsize: Int): Writer[Buf] =
    new OutputStreamWriter(out, bufsize)

  /**
   * Construct a [[Writer]] from a given OutputStream.
   *
   * This [[Writer]] is not thread safe. If multiple threads attempt to `write`, the
   * behavior is identical to multiple threads calling `write` on the underlying
   * OutputStream.
   */
  def fromOutputStream(out: OutputStream): Writer[Buf] =
    fromOutputStream(out, BufferSize)
}
