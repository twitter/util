package com.twitter.util

/**
 * An abstract [[ProxyTimer]] that provides callback methods which are called when
 * a task takes longer than the specified maximum runtime or if a task is observed
 * to be taking longer than the specified maximum runtime.
 *
 * @note Observation of slow task execution is performed when scheduling more work to
 *       avoid the overhead of another thread or timer checking on tasks. This results
 *       in lower overhead but means that slow running tasks may not be observed while
 *       executing. However, they will trigger a callback to the `slowTaskCompleted`
 *       regardless of whether additional work is scheduled.
 *
 * @note This makes assumptions that the underlying `Timer` will execute tasks
 *       sequentially in order to catch slow running tasks during execution. If the
 *       underlying `Timer` executes tasks in parallel the callback `slowTaskExecuting`
 *       will become unreliable. However, the `slowTaskCompleted` callback will remain
 *       reliable but must be a thread-safe implementation.
 */
abstract class SlowProbeProxyTimer(maxRuntime: Duration) extends ProxyTimer {

  /**
   * Called when a task takes longer than the specified maximum duration
   */
  protected def slowTaskCompleted(elapsed: Duration): Unit

  /**
   * Called when a task is observed to be executing longer than the specified
   * maximum duration
   */
  protected def slowTaskExecuting(elapsed: Duration): Unit

  @volatile
  private[this] var lastStartAt = Time.Top

  // let another thread check if the timer thread has been slow.
  // while this could be the timer thread scheduling more work,
  // we expect that at some point another thread will schedule something.
  // while this relies on application's doing scheduling to trigger
  // the findings, we expect that to be common. the alternative would've
  // been to use a separate dedicated thread, but the cost didn't seem
  // worth the benefits to me.
  override protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = {
    checkSlowTask()
    self.schedule(when)(meterTask(f))
  }

  override protected def schedulePeriodically(
    when: Time,
    period: Duration
  )(
    f: => Unit
  ): TimerTask = {
    checkSlowTask()
    self.schedule(when, period)(meterTask(f))
  }

  private[this] def checkSlowTask(): Unit = {
    val elapsed = Time.now - lastStartAt
    if (elapsed > maxRuntime) slowTaskExecuting(elapsed)
  }

  private[this] def meterTask(f: => Unit): Unit = {
    // mark this task as started, then finished in a finally block.
    val started = Time.now
    lastStartAt = started
    try f
    finally {
      val elapsed = Time.now - started
      lastStartAt = Time.Top
      if (elapsed > maxRuntime) slowTaskCompleted(elapsed)
    }
  }

  override def toString: String = s"${getClass.getSimpleName}($self)"
}
