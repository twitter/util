package com.twitter.util

import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer

private case class Cell[A](var value: A)

object Future {
  val DEFAULT_TIMEOUT = Math.MAX_LONG.millis

  def apply[E <: Throwable, A](a: => A) = {
    new Future[E, A] {
      val result = Try[E, A](a)
      def respond(k: Try[E, A] => Unit) { k(result) }
    }
  }
}

abstract class Future[+E <: Throwable, +A] extends Try[E, A] {
  import Future.DEFAULT_TIMEOUT

  def respond(k: Try[E, A] => Unit)

  override def apply = apply(DEFAULT_TIMEOUT)

  def apply(timeout: Duration) = within(timeout)()

  def isReturn = within(DEFAULT_TIMEOUT) isReturn

  def isThrow = within(DEFAULT_TIMEOUT) isThrow

  def within(timeout: Duration) = {
    val latch = new CountDownLatch(1)
    var result: Try[Throwable, A] = null
    respond { a =>
      result = a
      latch.countDown()
    }
    if (!latch.await(timeout)) {
      result = Throw(new TimeoutException(timeout.toString))
    }
    result
  }

  override def foreach(k: A => Unit) { respond(_ foreach k) }

  /**
   * Takes a function and returns a new `Future` whose value is the function applied to the value represented
   * by this Future. Note that `map` is *lazy*: it does not necessarily invoke the given function. The function
   * will be invoked if you later call `respond` on the returned future. If the function given to map has
   * side-effects, you probably want to call `respond` or `foreach` instead of `map`.
   */
  override def map[B](f: A => B) = new Future[E, B] {
    def respond(k: Try[E, B] => Unit) {
      Future.this.respond { x =>
        k(x map(f))
      }
    }
  }

  override def flatMap[E2 >: E <: Throwable, B >: A](f: A => Try[E2, B]) = new Future[E2, B] {
    def respond(k: Try[E2, B] => Unit) {
      Future.this.respond { x =>
        k(x flatMap(f))
      }
    }
  }

  override def filter(p: A => Boolean) = new Future[Throwable, A] {
    def respond(k: Try[Throwable, A] => Unit) {
      Future.this.respond { x =>
        k(x filter(p))
      }
    }
  }

  def rescue[E2 >: E <: Throwable, B >: A](rescueException: PartialFunction[E, Try[E2, B]]) =
    new Future[E2, B] {
      def respond(k: Try[E2, B] => Unit) {
        Future.this.respond { x =>
          k(x rescue(rescueException))
        }
      }
    }
}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

class Promise[E <: Throwable, A] extends Future[E, A] {
  import Promise._

  private var result: Option[Try[E, A]] = None
  private val computations = new ArrayBuffer[Try[E, A] => Unit]

  def update(result: Try[E, A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  def updateIfEmpty(newResult: Try[E, A]) = {
    if (result.isDefined) false else {
      val didSetResult = synchronized {
        if (result.isDefined) false else {
          result = Some(newResult)
          true
        }
      }
      if (didSetResult) computations foreach(_(newResult))
      didSetResult
    }
  }

  def respond(k: Try[E, A] => Unit) {
    result map(k) getOrElse {
      val wasDefined = synchronized {
        if (result.isDefined) true else {
          computations += k
          false
        }
      }
      if (wasDefined) result map(k)
    }
  }
}
