package com.twitter.util

import com.twitter.conversions.time._
import java.util.concurrent.{CancellationException, ExecutorService}
import java.util.concurrent.atomic.AtomicInteger
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.FunSuite
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar

@RunWith(classOf[JUnitRunner])
class TimerTest extends FunSuite with MockitoSugar with Eventually with IntegrationPatience {

  private def testTimerRunsWithLocals(timer: Timer): Unit = {
    val timerLocal = new AtomicInteger(0)
    val local = new Local[Int]
    val expectedVal = 99
    local.let(expectedVal) {
      timer.schedule(Time.now + 10.millis) {
        timerLocal.set(local().getOrElse(-1))
      }
    }
    eventually {
      assert(expectedVal == timerLocal.get())
    }
    timer.stop()
  }

  test("ThreadStoppingTimer should stop timers in a different thread") {
    val executor = mock[ExecutorService]
    val underlying = mock[Timer]
    val timer = new ThreadStoppingTimer(underlying, executor)

    verify(executor, never()).submit(any[Runnable])
    timer.stop()
    verify(underlying, never()).stop()
    val runnableCaptor = ArgumentCaptor.forClass(classOf[Runnable])
    verify(executor).submit(runnableCaptor.capture())
    runnableCaptor.getValue.run()
    verify(underlying).stop()
  }

  test("ReferenceCountingTimer calls the factory when it is first acquired") {
    val underlying = mock[Timer]
    val factory = mock[() => Timer]
    when(factory()).thenReturn(underlying)

    val refcounted = new ReferenceCountingTimer(factory)

    verify(factory, never()).apply()
    refcounted.acquire()
    verify(factory).apply()
  }

  test("ReferenceCountingTimer stops the underlying timer when acquire count reaches 0") {

    val underlying = mock[Timer]
    val factory = mock[() => Timer]
    when(factory()).thenReturn(underlying)

    val refcounted = new ReferenceCountingTimer(factory)

    refcounted.acquire()
    refcounted.acquire()
    refcounted.acquire()
    verify(factory).apply()

    refcounted.stop()
    verify(underlying, never()).stop()
    refcounted.stop()
    verify(underlying, never()).stop()
    refcounted.stop()
    verify(underlying).stop()
  }

  test("ReferenceCountingTimer should have Locals") {
    val timer = new ReferenceCountingTimer(() => new JavaTimer())
    timer.acquire()
    testTimerRunsWithLocals(timer)
  }

  test("ScheduledThreadPoolTimer should initialize and stop") {
    val timer = new ScheduledThreadPoolTimer(1)
    assert(timer != null)
    timer.stop()
  }

  test("ScheduledThreadPoolTimer should increment a counter") {
    val timer = new ScheduledThreadPoolTimer
    val counter = new AtomicInteger(0)
    timer.schedule(100.millis, 200.millis) {
      counter.incrementAndGet()
    }
    eventually { assert(counter.get() >= 2) }
    timer.stop()
  }

  test("ScheduledThreadPoolTimer should schedule(when)") {
    val timer = new ScheduledThreadPoolTimer
    val counter = new AtomicInteger(0)
    timer.schedule(Time.now + 200.millis) {
      counter.incrementAndGet()
    }
    eventually { assert(counter.get() == 1) }
    timer.stop()
  }

  test("ScheduledThreadPoolTimer should cancel schedule(when)") {
    val timer = new ScheduledThreadPoolTimer
    val counter = new AtomicInteger(0)
    val task = timer.schedule(Time.now + 200.millis) {
      counter.incrementAndGet()
    }
    task.cancel()
    Thread.sleep(1.seconds.inMillis)
    assert(counter.get() != 1)
    timer.stop()
  }

  test("ScheduledThreadPoolTimer should have Locals") {
    testTimerRunsWithLocals(new ScheduledThreadPoolTimer())
  }

  test("JavaTimer should not stop working when an exception is thrown") {
    var errors = 0
    var latch = new CountDownLatch(1)

    val timer = new JavaTimer {
      override def logError(t: Throwable): Unit = {
        errors += 1
        latch.countDown()
      }
    }

    timer.schedule(Time.now) {
      throw new scala.MatchError("huh")
    }

    latch.await(30.seconds)

    assert(errors == 1)

    var result = 0
    latch = new CountDownLatch(1)
    timer.schedule(Time.now) {
      result = 1 + 1
      latch.countDown()
    }

    latch.await(30.seconds)

    assert(result == 2)
    assert(errors == 1)
  }

  test("JavaTimer should schedule(when)") {
    val timer = new JavaTimer
    val counter = new AtomicInteger(0)
    timer.schedule(Time.now + 20.millis) {
      counter.incrementAndGet()
    }
    Thread.sleep(40.milliseconds.inMillis)
    eventually { assert(counter.get() == 1) }
    timer.stop()
  }

  test("JavaTimer should cancel schedule(when)") {
    val timer = new JavaTimer
    val counter = new AtomicInteger(0)
    val task = timer.schedule(Time.now + 20.millis) {
      counter.incrementAndGet()
    }
    task.cancel()
    Thread.sleep(1.seconds.inMillis)
    assert(counter.get() != 1)
    timer.stop()
  }

  test("JavaTimer should have Locals") {
    testTimerRunsWithLocals(new JavaTimer())
  }

  test("Timer should doLater") {
    val result = "boom"
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val f = timer.doLater(1.millis)(result)
      assert(!f.isDefined)
      ctl.advance(2.millis)
      timer.tick()
      assert(f.isDefined)
      assert(Await.result(f) == result)
    }
  }

  test("Timer should doLater throws exception") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val ex = new Exception
      def task: String = throw ex
      val f = timer.doLater(1.millis)(task)
      assert(!f.isDefined)
      ctl.advance(2.millis)
      timer.tick()
      assert(f.isDefined)
      intercept[Throwable] { Await.result(f, 0.millis) }
    }
  }

  test("Timer should interrupt doLater") {
    val result = "boom"
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val f = timer.doLater(1.millis)(result)
      assert(!f.isDefined)
      f.raise(new Exception)
      ctl.advance(2.millis)
      timer.tick()
      assert(f.isDefined)
      intercept[CancellationException] { Await.result(f) }
    }
  }

  test("Timer should doAt") {
    val result = "boom"
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val f = timer.doAt(Time.now + 1.millis)(result)
      assert(!f.isDefined)
      ctl.advance(2.millis)
      timer.tick()
      assert(f.isDefined)
      assert(Await.result(f) == result)
    }
  }

  test("Timer should cancel doAt") {
    val result = "boom"
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val f = timer.doAt(Time.now + 1.millis)(result)
      assert(!f.isDefined)
      val exc = new Exception
      f.raise(exc)
      ctl.advance(2.millis)
      timer.tick()
      assert {
        f.poll match {
          case Some(Throw(e: CancellationException)) if e.getCause eq exc => true
          case _ => false
        }
      }
    }
  }

  test("Timer should schedule(pre-epoch, negative-period)") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val counter = new AtomicInteger(0)

      timer.schedule(Time.Bottom, Duration.Bottom)(counter.incrementAndGet())

      ctl.advance(1.millis)
      timer.tick()
      assert(counter.get() == 1)

      ctl.advance(1.millis)
      timer.tick()
      assert(counter.get() == 2)
    }
  }

  test("Timer should schedule(when)") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val counter = new AtomicInteger(0)
      timer.schedule(Time.now + 1.millis)(counter.incrementAndGet())
      ctl.advance(2.millis)
      timer.tick()
      assert(counter.get() == 1)
    }
  }

  test("Tasks created using Timer.schedule(Time) shouldn't be retained after cancellation") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      assert(timer.tasks.isEmpty)
      val task = timer.schedule(Time.now + 3.seconds)(())
      assert(timer.tasks.size == 1)
      task.cancel()
      assert(timer.tasks.isEmpty)
    }
  }

  test("Tasks created using Timer.schedule(Duration) shouldn't be retained after cancellation") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      assert(timer.tasks.isEmpty)
      val task = timer.schedule(3.seconds)(())
      eventually { assert(timer.tasks.size == 1) }
      ctl.advance(30.seconds)
      timer.tick()
      eventually { assert(timer.tasks.size == 1) }
      task.cancel()
      assert(timer.tasks.isEmpty)
    }
  }

  test("Timer should cancel schedule(when)") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val counter = new AtomicInteger(0)
      val task = timer.schedule(Time.now + 1.millis)(counter.incrementAndGet())
      task.cancel()
      ctl.advance(2.millis)
      timer.tick()
      assert(counter.get() == 0)
    }
  }

  test("Timer should cancel schedule(duration)") {
    Time.withCurrentTimeFrozen { ctl =>
      val timer = new MockTimer
      val counter = new AtomicInteger(0)
      val task = timer.schedule(1.millis)(counter.incrementAndGet())
      ctl.advance(2.millis)
      timer.tick()
      task.cancel()
      ctl.advance(2.millis)
      timer.tick()
      assert(counter.get() == 1)
    }
  }

  private def mockTimerLocalPropagation(timer: MockTimer, localValue: Int): Int = {
    Time.withCurrentTimeFrozen { tc =>
      val timerLocal = new AtomicInteger(0)
      val local = new Local[Int]
      local.let(localValue) {
        timer.schedule(Time.now + 10.millis) {
          timerLocal.set(local().getOrElse(-1))
        }
      }
      tc.advance(20.millis)
      timer.tick()
      timerLocal.get()
    }
  }

  test("MockTimer propagateLocals") {
    val timer = new MockTimer()
    assert(mockTimerLocalPropagation(timer, 99) == 99)
  }

  private class SomeEx extends Exception

  private def testTimerUsesLocalMonitor(timer: Timer): Unit = {
    val seen = new AtomicInteger(0)
    val monitor = Monitor.mk {
      case _: SomeEx =>
        seen.incrementAndGet()
        true
    }
    Monitor.using(monitor) {
      timer.schedule(Time.now + 10.millis) { throw new SomeEx }
    }

    eventually {
      assert(1 == seen.get)
    }
    timer.stop()
  }

  test("JavaTimer uses local Monitor") {
    val timer = new JavaTimer(true, Some("TimerTest"))
    testTimerUsesLocalMonitor(timer)
  }

  test("ScheduledThreadPoolTimer uses local Monitor") {
    val timer = new ScheduledThreadPoolTimer()
    testTimerUsesLocalMonitor(timer)
  }

}
