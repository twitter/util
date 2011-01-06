package com.twitter.concurrent

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

trait Serialized {
  private[this] val nwaiters = new AtomicInteger(0)
  protected val serializedQueue: java.util.Queue[() => Unit] =
    new ConcurrentLinkedQueue[() => Unit]

  def serialized[T](f: T => Unit): T => Unit = { x => serialized { f(x) } }
  def serialized(f: => Any) {
    serializedQueue add { () => f }

    if (nwaiters.getAndIncrement() == 0) {
      do {
        serializedQueue.remove()()
      } while (nwaiters.decrementAndGet() > 0)
    }
  }
}
