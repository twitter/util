package com.twitter.io

import com.twitter.util.{Future, Promise, Closable, Time}
import java.io.{File, FileInputStream, FileNotFoundException, InputStream}

/**
 * A Reader represents a stream of bytes, read in discrete chunks.
 * Readers permit at most one outstanding read.
 */
trait Reader {
  /**
   * Asynchronously read at most `n` bytes from the byte stream. The
   * returned future represents the results of the read request. If
   * the read fails, the Reader is considered failed -- future reads
   * will also fail.
   *
   * A result of None indicates EOF.
   */
  def read(n: Int): Future[Option[Buf]]

  /**
   * Discard this reader: its output is no longer required.
   */
  def discard()
}

object Reader {

  val Null = new Reader {
    def read(n: Int) = Future.None
    def discard() = ()
  }

  /**
   * Read the entire bytestream presented by `r`.
   */
  def readAll(r: Reader): Future[Buf] = {
    def loop(left: Buf): Future[Buf] =
      r.read(Int.MaxValue) flatMap {
        case Some(right) => loop(left concat right)
        case none => Future.value(left)
      }

    loop(Buf.Empty)
  }

  class ReaderDiscarded 
    extends Exception("This writer's reader has been discarded")

  private sealed trait State
  private object Idle extends State
  private case class Reading(n: Int, p: Promise[Option[Buf]]) extends State
  private case class Writing(buf: Buf, p: Promise[Unit]) extends State
  private case class Failing(exc: Throwable) extends State
  private object Eof extends State

  /**
   * Create a Reader which is also a Writer. The reader is complete
   * (i.e. end-of-stream) when it is closed; it may only be closed
   * while there are no pending writes.
   */
  def writable(): Reader with Writer with Closable = new Reader with Writer with Closable {
    private[this] var state: State = Idle
    
    def close(deadline: Time): Future[Unit] = synchronized {
      state match {
        case Failing(_) | Eof =>
        case Idle =>
          state = Eof
        case Reading(_, p) =>
          state = Eof
          p.setValue(None)
        case Writing(_, p) =>
          state = Eof
          p.setValue(new IllegalStateException("close while write is pending"))
      }

      Future.Done
    }

    def write(buf: Buf): Future[Unit] = synchronized {
      state match {
        case Failing(exc) =>
          Future.exception(exc)
          
        case Eof =>
          Future.exception(new IllegalStateException("write after close"))

        case Idle =>
          val p = new Promise[Unit]
          state = Writing(buf, p)
          p

        case Reading(n, p) if n < buf.length =>
          val nextp = new Promise[Unit]
          state = Writing(buf.slice(n, buf.length), nextp)
          p.setValue(Some(buf.slice(0, n)))
          nextp

        case Reading(n, p) =>
          state = Idle
          p.setValue(Some(buf))
          Future.Done

        case Writing(_, _) =>
          Future.exception(new IllegalStateException("write while Writing"))
      }
    }

    def read(n: Int): Future[Option[Buf]] = synchronized {
      state match {
        case Failing(exc) =>
          Future.exception(exc)

        case Eof =>
          Future.None

        case Idle =>
          val p = new Promise[Option[Buf]]
          state = Reading(n, p)
          p

        case Writing(buf, p) if buf.length <= n =>
          state = Idle
          p.setDone()
          Future.value(Some(buf))

        case Writing(buf, p) =>
          state = Writing(buf.slice(n, buf.length), p)
          Future.value(Some(buf.slice(0, n)))

        case Reading(_, _) =>
          Future.exception(new IllegalStateException("read() while Reading"))
      }
    }

    def discard() = fail(new ReaderDiscarded)

    def fail(cause: Throwable): Unit = synchronized {
      state match {
        case Idle | Eof | Failing(_) =>
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
