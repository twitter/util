package com.twitter.concurrent

import java.io.EOFException

import scala.collection.mutable.ArrayBuffer

import com.twitter.util.{Await, Duration, Future, Return, Throw}

/**
 * A spool is an asynchronous stream. It more or less mimics the scala
 * {{Stream}} collection, but with cons cells that have either eager or
 * deferred tails.
 *
 * Construction of eager Spools is done with either Spool.cons or
 * the {{**::}} operator. To construct a lazy/deferred Spool which
 * materializes its tail on demand, use the {{*::}} operator. In order
 * to use these operators for deconstruction, they must be imported
 * explicitly (ie: {{import Spool.{*::, **::}}})
 *
 * {{{
 *   def fill(rest: Promise[Spool[Int]]) {
 *     asyncProcess foreach { result =>
 *       if (result.last) {
 *         rest() = Return(result **:: Spool.empty)
 *       } else {
 *         val next = new Promise[Spool[Int]]
 *         rest() = Return(result *:: next)
 *         fill(next)
 *       }
 *     }
 *   }
 *   val rest = new Promise[Spool[Int]]
 *   fill(rest)
 *   firstElem *:: rest
 * }}}
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.concurrent.AbstractSpool]].
 */
sealed trait Spool[+A] {
  // NB: Spools are always lazy internally in order to provide the expected behavior
  // during concatenation of two Spools, regardless of how they were constructed
  import Spool.{LazyCons,empty}

  def isEmpty: Boolean

  /**
   * The first element of the spool. Invalid for empty spools.
   */
  def head: A

  /**
   * The first element of the spool if it is non-empty.
   */
  def headOption: Option[A] =
    if (isEmpty) None
    else Some(head)

  /**
   * The (deferred) tail of the spool. Invalid for empty spools.
   */
  def tail: Future[Spool[A]]

  /**
   * Apply {{f}} for each item in the spool, until the end.  {{f}} is
   * applied as the items become available.
   */
  def foreach[B](f: A => B) = foreachElem (_ foreach f)

  /**
   * A version of {{foreach}} that wraps each element in an
   * {{Option}}, terminating the stream (EOF) with
   * {{None}}.
   */
  def foreachElem[B](f: Option[A] => B): Future[Unit] = {
    if (!isEmpty) {
      Future { f(Some(head)) } flatMap { _ =>
        tail transform {
          case Return(s) => s.foreachElem(f)
          case Throw(_: EOFException) => Future { f(None) }
          case Throw(cause) => Future.exception(cause)
        }
      }
    } else {
      Future { f(None) }
    }
  }

  def foldLeft[B](z: B)(f: (B, A) => B): Future[B] =
    if (isEmpty) {
      Future.value(z)
    } else {
      tail.flatMap(s => s.foldLeft(f(z, head))(f))
    }

  def reduceLeft[B >: A](f: (B, A) => B): Future[B] =
    if (isEmpty) {
      Future.exception(new UnsupportedOperationException("empty.reduceLeft"))
    } else {
      tail.flatMap(s => s.foldLeft[B](head)(f))
    }

  /**
   * The standard Scala collect, in order to implement map & filter.
   *
   * It may seem unnatural to return a Future[…] here, but we cannot
   * know whether the first element exists until we have applied its
   * filter.
   */
  def collect[B](f: PartialFunction[A, B]): Future[Spool[B]] =
    if (isEmpty) Future.value(empty[B])
    else {
      def _tail = tail flatMap (_.collect(f))
      if (f.isDefinedAt(head)) Future.value(new LazyCons(f(head), _tail))
      else _tail
    }

  def map[B](f: A => B): Spool[B] = {
    val s = collect { case x => f(x) }
    Await.result(s, Duration.Zero)
  }

  /**
   * Applies a function that generates a Future[B] for each element of this
   * spool. The returned future is satisfied when the head of the resulting
   * spool is available.
   */
  def mapFuture[B](f: A => Future[B]): Future[Spool[B]] = {
    if (isEmpty) Future.value(empty[B])
    else {
      f(head) map { h =>
        new LazyCons(h, tail flatMap (_ mapFuture f))
      }
    }
  }

  def filter(f: A => Boolean): Future[Spool[A]] = collect {
    case x if f(x) => x
  }

  /**
   * Take elements from the head of the Spool (lazily), while the given condition is true.
   */
  def takeWhile(f: A => Boolean): Spool[A] =
    if (isEmpty) {
      this
    } else if (f(head)) {
      new LazyCons(head, tail map (_ takeWhile f))
    } else {
      empty[A]
    }

  /**
   * Take the first n elements of the Spool as another Spool (adapted from Stream.take)
   */
  def take(n: Int): Spool[A] = {
    if (n <= 0 || isEmpty) {
      empty[A]
    } else if (n == 1) {
      new LazyCons(head, Future.value(empty[A]))
    } else {
      new LazyCons(head, tail map (_ take (n - 1)))
    }
  }

  /**
   * Concatenates two spools.
   */
  def ++[B >: A](that: => Spool[B]): Spool[B] =
    if (isEmpty) that else new LazyCons(head: B, tail map (_ ++ that))

  /**
   * @see operator ++
   */
  def concat[B >: A](that: Spool[B]): Spool[B] = this ++ that

  /**
   * Concatenates two spools.
   */
  def ++[B >: A](that: => Future[Spool[B]]): Future[Spool[B]] =
    if (isEmpty) that else Future.value(new LazyCons(head: B, tail flatMap (_ ++ that)))

  /**
   * @see operator ++
   */
  def concat[B >: A](that: Future[Spool[B]]): Future[Spool[B]] = this ++ that

  /**
   * Applies a function that generates a spool for each element in this spool,
   * flattening the result into a single spool.
   */
  def flatMap[B](f: A => Future[Spool[B]]): Future[Spool[B]] =
    if (isEmpty) Future.value(empty[B])
    else f(head).flatMap(headSpool => headSpool ++ tail.flatMap(_.flatMap(f)))

  /**
   * Fully buffer the spool to a {{Seq}}.  The returned future is
   * satisfied when the entire result is ready.
   */
  def toSeq: Future[Seq[A]] = {
    val as = new ArrayBuffer[A]
    foreach { a => as += a } map { _ => as }
  }

  /**
   * Eagerly executes all computation represented by this Spool (presumably for
   * side-effects), and returns a Future representing its completion.
   */
  def force: Future[Unit] = foreach { _ => () }
}

/**
 * Abstract `Spool` class for Java compatibility.
 */
abstract class AbstractSpool[A] extends Spool[A]

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.concurrent.Spools]].
 */
object Spool {
  case class Cons[A](head: A, tail: Future[Spool[A]])
    extends Spool[A]
  {
    def isEmpty = false
    override def toString = "Cons(%s, %c)".format(head, if (tail.isDefined) '*' else '?')
  }

  private class LazyCons[A](val head: A, next: => Future[Spool[A]])
    extends Spool[A]
  {
    def isEmpty = false
    lazy val tail = next
    // NB: not touching tail, to avoid forcing unnecessarily
    override def toString = "Cons(%s, ?)".format(head)
  }

  object Empty extends Spool[Nothing] {
    def isEmpty = true
    def head = throw new NoSuchElementException("spool is empty")
    def tail = Future.exception(new NoSuchElementException("spool is empty"))
    override def toString = "Empty"
  }

  /**
   * Cons a value & tail to a new {{Spool}}. To defer the tail of the Spool, use
   * the {{*::}} operator instead.
   *
   * @deprecated Both forms of cons are deprecated in favor of {{*::}}. They will eventually
   * be changed in an ABI-breaking fashion in order to act lazily on the tail.
   */
  @deprecated("Use *:: instead: the ABI for this method will be changing.", "6.14.1")
  def cons[A](value: A, next: Future[Spool[A]]): Spool[A] = Cons(value, next)
  @deprecated("Use *:: instead: the ABI for this method will be changing.", "6.14.1")
  def cons[A](value: A, nextSpool: Spool[A]): Spool[A] = Cons(value, Future.value(nextSpool))

  /**
   * The empty spool.
   */
  def empty[A]: Spool[A] = Empty

  /**
   * Syntax support.  We retain different constructors for future
   * resolving vs. not.
   *
   * *:: constructs and deconstructs deferred tails
   * **:: constructs and deconstructs eager tails
   */

  class Syntax[A](tail: => Future[Spool[A]]) {
    def *::(head: A): Spool[A] = new LazyCons(head, tail)
  }

  implicit def syntax[A](s: => Future[Spool[A]]) = new Syntax[A](s)

  object *:: {
    def unapply[A](s: Spool[A]): Option[(A, Future[Spool[A]])] = {
      if (s.isEmpty) None
      else Some((s.head, s.tail))
    }
  }

  class Syntax1[A](tail: Spool[A]) {
    /**
     * @deprecated Deprecated in favor of {{*::}}. This will eventually be removed.
     */
    @deprecated("Use *:: instead.", "6.14.1")
    def **::(head: A) = cons(head, tail)
  }

  implicit def syntax1[A](s: Spool[A]) = new Syntax1(s)

  object **:: {
    def unapply[A](s: Spool[A]): Option[(A, Spool[A])] = {
      if (s.isEmpty) None
      else Some((s.head, Await.result(s.tail)))
    }
  }

  /**
   * Lazily builds a Spool from a Seq.
   *
   * The main difference between this and `seqToSpool` is that this method also
   * consumes the Seq lazily, which means if used with Streams, it will
   * preserve laziness.
   */
  def fromSeq[A](seq: Seq[A]): Spool[A] = {
    def go(as: Seq[A]): Future[Spool[A]] =
      if (as.isEmpty) Future.value(Spool.empty)
      else Future.value(as.head *:: go(as.tail))

    if (seq.isEmpty) Spool.empty else seq.head *:: go(seq.tail)
  }

  /**
   * Adds an implicit method to efficiently convert a Seq[A] to a Spool[A]
   */
  class ToSpool[A](s: Seq[A]) {
    def toSpool: Spool[A] =
      s.reverse.foldLeft(Spool.empty: Spool[A]) {
        case (tail, head) => head *:: Future.value(tail)
      }
  }

  implicit def seqToSpool[A](s: Seq[A]) = new ToSpool(s)
}
