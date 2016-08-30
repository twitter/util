package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util._
import java.util.concurrent.{Executors, TimeUnit}
import java.util.logging.{Handler, Level, LogRecord}
import org.junit.runner.RunWith
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

abstract class LocalSchedulerTest(lifo: Boolean) extends FunSuite
  with Matchers {
  private val scheduler = new LocalScheduler(lifo)
  def submit(f: => Unit): Unit = scheduler.submit(new Runnable {
    def run(): Unit = f
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
      assert(ran == 0.until(N).toList)
    else
      assert(ran == 0.until(N).reverse.toList)
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

  test("numDispatches") {
    val runnable = new Runnable {
      def run(): Unit = ()
    }
    val start = scheduler.numDispatches

    // verify increments are seen by the calling thread
    scheduler.submit(runnable)
    assert(start + 1 == scheduler.numDispatches)

    // verify increments are seen by a different thread
    val exec = Executors.newCachedThreadPool()
    val result = exec.submit {
      new Runnable {
        def run(): Unit = scheduler.submit(runnable)
      }
    }
    exec.shutdown()
    result.get(5, TimeUnit.SECONDS)
    assert(start + 2 == scheduler.numDispatches)
  }

  private def sampleBlockingFraction(fraction: Double): LogRecord = {
    val prevScheduler = Scheduler()
    Scheduler.setUnsafe(new LocalScheduler.Activation(lifo, fraction))
    try {
      var record: LogRecord = null
      val handler = new Handler {
        def flush(): Unit = ()
        def close(): Unit = ()
        def publish(rec: LogRecord): Unit =
          record = rec
      }
      // setting the log level was necessary for this test to pass via `./sbt test`
      LocalScheduler.log.setLevel(Level.INFO)
      LocalScheduler.log.addHandler(handler)

      val initial = Awaitable.getBlockingTimeTracking
      Awaitable.enableBlockingTimeTracking()
      try {
        Await.result(Future.sleep(20.milliseconds)(new JavaTimer(true)))
        record
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

  test("sampleBlockingFraction enabled") {
    val record = sampleBlockingFraction(1.0)
    assert(record != null)
    record.getMessage should include("Scheduler blocked for")
    assert(record.getThrown != null)
  }

  test("sampleBlockingFraction disabled") {
    val record = sampleBlockingFraction(0.0)
    assert(record == null)
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
      def run(): Unit = p.setDone()
    })

    eventually { p.isDone }

    scheduler.shutdown()
  }
}
