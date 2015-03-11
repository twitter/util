package com.twitter.concurrent

import com.twitter.util.{Promise, Await}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OnceTest extends FunSuite {
  test("Once.apply should only be applied once") {
    var x = 0
    val once = Once { x += 1; 1 }
    once()
    assert(x == 1)
    once()
    assert(x == 1)
  }

  test("Once.apply should be visible from the other thread") {
    @volatile var x = 0
    val once = Once { x += 1; 1 }
    val p = Promise[Unit]
    val t = new Thread(new Runnable {
      def run(): Unit = {
        once()
        try {
          assert(x == 1)
        } catch {
          case t =>
            p.setException(t)
            throw t
        }
        p.setDone()
      }
    })
    once()
    t.start()
    assert(x == 1)
    t.join()
    Await.result(p)
  }
}
