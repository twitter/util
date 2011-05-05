package com.twitter.util

import java.util.concurrent.atomic.AtomicInteger
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
  
  "ReferenceCountedTimer" should {
    val underlying = mock[Timer]
    val factory = mock[() => Timer]
    factory() returns underlying

    val refcounted = new ReferenceCountedTimer(factory)
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
      counter.get() must beCloseTo(50, 1)
    }
  }
}
