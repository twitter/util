package com.twitter.concurrent

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Promise, Await}
import org.scalatest.funsuite.AnyFunSuite

class OnceTest extends AnyFunSuite {
  test("Once.apply should only be applied once") {
    var x = 0
    val once = Once { x += 1 }
    once()
    assert(x == 1)
    once()
    assert(x == 1)
  }

  test("Once.apply should be visible from the other thread") {
    @volatile var x = 0
    val once = Once { x += 1 }
    val p = Promise[Unit]()
    val t = new Thread(new Runnable {
      def run(): Unit = {
        once()
        try {
          assert(x == 1)
        } catch {
          case thr: Throwable =>
            p.setException(thr)
            throw thr
        }
        p.setDone()
      }
    })
    t.start()
    once()
    assert(x == 1)
    t.join(5.seconds.inMilliseconds)
    Await.result(p, 5.seconds)
    assert(x == 1)
  }
}
