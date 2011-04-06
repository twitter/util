package com.twitter.concurrent

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * A ThreadFactory which creates non-daemon threads with a name indicating which pool they came
 * from.
 *
 * A new ThreadGroup is created with the passed-in name, as a sub-group of whichever thread
 * creates the factory. Each new thread will be a member of this group, and have a unique name
 * including the group name and an increasing number. The idea is to make it easy to identify
 * members of a thread group in debug output.
 *
 * For example, a NamedPoolThreadFactory with the name "writer" will create a ThreadGroup named
 * "writer" and new threads will be named "writer-1", "writer-2", etc.
 */
class NamedPoolThreadFactory(name: String, makeDaemons: Boolean) extends ThreadFactory {
  def this(name: String) = this(name, false)

  val group = new ThreadGroup(Thread.currentThread().getThreadGroup(), name)
  val threadNumber = new AtomicInteger(1)

  def newThread(r: Runnable) = {
    val thread = new Thread(group, r, name + "-" + threadNumber.getAndIncrement())
    thread.setDaemon(makeDaemons)
    if (thread.getPriority != Thread.NORM_PRIORITY) {
      thread.setPriority(Thread.NORM_PRIORITY)
    }
    thread
  }
}
