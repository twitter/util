package com.twitter.util

import java.util.concurrent.atomic.AtomicInteger
import com.twitter.conversions.time._
import org.specs.Specification
import org.specs.mock.Mockito

object TimerSpec extends Specification with Mockito {
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
