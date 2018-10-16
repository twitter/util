package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.util._
import java.io.{File, FileInputStream, InputStream}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * A reader exposes a pull-based API to model a potentially infinite stream of arbitrary elements.
 *
 * Given the pull-based API, the consumer is responsible for driving the computation. A very
 * typical code pattern to consume a reader is to use a read-loop:
 *
 * {{{
 *   def consume[A](r: Reader[A]))(process: A => Future[Unit]): Future[Unit] =
 *     r.read().flatMap {
 *       case Some(a) => process(a).before(consume(r)(process))
 *       case None => Future.Done // reached the end of the stream; no need to discard
 *     }
 * }}}
 *
 * One way to reason about the read-loop idiom is to view it as a subscription to a publisher
 * (reader) in the Pub/Sub terminology. Perhaps the major difference between readers and traditional
 * publishers, is readers only allow one subscriber (read-loop). It's generally safer to assume the
 * reader is fully consumed (stream is exhausted) once its read-loop is run.
 *
 * == Error Handling ==
 *
 * Given the read-loop above, its returned [[Future]] could be used to observe both successful and
 * unsuccessful outcomes.
 *
 * {{{
 *   consume(stream)(processor).respond {
 *     case Return(()) => println("Consumed an entire stream successfully.")
 *     case Throw(e) => println(s"Encountered an error while consuming a stream: $e")
 *   }
 * }}}
 *
 * @note Once failed, a stream can not be restarted such that all future reads will resolve into a
 *       failure. There is no need to discard an already failed stream.
 *
 * == Resource Safety ==
 *
 * One of the important implications of readers, and streams in general, is that they are prone to
 * resource leaks unless fully consumed or discarded (failed). Specifically, readers backed by
 * network connections MUST be discarded unless already consumed (EOF observed) to prevent
 * connection leaks.
 *
 * @note The read-loop above, for example, exhausts the stream (observes EOF) hence does not have to
 *       discard it (stream).
 *
 * == Back Pressure ==
 *
 * The pattern above leverages [[Future]] recursion to exert back-pressure via allowing only one
 * outstanding read. It's usually a good idea to structure consumers this way (i.e., the next read
 * isn't issued until the previous read finishes). This would always ensure a finer grained
 * back-pressure in network systems allowing the consumers to artificially slow down the producers
 * and not rely solely on transport and IO buffering.
 *
 * @note Whether or not multiple outstanding reads are allowed on a `Reader` type is an undefined
 *       behaviour but could be changed in a refinement.
 *
 * == Cancellations ==
 *
 * If a consumer is no longer interested in the stream, it can discard it. Note a discarded reader
 * (or stream) can not be restarted.
 *
 * {{{
 *   def consumeN[A](r: Reader[A], n: Int)(process: A => Future[Unit]): Future[Unit] =
 *     if (n == 0) Future(r.discard())
 *     else r.read().flatMap {
 *       case Some(a) => process(a).before(consumeN(r, n - 1)(process))
 *       case None => Future.Done // reached the end of the stream; no need to discard
 *     }
 * }}}
 */
trait Reader[+A] {

  /**
   * Asynchronously read the next element of this stream. Returned [[Future]] will resolve into
   * `Some(e)` when the element is available or into `None` when stream is exhausted.
   *
   * Stream failures are terminal such that all subsequent reads will resolve in failed [[Future]]s.
   */
  def read(n: Int): Future[Option[A]]

  /**
   * Discard this stream as its output is no longer required. This could be used to signal the
   * producer of this stream similarly how [[Future.raise]] used to propagate interrupts across
   * future chains.
   *
   * @note Although unnecessary, it's always safe to discard a fully-consumed stream.
   */
  def discard(): Unit
}

object Reader {

  class ReaderDiscarded extends Exception("This writer's reader has been discarded")

  val Null: Reader[Nothing] = new Reader[Nothing] {
    def read(n: Int): Future[Option[Nothing]] = Future.None
    def discard(): Unit = ()
  }

  def empty[A]: Reader[A] = Null.asInstanceOf[Reader[A]]

  // see Reader.chunked
  private final class ChunkedFramer(chunkSize: Int) extends (Buf => Seq[Buf]) {
    require(chunkSize > 0, s"chunkSize should be > 0 but was $chunkSize")

    @tailrec
    private def loop(acc: Seq[Buf], in: Buf): Seq[Buf] = {
      if (in.length < chunkSize) acc :+ in
      else {
        loop(
          acc :+ in.slice(0, chunkSize),
          in.slice(chunkSize, in.length)
        )
      }
    }

    def apply(in: Buf): Seq[Buf] = {
      loop(ListBuffer(), in)
    }
  }

  // see Reader.framed
  private final class Framed(r: Reader[Buf], framer: Buf => Seq[Buf])
      extends Reader[Buf]
      with (Option[Buf] => Future[Option[Buf]]) {

    private[this] var frames: Seq[Buf] = Seq.empty

    // we only enter here when `frames` is empty.
    def apply(in: Option[Buf]): Future[Option[Buf]] = synchronized {
      in match {
        case Some(data) =>
          frames = framer(data)
          read(Int.MaxValue)
        case None =>
          Future.None
      }
    }

    def read(n: Int): Future[Option[Buf]] = synchronized {
      frames match {
        case nextFrame :: rst =>
          frames = rst
          Future.value(Some(nextFrame))
        case _ =>
          // flatMap to `this` to prevent allocating
          r.read(Int.MaxValue).flatMap(this)
      }
    }

    def discard(): Unit = synchronized {
      frames = Seq.empty
      r.discard()
    }
  }

  /**
   * Read the entire bytestream presented by `r`.
   */
  def readAll(r: Reader[Buf]): Future[Buf] = {
    def loop(left: Buf): Future[Buf] =
      r.read(Int.MaxValue).flatMap {
        case Some(right) => loop(left concat right)
        case _ => Future.value(left)
      }

    loop(Buf.Empty)
  }

  /**
   * Chunk the output of a given [[Reader]] by at most `chunkSize` (bytes). This consumes the
   * reader.
   *
   * @note The `n` (number of bytes to read) argument on the returned reader is ignored
   *       (`Int.MaxValue` is used instead).
   */
  def chunked(r: Reader[Buf], chunkSize: Int): Reader[Buf] =
    new Framed(r, new ChunkedFramer(chunkSize))

  /**
   * Create a new [[Reader]] from a given [[Buf]]. The output of a returned reader is chunked by
   * a least `chunkSize` (bytes).
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   */
  def fromBuf(buf: Buf): Reader[Buf] = fromBuf(buf, Int.MaxValue)

  /**
   * Create a new [[Reader]] from a given [[Buf]]. The output of a returned reader is chunked by
   * at most `chunkSize` (bytes).
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   */
  def fromBuf(buf: Buf, chunkSize: Int): Reader[Buf] = BufReader(buf, chunkSize)

  /**
   * Create a new [[Reader]] from a given [[File]]. The output of a returned reader is chunked by
   * at most `chunkSize` (bytes).
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   * @see `Readers.fromFile` for a Java API
   */
  def fromFile(f: File): Reader[Buf] = fromFile(f, InputStreamReader.DefaultMaxBufferSize)

  /**
   * Create a new [[Reader]] from a given [[File]]. The output of a returned reader is chunked by
   * at most `chunkSize` (bytes).
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   * @see `Readers.fromFile` for a Java API
   */
  def fromFile(f: File, chunkSize: Int): Reader[Buf] = fromStream(new FileInputStream(f), chunkSize)

  /**
   * Create a new [[Reader]] from a given [[InputStream]]. The output of a returned reader is
   * chunked by at most `chunkSize` (bytes).
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   * @see `Readers.fromStream` for a Java API
   */
  def fromStream(s: InputStream): Reader[Buf] =
    fromStream(s, InputStreamReader.DefaultMaxBufferSize)

  /**
   * Create a new [[Reader]] from a given [[InputStream]]. The output of a returned reader is
   * chunked by at most `chunkSize` (bytes).
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
   *
   * @note The `n` (number of bytes to read) argument on the returned reader's `read` is ignored.
   * @see `Readers.fromStream` for a Java API
   */
  def fromStream(s: InputStream, chunkSize: Int): Reader[Buf] = InputStreamReader(s, chunkSize)

  /**
   * Allow [[AsyncStream]] to be consumed as a [[Reader]]
   */
  def fromAsyncStream[A <: Buf](as: AsyncStream[A]): Reader[A] = {
    val pipe = new Pipe[A]()
    // orphan the Future but allow it to clean up
    // the Pipe IF the stream ever finishes or fails
    as.foreachF(pipe.write).respond {
      case Return(_) => pipe.close()
      case Throw(e) => pipe.fail(e)
    }
    pipe
  }

  /**
   * Transformation (or lift) from [[Reader]] into `AsyncStream`.
   */
  def toAsyncStream[A <: Buf](r: Reader[A], chunkSize: Int = Int.MaxValue): AsyncStream[A] =
    AsyncStream.fromFuture(r.read(chunkSize)).flatMap {
      case Some(buf) => buf +:: Reader.toAsyncStream(r, chunkSize)
      case None => AsyncStream.empty[A]
    }

  /**
   * Convenient abstraction to read from a stream of Readers as if it were a
   * single Reader.
   */
  def concat(readers: AsyncStream[Reader[Buf]]): Reader[Buf] = {
    val target = new Pipe[Buf]()
    val f = copyMany(readers, target).respond {
      case Throw(exc) => target.fail(exc)
      case _ => target.close()
    }
    new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = target.read(n)
      def discard(): Unit = {
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
  def copyMany(readers: AsyncStream[Reader[Buf]], target: Writer[Buf], bufsize: Int): Future[Unit] =
    readers.foreachF(Reader.copy(_, target, bufsize))

  /**
   * Copy bytes from many Readers to a Writer. The Writer is unmanaged, the
   * caller is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copyMany(readers, writer) ensure writer.close()
   * }}}
   */
  def copyMany(readers: AsyncStream[Reader[Buf]], target: Writer[Buf]): Future[Unit] =
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
  def copy(r: Reader[Buf], w: Writer[Buf], n: Int): Future[Unit] = {
    def loop(): Future[Unit] =
      r.read(n).flatMap {
        case None => Future.Done
        case Some(buf) => w.write(buf) before loop()
      }
    val p = new Promise[Unit]
    // We have to do this because discarding the writer doesn't interrupt read
    // operations, it only fails the next write operation.
    loop().proxyTo(p)
    p.setInterruptHandler { case exc => r.discard() }
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
  def copy(r: Reader[Buf], w: Writer[Buf]): Future[Unit] = copy(r, w, Writer.BufferSize)

  /**
   * Wraps a [[ Reader[Buf] ]] and emits frames as decided by `framer`.
   *
   * @note The returned `Reader` may not be thread safe depending on the behavior
   *       of the framer.
   */
  def framed(r: Reader[Buf], framer: Buf => Seq[Buf]): Reader[Buf] = new Framed(r, framer)
}
