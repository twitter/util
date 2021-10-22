package com.twitter.util

import com.twitter.concurrent.Scheduler
import com.twitter.concurrent.ForkingScheduler
import com.twitter.conversions.DurationOps._
import java.util.concurrent.{Future => JFuture, _}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import scala.runtime.NonLocalReturnControl
import scala.util.control.NonFatal
import org.scalatest.funsuite.AnyFunSuite

class FuturePoolTest extends AnyFunSuite with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = scaled(Span(5, Millis)))

  test("FuturePool should dispatch to another thread") {
    val executor = Executors.newFixedThreadPool(1)
    val pool = FuturePool(executor)

    val source = new Promise[Int]
    val result = pool { Await.result(source) } // simulate blocking call

    source.setValue(1)
    assert(Await.result(result) == 1)
  }

  test("Executor failing contains failures") {
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
    Await.ready(result1)

    assert(runCount.get() == 0)
  }

  test("does not execute interrupted tasks") {
    val executor = Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
    val pool = FuturePool(executor)

    val runCount = new atomic.AtomicInteger

    val source1 = new Promise[Int]
    val source2 = new Promise[Int]

    val result1 = pool { runCount.incrementAndGet(); Await.result(source1) }
    val result2 = pool { runCount.incrementAndGet(); Await.result(source2) }

    val exc = new CancellationException
    result2.raise(exc)
    source1.setValue(1)

    // The executor will run the task for result 2, but the wrapper
    // in FuturePool will throw away the work if the future
    // representing the outcome has already been interrupted,
    // and will set the result to a CancellationException
    eventually { assert(executor.getCompletedTaskCount == 2) }

    assert(runCount.get() == 1)
    assert(Await.result(result1) == 1)
    intercept[CancellationException] { Await.result(result2) }
  }

  test("continue to run a task if it's interrupted while running") {
    val executor = Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
    val pool = FuturePool(executor)

    val runCount = new atomic.AtomicInteger

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

    startedLatch.await(1, TimeUnit.SECONDS)
    result.raise(new Exception)
    cancelledLatch.countDown()

    eventually { assert(executor.getCompletedTaskCount == 1) }

    assert(runCount.get() == 2)
    intercept[RuntimeException] { Await.result(result) }
  }

  test("returns exceptions that result from submitting a task to the pool") {
    val executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue(1))
    val pool = FuturePool(executor)

    val source = new Promise[Int]
    pool { Await.result(source) } // occupy the thread
    pool { Await.result(source) } // fill the queue

    val rv = pool { "yay!" }

    assert(rv.isDefined)
    intercept[RejectedExecutionException] { Await.result(rv) }

    source.setValue(1)
  }

  test("interrupt threads when interruptible") {
    val executor = Executors.newFixedThreadPool(1)
    val started = new Promise[Unit]
    val interrupted = new Promise[Unit]
    val ipool = FuturePool.interruptible(executor)

    val f = ipool {
      try {
        started.setDone()
        while (true) {
          Thread.sleep(Long.MaxValue)
        }
      } catch {
        case _: InterruptedException =>
          interrupted.setDone()
      }
    }

    Await.result(started)
    f.raise(new RuntimeException("foo"))
    intercept[RuntimeException] { Await.result(f) }
    assert(Await.result(interrupted.liftToTry) == Return(()))
  }

  test("not interrupt threads when not interruptible") {
    val executor = Executors.newFixedThreadPool(1)
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
    assert(Await.result(f) == 1)
  }

  test("satisfies result promise on fatal exceptions thrown by task") {
    val executor = Executors.newFixedThreadPool(1)
    val pool = FuturePool(executor)
    val fatal = new LinkageError
    assert(!NonFatal(fatal))
    val rv = pool { throw fatal }

    val ex = intercept[ExecutionException] { Await.result(rv) }
    assert(ex.getCause == fatal)
  }

  class PoolCtx {
    val executor: ExecutorService = Executors.newFixedThreadPool(1)
    val pool: ExecutorServiceFuturePool = FuturePool(executor)

    val pools: Seq[FuturePool] = Seq(FuturePool.immediatePool, pool)
  }

  test("handles NonLocalReturnControl properly") {
    val ctx = new PoolCtx
    import ctx._

    def fake(): String = {
      pools foreach { pool =>
        val rv = pool { return "OK" }

        val e = intercept[FutureNonLocalReturnControl] { Await.result(rv) }
        val f = intercept[NonLocalReturnControl[String]] { throw e.getCause }
        assert(f.value == "OK")
      }
      "FINISHED"
    }

    assert(fake() == "FINISHED")
  }

  test("FuturePool metrics") {
    // We want isolation and thus can't use FuturePool.unboundedPool
    // But we want to make sure it will have the correct behavior.
    // We compromise by roughly creating an ExecutorService/FuturePool
    // that behaves the same.
    val executor = Executors.newFixedThreadPool(1)
    val pool = new ExecutorServiceFuturePool(executor)
    // verify the initial state
    assert(pool.poolSize == 0)
    assert(pool.numActiveTasks == 0)
    assert(pool.numCompletedTasks == 0)
    assert(pool.numPendingTasks == 0)

    // execute a task we can control
    val latch = new CountDownLatch(1)
    val future = pool {
      latch.await(10, TimeUnit.SECONDS)
      true
    }
    pool(()) // pending task

    eventually { assert(pool.poolSize == 1) }
    eventually { assert(pool.numActiveTasks == 1) }
    eventually { assert(pool.numCompletedTasks == 0) }
    eventually { assert(pool.numPendingTasks == 1) }

    // let the task complete
    latch.countDown()
    Await.ready(future, 5.seconds)
    eventually { assert(pool.poolSize == 1) }
    eventually { assert(pool.numActiveTasks == 0) }
    eventually { assert(pool.numCompletedTasks == 2) }
    eventually { assert(pool.numPendingTasks == 0) }

    // cleanup.
    executor.shutdown()
  }

  class TestForkingScheduler(redirect: Boolean) extends ForkingScheduler {
    var forked = Option.empty[Future[_]]
    override def fork[T](f: => Future[T]) = {
      val r = f
      forked = Some(r)
      r
    }
    override def tryFork[T](f: => Future[T]): Future[Option[T]] = ???
    override def redirectFuturePools(): Boolean = redirect
    override def blocking[T](f: => T)(implicit perm: Awaitable.CanAwait): T = f
    override def flush(): Unit = {}

    override def submit(r: Runnable): Unit = r.run()

    override def fork[T](executor: Executor)(f: => Future[T]) = ???
    override def asExecutorService(): ExecutorService = ???
    override def numDispatches: Long = ???
  }

  test("FuturePool should be redirected to the forking scheduler if specified by the scheduler") {
    val scheduler = new TestForkingScheduler(redirect = true)
    val prevScheduler = Scheduler()
    val executor = Executors.newFixedThreadPool(1)
    Scheduler.setUnsafe(scheduler)
    try {
      val pool = FuturePool(executor)
      var executed = false
      val result =
        pool {
          executed = true
          1
        }
      assert(Await.result(result, 1.second) == 1)
      assert(Await.result(scheduler.forked.get, 1.second) == 1)
      assert(executed)
    } finally {
      Scheduler.setUnsafe(prevScheduler)
      executor.shutdown()
    }
  }

  test(
    "FuturePool should not be redirected to the forking scheduler if specified by the scheduler") {
    val scheduler = new TestForkingScheduler(redirect = false)
    val executor = Executors.newFixedThreadPool(1)
    val prevScheduler = Scheduler()
    Scheduler.setUnsafe(scheduler)
    try {
      val pool = FuturePool(executor)
      var executed = false
      val result =
        pool {
          executed = true
          1
        }
      assert(Await.result(result, 1.second) == 1)
      assert(scheduler.forked.isEmpty)
      assert(executed)
    } finally {
      Scheduler.setUnsafe(prevScheduler)
      executor.shutdown()
    }
  }

}
