package com.twitter.concurrent.exp

import com.twitter.util.{Await, Future, Return, Throw}
import scala.collection.mutable

/**
 * A cons list (a linked list like node) with an AsyncStream tail.
 */
private sealed trait LazySeq[+A]

private object LazySeq {
  case object Empty extends LazySeq[Nothing]

  // It'd be nice to make this a value class, but we run into a type erasure
  // bug: https://issues.scala-lang.org/browse/SI-6260, which seems to be fixed
  // in 2.11, so let's revisit this in the future.
  case class One[A](a: A) extends LazySeq[A]

  final class Cons[A] private (val head: A, next: => AsyncStream[A]) extends LazySeq[A] {
    lazy val tail: AsyncStream[A] = next
    override def toString = s"Cons($head, ?)"
  }

  object Cons {
    def apply[A](head: A, next: => AsyncStream[A]): Cons[A] =
      new Cons(head, next)
  }
}

/**
 * A representation of a lazy (and possibly infinite) sequence of asynchronous
 * values. We provide combinators for non-blocking computation over the sequence
 * of values.
 *
 * It is composable with Future, Seq and Option.
 *
 * {{{
 *   val ids = Seq(123, 124, ...)
 *   val users = fromSeq(ids).flatMap(id => fromFuture(getUser(id)))
 *
 *   // Or as a for-comprehension...
 *
 *   val users = for {
 *     id <- fromSeq(ids)
 *     user <- fromFuture(getUser(id))
 *   } yield user
 * }}}
 *
 * All of its operations are lazy and don't force evaluation, unless otherwise
 * noted.
 *
 * The stream is persistent and can be shared safely by multiple threads.
 */
final class AsyncStream[+A] private (private val underlying: Future[LazySeq[A]]) {
  import AsyncStream._
  import LazySeq.{Empty, One, Cons}

  /**
   * Returns true if there are no elements in the stream.
   */
  def isEmpty: Future[Boolean] = underlying.flatMap {
    case Empty => Future.True
    case _ => Future.False
  }

  /**
   * Returns the head of this stream if not empty.
   */
  def head: Future[Option[A]] = underlying.map {
    case Empty => None
    case One(a) => Some(a)
    case cons: Cons[A] => Some(cons.head)
  }

  /**
   * Note: forces the first element of the tail.
   */
  def tail: Future[Option[AsyncStream[A]]] = underlying.map {
    case Empty | One(_) => None
    case cons: Cons[A] => Some(cons.tail)
  }

  /**
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def foreach(f: A => Unit): Future[Unit] =
    foldLeft(()) { (_, a) => f(a) }

  /**
   * Maps each element of the stream to a Future action, resolving them from
   * head to tail. The resulting Future completes when the action completes
   * for the last element.
   *
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def foreachF(f: A => Future[Unit]): Future[Unit] =
    foldLeftF(()) { (_, a) => f(a) }

  /**
   * Given a predicate `p`, returns the longest prefix (possibly empty) of this
   * stream that satisfes `p`:
   *
   * {{{
   *   AsyncStream(1, 2, 3, 4, 1).takeWhile(_ < 3) = AsyncStream(1, 2)
   *   AsyncStream(1, 2, 3).takeWhile(_ < 5) = AsyncStream(1, 2, 3)
   *   AsyncStream(1, 2, 3).takeWhile(_ < 0) = AsyncStream.empty
   * }}}
   */
  def takeWhile(p: A => Boolean): AsyncStream[A] = AsyncStream(
    foldRight(empty[A].underlying) { (a, as) =>
      if (p(a)) Future.value(Cons(a, AsyncStream(as)))
      else empty.underlying
    }
  )

  /**
   * Given a predicate `p` returns the suffix remaining after `takeWhile(p)`:
   *
   * {{{
   *   AsyncStream(1, 2, 3, 4, 1).dropWhile(_ < 3) = AsyncStream(3, 4, 1)
   *   AsyncStream(1, 2, 3).dropWhile(_ < 5) = AsyncStream.empty
   *   AsyncStream(1, 2, 3).dropWhile(_ < 0) = AsyncStream(1, 2, 3)
   * }}}
   */
  def dropWhile(p: A => Boolean): AsyncStream[A] = AsyncStream(
    foldRight(empty[A].underlying) { (a, as) =>
      if (p(a)) as else Future.value(Cons(a, AsyncStream(as)))
    }
  )

  /**
   * Concatenates two streams.
   *
   * Note: If this stream is infinite, we never process the concatenated
   * stream; effectively: m ++ k == m.
   *
   * @see [[concat]] for Java users.
   */
  def ++[B >: A](that: => AsyncStream[B]): AsyncStream[B] = AsyncStream(
    underlying.flatMap {
      case Empty => that.underlying
      case One(a) => (a +:: that).underlying
      case cons: Cons[A] => Future.value(Cons(cons.head, cons.tail ++ that))
    }
  )

  /**
   * @see ++
   */
  def concat[B >: A](that: => AsyncStream[B]): AsyncStream[B] = ++(that)

  /**
   * Map a function `f` over the elements in this stream and concatenate the
   * results.
   */
  def flatMap[B](f: A => AsyncStream[B]): AsyncStream[B] = AsyncStream(
    underlying.flatMap {
      case Empty => empty.underlying
      case One(a) => f(a).underlying
      case cons: Cons[A] => (f(cons.head) ++ cons.tail.flatMap(f)).underlying
    }
  )

  /**
   * `stream.map(f)` is the stream obtained by applying `f` to each element of
   * `stream`.
   */
  def map[B](f: A => B): AsyncStream[B] = AsyncStream(
    underlying.flatMap {
      case Empty => empty.underlying
      case One(a) => Future.value(One(f(a)))
      case cons: Cons[A] => (f(cons.head) +:: cons.tail.map(f)).underlying

    }
  )

  /**
   * Returns a stream of elements that satisfy the predicate `p`.
   *
   * Note: forces the stream up to the first element which satisfies the
   * predicate. This operation may block forever on infinite streams in which
   * no elements match.
   */
  def filter(p: A => Boolean): AsyncStream[A] = AsyncStream(
    underlying.flatMap {
      case Empty => empty.underlying
      case one@One(a) => if (p(a)) Future.value(one) else empty.underlying
      case cons: Cons[A] =>
        if (p(cons.head)) Future.value(Cons(cons.head, cons.tail.filter(p)))
        else cons.tail.filter(p).underlying
    }
  )

  /**
   * @see filter
   */
  def withFilter(f: A => Boolean): AsyncStream[A] = filter(f)

  /**
   * Returns the prefix of this stream of length `n`, or the stream itself if
   * `n` is larger than the number of elements in the stream.
   */
  def take(n: Int): AsyncStream[A] =
    if (n < 1) AsyncStream.empty else AsyncStream(
      underlying.map {
        case Empty => Empty
        case o@One(_) => o
        case cons: Cons[A] => Cons(cons.head, cons.tail.take(n - 1))
      }
    )

  /**
   * Returns the suffix of this stream after the first `n` elements, or
   * `AsyncStream.empty` if `n` is larger than the number of elements in the
   * stream.
   *
   * Note: this forces all of the intermediate dropped elements.
   */
  def drop(n: Int): AsyncStream[A] =
    if (n < 1) this else AsyncStream(
      underlying.flatMap {
        case Empty | One(_) => empty.underlying
        case cons: Cons[A] => cons.tail.drop(n - 1).underlying
      }
    )

  /**
   * Constructs a new stream by mapping each element of this stream to a
   * Future action, evaluated from head to tail.
   */
  def mapF[B](f: A => Future[B]): AsyncStream[B] = AsyncStream(
    underlying.flatMap {
      case Empty => empty[B].underlying
      case One(a) => f(a).map(One[B](_))
      case cons: Cons[A] => f(cons.head).map { b =>
        Cons(b, cons.tail.mapF(f))
      }
    }
  )

  /**
   * Applies a binary operator to a start value and all elements of the stream,
   * from head to tail.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   *
   * @param z the starting value.
   * @param f a binary operator applied to elements of this stream.
   */
  def foldLeft[B](z: B)(f: (B, A) => B): Future[B] = underlying.flatMap {
    case Empty => Future.value(z)
    case One(a) => Future.value(f(z, a))
    case cons: Cons[A] => cons.tail.foldLeft(f(z, cons.head))(f)
  }

  /**
   * Like `foldLeft`, except that its result is encapsulated in a Future.
   * `foldLeftF` works from head to tail over the stream.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   *
   * @param z the starting value.
   * @param f a binary operator applied to elements of this stream.
   */
  def foldLeftF[B](z: B)(f: (B, A) => Future[B]): Future[B] =
    underlying.flatMap {
      case Empty => Future.value(z)
      case One(a) => f(z, a)
      case cons: Cons[A] => f(z, cons.head).flatMap(cons.tail.foldLeftF(_)(f))
    }

  /**
   * This is a powerful and expert level function. A fold operation
   * encapsulated in a Future. Like foldRight on normal lists, it replaces
   * every cons with the folded function `f`, and the empty element with `z`.
   *
   * Note: For clarity, we imagine that surrounding a function with backticks
   * (`) allows infix usage.
   * 
   * {{{
   *     (1 +:: 2 +:: 3 +:: empty).foldRight(z)(f)
   *   = 1 `f` flatMap (2 `f` flatMap (3 `f` z))
   * }}}
   *
   * Note: if `f` always forces the second parameter, for infinite streams the
   * future never resolves.
   *
   * @param z the parameter that replaces the end of the list.
   * @param f a binary operator applied to elements of this stream. Note that
   * the second paramter is call-by-name.
   */
  def foldRight[B](z: => Future[B])(f: (A, => Future[B]) => Future[B]): Future[B] =
    underlying.flatMap {
      case Empty => z
      case One(a) => f(a, z)
      case cons: Cons[A] => f(cons.head, cons.tail.foldRight(z)(f))
    }

  /**
   * A Future of the stream realized as a list. This future completes when all
   * elements of the stream are resolved.
   *
   * Note: forces the entire stream. If one asynchronous call fails, it fails
   * the aggregated result.
   */
  def toSeq(): Future[Seq[A]] =
    observe().flatMap {
      case (s, None) => Future.value(s)
      case (_, Some(exc)) => Future.exception(exc)
    }

  /**
   * Attempts to transform the stream into a Seq, and in the case of failure,
   * `observe` returns whatever was able to be transformed up to the point of
   * failure along with the exception. As a result, this Future never fails,
   * and if there are errors they can be accessed via the Option.
   *
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def observe(): Future[(Seq[A], Option[Throwable])] = {
    val buf: mutable.ListBuffer[A] = mutable.ListBuffer.empty

    def go(as: AsyncStream[A]): Future[Unit] =
      as.underlying.flatMap {
        case Empty => Future.Done
        case One(a) =>
          buf += a
          Future.Done
        case cons: Cons[A] =>
          buf += cons.head
          go(cons.tail)
      }

    go(this).transform {
      case Throw(exc) => Future.value(buf.toList -> Some(exc))
      case Return(_) => Future.value(buf.toList -> None)
    }
  }

  override def toString(): String = s"AsyncStream($underlying)"
}

object AsyncStream {
  import LazySeq.{Empty, One, Cons}

  implicit class Ops[A](tail: => AsyncStream[A]) {
    /**
     * Right-associative infix Cons constructor.
     *
     * Note: Because of https://issues.scala-lang.org/browse/SI-1980 we can't
     * define +:: as a method on AsyncStream without losing tail laziness.
     */
    def +::[B >: A](b: B): AsyncStream[B] = mk(b, tail)
  }

  private val nothing: AsyncStream[Nothing] = AsyncStream(Future.value(Empty))
  def empty[A]: AsyncStream[A] = nothing.asInstanceOf[AsyncStream[A]]

  /**
   * An AsyncStream from a `Future[LazySeq[A]]`.
   */
  private def apply[A](go: Future[LazySeq[A]]): AsyncStream[A] =
    new AsyncStream(go)

  /**
   * Var-arg constructor for AsyncStreams.
   *
   * {{{
   *   AsyncStream(1,2,3)
   * }}}
   *
   * Note: we can't annotate this with varargs because of
   * https://issues.scala-lang.org/browse/SI-8743. This seems to be fixed in a
   * more recent scala version so we will revisit this soon.
   */
  def apply[A](as: A*): AsyncStream[A] = fromSeq(as)

  /**
   * An AsyncStream with a single element.
   */
  def of[A](a: A): AsyncStream[A] = AsyncStream(Future.value(One(a)))

  /**
   * Like `Ops.+::`.
   */
  def mk[A](a: A, tail: => AsyncStream[A]): AsyncStream[A] =
    AsyncStream(Future.value(Cons(a, tail)))

  /**
   * Transformation (or lift) from `Seq[A]` into `AsyncStream[A]`.
   */
  def fromSeq[A](seq: Seq[A]): AsyncStream[A] = seq match {
    case Nil => empty
    case _ if seq.hasDefiniteSize && seq.tail.isEmpty => of(seq.head)
    case _ => seq.head +:: fromSeq(seq.tail)
  }

  /**
   * Transformation (or lift) from `Future[A]` into `AsyncStream[A]`.
   */
  def fromFuture[A](f: Future[A]): AsyncStream[A] =
    AsyncStream(f.map(One(_)))
  
  /**
   * Transformation (or lift) from `Option[A]` into `AsyncStream[A]`.
   */
  def fromOption[A](o: Option[A]): AsyncStream[A] =
    o match {
      case None => empty
      case Some(a) => of(a)
    }

  /**
   * Collapses the Future structure into the AsyncStream. This is possible
   * because we can unnaply the AsyncStream constructor to access
   * Future[LazySeq[A]], and now we have two Futures which we can flatten using
   * `flatMap`, e.g.:
   *
   * {{{
   *     Future[AsyncStream[A]]     // The input `f`.
   *   = Future[Future[LazySeq[A]]] // Unapply the AsyncStream constructor.
   *   = Future[LazySeq[A]]         // Future.flatMap(identity).
   *   = AsyncStream[A]             // Apply the AsyncStream constructor.
   * }}}
   */
  def flatten[A](f: Future[AsyncStream[A]]): AsyncStream[A] = AsyncStream(
    f.flatMap(_.underlying.flatMap {
      case Empty => empty.underlying
      case o@One(a) => Future.value(o)
      case cons: Cons[A] => (cons.head +:: cons.tail).underlying
    })
  )
}
