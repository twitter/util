package com.twitter.util

import com.twitter.conversions.DurationOps.richDurationFromInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ListBuffer

class WorkQueueFiberTest extends AnyFunSuite {

  private def await[T](t: Awaitable[T]): T = Await.result(t, 10.seconds)

  private class TestMetrics extends WorkQueueFiber.FiberMetrics {

    val fiberFlushed, fibersCreated, threadLocalSubmits, taskSubmissions, schedulerSubmissions =
      new AtomicInteger()

    def flushIncrement(): Unit = fiberFlushed.incrementAndGet()

    def fiberCreated(): Unit = fibersCreated.incrementAndGet()
    def threadLocalSubmitIncrement(): Unit = threadLocalSubmits.incrementAndGet()
    def taskSubmissionIncrement(): Unit = taskSubmissions.incrementAndGet()
    def schedulerSubmissionIncrement(): Unit = schedulerSubmissions.incrementAndGet()
  }

  private class TestFiber(metrics: TestMetrics) extends WorkQueueFiber(metrics) {

    var runnable: Runnable = null

    def executePendingRunnable(): Unit = {
      assert(runnable != null)
      val r = runnable
      runnable = null
      r.run()
    }

    protected def schedulerSubmit(r: Runnable): Unit = {
      assert(runnable == null)
      runnable = r
    }

    override protected def schedulerFlush(): Unit = ()
  }

  test("fiber creations are tracked") {
    val N = 5
    val metrics = new TestMetrics
    for (_ <- 0 until N) {
      new TestFiber(metrics)
    }
    assert(metrics.fibersCreated.get == N)
  }

  test("submit multiple tasks while fiber not running") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    assert(metrics.fibersCreated.get == 1)

    val executed = new AtomicInteger()

    val N = 5
    for (_ <- 0 until N) {
      f.submitTask { () =>
        executed.incrementAndGet()
      }
    }

    assert(f.runnable != null)
    assert(metrics.threadLocalSubmits.get == 0)
    assert(metrics.taskSubmissions.get == N)
    assert(metrics.schedulerSubmissions.get == 1)
    assert(executed.get == 0)

    f.executePendingRunnable()
    assert(executed.get == N)
  }

  test("submit a thread local task") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    assert(metrics.fibersCreated.get == 1)

    val executed = new AtomicInteger()
    val submissionOrder = new ListBuffer[Int]

    f.submitTask { () =>
      executed.incrementAndGet()

      // This one should be submitted thread-locally.
      f.submitTask { () =>
        executed.incrementAndGet()
        submissionOrder += 1
      }

      // fiber task 1 should complete all the way through before we execute the sub-task
      submissionOrder += 0
    }

    assert(f.runnable != null)
    assert(metrics.threadLocalSubmits.get == 0)
    assert(metrics.taskSubmissions.get == 1)
    assert(metrics.schedulerSubmissions.get == 1)
    assert(executed.get == 0)

    f.executePendingRunnable()
    assert(metrics.threadLocalSubmits.get == 1)
    assert(metrics.taskSubmissions.get == 2)
    assert(executed.get == 2)
    assert(submissionOrder.result() == List(0, 1))

    // Now throw in one more to make sure we don't get a thread-local submit since the fiber
    // is clearly not executing.
    f.submitTask { () =>
      executed.incrementAndGet()
      submissionOrder += 2
    }

    assert(metrics.threadLocalSubmits.get == 1)
    assert(metrics.taskSubmissions.get == 3)
    assert(metrics.schedulerSubmissions.get == 2)
    f.executePendingRunnable()

    assert(executed.get == 3)
    assert(submissionOrder.result() == List(0, 1, 2))
  }

  test("submit a task while a separate thread is executing the fiber") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    assert(metrics.fibersCreated.get == 1)

    val taskAwait, taskExecuting = new CountDownLatch(1)

    f.submitTask { () =>
      taskExecuting.countDown()
      taskAwait.await()
    }

    assert(f.runnable != null)
    assert(metrics.threadLocalSubmits.get == 0)
    assert(metrics.taskSubmissions.get == 1)
    assert(metrics.schedulerSubmissions.get == 1)

    // Start the fiber in a separate thread
    val t = new Thread({ () =>
      f.executePendingRunnable()
    })
    t.start()

    taskExecuting.await()

    val executingThread = new Promise[Thread]()
    f.submitTask { () =>
      executingThread.setValue(Thread.currentThread)
    }

    assert(metrics.schedulerSubmissions.get == 1)
    assert(metrics.taskSubmissions.get == 2)
    assert(metrics.threadLocalSubmits.get == 0)
    assert(!executingThread.isDefined)

    taskAwait.countDown()
    assert(await(executingThread) == t)
  }

  test("flushing re-submits to the scheduler") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    val latch1, latch2, latch3, flushed = new CountDownLatch(1)

    // This will eventually be the test thread so we don't need it to be volatile.
    var threadLocalSubmissionsExecutionThread: Thread = null
    var lastTaskDone = false
    f.submitTask { () =>
      latch1.countDown()
      latch2.await()
      f.submitTask { () =>
        threadLocalSubmissionsExecutionThread = Thread.currentThread()
      }

      // Now flush and continue to suspend the thread.
      assert(metrics.fiberFlushed.get == 0)
      f.flush()
      assert(metrics.fiberFlushed.get == 1)
      flushed.countDown()
      latch3.await()

      // schedule one more task that should not be a thread local submit because flush
      // voided that.
      f.submitTask { () =>
        lastTaskDone = true
      }
    }

    assert(metrics.taskSubmissions.get == 1)

    val t = new Thread({ () =>
      f.executePendingRunnable()
    })
    t.start()

    latch1.await()

    // We'll run this one in the test thread but it's going to be submitted as a non-thread local
    var done = false
    f.submitTask { () =>
      done = true
    }

    // Release thread t and then stall t right after the flush
    latch2.countDown()
    flushed.await()

    assert(metrics.taskSubmissions.get == 3)
    assert(metrics.threadLocalSubmits.get == 1)
    assert(metrics.schedulerSubmissions.get == 2)
    assert(threadLocalSubmissionsExecutionThread == null)
    assert(!done)

    // We should now have 2 tasks awaiting whoever executes the next submission. Now run it.
    f.executePendingRunnable()
    assert(threadLocalSubmissionsExecutionThread == Thread.currentThread())
    assert(done)

    // Now let our thread submit it's final task which shouldn't be a thread local task even
    // though it's submitted from within a FiberTask.
    latch3.countDown()
    t.join()

    assert(metrics.taskSubmissions.get == 4)
    assert(metrics.threadLocalSubmits.get == 1)
    assert(metrics.schedulerSubmissions.get == 3)
    assert(!lastTaskDone)

    f.executePendingRunnable()
    assert(lastTaskDone)
  }

  test("flushing to a recursive scheduler") {
    val metrics = new TestMetrics
    val f = new WorkQueueFiber(metrics) {
      override protected def schedulerSubmit(r: Runnable): Unit = {
        r.run()
      }
      override protected def schedulerFlush(): Unit = ()
    }

    f.submitTask { () =>
      assert(metrics.taskSubmissions.get == 1)
      assert(metrics.schedulerSubmissions.get == 1)
      assert(metrics.threadLocalSubmits.get == 0)

      var threadLocalSubmit = false
      f.submitTask { () =>
        threadLocalSubmit = true
      }
      assert(metrics.taskSubmissions.get == 2)
      assert(metrics.schedulerSubmissions.get == 1)
      assert(metrics.threadLocalSubmits.get == 1)

      assert(!threadLocalSubmit)
      assert(metrics.fiberFlushed.get == 0)
      f.flush()
      assert(metrics.fiberFlushed.get == 1)
      assert(threadLocalSubmit)
      assert(metrics.taskSubmissions.get == 2)
      assert(metrics.schedulerSubmissions.get == 2)
      assert(metrics.threadLocalSubmits.get == 1)

      // After the flush() call, even recursive calls should act like an external submission.
      var shouldExecuteNow = false
      f.submitTask { () =>
        shouldExecuteNow = true
      }
      assert(shouldExecuteNow)
      assert(metrics.taskSubmissions.get == 3)
      assert(metrics.schedulerSubmissions.get == 3)
      assert(metrics.threadLocalSubmits.get == 1)
    }
  }

  test("thread-local flush with empty work queues still de-schedules itself") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    var executed = false
    f.submitTask { () =>
      assert(metrics.taskSubmissions.get == 1)
      assert(metrics.schedulerSubmissions.get == 1)
      assert(metrics.threadLocalSubmits.get == 0)
      assert(metrics.fiberFlushed.get == 0)

      f.flush()

      // At this point we should be as if we're not actually executing in the fiber.

      assert(metrics.taskSubmissions.get == 1)
      assert(metrics.schedulerSubmissions.get == 1)
      assert(metrics.threadLocalSubmits.get == 0)
      assert(metrics.fiberFlushed.get == 1)

      // This will get added to the end of the queue and we'll run it without
      // needing a resubmit.
      f.submitTask { () =>
        executed = true
      }

      assert(!executed)
      assert(metrics.taskSubmissions.get == 2)
      assert(metrics.schedulerSubmissions.get == 2)
      assert(metrics.threadLocalSubmits.get == 0)
      assert(metrics.fiberFlushed.get == 1)
    }

    f.executePendingRunnable()
    // Still not executed. Even though the submission was done inside a FiberTask
    // the flush() had caused the task to no longer see itself as the executor of
    // the fiber.
    assert(!executed)

    f.executePendingRunnable()
    assert(executed)
  }

  test("a flush outside the executing code is a no-op") {
    val metrics = new TestMetrics
    val f = new TestFiber(metrics)

    var executed = false
    f.submitTask { () =>
      executed = true
    }

    assert(metrics.taskSubmissions.get == 1)
    assert(metrics.schedulerSubmissions.get == 1)
    assert(metrics.threadLocalSubmits.get == 0)
    assert(metrics.fiberFlushed.get == 0)

    f.flush()
    assert(metrics.fiberFlushed.get == 0)
    assert(!executed)

    f.executePendingRunnable()
    assert(executed)
  }
}
