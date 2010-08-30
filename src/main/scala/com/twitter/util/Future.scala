package com.twitter.util

import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer

private case class Cell[A](var value: A)

object Future {
  val DEFAULT_TIMEOUT = Math.MAX_LONG.millis

  def apply[E <: Throwable, A](a: => A): Future[E, A] = {
    new Promise[E, A] {
      update(Try(a))
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
  def flatMap[E2 >: E <: Throwable, B](f: A => Try[E2, B]): Future[E2, B]
  def map[X](f: A => X): Future[E, X]
  def filter(p: A => Boolean): Future[Throwable, A]
  def rescue[E2 <: Throwable, B >: A](rescueException: E => Try[E2, B]): Future[E2, B]
}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

class Promise[E <: Throwable, A] extends Future[E, A] {
  import Promise._

  private[this] var result: Option[Try[E, A]] = None
  private[this] val computations = new ArrayBuffer[Try[E, A] => Unit]

  def update(result: Try[E, A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  private def updateIfEmpty(newResult: Try[E, A]) = {
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

  override def map[B](f: A => B) = new Promise[E, B] {
    Promise.this.respond { x =>
      update(x map(f))
    }
  }

  override def flatMap[E2 >: E <: Throwable, B](f: A => Try[E2, B]) = new Promise[E2, B] {
    Promise.this.respond { x =>
      update(x flatMap f)
    }
  }

  override def filter(p: A => Boolean) = new Promise[Throwable, A] {
    Promise.this.respond { x =>
      update(x filter(p))
    }
  }

  def rescue[E2 <: Throwable, B >: A](rescueException: E => Try[E2, B]) =
    new Promise[E2, B] {
      Promise.this.respond { x =>
        update(x rescue(rescueException))
      }
    }
}