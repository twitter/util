package com.twitter.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.funsuite.AnyFunSuite

class ExecutorWorkQueueFiberTest extends AnyFunSuite {

  private class NoOpMetrics extends WorkQueueFiber.FiberMetrics {
    def flushIncrement(): Unit = ()
    def fiberCreated(): Unit = ()
    def threadLocalSubmitIncrement(): Unit = ()
    def taskSubmissionIncrement(): Unit = ()
    def schedulerSubmissionIncrement(): Unit = ()
  }

  private class TestPool(exec: ExecutorService) extends Executor {
    val poolSubmitCount = new AtomicInteger(0)
    override def execute(command: Runnable): Unit = {
      poolSubmitCount.incrementAndGet()
      exec.execute(command)
    }

    def shutdown() = exec.shutdown()
  }

  test("fiber submits tasks to underlying future pool in single runnable") {
    val pool = new TestPool(Executors.newFixedThreadPool(1))
    val fiber = new ExecutorWorkQueueFiber(pool, new NoOpMetrics)

    val latch = new CountDownLatch(1)
    val N = 5
    for (_ <- 0 until N) {
      fiber.submitTask { () =>
        // wait to finish task execution so that all task submissions queue up in
        // the fiber and are submitted to the pool as a single runnable
        latch.await()
      }
    }
    latch.countDown()
    assert(pool.poolSubmitCount.get() == 1)

    pool.shutdown()
  }

  test("fiber submits tasks individually to underlying future pool") {
    val pool = new TestPool(Executors.newFixedThreadPool(1))
    val fiber = new ExecutorWorkQueueFiber(pool, new NoOpMetrics)

    val N = 5
    for (_ <- 0 until N) {
      val latch = new CountDownLatch(1)
      fiber.submitTask { () =>
        latch.countDown()
      }
      // wait for the task to finish executing so that the next task gets its own
      // submission to the pool
      latch.await()
      // give the executing thread some time to finish up resource tracking and fully
      // exit out of its work loop
      Thread.sleep(1)
    }
    assert(pool.poolSubmitCount.get() == N)

    pool.shutdown()
  }
}
