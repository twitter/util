package com.twitter.util

import org.specs.Specification
import java.util.concurrent._

object FuturePoolSpec extends Specification {
  val executor = Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
  val pool     = FuturePool(executor)

  "FuturePool" should {
    "dispatch to another thread" in {
      val source = new Promise[Int]
      val result = pool {
        source.get() // simulate blocking call
      }

      source.setValue(1)
      result.get() mustEqual 1
    }

    "does not execute cancelled tasks" in {
      val runCount = new atomic.AtomicInteger

      val source1 = new Promise[Int]
      val source2 = new Promise[Int]

      val result1 = pool { runCount.incrementAndGet(); source1.get() }
      val result2 = pool { runCount.incrementAndGet(); source2.get() }

      result2.cancel()
      source1.setValue(1)

      // The executor will run the task for result 2, but the wrapper
      // in FuturePool will throw away the work if the future
      // representing the outcome has already been cancelled.
      executor.getCompletedTaskCount must eventually(be_==(2))

      runCount.get() mustEqual 1
      result1.get()  mustEqual 1
      result2.isDefined   mustEqual false
      result2.isCancelled mustEqual true
    }
  }
}
