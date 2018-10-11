package com.twitter.cache

import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Promise, Time}
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

class RefreshTest extends FunSuite with MockitoSugar {

  class Ctx {
    val provider = mock[() => Future[Int]]
    when(provider())
      .thenReturn(Future.value(1))
      .thenReturn(Future.value(2))

    val ttl = 1.minute
    val memoizedFuture = Refresh.every(ttl) { provider() }
  }

  test("it should call through on first request") {
    val ctx = new Ctx
    import ctx._

    val result = memoizedFuture()
    assert(Await.result(result) == 1)
    verify(provider, times(1))()
  }

  test("it should not call through on second request") {
    val ctx = new Ctx
    import ctx._

    assert(Await.result(memoizedFuture()) == 1)
    assert(Await.result(memoizedFuture()) == 1)
    verify(provider, times(1))()
  }

  test("it should call through after timeout, but only once") {
    val ctx = new Ctx
    import ctx._

    Time.withTimeAt(Time.fromMilliseconds(0)) { timeControl =>
      assert(Await.result(memoizedFuture()) == 1)
      timeControl.advance(ttl - 1.millis)
      assert(Await.result(memoizedFuture()) == 1)
      timeControl.advance(2.millis)
      assert(Await.result(memoizedFuture()) == 2)
      assert(Await.result(memoizedFuture()) == 2)
      verify(provider, times(2))()
    }
  }

  test("it should retry on failed future") {
    val ctx = new Ctx
    import ctx._

    reset(provider)
    when(provider())
      .thenReturn(Future.exception(new RuntimeException))
      .thenReturn(Future.value(2))
    intercept[RuntimeException] {
      Await.result(memoizedFuture())
    }
    assert(Await.result(memoizedFuture()) == 2)
    verify(provider, times(2))()
  }

  test("it should not retry if request is in flight") {
    val ctx = new Ctx
    import ctx._

    val promise = Promise[Int]()
    reset(provider)
    when(provider())
      .thenReturn(promise)

    val result1 = memoizedFuture()
    val result2 = memoizedFuture()

    promise.setValue(1)

    assert(Await.result(result1) == 1)
    assert(Await.result(result2) == 1)

    verify(provider, times(1))()
  }

  test("it should fail both responses if request is in flight, then request again") {
    val ctx = new Ctx
    import ctx._

    val promise = Promise[Int]()
    reset(provider)
    when(provider()).thenReturn(promise)

    val result1 = memoizedFuture()
    val result2 = memoizedFuture()

    promise.setException(new RuntimeException)

    intercept[RuntimeException] { Await.result(result1) }
    intercept[RuntimeException] { Await.result(result2) }

    verify(provider, times(1))()

    reset(provider)
    val promise2 = Promise[Int]()
    when(provider()).thenReturn(promise2)

    val result3 = memoizedFuture()
    promise2.setValue(2)

    assert(Await.result(result3) == 2)

    verify(provider, times(1))()
  }
}
