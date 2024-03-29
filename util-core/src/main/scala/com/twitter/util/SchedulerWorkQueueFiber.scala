package com.twitter.util

import com.twitter.concurrent.Scheduler

/**
 * A [[WorkQueueFiber]] implementation that submits its work queue to a scheduler.
 *
 * This allows us to gain the benefits of the WorkQueueFiber, namely the bundling of work
 * into Runnables of multiple tasks, while still utilizing the global scheduler. This
 * implementation of the WorkQueueFiber is meant for servers that are not well suited
 * for offloading tasks into a thread pool for execution.
 *
 * @param scheduler the task scheduler that will execute tasks.
 * @param fiberMetrics telemetry used for tracking the execution of this fiber, and likely
 *                     other fibers as well.
 */
private[twitter] final class SchedulerWorkQueueFiber(
  scheduler: Scheduler,
  fiberMetrics: WorkQueueFiber.FiberMetrics)
    extends WorkQueueFiber(fiberMetrics) {

  /**
   * Submit the work to the underlying scheduler for execution.
   */
  override protected def schedulerSubmit(r: Runnable): Unit = scheduler.submit(r)

  /**
   * Flush the underlying scheduler.
   */
  override protected def schedulerFlush(): Unit = scheduler.flush()
}
