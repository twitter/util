package com.twitter.concurrent

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{Future, Promise, Try}

/**
 * Efficient ordered ''serialization'' of operations.
 *
 * '''Note:''' This should not be used in place of Scala's
 * `synchronized`, but rather only when serialization semantics are
 * required.
 */
trait Serialized {
  protected case class Job[T](promise: Promise[T], doItToIt: () => T) {
    def apply(): Unit = {
      promise.update { Try { doItToIt() } }
    }
  }

  private[this] val nwaiters: AtomicInteger = new AtomicInteger(0)
  protected val serializedQueue: java.util.Queue[Job[_]] = new ConcurrentLinkedQueue[Job[_]]

  protected def serialized[A](f: => A): Future[A] = {
    val result = new Promise[A]

    serializedQueue add { Job(result, () => f) }

    if (nwaiters.getAndIncrement() == 0) {
      Try { serializedQueue.remove()() }
      while (nwaiters.decrementAndGet() > 0) {
        Try { serializedQueue.remove()() }
      }
    }

    result
  }
}
