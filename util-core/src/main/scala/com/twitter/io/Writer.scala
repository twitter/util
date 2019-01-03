package com.twitter.io

import com.twitter.util.{Closable, Future, Time}
import java.io.OutputStream

/**
 * A writer represents a sink for a stream of arbitrary elements. While [[Reader]] provides an API
 * to consume streams, [[Writer]] provides an API to produce streams.
 *
 * Similar to [[Reader]]s, a very typical way to work with [[Writer]]s is to model a stream producer
 * as a write-loop:
 *
 * {{{
 *   def produce[A](w: Writer[A])(generate: () => Option[A]): Future[Unit] =
 *     generate match {
 *       case Some(a) => w.write(a).before(produce(w)(generate))
 *       case None => w.close() // signal EOF and quit producing
 *     }
 * }}}
 *
 * == Closures and Failures ==
 *
 * Writers are [[Closable]] and the producer MUST `close` the stream when it finishes or `fail` the
 * stream when it encounters a failure and can no longer continue. Streams backed by network
 * connections are particularly prone to resource leaks when they aren't cleaned up properly.
 *
 * Observing stream failures in the producer could be done via a [[Future]] returned form
 * a write-loop:
 *
 * {{{
 *   produce(writer)(generator).respond {
 *     case Return(()) => println("Produced a stream successfully.")
 *     case Throw(e) => println(s"Could not produce a stream because of a failure: $e")
 *   }
 * }}}
 *
 * @note Encountering a stream failure would terminate the write-loop given the [[Future]]
 *       recursion semantics.
 *
 * @note Once failed or closed, a stream can not be restarted such that all future writes will
 *       resolve into a failure.
 *
 * @note Closing an already failed stream does not have an effect.
 *
 * == Back Pressure ==
 *
 * By analogy with read-loops (see [[Reader]] API), write-loops leverage [[Future]] recursion to
 * exert back-pressure: the next write isn't issued until the previous write finishes. This will
 * always ensure a finer grained back-pressure in network systems allowing the producers to
 * adjust the flow rate based on consumer's speed and not on IO buffering.
 *
 * @note Whether or not multiple pending writes are allowed on a `Writer` type is an undefined
 *       behaviour but could be changed in a refinement.
 */
trait Writer[-A] extends Closable { self =>

  /**
   * Write an `element` into this stream. Although undefined by this contract, a trustworthy
   * implementation (such as [[Pipe]]) would do its best to resolve the returned [[Future]] only
   * when a consumer observes a written `element`.
   *
   * The write can also resolve into a failure (failed [[Future]]).
   */
  def write(element: A): Future[Unit]

  /**
   * Fail this stream with a given `cause`. No further writes are allowed, but if happen, will
   * resolve into a [[Future]] failed with `cause`.
   *
   * @note Failing an already closed stream does not have an effect.
   */
  def fail(cause: Throwable): Unit

  /**
   * A [[Future]] that resolves once this writer is closed and flushed.
   *
   * It may contain an error if the writer is failed.
   *
   * This is useful for any extra resource cleanup that you must do once the stream is no longer
   * being used.
   */
  def onClose: Future[StreamTermination]

  /**
   * Given f, a function from B into A, creates an Writer[B] whose `fail` and `close` functions
   * are equivalent to Writer[A]'s. Writer[B]'s `write` function is equivalent to:
   * {{{
   * def write(element: B) = Writer[A].write(f(element))
   * }}}
   */
  final def contramap[B](f: B => A): Writer[B] = new Writer[B] {
    def write(element: B): Future[Unit] = self.write(f(element))
    def fail(cause: Throwable): Unit = self.fail(cause)
    def close(deadline: Time): Future[Unit] = self.close(deadline)
    def onClose: Future[StreamTermination] = self.onClose
  }
}

/**
 * Abstract `Writer` class for Java compatibility.
 */
abstract class AbstractWriter[-A] extends Writer[A]

/**
 * @see Writers for Java friendly APIs.
 */
object Writer {

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
