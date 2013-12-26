package com.twitter.io

import com.twitter.util.{Future, Promise}
import java.io.{File, FileInputStream, FileNotFoundException, InputStream}

/**
 * A Reader represents a stream of bytes, read in chunks. Readers
 * permit only one outstanding read at a time.
 */
trait Reader {
  /**
   * Asynchronously read at most `n` bytes from the byte stream. The
   * returned future represents the results of the read operation.
   * Any failure indicates an error; a buffer value of
   * [[com.twitter.io.Buf.Eof]] indicates that the stream has
   * completed.
   */
  def read(n: Int): Future[Buf]

  /**
   * Discard this reader: its output is no longer required.
   */
  def discard()
}

object Reader {
  /**
   * Read the entire bytestream presented by `r`.
   */
  def readAll(r: Reader): Future[Buf] = {
    def loop(buf: Buf): Future[Buf] =
      r.read(Int.MaxValue) flatMap {
        case Buf.Eof => Future.value(buf)
        case next => loop(buf concat next)
      }

    loop(Buf.Empty)
  }

  class ReaderDiscarded 
    extends Exception("This writer's reader has been discarded")

  private sealed trait State
  private object Idle extends State
  private case class Reading(n: Int, p: Promise[Buf]) extends State
  private case class Writing(buf: Buf, p: Promise[Unit]) extends State
  private case class Failing(exc: Throwable) extends State

  /**
   * Create a reader which is also a writer.
   */
  def writable(): Reader with Writer = new Reader with Writer {
    private[this] var state: State = Idle

    def write(buf: Buf): Future[Unit] = synchronized {
      state match {
        case Failing(exc) =>
          Future.exception(exc)

        case Idle =>
          val p = new Promise[Unit]
          state = Writing(buf, p)
          p

        case Reading(n, p) if n < buf.length =>
          val nextp = new Promise[Unit]
          state = Writing(buf.slice(n, buf.length), nextp)
          p.setValue(buf.slice(0, n))
          nextp

        case Reading(n, p) =>
          state = Idle
          p.setValue(buf)
          Future.Done

        case Writing(_, _) =>
          Future.exception(new IllegalStateException("write while Writing"))
      }
    }

    def read(n: Int): Future[Buf] = synchronized {
      state match {
        case Failing(exc) =>
          Future.exception(exc)

        case Idle =>
          val p = new Promise[Buf]
          state = Reading(n, p)
          p

        case Writing(buf, p) if buf.length <= n =>
          state = Idle
          p.setDone()
          Future.value(buf)

        case Writing(buf, p) =>
          state = Writing(buf.slice(n, buf.length), p)
          Future.value(buf.slice(0, n))

        case Reading(_, _) =>
          Future.exception(new IllegalStateException("read() while Reading"))
      }
    }

    def discard() = fail(new ReaderDiscarded)

    def fail(cause: Throwable): Unit = synchronized {
      state match {
        case Idle | Failing(_) =>
        case Reading(_, p) =>
          p.setException(cause)
        case Writing(_, p) =>
          p.setException(cause)
      }
      state = Failing(cause)
    }
  }

  /**
   * Create a new Reader for a File
   */
  @throws(classOf[FileNotFoundException])
  @throws(classOf[SecurityException])
  def fromFile(f: File): Reader = fromStream(new FileInputStream(f))

  /**
   * Wrap InputStream s in with a Reader
   */
  def fromStream(s: InputStream): Reader = InputStreamReader(s)
}

/**
 * A Writer represents a sink for a stream of bytes, providing
 * a convenient interface for the producer of such streams.
 */
trait Writer {
  /**
    * Write a chunk. The returned future is completed
    * when the chunk has been fully read by the sink.
    *
    * Only one outstanding read is permitted, thus
    * backpressure is asserted.
    */
  def write(buf: Buf): Future[Unit]
  
  /**
   * Indicate that the producer of the bytestream has
   * failed. No further writes are allowed.
   */
  def fail(cause: Throwable)
}
