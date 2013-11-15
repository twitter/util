package com.twitter.util

import com.twitter.conversions.time._
import java.util.concurrent.{Future => JFuture}
import java.util.concurrent._
import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import org.mockito.Matchers._

class FuturePoolSpec extends SpecificationWithJUnit with Mockito {
  val executor = Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
  val pool     = FuturePool(executor)

  "FuturePool" should {
    "dispatch to another thread" in {
      val source = new Promise[Int]
      val result = pool { Await.result(source) } // simulate blocking call

      source.setValue(1)
      Await.result(result) mustEqual 1
    }

    "Executor failing contains failures" in {
      val badExecutor = new ScheduledThreadPoolExecutor(1) {
        override def submit(runnable: Runnable): JFuture[_] = {
          throw new RejectedExecutionException()
        }
      }

      val pool = FuturePool(badExecutor)

      val runCount = new atomic.AtomicInteger(0)

      val result1 = pool {
        runCount.incrementAndGet()
      }

      runCount.get() mustEqual 0
    }

    "does not execute interrupted tasks" in {
      val runCount = new atomic.AtomicInteger

      val source1 = new Promise[Int]
      val source2 = new Promise[Int]

      val result1 = pool { runCount.incrementAndGet(); Await.result(source1) }
      val result2 = pool { runCount.incrementAndGet(); Await.result(source2) }

      result2.raise(new Exception)
      source1.setValue(1)

      // The executor will run the task for result 2, but the wrapper
      // in FuturePool will throw away the work if the future
      // representing the outcome has already been interrupted,
      // and will set the result to a CancellationException
      executor.getCompletedTaskCount must eventually(be_==(2))

      runCount.get() mustEqual 1
      Await.result(result1)  mustEqual 1
      Await.result(result2) must throwA[CancellationException]
    }

    "continue to run a task if it's interrupted while running" in {
      val runCount = new atomic.AtomicInteger

      val source = new Promise[Int]

      val startedLatch = new CountDownLatch(1)
      val cancelledLatch = new CountDownLatch(1)

      val result: Future[Int] = pool {
        try {
          startedLatch.countDown()
          runCount.incrementAndGet()
          cancelledLatch.await()
          throw new RuntimeException()
        } finally {
          runCount.incrementAndGet()
        }
        runCount.get
      }

      startedLatch.await(50.milliseconds)
      result.raise(new Exception)
      cancelledLatch.countDown()

      executor.getCompletedTaskCount must eventually(be_==(1))

      runCount.get() mustEqual 2
      Await.result(result) must throwA[RuntimeException]
    }

    "returns exceptions that result from submitting a task to the pool" in {
      val executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue(1))
      val pool     = FuturePool(executor)

      val source   = new Promise[Int]
      val blocker1  = pool { Await.result(source) } // occupy the thread
      val blocker2  = pool { Await.result(source) } // fill the queue

      val rv = pool { "yay!" }

      rv.isDefined mustEqual true
      Await.result(rv) must throwA[RejectedExecutionException]

      source.setValue(1)
    }

    "interrupt threads when interruptible" in {
      val started = new Promise[Unit]
      val interrupted = new Promise[Unit]
      val ipool = FuturePool.interruptible(executor)

      val f = ipool {
        try {
          started.setDone()
          Thread.sleep(Long.MaxValue)
        } catch { case exc: InterruptedException =>
            interrupted.setDone()
        }
      }

      Await.result(started)
      f.raise(new RuntimeException("foo"))
      Await.result(f) must throwA[RuntimeException]
      Await.result(interrupted) mustEqual ()
    }

    "not interrupt threads when not interruptible" in {
      val a = new Promise[Unit]
      val b = new Promise[Unit]
      val nipool = FuturePool(executor)

      val f = nipool {
        a.setDone()
        Await.result(b)
        1
      }

      Await.result(a)
      f.raise(new RuntimeException("foo"))
      b.setDone()
      Await.result(f) mustEqual 1
    }
  }
}
