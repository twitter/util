package com.twitter.concurrent

import com.twitter.util._
import com.twitter.conversions.DurationOps._
import java.util.concurrent.{RejectedExecutionException, CancellationException}
import org.scalatest.funsuite.AnyFunSuite

class AsyncMeterTest extends AnyFunSuite {
  import AsyncMeter._

  // Workaround methods for dealing with Scala compiler warnings:
  //
  // assert(f.isDone) cannot be called directly because it results in a Scala compiler
  // warning: 'possible missing interpolator: detected interpolated identifier `$conforms`'
  //
  // This is due to the implicit evidence required for `Future.isDone` which checks to see
  // whether the Future that is attempting to have `isDone` called on it conforms to the type
  // of `Future[Unit]`. This is done using `Predef.$conforms`
  // https://www.scala-lang.org/api/2.12.2/scala/Predef$.html#$conforms[A]:A%3C:%3CA
  //
  // Passing that directly to `assert` causes problems because the `$conforms` is also seen as
  // an interpolated string. We get around it by evaluating first and passing the result to
  // `assert`.
  private[this] def isDone(f: Future[Unit]): Boolean =
    f.isDone

  private[this] def assertIsDone(f: Future[Unit]): Unit =
    assert(isDone(f))

  test("AsyncMeter shouldn't wait at all when there aren't any waiters.") {
    val timer = new MockTimer
    val meter = newMeter(1, 1.second, 100)(timer)
    val result = meter.await(1)
    assertIsDone(result)
  }

  test("AsyncMeter should allow more than one waiter and allow them on the schedule.") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(1, 1.second, 100)(timer)
      val ready = meter.await(1)
      val waiter = meter.await(1)
      assertIsDone(ready)
      assert(!waiter.isDefined)

      ctl.advance(1.second)
      timer.tick()
      assertIsDone(waiter)
    }
  }

  test("AsyncMeter shouldn't allow a waiter until interval has passed since the last allowance.") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(1, 1.second, 100)(timer)
      val ready = meter.await(1)
      assertIsDone(ready)

      val waiter = meter.await(1)
      assert(!waiter.isDefined)

      timer.tick()
      assert(!waiter.isDefined)

      ctl.advance(1.second)
      timer.tick()
      assertIsDone(waiter)
    }
  }

  test("AsyncMeter should fail waiters that wait over the limit, but still allow the rest") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(1, 1.second, 1)(timer)
      val ready = meter.await(1)
      assertIsDone(ready)

      val waiter = meter.await(1)
      val rejected = meter.await(1)
      assert(!waiter.isDefined)
      assert(rejected.isDefined)
      intercept[RejectedExecutionException] {
        Await.result(rejected, 5.seconds)
      }

      ctl.advance(1.second)
      timer.tick()
      waiter.isDone
    }
  }

  test("AsyncMeter should allow a waiter to be removed from the queue on interruption.") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(1, 1.second, 100)(timer)
      var nr = 0
      val ready = meter.await(1)
      val waiter = meter.await(1)
      assertIsDone(ready)
      assert(!waiter.isDefined)
      val e = new Exception("boom!")

      waiter.raise(e)
      assert(waiter.isDefined)
      val actual = intercept[CancellationException] {
        Await.result(waiter, 5.seconds)
      }
      assert(actual.getCause == e)
    }
  }

  test("AsyncMeter should allow more than one waiter in a ready period") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(2, 1.second, 100)(timer)
      val ready = meter.await(2)
      assertIsDone(ready)

      val first = meter.await(1)
      val second = meter.await(1)
      assert(!first.isDefined)
      assert(!second.isDefined)

      ctl.advance(1.second)
      timer.tick()
      assertIsDone(first)
      assertIsDone(second)
    }
  }

  test("AsyncMeter should handle small burst sizes and periods smaller than timer granularity") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(1, 500.microseconds, 100)(timer)
      val ready = meter.await(1)
      assertIsDone(ready)

      val first = meter.await(1)
      val second = meter.await(1)
      assert(!first.isDefined)
      assert(!second.isDefined)

      ctl.advance(1.millisecond)
      timer.tick()
      assertIsDone(first)
      assertIsDone(second)
    }
  }

  test("AsyncMeter should handle small, short bursts with big token amounts") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(2, 500.microseconds, 100)(timer)
      val ready = meter.await(2)
      assertIsDone(ready)

      val first = meter.await(1)
      val second = meter.await(2)
      val third = meter.await(1)
      assert(!first.isDefined)
      assert(!second.isDefined)
      assert(!third.isDefined)

      ctl.advance(1.millisecond)
      timer.tick()
      assertIsDone(first)
      assertIsDone(second)
      assertIsDone(third)
    }
  }

  test("AsyncMeter should hit the full rate even with insufficient granularity") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newUnboundedMeter(1, 500.microseconds)(timer)
      val ready = Future.join(Seq.fill(1000)(meter.await(1))).join {
        FuturePool.unboundedPool {
          for (_ <- 0 until 500) {
            ctl.advance(1.millisecond)
            timer.tick()
          }
        }
      }
      Await.ready(ready, 5.seconds)
      assert(ready.isDefined)
    }
  }

  test("AsyncMeter should allow an expensive call to be satisfied slowly") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(2, 1.second, 100)(timer)
      val ready = meter.await(2)
      assertIsDone(ready)

      val waiter = meter.await(2)
      assert(!waiter.isDefined)

      ctl.advance(500.milliseconds)
      timer.tick()
      assert(!waiter.isDefined)

      ctl.advance(500.milliseconds)
      timer.tick()
      assertIsDone(waiter)
    }
  }

  test("AsyncMeter should reject greedy awaiters") {
    val timer = new MockTimer
    val meter = newMeter(2, 1.second, 100)(timer)
    val greedy = meter.await(3)
    assert(greedy.isDefined)
    intercept[IllegalArgumentException] {
      Await.result(greedy, 5.seconds)
    }
  }

  test("AsyncMeter should not allow small queue jumpers") {
    val timer = new MockTimer
    val meter = newMeter(6, 1.second, 100)(timer)
    val ready = meter.await(3)
    val first = meter.await(4)
    val second = meter.await(4)
    assertIsDone(ready)
    assert(!first.isDefined)
    assert(!second.isDefined)
  }

  test("AsyncMeter should allow parts of tokens") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(3, 2.millisecond, 100)(timer)
      val ready = meter.await(3)
      val first = meter.await(1)
      val second = meter.await(1)
      val third = meter.await(1)
      assertIsDone(ready)
      ctl.advance(1.millisecond)
      timer.tick()
      assertIsDone(first)
      assert(!second.isDefined)
      assert(!third.isDefined)
      ctl.advance(1.millisecond)
      timer.tick()
      assertIsDone(second)
      assertIsDone(third)
    }
  }

  test("AsyncMeter.extraWideAwait should handle big awaits") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(6, 1.second, 100)(timer)
      val greedy = extraWideAwait(12, meter)
      assert(!greedy.isDefined)
      ctl.advance(1.second)
      timer.tick()
      assertIsDone(greedy)
    }
  }

  test("AsyncMeter.extraWideAwait shouldn't block after being rejected") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(6, 1.second, 2)(timer)
      val greedy = extraWideAwait(24, meter)
      val first = meter.await(6)
      assert(greedy.isDefined)
      assert(!first.isDefined)
      intercept[RejectedExecutionException] {
        Await.result(greedy, 5.seconds)
      }
      ctl.advance(1.second)
      timer.tick()
      assertIsDone(first)
    }
  }

  test("AsyncMeter.extraWideAwait shouldn't block after being interrupted") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = newMeter(6, 1.second, 100)(timer)
      val greedy = extraWideAwait(18, meter)
      val first = meter.await(6)
      assert(!greedy.isDefined)
      assert(!first.isDefined)

      val e = new Exception("boom!")
      greedy.raise(e)
      assert(greedy.isDefined)
      val actual = intercept[CancellationException] {
        Await.result(greedy, 5.seconds)
      }
      assert(actual.getCause == e)

      ctl.advance(1.second)
      timer.tick()
      assertIsDone(first)
    }
  }

  test("AsyncMeter.perSecondLimited shouldn't allow more than N waiters to continue over 1 s") {
    val timer = new MockTimer
    Time.withCurrentTimeFrozen { ctl =>
      val meter = perSecondLimited(2, 100)(timer) // 2 QPS

      val first = meter.await(1)
      val second = meter.await(1)
      val third = meter.await(1)
      val forth = meter.await(1)
      val fifth = meter.await(1)
      val sixth = meter.await(1)

      assert(first.isDefined)
      assert(!second.isDefined)

      ctl.advance(1.second)
      timer.tick()
      assert(second.isDefined)
      assert(third.isDefined)
      assert(!forth.isDefined)

      ctl.advance(1.second)
      timer.tick()
      assert(forth.isDefined)
      assert(fifth.isDefined)
      assert(!sixth.isDefined)
    }
  }
}
