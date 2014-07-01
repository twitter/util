package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CloseAwaitablyTest extends FunSuite {
  def make() = new Closable with CloseAwaitably {
    val p = new Promise[Unit]
    var n = 0
    def close(deadline: Time) = closeAwaitably {
      n += 1
      p
    }
  }

  test("close") {
    val c = make()
    assert(c.n == 0)
    val f = c.close(Time.now)
    assert(f != c.p)
    assert(c.n == 1)
    assert(c.close(Time.now) == f)
    assert(c.n == 1)
    assert(f.poll == None)
    c.p.setDone()
    assert(f.poll == Some(Return.Unit))
  }

  test("Await.ready") {
    val c = make()
    val t = new Thread {
      start()
      override def run() {
        Await.ready(c)
      }
    }

    c.close(Time.now)
    assert(t.isAlive)
    c.p.setDone()
    t.join(10000)
    assert(!t.isAlive)
  }
}
