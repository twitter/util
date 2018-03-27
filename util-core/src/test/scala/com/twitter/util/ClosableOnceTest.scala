package com.twitter.util

import com.twitter.conversions.time._
import org.scalatest.FunSuite

class ClosableOnceTest extends FunSuite {

  private def ready[T <: Awaitable[_]](awaitable: T): T =
    Await.ready(awaitable, 10.seconds)

  test("wrap") {
    var closedCalls = 0
    val underlying = new Closable {
      def close(deadline: Time): Future[Unit] = {
        closedCalls += 1
        Future.Done
      }
    }

    val closableOnce = ClosableOnce.of(underlying)
    closableOnce.close()
    closableOnce.close()
    assert(closedCalls == 1)
  }

  test("if doClose throws an exception, the closeable is closed with that exception") {
    val ex = new Exception("boom")
    var closedCalls = 0
    val closableOnce = new ClosableOnce {
      protected def doClose(deadline: Time): Future[Unit] = {
        closedCalls += 1
        throw ex
      }
    }

    assert(ready(closableOnce.close()).poll.get.throwable == ex)
    assert(closedCalls == 1)

    assert(ready(closableOnce.close()).poll.get.throwable == ex)
    assert(closedCalls == 1)
  }
}
