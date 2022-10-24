package com.twitter.util

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec

/**
 * An experimental [[Fiber]] implementation that has its own internal work queue
 *
 * This `Fiber` abstraction is intended to allow work associated with the fiber to have
 * a common execution context while not defining the specific thread model that will execute
 * this `Fiber`s work queue.
 *
 * The work queue is actually two work queues: one that accepts tasks submitted by this thread
 * and one that is thread safe. This is to optimize for the common case where work can immediate
 * finish other work and immediately trigger another task. This lets us avoid the overhead
 * of using a thread safe queue in a very common case.
 *
 * @note that priority is given to the work submitted by this thread.
 *
 * @param fiberMetrics telemetry used for tracking the execution of this fiber, and likely
 *                     other fibers as well.
 */
private[twitter] abstract class WorkQueueFiber(fiberMetrics: WorkQueueFiber.FiberMetrics)
    extends Fiber {

  fiberMetrics.fiberCreated()

  // The thread safe queue that we can submit work to
  private[this] val multiThreadedQueue = new ConcurrentLinkedQueue[FiberTask]

  // Flag used to ensure that at most one thread is processing this fibers work queue
  // at any given time.
  private[this] val executing = new AtomicBoolean(false)

  // State used for the non-thread safe queue. This is a micro opt to make
  // it so we can quickly trampoline tasks, potentially without allocating.
  private[this] var r0, r1, r2: FiberTask = null
  private[this] val rs = new ArrayDeque[FiberTask](0 /* effectively 8 due to impl */ )

  // This is thread safe. Why?
  // * We only care if the value stored is *this* thread.
  // * A thread will only ever set the field to itself when beginning execution
  //     and unsets the field on exiting the submission loop.
  //
  // So if we see a value that is equal to `Thread.currentThread`, it must
  // be *this* thread that set it, and if it is *this* thread that set it, the access has been
  // single threaded all along.
  private[this] var executingThread: Thread = null

  private[this] val workRunnable: Runnable = { () => startWork() }

  /**
   * Submit the work to an actual thread for execution
   *
   * @note that we require this method call to be thread safe such that the memory updates
   *       from the thread that calls this method will be visible to the thread that begins
   *       the execution of `r`. This is generally true of thread-safe execution of `Runnable`s.
   */
  protected def schedulerSubmit(r: Runnable): Unit

  final def submitTask(r: FiberTask): Unit = {
    fiberMetrics.taskSubmissionIncrement()
    // Check if anybody is running this already before getting a reference to the current thread.
    if ((executingThread ne null) && (executingThread eq Thread.currentThread())) {
      fiberMetrics.threadLocalSubmitIncrement()
      threadLocalSubmit(r)
    } else multiThreadedSubmit(r)
  }

  /**
   * If necessary, execute any scheduler specific flushing.
   */
  protected def schedulerFlush(): Unit

  /**
   * If necessary, drain any thread specific state and resubmit the work queue.
   *
   * @note This is in service of blocking calls which are not desirable in async code
   *       but sometimes you gotta do what you gotta do. It should be noted that in the
   *       absence of `flush()` calls this fiber can guarantee that tasks are executed
   *       serially, eg one task is fully completed before the next is begun. However,
   *       a `flush()` call will necessarily shift the work potentially to a separate
   *       thread allowing all the code after a `flush()` call to potentially run parallel
   *       with other work submitted to the fiber.
   */
  final def flush(): Unit = {
    if (Thread.currentThread() eq executingThread) {
      fiberMetrics.flushIncrement()
      // We need to move all our work into a new `schedulerSubmit` call. That means moving all
      // our thread local work into the thread-safe queue and resubmitting this fiber if there
      // are still tasks queued.
      while (localHasNext) {
        multiThreadedQueue.add(localNext())
      }

      // Set this thread to non-executing and then try to re-submit ourselves, if necessary.
      // Someone else might re-submit first, which is fine.
      //
      // Note: that we _must_ de-schedule ourselves in the case that the work we're waiting
      // on belongs to this fiber but is completed later, eg, it wasn't already scheduled.
      executingThread = null
      executing.set(false)

      if (!multiThreadedQueue.isEmpty) {
        // There is fiber work that needs to be executed. Attempt to reschedule.
        tryScheduleFiber()
      }
    }
    // Depending on the underlying execution engine, we may need to flush the scheduler as well.
    // Special consideration will need to be taken for CPU usage tracking if that is the case.
    // We can work this all out in the future.
    schedulerFlush()
  }

  private[this] def threadLocalSubmit(r: FiberTask): Unit = {
    if (r0 == null) r0 = r
    else if (r1 == null) r1 = r
    else if (r2 == null) r2 = r
    else rs.addLast(r)
  }

  private[this] def multiThreadedSubmit(r: FiberTask): Unit = {
    multiThreadedQueue.add(r)
    tryScheduleFiber()
  }

  private[this] def tryScheduleFiber(): Unit = {
    if (!executing.getAndSet(true)) {
      // Lucky us! We're the ones that submit to the scheduler.
      fiberMetrics.schedulerSubmissionIncrement()
      schedulerSubmit(workRunnable)
    }
  }

  /** Start executing the work queue.
   *
   * Invoking this will execute all the tasks in both the local and multi-threaded work queues.
   */
  private[this] def startWork(): Unit = {

    // This check serves two purposes. First, it guarantees that all updates to the non-thread
    // safe state are visible to this thread because every time we exit the `startWork` loop we
    // end by setting this state to false, and this thread reading it is a happens-before. We
    // should also get that through the `schedulerSubmit` calls but this leaves no doubt.
    // If it shows up as a performance bottleneck then we can consider removing it.
    //
    // Second, failure to see the expected state represents a very serious error: if executing
    // is not true then something has gone catastrophically wrong.
    if (!executing.get) {
      throw new IllegalStateException("Fiber entered startWork loop with execution flag false")
    }

    val currentThread = Thread.currentThread()
    executingThread = currentThread

    // A thread just starting work should find two things: that the thread local queue is empty
    // and that the multi-threaded queue is non-empty.
    assert(!localHasNext)
    assert(!multiThreadedQueue.isEmpty)

    // Expects that the first thing to do is execute a thread local task.
    @tailrec def go(n: FiberTask): Unit = {
      n.doRun()
      // If we're not the executing thread anymore it is because a task has "flushed" us.
      // If we've been flushed then we just need to exit now. The `flush()` method will
      // take care of the state management.
      if (executingThread eq currentThread) {
        // Get our next task, if it exists.
        var nn = localNext()
        if (nn == null) {
          nn = multiThreadedQueue.poll()
        }

        if (nn != null) go(nn)
        else {
          // We found our local queue and multi-threaded queues both empty. We now go about
          // exiting this fiber submission.

          // We set `executingThread` to null before unsetting our executing flag to ensure
          // that it is always null when entering this method. Otherwise we might race between
          // unsetting the flag, thread pause, another thread submits to the scheduler,
          // and finally this thread gets around to unsetting.
          executingThread = null
          executing.set(false)

          // Now we need to double check that someone didn't submit a task and not submit
          // it to the scheduler because this loop was running.
          if (!multiThreadedQueue.isEmpty && !executing.getAndSet(true)) {
            executingThread = currentThread
            go(multiThreadedQueue.poll())
          }
        }
      }
    }

    go(multiThreadedQueue.poll())
  }

  @inline private[this] def localHasNext: Boolean = r0 != null

  @inline private[this] def localNext(): FiberTask = {
    // via moderately silly benchmarking, the
    // queue unrolling gives us a ~50% speedup
    // over pure Queue usage for common
    // situations.

    val r = r0
    r0 = r1
    r1 = r2
    if (r2 != null) {
      // If r2 was null then rs must be empty and no need to consult the
      // heavy(ish) data structure.
      r2 = rs.poll()
    }
    r
  }
}

private object WorkQueueFiber {

  /** Telemetry used to monitor [[WorkQueueFiber]]s */
  abstract class FiberMetrics {

    /** Called when a new [[WorkQueueFiber]] is created */
    def fiberCreated(): Unit

    /** Called when a new task is submitted to a [[WorkQueueFiber]] via the thread-local queue */
    def threadLocalSubmitIncrement(): Unit

    /** Called when a task is submitted to a [[WorkQueueFiber]] */
    def taskSubmissionIncrement(): Unit

    /** Called when a [[WorkQueueFiber]] is submitted for execution */
    def schedulerSubmissionIncrement(): Unit

    /**
     * Called when a [[Fiber]] performs a flush
     *
     * @note not invoked if the flush() call is a no-op
     */
    def flushIncrement(): Unit
  }
}
