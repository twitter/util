package com.twitter.util

import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer

private case class Cell[A](var value: A)

object Future {
  val DEFAULT_TIMEOUT = Long.MaxValue.millis

  def apply[A](a: => A): Future[A] = {
    new Promise[A] {
      update(Try(a))
    }
  }
}

// This is an alternative interface for maximum Java friendlyness.
trait FutureEventListener[T] {
  def onSuccess(value: T): Unit
  def onFailure(cause: Throwable): Unit
}

abstract class Future[+A] extends Try[A] {
  import Future.DEFAULT_TIMEOUT

  def respond(k: Try[A] => Unit)

  override def apply = apply(DEFAULT_TIMEOUT): A
  def apply(timeout: Duration): A = within(timeout)()

  def isReturn = within(DEFAULT_TIMEOUT) isReturn
  def isThrow = within(DEFAULT_TIMEOUT) isThrow

  def isDefined: Boolean

  def within(timeout: Duration): Try[A] = {
    val latch = new CountDownLatch(1)
    var result: Try[A] = null
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
  def flatMap[B](f: A => Try[B]): Future[B]
  def map[X](f: A => X): Future[X]
  def filter(p: A => Boolean): Future[A]
  def rescue[B >: A](rescueException: Throwable => Try[B]): Future[B]

  def addEventListener[U >: A](listener: FutureEventListener[U]) = respond {
    case Throw(cause)  => listener.onFailure(cause)
    case Return(value) => listener.onSuccess(value)
  }

}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

class Promise[A] extends Future[A] {
  import Promise._

  private[this] var result: Option[Try[A]] = None
  private[this] val computations = new ArrayBuffer[Try[A] => Unit]

  def isDefined = result.isDefined

  def update(result: Try[A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  def updateIfEmpty(newResult: Try[A]) = {

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

  override def respond(k: Try[A] => Unit) {
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

  override def map[B](f: A => B) = new Promise[B] {
    Promise.this.respond { x =>
      update(x map(f))
    }
  }

  override def flatMap[B](f: A => Try[B]) = new Promise[B] {
    Promise.this.respond { x =>
      x flatMap(f) respond { result =>
        update(result)
      }
    }
  }

  def rescue[B >: A](rescueException: Throwable => Try[B]) =
    new Promise[B] {
      Promise.this.respond { x =>
        x rescue(rescueException) respond { result =>
          update(result)
        }
      }
    }

  override def filter(p: A => Boolean) = new Promise[A] {
    Promise.this.respond { x =>
      update(x filter(p))
    }
  }
}

class FutureTask[A](fn: => A) extends Promise[A] with Runnable {
  def run() {
    update(Try(fn))
  }
}

object FutureTask {
  def apply[A](fn: => A) = new FutureTask[A](fn)
}
