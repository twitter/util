package com.twitter.util

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.ExecutorService
import com.twitter.conversions.time._
import org.specs.Specification
import org.specs.mock.Mockito
import org.mockito.{Matchers, ArgumentCaptor}

object TimerSpec extends Specification with Mockito {
  "ThreadStoppingTimer" should {
    "stop timers in a different thread" in {
      // For some reason proper type inference fails here.
      type R = org.specs.specification.Result[java.util.concurrent.Future[_]]
      val executor = mock[ExecutorService]
      val underlying = mock[Timer]
      val timer = new ThreadStoppingTimer(underlying, executor)
      there was no(executor).submit(any[Runnable]): R
      timer.stop()
      there was no(underlying).stop()
      val runnableCaptor = ArgumentCaptor.forClass(classOf[Runnable])
      there was one(executor).submit(runnableCaptor.capture()): R
      runnableCaptor.getValue.run()
      there was one(underlying).stop()
    }
  }

  "ReferenceCountingTimer" should {
    val underlying = mock[Timer]
    val factory = mock[() => Timer]
    factory() returns underlying

    val refcounted = new ReferenceCountingTimer(factory)
    there was no(factory)()

    "call the factory when it is first acquired" in {
      refcounted.acquire()
      there was one(factory)()
    }

    "stop the underlying timer when acquire count reaches 0" in {
      refcounted.acquire()
      refcounted.acquire()
      refcounted.acquire()
      there was one(factory)()

      refcounted.stop()
      there was no(underlying).stop()
      refcounted.stop()
      there was no(underlying).stop()
      refcounted.stop()
      there was one(underlying).stop()
    }
  }

  "ScheduledThreadPoolTimer" should {
    "initialize and stop" in {
      val timer = new ScheduledThreadPoolTimer(1)
      timer must notBeNull
      timer.stop()
    }

    "increment a counter" in {
      val timer = new ScheduledThreadPoolTimer
      val counter = new AtomicInteger(0)
      timer.schedule(0.millis, 20.millis) {
        counter.incrementAndGet()
      }
      Thread.sleep(1.second.inMillis)
      timer.stop()
      counter.get() must beCloseTo(50, 2)
    }
  }

  "JavaTimer" should {
    "not stop working when an exception is thrown" in {
      var errors = 0
      var latch = new CountDownLatch(1)

      val timer = new JavaTimer {
        override def logError(t: Throwable) {
          errors += 1
          latch.countDown
        }
      }

      timer.schedule(Time.now) {
        throw new scala.MatchError
      }

      latch.await(30.seconds)

      errors mustEqual 1

      var result = 0
      latch = new CountDownLatch(1)
      timer.schedule(Time.now) {
        result = 1 + 1
        latch.countDown
      } mustNot throwA[Throwable]

      latch.await(30.seconds)

      result mustEqual 2
      errors mustEqual 1
    }
  }

  "Timer" should {
    val result = "boom"

    "doLater" in {
      val timer = new MockTimer
      val f = timer.doLater(1.millis)(result)
      f.isDefined must beFalse
      Thread.sleep(2.millis)
      timer.tick()
      f.isDefined must beTrue
      f() mustEqual result
    }

    "doLater throws exception" in {
      val timer = new MockTimer
      val ex = new Exception
      def task: String = throw ex
      val f = timer.doLater(1.millis)(task)
      f.isDefined must beFalse
      Thread.sleep(2.millis)
      timer.tick()
      f.isDefined must beTrue
      f.get(0.millis) mustEqual Throw(ex)
    }

    "cancel doLater" in {
      val timer = new MockTimer
      val f = timer.doLater(1.millis)(result)
      f.isDefined must beFalse
      f.cancel()
      Thread.sleep(2.millis)
      timer.tick()
      f.isDefined must beFalse
    }

    "doAt" in {
      val timer = new MockTimer
      val f = timer.doAt(Time.now + 1.millis)(result)
      f.isDefined must beFalse
      Thread.sleep(2.millis)
      timer.tick()
      f.isDefined must beTrue
      f() mustEqual result
    }

    "cancel doAt" in {
      val timer = new MockTimer
      val f = timer.doAt(Time.now + 1.millis)(result)
      f.isDefined must beFalse
      f.cancel()
      Thread.sleep(2.millis)
      timer.tick()
      f.isDefined must beFalse
    }
  }
}
