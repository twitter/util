package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.util._
import java.io.{
  File, FileInputStream, FileNotFoundException, InputStream, OutputStream}

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

  /**
   * Reader from a Buf.
   */
  def fromBuf(buf: Buf): Reader = BufReader(buf)

  class ReaderDiscarded
    extends Exception("This writer's reader has been discarded")

  /**
   * A [[Reader]] that is linked with a [[Writer]] and `close`-ing
   * is synchronous.
   *
   * Just as with [[Reader readers]] and [[Writer writers]],
   * only one outstanding `read` or `write` is permitted.
   *
   * For a proper `close`, it should only be done when
   * no writes are outstanding:
   * {{{
   *   val rw = Reader.writable()
   *   ...
   *   rw.write(buf).before(rw.close())
   * }}}
   *
   * If a producer is interested in knowing when all writes
   * have been read and the reader has seen the EOF, it can
   * wait until the future returned by `close()` is satisfied:
   * {{{
   *   val rw = Reader.writable()
   *   ...
   *   rw.close().ensure {
   *     println("party on! ♪┏(・o･)┛♪ the Reader has seen the EOF")
   *   }
   * }}}
   */
  trait Writable extends Reader with Writer with Closable

  private sealed trait State

  /** Indicates no reads or writes are pending, and is not closed. */
  private object Idle extends State

  /**
   * Indicates a read is pending and is awaiting a `write`.
   *
   * @param n number of bytes to read.
   * @param p when satisfied it indicates that this read has completed.
   */
  private case class Reading(n: Int, p: Promise[Option[Buf]]) extends State

  /**
   * Indicates a write of `buf` is pending to be `read`.
   *
   * @param buf the [[Buf]] to write.
   * @param p when satisfied it indicates that this write has been fully read.
   */
  private case class Writing(buf: Buf, p: Promise[Unit]) extends State

  /** Indicates this was `fail`-ed or `discard`-ed. */
  private case class Failing(exc: Throwable) extends State

  /**
   * Indicates a close occurred while `Idle` — no reads or writes were pending.
   *
   * @param reof satified when a `read` sees the EOF.
   */
  private case class Closing(reof: Promise[Unit]) extends State

  /** Indicates the reader has seen the EOF. No more reads or writes are allowed. */
  private case object Eof extends State

  /**
   * Create a new [[Writable]] which is a [[Reader]] that is linked
   * with a [[Writer]].
   *
   * @see Readers.writable() for a Java API.
   */
  def writable(): Writable = new Writable {
    // thread-safety provided by synchronization on `this`
    private[this] var state: State = Idle

    override def toString: String = synchronized {
      s"Reader.writable(state=$state)"
    }

    /**
     * The returned [[com.twitter.util.Future]] is satisfied when this has either been
     * [[discard discarded]], a [[read]] has seen the EOF, or a [[read]]
     * has seen the [[fail failure]].
     */
    def close(deadline: Time): Future[Unit] = synchronized {
      state match {
        case Failing(t) =>
          Future.exception(t)

        case Eof =>
          Future.Done

        case Closing(p) =>
          p

        case Idle =>
          val reof = new Promise[Unit]()
          state = Closing(reof)
          reof

        case Reading(_, p) =>
          state = Eof
          p.update(Return.None)
          Future.Done

        case Writing(_, p) =>
          val reof = new Promise[Unit]()
          state = Closing(reof)
          p.setException(new IllegalStateException("close while write is pending"))
          reof

      }
    }

    def write(buf: Buf): Future[Unit] = synchronized {
      state match {
        case Failing(exc) =>
          Future.exception(exc)

        case Eof | Closing(_) =>
          Future.exception(new IllegalStateException("write after close"))

        case Idle =>
          val p = new Promise[Unit]()
          state = Writing(buf, p)
          p

        case Reading(n, p) if n < buf.length =>
          // pending reader doesn't have enough space for this write
          val nextp = new Promise[Unit]()
          state = Writing(buf.slice(n, buf.length), nextp)
          p.setValue(Some(buf.slice(0, n)))
          nextp

        case Reading(n, p) =>
          // pending reader has enough space for the full write
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

        case Closing(reof) =>
          state = Eof
          reof.setDone()
          Future.None

        case Eof =>
          Future.None

        case Idle =>
          val p = new Promise[Option[Buf]]
          state = Reading(n, p)
          p

        case Writing(buf, p) if buf.length <= n =>
          // pending write can fully fit into this requested read
          state = Idle
          p.setDone()
          Future.value(Some(buf))

        case Writing(buf, p) =>
          // pending write is larger than the requested read
          state = Writing(buf.slice(n, buf.length), p)
          Future.value(Some(buf.slice(0, n)))

        case Reading(_, _) =>
          Future.exception(new IllegalStateException("read() while Reading"))
      }
    }

    def discard(): Unit = synchronized {
      val cause = new ReaderDiscarded()
      fail(cause)
    }

    def fail(cause: Throwable): Unit = synchronized {
      // We fix the state before inspecting it so we enter in a
      // good state if setting promises recurses.
      val oldState = state
      oldState match {
        case Eof | Failing(_) =>
          // do not update state to failing
        case Idle =>
          state = Failing(cause)
        case Closing(reof) =>
          state = Failing(cause)
          reof.setException(cause)
        case Reading(_, p) =>
          state = Failing(cause)
          p.setException(cause)
        case Writing(_, p) =>
          state = Failing(cause)
          p.setException(cause)
      }
    }
  }

  /**
   * Create a new Reader for a File
   *
   * @see Readers.fromFile for a Java API
   */
  @throws(classOf[FileNotFoundException])
  @throws(classOf[SecurityException])
  def fromFile(f: File): Reader = fromStream(new FileInputStream(f))

  /**
   * Wrap InputStream s in with a Reader
   *
   * @see Readers.fromStream for a Java API
   */
  def fromStream(s: InputStream): Reader = InputStreamReader(s)

  /**
   * Convenient abstraction to read from a stream of Readers as if it were a
   * single Reader.
   */
  def concat(readers: AsyncStream[Reader]): Reader = {
    val target = Reader.writable()
    val f = copyMany(readers, target) respond {
      case Throw(exc) => target.fail(exc)
      case _ => target.close()
    }
    new Reader {
      def read(n: Int) = target.read(n)
      def discard() {
        // We have to do this so that when the the target is discarded we can
        // interrupt the read operation. Consider the following:
        //
        //     r.read(..) { case Some(b) => target.write(b) }
        //
        // The computation r.read(..) will be interupted because we set an
        // interrupt handler in Reader.copy to discard `r`.
        f.raise(new Reader.ReaderDiscarded())
        target.discard()
      }
    }
  }

  /**
   * Copy bytes from many Readers to a Writer. The Writer is unmanaged, the
   * caller is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copyMany(readers, writer) ensure writer.close()
   * }}}
   *
   * @param bufsize The number of bytes to read each time.
   */
  def copyMany(readers: AsyncStream[Reader], target: Writer, bufsize: Int): Future[Unit] =
    readers.foreachF(Reader.copy(_, target, bufsize))

  /**
   * Copy bytes from many Readers to a Writer. The Writer is unmanaged, the
   * caller is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copyMany(readers, writer) ensure writer.close()
   * }}}
   */
  def copyMany(readers: AsyncStream[Reader], target: Writer): Future[Unit] =
    copyMany(readers, target, Writer.BufferSize)

  /**
   * Copy the bytes from a Reader to a Writer in chunks of size `n`. The Writer
   * is unmanaged, the caller is responsible for finalization and error
   * handling, e.g.:
   *
   * {{{
   * Reader.copy(r, w, n) ensure w.close()
   * }}}
   *
   * @param n The number of bytes to read on each refill of the Writer.
   */
  def copy(r: Reader, w: Writer, n: Int): Future[Unit] = {
    def loop(): Future[Unit] =
      r.read(n) flatMap {
        case None => Future.Done
        case Some(buf) => w.write(buf) before loop()
      }
    val p = new Promise[Unit]
    // We have to do this because discarding the writer doesn't interrupt read
    // operations, it only fails the next write operation.
    loop() proxyTo p
    p setInterruptHandler { case exc => r.discard() }
    p
  }

  /**
   * Copy the bytes from a Reader to a Writer in chunks of size
   * `Writer.BufferSize`. The Writer is unmanaged, the caller is responsible
   * for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copy(r, w) ensure w.close()
   * }}}
   */
  def copy(r: Reader, w: Writer): Future[Unit] = copy(r, w, Writer.BufferSize)
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
    * Only one outstanding `write` is permitted, thus
    * backpressure is asserted.
    */
  def write(buf: Buf): Future[Unit]

  /**
   * Indicate that the producer of the bytestream has
   * failed. No further writes are allowed.
   */
  def fail(cause: Throwable)
}

/**
 * @see Writers for Java friendly APIs.
 */
object Writer {

  /**
   * Represents a [[Writer]] which is [[Closable]].
   *
   * Exists primarily for Java compatibility.
   */
  trait ClosableWriter extends Writer with Closable

  val BufferSize = 4096

  /**
   * Construct a [[ClosableWriter]] from a given OutputStream.
   *
   * This [[Writer]] is not thread safe. If multiple threads attempt to `write`, the
   * behavior is identical to multiple threads calling `write` on the underlying
   * OutputStream.
   *
   * @param bufsize Size of the copy buffer between Writer and OutputStream.
   */
  def fromOutputStream(out: OutputStream, bufsize: Int): ClosableWriter =
    new OutputStreamWriter(out, bufsize)

  /**
   * Construct a [[ClosableWriter]] from a given OutputStream.
   *
   * This [[Writer]] is not thread safe. If multiple threads attempt to `write`, the
   * behavior is identical to multiple threads calling `write` on the underlying
   * OutputStream.
   */
  def fromOutputStream(out: OutputStream): ClosableWriter =
    fromOutputStream(out, BufferSize)
}
