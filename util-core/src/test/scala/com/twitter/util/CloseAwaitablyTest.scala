package com.twitter.util

import com.twitter.conversions.DurationOps._
import org.scalatest.FunSuite

class CloseAwaitablyTest extends FunSuite {
  class Context extends Closable with CloseAwaitably {
    val p: Promise[Unit] = new Promise[Unit]
    var n: Int = 0
    def close(deadline: Time): Future[Unit] = closeAwaitably {
      n += 1
      p
    }
  }

  class TestClosable(f: () => Future[Unit]) extends Closable with CloseAwaitably {
    def close(deadline: Time): Future[Unit] = closeAwaitably {
      f()
    }
  }

  def make(): Context = new Context()

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
      override def run(): Unit = {
        Await.ready(c)
      }
    }

    c.close(Time.now)
    assert(t.isAlive)
    c.p.setDone()
    t.join(10000)
    assert(!t.isAlive)
  }

  test("close awaitably with error") {
    val message = "FORCED EXCEPTION"
    val closeFn: () => Future[Unit] = { () => throw new Exception(message) }

    val testClosable = new TestClosable(closeFn)
    val f = testClosable.close(Time.now)
    // call again -- should not error and should return same computed f
    assert(testClosable.close(Time.now) == f)
    val e = intercept[Exception] {
      Await.result(f, 1.second)
    }
    assert(e.getMessage == message)
  }

  test("close awaitably with fatal error, fatal error blows up close()") {
    val message = "FORCED INTERRUPTED EXCEPTION"
    val closeFn: () => Future[Unit] = { () => throw new InterruptedException(message) }

    val testClosable = new TestClosable(closeFn)
    val e = intercept[InterruptedException] {
      testClosable.close(Time.now)
    }
    assert(e.getMessage == message)
  }
}
