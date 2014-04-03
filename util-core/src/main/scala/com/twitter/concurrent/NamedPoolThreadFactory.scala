package com.twitter.concurrent

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [[java.util.concurrent.ThreadFactory]] which creates threads with a name
 * indicating the pool from which they originated.
 *
 * A new [[java.lang.ThreadGroup]] (named `name`) is created as a sub-group of
 * whichever group to which the thread that created the factory belongs. Each
 * thread created by this factory will be a member of this group and have a
 * unique name including the group name and an monotonically increasing number.
 * The intention of this naming is to ease thread identification in debugging
 * output.
 *
 * For example, a `NamedPoolThreadFactory` with `name="writer"` will create a
 * `ThreadGroup` named "writer" and new threads will be named "writer-1",
 * "writer-2", etc.
 *
 * @param name the name of the new thread group
 * @param makeDaemons determines whether or not this factory will creates
 * daemon threads.
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
