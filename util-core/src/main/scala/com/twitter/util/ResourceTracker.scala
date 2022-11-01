package com.twitter.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

private[twitter] object ResourceTracker {
  private[this] final val threadMxBean = java.lang.management.ManagementFactory.getThreadMXBean()

  /**
   * An indicator if the running JDK supports querying thread statistics.
   */
  final val threadCpuTimeSupported = threadMxBean.isCurrentThreadCpuTimeSupported

  /**
   * Retreive the current thread's cpu time only if [[threadCpuTimeSupported]] is true.
   * Otherwise, -1 is returned. Results may vary depending on the JDK's management implementation
   */
  final def currentThreadCpuTime: Long = {
    if (threadCpuTimeSupported) threadMxBean.getCurrentThreadCpuTime
    else -1
  }

  /**
   * Wrap the invocation of `f` with low-level resource usage that's pushed to the `tracker`.
   */
  private[util] def wrapAndMeasureUsage[A, B](
    f: A => B,
    tracker: ResourceTracker
  ): A => B = { (result) =>
    {
      tracker.incContinuations()
      val cpuStart = threadMxBean.getCurrentThreadCpuTime
      try f(result)
      finally {
        val cpuTime = threadMxBean.getCurrentThreadCpuTime - cpuStart
        tracker.addCpuTime(cpuTime)
      }
    }
  }

  /**
   * Wrap the invocation of `f` with low-level resource usage that's pushed to the `tracker`.
   */
  private[util] def wrapAndMeasureUsage[T](
    f: => T,
    tracker: ResourceTracker
  ): () => T = { () =>
    {
      tracker.incContinuations()
      val cpuStart = threadMxBean.getCurrentThreadCpuTime
      try f
      finally {
        val cpuTime = threadMxBean.getCurrentThreadCpuTime - cpuStart
        tracker.addCpuTime(cpuTime)
      }
    }
  }

  // exposed for testing
  private[util] def let[T](tracker: ResourceTracker)(f: => T): T = {
    val oldCtx = Local.save()
    val newCtx = oldCtx.setResourceTracker(tracker)
    Local.restore(newCtx)
    try f
    finally Local.restore(oldCtx)
  }

  // exposed for testing
  private[util] def set(tracker: ResourceTracker): Unit = {
    val oldCtx = Local.save()
    val newCtx = oldCtx.setResourceTracker(tracker)
    Local.restore(newCtx)
  }

  // exposed for testing
  private[util] def clear(): Unit = {
    val oldCtx = Local.save()
    val newCtx = oldCtx.removeResourceTracker()
    if (newCtx ne oldCtx) Local.restore(newCtx)
  }

  /**
   * Track the execution of completed [[Future]]s within `f`.
   */
  def apply[A](f: ResourceTracker => A) = {
    val accumulator = new ResourceTracker()
    let(accumulator)(f(accumulator))
  }
}

/**
 * A class used as the value for the local [[ResourceTracker#resourceTracker]] that's updated
 * with low level resource usage as [[Promise]] continuations are executed.
 *
 * @note some measurements such as the cpu time relies on the JDK's implementation of the
 * management interfaces. This means that the results may vary between JDK implementations.
 */
private[twitter] class ResourceTracker {
  private[this] val cpuTime: LongAdder = new LongAdder()
  private[this] val continuations: AtomicInteger = new AtomicInteger()

  private[util] def addCpuTime(time: Long): Unit = cpuTime.add(time)
  private[util] def incContinuations(): Unit = continuations.getAndIncrement()
  private[util] def addContinuations(count: Int): Unit = continuations.getAndAdd(count)

  /**
   * Retrieve the total accumulated CPU time
   */
  def totalCpuTime(): Long = cpuTime.sum()

  /**
   * Retrieve the total number of executed continuations
   */
  def numContinuations(): Int = continuations.get()
}
