package com.twitter.util

import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer

private case class Cell[A](var value: A)

object Future {
  def apply[A](a: A) = new Future[A] {
    def respond(k: A => Unit) { k(a) }
  }
}

abstract class Future[+A] extends (() => A) {
  private var cell = Cell[Option[Any]](None)

  def respond(k: A => Unit)

  def apply: A = apply(Math.MAX_LONG.millis).get

  def apply(timeout: Duration): Option[A] = cell.synchronized {
    cell.value.map(_.asInstanceOf[A]) orElse {
      val latch = new CountDownLatch(1)
      respond { a =>
        cell.value = Some(a)
        latch.countDown()
      }
      latch.await(timeout)
      cell.value.map(_.asInstanceOf[A])
    }
  }

  def getWithin(timeout: Duration): A = {
    apply(timeout).getOrElse(throw new TimeoutException(timeout.toString))
  }

  def foreach(k: A => Unit) { respond(k) }

  /**
   * Takes a function and returns a new `Future` whose value is the function applied to the value represented
   * by this Future. Note that `map` is *lazy*: it does not necessarily invoke the given function. The function
   * will be invoked if you later call `respond` on the returned future. If the function given to map has
   * side-effects, you probably want to call `respond` or `foreach` instead of `map`.
   */
  def map[B](f: A => B) = new Future[B] {
    def respond(k: B => Unit) {
      Future.this.respond(x => k(f(x)))
    }
  }

  def flatMap[B](f: A => Future[B]) = new Future[B] {
    def respond(k: B => Unit) {
      Future.this.respond(x => f(x).respond(k))
    }
  }

  def filter(p: A => Boolean) = new Future[A] {
    def respond(k: A => Unit) {
      Future.this.respond(x => if (p(x)) k(x) else ())
    }
  }

  def ++[B](right: Future[B]) = new Future[(A,B)] {
    def respond(k: ((A,B)) => Unit) {
      Future.this.respond { a =>
        right.respond { b => k((a,b)) }
      }
    }

    override def apply(timeout: Duration): Option[(A,B)] = {
      val startTime = System.currentTimeMillis
      Future.this.apply(timeout) flatMap { a =>
        val remainingTimeout = timeout - new Duration(System.currentTimeMillis - startTime)
        right.apply(remainingTimeout).map(b => (a, b))
      }
    }
  }
}

class FutureList[+A](list: List[Future[A]]) extends Future[List[A]] {
  def isEmpty = list.isEmpty
  def head = list.head
  def firstOption = list.firstOption
  def tail = new FutureList(list.tail)

  def respond(k: List[A] => Unit) {
    if (isEmpty) k(Nil)
    else {
      head.respond { a =>
        tail.respond { b =>
          k(a :: b)
        }
      }
    }
  }
  
  override def apply(timeout: Duration): Option[List[A]] = {
    if (isEmpty) Some(Nil)
    else {
      val startTime = System.currentTimeMillis
      head(timeout) flatMap { a =>
        val remainingTimeout = timeout - new Duration(System.currentTimeMillis - startTime)
        tail(remainingTimeout).map(b => a :: b)
      }
    }
  }

  def ::[B >: A](head: Future[B]) = new FutureList(head :: list)
}

object FutureNil extends FutureList(Nil)

object Promise {
  class ImmutableResult(message: String) extends Exception(message)
}

class Promise[A] extends Future[A] {
  import Promise._

  private var result: Option[A] = None
  private val computations = new ArrayBuffer[A => Unit]

  def update(result: A) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  def updateIfEmpty(newResult: A) = {
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

  def respond(k: A => Unit) {
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
