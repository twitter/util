package com.twitter.util

import com.twitter.concurrent.Scheduler

/**
 * A Fiber receives runnable tasks and submits them to a Scheduler for execution.
 */
private[twitter] abstract class Fiber {

  /** Submit work to the Fiber */
  def submitTask(r: FiberTask): Unit
}

private[twitter] object Fiber {

  // Global default fiber which solely submits tasks to the global Scheduler
  val Global: Fiber = new Fiber {
    override def submitTask(r: FiberTask): Unit = {
      Scheduler.submit(r)
    }
  }

  def let[T](fiber: Fiber)(f: => T): T = {
    val oldCtx = Local.save()
    val newCtx = oldCtx.setFiber(fiber)
    Local.restore(newCtx)
    try f
    finally Local.restore(oldCtx)
  }
}
