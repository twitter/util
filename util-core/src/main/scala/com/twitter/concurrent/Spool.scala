package com.twitter.concurrent

import com.twitter.util.{Await, Duration, Future, Promise, Return, Throw}
import java.io.EOFException
import scala.collection.mutable.ArrayBuffer

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
 */
sealed trait Spool[+A] {
  import Spool.{cons, empty}

  def isEmpty: Boolean

  /**
   * The first element of the spool. Invalid for empty spools.
   */
  def head: A

  /**
   * The (deferred) tail of the spool. Invalid for empty spools.
   */
  def tail: Future[Spool[A]]

  /**
   * Apply {{f}} for each item in the spool, until the end.  {{f}} is
   * applied as the items become available.
   */
  def foreach[B](f: A => B) = foreachElem { _ foreach(f) }

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
  def collect[B](f: PartialFunction[A, B]): Future[Spool[B]]

  def map[B](f: A => B): Spool[B] = {
    val s = collect { case x => f(x) }
    Await.result(s, Duration.Zero)
  }

  def filter(f: A => Boolean): Future[Spool[A]] = collect {
    case x if f(x) => x
  }

  /**
   * Concatenates two spools.
   */
  def ++[B >: A](that: Spool[B]): Spool[B] =
    if (isEmpty) that else cons(head: B, tail map { _ ++ that })

  /**
   * Concatenates two spools.
   */
  def ++[B >: A](that: Future[Spool[B]]): Future[Spool[B]] =
    if (isEmpty) that else Future.value(cons(head: B, tail flatMap { _ ++ that }))

  /**
   * Applies a function that generates a spool to each element in this spool,
   * flattening the result into a single spool.
   */
  def flatMap[B](f: A => Future[Spool[B]]): Future[Spool[B]] =
    if (isEmpty) Future.value(empty[B])
    else f(head) flatMap { _ ++ (tail flatMap { _ flatMap f }) }

  /**
   * Fully buffer the spool to a {{Seq}}.  The returned future is
   * satisfied when the entire result is ready.
   */
  def toSeq: Future[Seq[A]] = {
    val as = new ArrayBuffer[A]
    foreach { a => as += a } map { _ => as }
  }
}

object Spool {
  case class Cons[A](value: A, next: Future[Spool[A]])
    extends Spool[A]
  {
    def isEmpty = false
    def head = value
    def tail = next
    def collect[B](f: PartialFunction[A, B]) = {
      val next_ = next flatMap { _.collect(f) }
      if (f.isDefinedAt(head)) Future.value(Cons(f(head), next_))
      else next_
    }

    override def toString = "Cons(%s, %c)".format(head, if (tail.isDefined) '*' else '?')
  }

  private class LazyCons[A](val head: A, next: => Future[Spool[A]])
    extends Spool[A]
  {
    def isEmpty = false
    lazy val tail = next
    def collect[B](f: PartialFunction[A, B]) = {
      val next_ = tail flatMap { _.collect(f) }
      if (f.isDefinedAt(head)) Future.value(Cons(f(head), next_))
      else next_
    }

    // NB: not touching tail, to avoid forcing unnecessarily
    override def toString = "Cons(%s, ?)".format(head)
  }

  object Empty extends Spool[Nothing] {
    def isEmpty = true
    def head = throw new NoSuchElementException("spool is empty")
    def tail = Future.exception(new NoSuchElementException("spool is empty"))
    def collect[B](f: PartialFunction[Nothing, B]) = Future.value(this)
    override def toString = "Empty"
  }

  /**
   * Cons a value & tail to a new {{Spool}}. To defer the tail of the Spool, use
   * the {{*::}} operator instead.
   */
  def cons[A](value: A, next: Future[Spool[A]]): Spool[A] = Cons(value, next)
  def cons[A](value: A, nextSpool: Spool[A]): Spool[A] = Cons(value, Future.value(nextSpool))

  /**
   * The empty spool.
   */
  def empty[A]: Spool[A] = Empty

  /**
   * Adds an implicit method to efficiently convert a Seq[A] to a Spool[A]
   */
  class ToSpool[A](s: Seq[A]) {
    def toSpool: Spool[A] =
      s.reverse.foldLeft(Spool.empty: Spool[A]) {
        case (tail, head) => cons(head, tail)
      }
  }

  implicit def seqToSpool[A](s: Seq[A]) = new ToSpool(s)

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

  implicit def syntax[A](s: => Future[Spool[A]]) = new Syntax(s)

  object *:: {
    def unapply[A](s: Spool[A]): Option[(A, Future[Spool[A]])] = {
      if (s.isEmpty) None
      else Some((s.head, s.tail))
    }
  }
  class Syntax1[A](tail: Spool[A]) {
    def **::(head: A) = cons(head, tail)
  }

  implicit def syntax1[A](s: Spool[A]) = new Syntax1(s)

  object **:: {
    def unapply[A](s: Spool[A]): Option[(A, Spool[A])] = {
      if (s.isEmpty) None
      else Some((s.head, Await.result(s.tail)))
    }
  }
}
