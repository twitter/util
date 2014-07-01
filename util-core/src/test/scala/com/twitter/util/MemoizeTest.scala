package com.twitter.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{TimeUnit, CountDownLatch => JavaCountDownLatch}

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import com.twitter.conversions.time._

@RunWith(classOf[JUnitRunner])
class MemoizeTest extends FunSuite {
  test("Memoize.apply: only runs the function once for the same input") {
    // mockito can't spy anonymous classes,
    // and this was the simplest approach i could come up with.
    class Adder extends (Int => Int) {
      override def apply(i: Int) = i + 1
    }

    val adder = spy(new Adder)
    val memoizer = Memoize { adder(_: Int) }

    assert(2 === memoizer(1))
    assert(2 === memoizer(1))
    assert(3 === memoizer(2))

    verify(adder, times(1))(1)
    verify(adder, times(1))(2)
  }

  test("Memoize.apply: only executes the memoized computation once per input") {
    val callCount = new AtomicInteger(0)

    val startUpLatch = new JavaCountDownLatch(1)
    val memoizer = Memoize { i: Int =>
      // Wait for all of the threads to be started before
      // continuing. This gives races a chance to happen.
      startUpLatch.await()

      // Perform the effect of incrementing the counter, so that we
      // can detect whether this code is executed more than once.
      callCount.incrementAndGet()

      // Return a new object so that object equality will not pass
      // if two different result values are used.
      "." * i
    }

    val ConcurrencyLevel = 5
    val computations =
      Future.collect(1 to ConcurrencyLevel map { _ =>
        FuturePool.unboundedPool(memoizer(5))
      })

    startUpLatch.countDown()
    val results = Await.result(computations)

    // All of the items are equal, up to reference equality
    results foreach { item =>
      assert(item === results(0))
      assert(item eq results(0))
    }

    // The effects happen exactly once
    assert(callCount.get() === 1)
  }

  test("Memoize.apply: handles exceptions during computations") {
    val TheException = new RuntimeException
    val startUpLatch = new JavaCountDownLatch(1)
    val callCount = new AtomicInteger(0)

    // A computation that should fail the first time, and then
    // succeed for all subsequent attempts.
    val memo = Memoize { i: Int =>
      // Ensure that all of the callers have been started
      startUpLatch.await(200, TimeUnit.MILLISECONDS)
      // This effect should happen once per exception plus once for
      // all successes
      val n = callCount.incrementAndGet()
      if (n == 1) throw TheException else i + 1
    }

    val ConcurrencyLevel = 5
    val computation =
      Future.collect(1 to ConcurrencyLevel map { _ =>
        FuturePool.unboundedPool(memo(5)) transform { Future.value _ }
      })

    startUpLatch.countDown()
    val (successes, failures) =
      Await.result(computation, 200.milliseconds).toList partition { _.isReturn }

    // One of the times, the computation must have failed.
    assert(failures === List(Throw(TheException)))

    // Another time, it must have succeeded, and then the stored
    // result will be reused for the other calls.
    assert(successes === List.fill(ConcurrencyLevel - 1)(Return(6)))

    // The exception plus another successful call:
    assert(callCount.get() === 2)
  }
}
