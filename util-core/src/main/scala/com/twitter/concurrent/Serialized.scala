package com.twitter.concurrent

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.util.{Try, Future, Promise}

/**
 * Efficient ordered ''serialization'' of operations.
 *
 * '''Note:''' This should not be used in place of Scala's
 * `synchronized`, but rather only when serialization semantics are
 * required.
 */
trait Serialized {
  protected case class Job[T](promise: Promise[T], doItToIt: () => T) {
    def apply() {
      promise.update { Try { doItToIt() } }
    }
  }

  private[this] val nwaiters = new AtomicInteger(0)
  protected val serializedQueue: java.util.Queue[Job[_]] = new ConcurrentLinkedQueue[Job[_]]

  protected def serialized[A](f: => A): Future[A] = {
    val result = new Promise[A]

    serializedQueue add { Job(result, () => f) }

    if (nwaiters.getAndIncrement() == 0) {
      do {
        Try { serializedQueue.remove()() }
      } while (nwaiters.decrementAndGet() > 0)
    }

    result
  }
}
