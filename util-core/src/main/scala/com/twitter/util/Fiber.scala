package com.twitter.util

import com.twitter.concurrent.Scheduler

/**
 * A Fiber receives runnable tasks and submits them to a Scheduler for execution.
 */
private[twitter] abstract class Fiber {

  /** Submit work to the Fiber */
  def submitTask(r: FiberTask): Unit

  /** Flush outstanding tasks */
  def flush(): Unit
}

private[twitter] object Fiber extends Fiber {

  // Global default fiber which solely submits tasks to the global Scheduler
  val Global: Fiber = new Fiber {
    override def submitTask(r: FiberTask): Unit = {
      Scheduler.submit(r)
    }
    override def flush(): Unit = {
      Scheduler.flush()
    }
  }

  // Create fiber that captures the current Scheduler to avoid the volatile lookup on each
  // submission
  def newCachedSchedulerFiber(): Fiber = new Fiber {
    // Just cache it so we don't have to keep looking at the volatile var each time.
    private val scheduler = Scheduler()
    def submitTask(r: FiberTask): Unit = {
      scheduler.submit(r)
    }
    override def flush(): Unit = {
      scheduler.flush()
    }
  }

  def let[T](fiber: Fiber)(f: => T): T = {
    val oldCtx = Local.save()
    val newCtx = oldCtx.setFiber(fiber)
    Local.restore(newCtx)
    try f
    finally Local.restore(oldCtx)
  }

  // Submit task to the fiber stored in the Local Context
  def submitTask(r: FiberTask): Unit = Local.save().fiber.submitTask(r)

  // Flush fiber stored in the Local Context
  def flush(): Unit = Local.save().fiber.flush()
}
