package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

abstract class LocalSchedulerTest(lifo: Boolean) extends FunSuite {
  private val scheduler = new LocalScheduler(lifo)
  def submit(f: => Unit) = scheduler.submit(new Runnable {
    def run() = f
  })

  val N = 100

  test("run the first submitter immediately") {
    var ok = false
    submit {
      ok = true
    }
    assert(ok)
  }

  test("run subsequent submits serially") {
    var n = 0
    submit {
      assert(n == 0)
      submit {
        assert(n == 1)
        submit {
          assert(n == 2)
          n += 1
        }
        n += 1
      }
      n += 1
    }

    assert(n == 3)
  }

  test("handle many submits") {
    var ran = Nil: List[Int]
    submit {
      for (which <- 0 until N)
        submit {
          ran ::= which
        }
    }
    if (lifo)
      assert(ran == (0 until N))
    else
      assert(ran == (0 until N).reverse)
  }

  test("tracks blocking time") {
    val prevScheduler = Scheduler()
    Scheduler.setUnsafe(scheduler)
    try {
      implicit val timer = new JavaTimer(isDaemon = true)
      val initial = Awaitable.getBlockingTimeTracking
      Awaitable.enableBlockingTimeTracking()
      try {
        var start = scheduler.blockingTimeNanos
        Await.result(Future.sleep(50.milliseconds))
        var elapsed = scheduler.blockingTimeNanos - start
        assert(Duration.fromNanoseconds(elapsed) > 0.seconds)

        Awaitable.disableBlockingTimeTracking()
        start = scheduler.blockingTimeNanos
        Await.result(Future.sleep(50.milliseconds))
        elapsed = scheduler.blockingTimeNanos - start
        assert(elapsed == 0)
      } finally {
        if (initial)
          Awaitable.enableBlockingTimeTracking()
        else
          Awaitable.disableBlockingTimeTracking()
      }
    } finally {
      Scheduler.setUnsafe(prevScheduler)
    }
  }
}

@RunWith(classOf[JUnitRunner])
class LocalSchedulerFifoTest extends LocalSchedulerTest(false)

@RunWith(classOf[JUnitRunner])
class LocalSchedulerLifoTest extends LocalSchedulerTest(true)

@RunWith(classOf[JUnitRunner])
class ThreadPoolSchedulerTest extends FunSuite with Eventually with IntegrationPatience {
  test("works") {
    val p = new Promise[Unit]
    val scheduler = new ThreadPoolScheduler("test")
    scheduler.submit(new Runnable {
      def run() { p.setDone() }
    })

    eventually { p.isDone }

    scheduler.shutdown()
  }
}
