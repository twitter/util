package com.twitter.util

import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer

private case class Cell[A](var value: A)

object Future {
  def apply[A](a: => A) = new Future[A] {
    val result = Try(a)
    def respond(k: Try[Throwable, A] => Unit) { k(result) }
  }
}

abstract class Future[+A] extends (() => A) {
  private var cell = Cell[Option[Any]](None)

  def respond(k: Try[Throwable, A] => Unit)

  final def apply: A = apply(Math.MAX_LONG.millis)

  final def apply(timeout: Duration) =
    within(timeout) { result => result } {
      throw new TimeoutException(timeout.toString)
    }

  final def within[B](timeout: Duration)(f: A => B)(onTimeout: => B) = cell.synchronized {
    if (!cell.value.isDefined) {
      val latch = new CountDownLatch(1)
      respond { a =>
        cell.value = Some(a)
        latch.countDown()
      }
      if (!latch.await(timeout)) onTimeout
    }
    f(cell.value.get.asInstanceOf[Try[Throwable, A]]())
  }

  final def foreach(k: A => Unit) { respond(_ foreach k) }

  /**
   * Takes a function and returns a new `Future` whose value is the function applied to the value represented
   * by this Future. Note that `map` is *lazy*: it does not necessarily invoke the given function. The function
   * will be invoked if you later call `respond` on the returned future. If the function given to map has
   * side-effects, you probably want to call `respond` or `foreach` instead of `map`.
   */
  final def map[B](f: A => B) = new Future[B] {
    def respond(k: Try[Throwable, B] => Unit) {
      Future.this.respond { x =>
        k(x map(f))
      }
    }
  }

  final def flatMap[B](f: A => Future[B]) = new Future[B] {
    def respond(k: Try[Throwable, B] => Unit) {
      Future.this.respond { x =>
        x map(f) foreach (_.respond(k))
      }
    }
  }

  final def filter(p: A => Boolean) = new Future[A] {
    def respond(k: Try[Throwable, A] => Unit) {
      Future.this.respond { x =>
        x filter(p) foreach k
      }
    }
  }
}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

class Promise[A] extends Future[A] {
  import Promise._

  private var result: Option[Try[Throwable, A]] = None
  private val computations = new ArrayBuffer[Try[Throwable, A] => Unit]

  def update(result: Try[Throwable, A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  def updateIfEmpty(newResult: Try[Throwable, A]) = {
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

  def respond(k: Try[Throwable, A] => Unit) {
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
