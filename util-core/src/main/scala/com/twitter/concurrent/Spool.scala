package com.twitter.concurrent

import scala.collection.mutable.ArrayBuffer

import com.twitter.util.{Future, Promise, Return}

/**
 * A spool is an asynchronous stream. It more or less
 * mimics the scala {{Stream}} collection, but with cons
 * cells that have deferred tails.
 *
 * Construction is done with Spool.cons, Spool.empty.  Convenience
 * syntax like that of [[scala.Stream]] is provided.  In order to use
 * these operators for deconstruction, they must be imported
 * explicitly ({{import Spool.{*::, **::}}})
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
   * {{Option}}, terminating the stream (EOF or failure) with
   * {{None}}.
   */
  def foreachElem[B](f: Option[A] => B) {
    if (!isEmpty) {
      f(Some(head))
      // note: this hack is to avoid deep
      // stacks in case a large portion
      // of the stream is already defined
      var next = tail
      while (next.isDefined && next.isReturn && !next().isEmpty) {
        f(Some(next().head))
        next = next().tail
      }
      next onSuccess { s =>
        s.foreachElem(f)
      } onFailure { _ =>
        f(None)
      }
    } else {
      f(None)
    }
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
    require(s.isDefined)
    s()
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
    val p = new Promise[Seq[A]]
    val as = new ArrayBuffer[A]
    foreachElem {
      case Some(a) => as += a
      case None => p() = Return(as)
    }
    p
  }
}

object Spool {
  private[Spool]
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

  private[Spool] object Empty extends Spool[Nothing] {
    def isEmpty = true
    def head = throw new NoSuchElementException("spool is empty")
    def tail = Future.exception(new NoSuchElementException("spool is empty"))
    def collect[B](f: PartialFunction[Nothing, B]) = Future.value(this)
    override def toString = "Empty"
  }

  /**
   * Cons a value & (possibly deferred) tail to a new {{Spool}}.
   */
  def cons[A](value: A, next: Future[Spool[A]]): Spool[A] = Cons(value, next)
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
    def *::(head: A) = cons(head, tail)
  }

  implicit def syntax[A](s: Future[Spool[A]]) = new Syntax(s)

  object *:: {
    def unapply[A](s: Spool[A]): Option[(A, Future[Spool[A]])] = {
      if (s.isEmpty) None
      else Some((s.head, s.tail))
    }
  }
  class Syntax1[A](tail: => Spool[A]) {
    def **::(head: A) = cons(head, tail)
  }

  implicit def syntax1[A](s: Spool[A]) = new Syntax1(s)

  object **:: {
    def unapply[A](s: Spool[A]): Option[(A, Spool[A])] = {
      if (s.isEmpty) None
      else Some((s.head, s.tail()))
    }
  }
}
