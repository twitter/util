package com.twitter.util.capturepoints

import com.twitter.util.{Await, Future, FuturePool, Promise}
import org.scalatest.FunSuite

/**
 * A trivial unit test used for demonstrating the value of async stacktraces.
 *
 * Place a breakpoint in the `onSuccess` method inside `lordBusinessLogic`
 * to see the difference in the actual stacktrace versus the IDE's synthetic
 * one.
 */
class Demo extends FunSuite {

  test("async stack traces are rad") {
    val promise = new Promise[Int]()
    val future = someBusinessLogic(promise)

    FuturePool.unboundedPool {
      promise.setValue(1)
    }

    val result: Int = Await.result(future)
    assert(4 == result)
  }

  def someBusinessLogic(future: Future[Int]): Future[Int] = {
    val result: Future[Int] = future.map { i =>
      i + 1
    }
    moreBusinessLogic(result)
  }

  def moreBusinessLogic(future: Future[Int]): Future[Int] = {
    val result: Future[Int] = future.map { i =>
      i * 2
    }
    lordBusinessLogic(result)
  }

  def lordBusinessLogic(future: Future[Int]): Future[Int] = {
    future.onSuccess { i =>
      // NOTE: with Intellij's async stacktraces configured a breakpoint
      // here *will include* `someBusinessLogic` and `moreBusinessLogic`
      // in the debugger's stacktrace, despite them not being in the
      // actual stacktrace.
      val stackTrace = Thread.currentThread.getStackTrace.mkString("\n")
      println(stackTrace)

      assert(!stackTrace.contains("someBusinessLogic"))
      assert(!stackTrace.contains("moreBusinessLogic"))
      assert(stackTrace.contains("lordBusinessLogic"))

      println("\n    (⊙_◎)    (⧉ ⦣ ⧉)    (｀_´)ゞ    「(°ヘ°)\n")
      println(s"    How did we get here? Where are `someBusinessLogic` and `moreBusinessLogic`?")
      println("")
    }
  }

}
