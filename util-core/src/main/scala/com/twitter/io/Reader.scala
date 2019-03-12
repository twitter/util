package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.util._
import java.io.{File, FileInputStream, InputStream}
import java.util.concurrent.atomic.AtomicReference
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
trait Reader[+A] { self =>

  /**
   * Asynchronously read the next element of this stream. Returned [[Future]] will resolve into
   * `Some(e)` when the element is available or into `None` when stream is exhausted.
   *
   * Stream failures are terminal such that all subsequent reads will resolve in failed [[Future]]s.
   */
  def read(): Future[Option[A]]

  /**
   * Discard this stream as its output is no longer required. This could be used to signal the
   * producer of this stream similarly how [[Future.raise]] used to propagate interrupts across
   * future chains.
   *
   * @note Although unnecessary, it's always safe to discard a fully-consumed stream.
   */
  def discard(): Unit

  /**
   * A [[Future]] that resolves once this reader is closed upon reading of end-of-stream.
   *
   * If the result is a failed future, this indicates that it was not closed either by reading
   * until the end of the stream nor by discarding. This is useful for any extra resource cleanup
   * that you must do once the stream is no longer being used.
   */
  def onClose: Future[StreamTermination]

  /**
   * Construct a new Reader by applying `f` to every item read from this Reader
   * @param f the function constructs a new Reader[B] from the value of this Reader.read
   */
  final def flatMap[B](f: A => Reader[B]): Reader[B] = Reader.flatten(map(f))

  /**
   * Construct a new Reader by applying `f` to every item read from this Reader
   * @param f the function transforms data of type A to B
   */
  final def map[B](f: A => B): Reader[B] = new Reader[B] {
    def read(): Future[Option[B]] = self.read().map(oa => oa.map(f))
    def discard(): Unit = self.discard()
    def onClose: Future[StreamTermination] = self.onClose
  }

  /**
   * Converts a `Reader[Reader[B]]` into a `Reader[B]`
   */
  def flatten[B](implicit ev: A <:< Reader[B]): Reader[B] =
    Reader.flatten(this.asInstanceOf[Reader[Reader[B]]])
}

/**
 * Abstract `Reader` class for Java compatibility.
 */
abstract class AbstractReader[+A] extends Reader[A]

/**
 * Indicates that a given stream was discarded by the Reader's consumer.
 */
class ReaderDiscardedException extends Exception("Reader's consumer has discarded the stream")

object Reader {

  def empty[A]: Reader[A] = new Reader[A] {
    private[this] val closep = Promise[StreamTermination]()
    def read(): Future[Option[Nothing]] = {
      closep.updateIfEmpty(StreamTermination.FullyRead.Return)
      Future.None
    }
    def discard(): Unit = {
      closep.updateIfEmpty(StreamTermination.Discarded.Return)
      ()
    }
    def onClose: Future[StreamTermination] = closep
  }

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
          read()
        case None =>
          Future.None
      }
    }

    def read(): Future[Option[Buf]] = synchronized {
      frames match {
        case nextFrame :: rst =>
          frames = rst
          Future.value(Some(nextFrame))
        case _ =>
          // flatMap to `this` to prevent allocating
          r.read().flatMap(this)
      }
    }

    def discard(): Unit = synchronized {
      frames = Seq.empty
      r.discard()
    }

    def onClose: Future[StreamTermination] = r.onClose
  }

  /**
   * Construct a `Reader` from a `Future`
   *
   * We want to ensure that this reader always satisfies these invariants:
   * 1. Satisfied closep is always aligned with the state
   * 2. Reading from a discarded reader will always return ReaderDiscardedException
   * 3. Reading from a fully read reader will always return None
   * 4. Reading from an exceptioned reader will always return the exception it has thrown
   *
   * We achieved this with a state machine where any access to the state is synchronized,
   * and by ensuring that we always verify the update of state succeeded before setting closep,
   * and by preventing changing the state when the reader is exceptioned, fully read, or discarded
   *
   * @note Multiple outstanding reads are not allowed on this reader
   *
   */
  def fromFuture[A](fa: Future[A]): Reader[A] = new Reader[A] {

    private[this] val closep = Promise[StreamTermination]()
    private[this] val state = new AtomicReference[State](State.Idle)

    final def read(): Future[Option[A]] = {
      state.get() match {
        case State.Idle =>
          fa.map(Some.apply).respond {
            case t: Throw[_] =>
              if (state.compareAndSet(State.Idle, State.Exception)) {
                closep.update(t.cast[StreamTermination])
              }
            case Return(_) =>
              state.compareAndSet(State.Idle, State.Read)
          }
        case State.Read =>
          if (state.compareAndSet(State.Read, State.FullyRead)) {
            closep.update(StreamTermination.FullyRead.Return)
          }
          closep.flatMap {
            case StreamTermination.FullyRead => Future.None
            case StreamTermination.Discarded => Future.exception(new ReaderDiscardedException)
          }
        case State.Exception =>
          /** closep is guaranteed to be an exception, flatMap should never be triggered but return the exception */
          closep.flatMap(_ => Future.None)
        case State.FullyRead =>
          Future.None
        case State.Discard =>
          Future.exception(new ReaderDiscardedException)
      }
    }

    final def discard(): Unit = {
      if (state.compareAndSet(State.Idle, State.Discard) || state
          .compareAndSet(State.Read, State.Discard)) {
        closep.update(StreamTermination.Discarded.Return)
        fa.raise(new ReaderDiscardedException)
      }
    }

    final def onClose: Future[StreamTermination] = closep
  }

  /**
   * Construct a `Reader` from a value `a`
   * @note Multiple outstanding reads are not allowed on this reader
   */
  def value[A](a: A): Reader[A] = fromFuture[A](Future.value(a))

  /**
   * Construct a `Reader` from an exception `e`
   */
  def exception[A](e: Throwable): Reader[A] = fromFuture[A](Future.exception(e))

  /**
   * Read the entire bytestream presented by `r`.
   */
  def readAll(r: Reader[Buf]): Future[Buf] = {
    def loop(left: Buf): Future[Buf] =
      r.read().flatMap {
        case Some(right) => loop(left concat right)
        case _ => Future.value(left)
      }

    loop(Buf.Empty)
  }

  /**
   * Chunk the output of a given [[Reader]] by at most `chunkSize` (bytes). This consumes the
   * reader.
   */
  def chunked(r: Reader[Buf], chunkSize: Int): Reader[Buf] =
    new Framed(r, new ChunkedFramer(chunkSize))

  /**
   * Create a new [[Reader]] from a given [[Buf]]. The output of a returned reader is chunked by
   * a least `chunkSize` (bytes).
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
   * @see `Readers.fromFile` for a Java API
   */
  def fromFile(f: File, chunkSize: Int): Reader[Buf] = fromStream(new FileInputStream(f), chunkSize)

  /**
   * Create a new [[Reader]] from a given [[InputStream]]. The output of a returned reader is
   * chunked by at most `chunkSize` (bytes).
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
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
   * @see `Readers.fromStream` for a Java API
   */
  def fromStream(s: InputStream, chunkSize: Int): Reader[Buf] = InputStreamReader(s, chunkSize)

  /**
   * Create a new [[Reader]] from a given [[Seq]].
   *
   * The resources held by the returned [[Reader]] are released on reading of EOF and
   * [[Reader.discard()]].
   */
  def fromSeq[A](seq: Seq[A]): Reader[A] = new Reader[A] { self =>
    private[this] val closep = Promise[StreamTermination]()
    private[this] var state: Try[Seq[A]] = Return(seq)

    def read(): Future[Option[A]] = {
      val result: Future[Option[A]] = self.synchronized {
        state match {
          case Return(Nil) => Future.None
          case Return(head +: tail) =>
            state = Return(tail)
            Future.value(Some(head))
          case t: Throw[_] =>
            Future.const(t.cast[Option[A]])
        }
      }
      if (result eq Future.None) {
        closep.updateIfEmpty(StreamTermination.FullyRead.Return)
      }
      result
    }

    def discard(): Unit = {
      self.synchronized {
        state = Throw(new ReaderDiscardedException)
      }
      closep.updateIfEmpty(StreamTermination.Discarded.Return)
    }

    def onClose: Future[StreamTermination] = closep
  }

  /**
   * Allow [[AsyncStream]] to be consumed as a [[Reader]]
   */
  def fromAsyncStream[A](as: AsyncStream[A]): Reader[A] = {
    val pipe = new Pipe[A]
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
  def toAsyncStream[A](r: Reader[A]): AsyncStream[A] =
    AsyncStream.fromFuture(r.read()).flatMap {
      case Some(buf) => buf +:: Reader.toAsyncStream(r)
      case None => AsyncStream.empty[A]
    }

  /**
   * Convenient abstraction to read from a stream (AsyncStream) of Readers as if
   * it were a single Reader.
   * @param readers An AsyncStream holds a stream of Reader[A]
   */
  def concat[A](readers: AsyncStream[Reader[A]]): Reader[A] = {
    val target = new Pipe[A]
    val copied = copyMany(readers, target).respond {
      case Throw(exc) => target.fail(exc)
      case _ => target.close()
    }

    target.onClose.respond {
      case Return(StreamTermination.Discarded) =>
        // We have to do this so that when the the target is discarded we can
        // interrupt the read operation. Consider the following:
        //
        //     r.read(..) { case Some(b) => target.write(b) }
        //
        // The computation r.read(..) will be interrupted because we set an
        // interrupt handler in Reader.copy to discard `r`.
        copied.raise(new ReaderDiscardedException())
      case _ => ()
    }

    target
  }

  /**
   * Convenient abstraction to read from a stream (Reader) of Readers as if
   * it were a single Reader. The subsequent readers are unmanaged, the caller is
   * responsible for discarding those when abandoned.
   * @param readers A Reader holds a stream of Reader[A]
   */
  def flatten[A](readers: Reader[Reader[A]]): Reader[A] = new Reader[A] { self =>

    private[this] val closep = Promise[StreamTermination]()
    // access this currentReader is synchronized on `self`
    private[this] var currentReader: Reader[A] = Reader.empty

    readers.onClose.respond(closep.updateIfEmpty)

    private def updateCurrentAndRead(): Future[Option[A]] =
      readers.read().flatMap {
        case Some(reader) =>
          self.synchronized { currentReader = reader }
          read()
        case None =>
          Future.None
      }

    def read(): Future[Option[A]] = self.synchronized {
      currentReader.read().transform {
        case Return(None) => updateCurrentAndRead()
        case Return(sa) => Future.value(sa)
        case t @ Throw(_) =>
          // update `closep` with the exception thrown before discarding readers,
          // because discard will update `closep` to a `Discarded` StreamTermination
          // and return a `ReaderDiscardedException` during reading.
          closep.updateIfEmpty(t.cast[StreamTermination])
          readers.discard()
          Future.const(t)
      }
    }

    def discard(): Unit = {
      self.synchronized(currentReader).discard()
      readers.discard()
    }

    def onClose: Future[StreamTermination] = closep
  }

  /**
   * Copy elements from many Readers to a Writer. Readers will be discarded if
   * `copy` is cancelled (discarding the target). The Writer is unmanaged, the
   * caller is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copyMany(readers, writer) ensure writer.close()
   * }}}
   * @param readers An AsyncStream holds a stream of Reader[A]
   */
  def copyMany[A](readers: AsyncStream[Reader[A]], target: Writer[A]): Future[Unit] =
    readers.foreachF(Reader.copy(_, target))

  /**
   * Copy elements from a Reader to a Writer. The Reader will be discarded if
   * `copy` is cancelled (discarding the writer). The Writer is unmanaged, the caller
   * is responsible for finalization and error handling, e.g.:
   *
   * {{{
   * Reader.copy(r, w, n) ensure w.close()
   * }}}
   */
  def copy[A](r: Reader[A], w: Writer[A]): Future[Unit] = {
    def loop(): Future[Unit] =
      r.read().flatMap {
        case None => Future.Done
        case Some(elem) => w.write(elem) before loop()
      }

    w.onClose.respond {
      case Return(StreamTermination.Discarded) => r.discard()
      case _ => ()
    }
    val p = new Promise[Unit]
    // We have to do this because discarding the writer doesn't interrupt read
    // operations, it only fails the next write operation.
    loop().proxyTo(p)
    p.setInterruptHandler { case _ => r.discard() }
    p
  }

  /**
   * Wraps a [[ Reader[Buf] ]] and emits frames as decided by `framer`.
   *
   * @note The returned `Reader` may not be thread safe depending on the behavior
   *       of the framer.
   */
  def framed(r: Reader[Buf], framer: Buf => Seq[Buf]): Reader[Buf] = new Framed(r, framer)

  private sealed trait State

  private object State {

    /** Indicates no actions are taken on the Reader. */
    case object Idle extends State

    /** Indicates the reader has been read once. */
    case object Read extends State

    /** Indicates an exception occurred during reading */
    case object Exception extends State

    /** Indicates the reader is fully read. */
    case object FullyRead extends State

    /** Indicates the reader has been discarded. */
    case object Discard extends State
  }

}
