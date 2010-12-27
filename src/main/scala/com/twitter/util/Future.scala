package com.twitter.util

import com.twitter.conversions.time._
import scala.collection.mutable.ArrayBuffer

object Future {
  val DEFAULT_TIMEOUT = Long.MaxValue.millis
  val Done = apply(())

  def value[A](a: A) = Future(a)
  def exception[A](e: Throwable) = Future { throw e }

  /**
   * A factory function to "lift" computations into the Future monad. It will catch
   * exceptions and wrap them in the Throw[_] type. Non-exceptional values are wrapped
   * in the Return[_] type.
   */
  def apply[A](a: => A): Future[A] =
    new Promise[A] {
      update(Try(a))
    }
}

/**
 * An alternative interface for handling Future Events. This interface is designed
 * to be friendly to Java users since it does not require closures.
 */
trait FutureEventListener[T] {
  /**
   * Invoked if the computation completes successfully
   */
  def onSuccess(value: T): Unit

  /**
   * Invoked if the computation completes unsuccessfully
   */
  def onFailure(cause: Throwable): Unit
}

/**
 * A computation evaluated asynchronously. This implementation of Future does not
 * assume any concrete implementation (in particular, it does not couple the user
 * to a specific executor or event loop.
 *
 * Note that this class extends Try[_] indicating that the results of the computation
 * may succeed or fail.
 */
abstract class Future[+A] extends Try[A] {
  import Future.DEFAULT_TIMEOUT

  def respond(k: Try[A] => Unit)

  override def apply = apply(DEFAULT_TIMEOUT): A
  def apply(timeout: Duration): A = within(timeout)()

  def isReturn = within(DEFAULT_TIMEOUT) isReturn
  def isThrow = within(DEFAULT_TIMEOUT) isThrow

  def isDefined: Boolean

  /**
   * Demands that the result of the future be available within `timeout`. The result
   * is a Return[_] or Throw[_] depending upon whether the computation finished in
   * time.
   */
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

  override def ensure(f: => Unit) = {
    respond { _ =>
      f
    }
    this
  }

  def flatMap[B](f: A => Try[B]): Future[B]

  def map[X](f: A => X): Future[X]

  def filter(p: A => Boolean): Future[A]

  def rescue[B >: A](rescueException: Throwable => Try[B]): Future[B]

  def handle[B >: A](rescueException: Throwable => B) =
    rescue { throwable =>
      Future(rescueException(throwable))
    }

  def onSuccess[B](f: A => B) = respond {
    case Return(value) => f(value)
    case _ =>
  }

  def onFailure[B](rescueException: Throwable => B) = respond {
    case Throw(throwable) => rescueException(throwable)
    case _ =>
  }

  def addEventListener[U >: A](listener: FutureEventListener[U]) = respond {
    case Throw(cause)  => listener.onFailure(cause)
    case Return(value) => listener.onSuccess(value)
  }

}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

/**
 * A concrete Future implementation that is updatable by some executor or event loop.
 * A typical use of Promise is for a client to submit a request to some service.
 * The client is given an object that inherits from Future[_]. The server stores a
 * reference to this object as a Promise[_] and updates the value when the computation
 * completes.
 */
class Promise[A] extends Future[A] {
  import Promise._

  @volatile private[this] var result: Option[Try[A]] = None
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
