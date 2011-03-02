package com.twitter.concurrent

import java.util.ArrayDeque

import com.twitter.util.{Future, Promise, Return}

class AsyncMutex {
  private[this] var busy = false
  private[this] val waiters = new ArrayDeque[Promise[() => Unit]]

  private[this] def dequeueWaiters(): Unit = synchronized {
    val waiter = waiters.poll()
    if (waiter ne null) {
      busy = true
      waiter() = Return(() => { busy = false; dequeueWaiters() })
    }
  }

  def acquire(): Future[() => Unit] = synchronized {
    val future = new Promise[() => Unit]
    waiters offer future
    if (!busy) dequeueWaiters()
    future
  }
}
