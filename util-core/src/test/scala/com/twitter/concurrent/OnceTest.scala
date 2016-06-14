package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util.{Promise, Await}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OnceTest extends FunSuite {
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
