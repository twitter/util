package com.twitter.util

import com.twitter.concurrent.LocalScheduler
import java.util.concurrent.CountDownLatch
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.Seconds
import org.scalatest.time.Span

class SchedulerWorkQueueFiberTest extends AnyFunSuite {

  private class NoOpMetrics extends WorkQueueFiber.FiberMetrics {
    def flushIncrement(): Unit = ()
    def fiberCreated(): Unit = ()
    def threadLocalSubmitIncrement(): Unit = ()
    def taskSubmissionIncrement(): Unit = ()
    def schedulerSubmissionIncrement(): Unit = ()
  }

  test("fiber flushes the underlying LocalScheduler") {
    val flushLatch = new CountDownLatch(1)
    val localScheduler = new LocalScheduler {
      override def flush(): Unit = {
        super.flush()
        flushLatch.countDown()
      }
    }
    val localSchedulerFiber = new SchedulerWorkQueueFiber(localScheduler, new NoOpMetrics)

    var promiseSatisfied = false

    val runnable = new Runnable {
      def run(): Unit = {
        val p = Promise[Boolean]()
        val f2 = new SchedulerWorkQueueFiber(localScheduler, new NoOpMetrics)
        // Satisfy the promise on another fiber, whose work will get scheduled on the same thread
        f2.submitTask(() => p.setValue(true))

        // This will trigger a flush of the current fiber and the local scheduler
        Await.result(p)
        promiseSatisfied = true
      }
    }

    val t = new Thread({ () =>
      Fiber.let(localSchedulerFiber) {
        localSchedulerFiber.submitTask(() => runnable.run())
      }
    })
    t.start()

    // Ensure flush completed
    flushLatch.await()

    // The promise should be satisfied even though the second fiber was scheduled on the
    // same thread as the Await.result
    eventually(Timeout(Span(1, Seconds))) { assert(promiseSatisfied) }
  }
}
