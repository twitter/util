package com.twitter.concurrent

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.util.{Try, Future, Promise}

/**
 * A trait ensure ordered, non-interleaving operations. Serialized is analogous
 * to the JVM's synchronized operation, except that it is non-blocking. If you
 * cannot acquire the "lock" items are queued.
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
