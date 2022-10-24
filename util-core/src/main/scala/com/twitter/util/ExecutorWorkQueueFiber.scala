package com.twitter.util

import java.util.concurrent.Executor

/**
 * A [[WorkQueueFiber]] implementation that submits its work queue to the underlying
 * executor.
 *
 * This is intended to be used with a thread pool executor that manages task execution
 * across threads. The ExecutorWorkQueueFiber pairs up a thread pool and a WorkQueueFiber
 * in order to act as a scheduler and handle all task scheduling responsibilities
 * for a server.
 */
private[twitter] final class ExecutorWorkQueueFiber(
  pool: Executor,
  metrics: WorkQueueFiber.FiberMetrics)
    extends WorkQueueFiber(metrics) {

  /**
   * Submit the work to the underlying executor.
   */
  override protected def schedulerSubmit(r: Runnable): Unit = pool.execute(r)

  /**
   * No need to flush. Thread pools usually do not have per thread queues, and any that do
   * should have some sort of work stealing mechanism to pass tasks around between threads.
   */
  override protected def schedulerFlush(): Unit = ()
}
