package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PromiseTest extends FunSuite {

  test("Promise.detach should not detach other attached promises") {
    val p = new Promise[Unit]
    val attached1 = Promise.attached(p)
    val attached2 = Promise.attached(p)

    // detaching `attached2` doesn't detach `attached1`
    assert(attached2.detach())

    p.setDone()
    assert(!attached2.isDefined)
    assert(attached1.isDefined)
  }

  test("Promise.attached should detach via interruption") {
    val p = new HandledPromise[Unit]()
    val f = Promise.attached(p)
    f.setInterruptHandler { case t: Throwable =>
      if (f.detach())
        f.update(Throw(t))
    }
    f.raise(new Exception())
    assert(p.handled == None)
    assert(f.isDefined)
    intercept[Exception] {
      Await.result(f)
    }
  }

  test("Promise.attached should validate success") {
    val p = Promise[Unit]()
    val f = Promise.attached(p)
    p.setValue(())
    assert(f.isDefined)
    assert(Await.result(f.liftToTry) == Return(()))
  }

  test("Promise.attached should validate failure") {
    val p = Promise[Unit]()
    val f = Promise.attached(p)
    val e = new Exception
    p.setException(e)
    assert(f.isDefined)
    val actual = intercept[Exception] {
      Await.result(f)
    }
    assert(actual == e)
  }

  test("Promise.attached should detach properly for futures") {
    val f = Future.Unit
    val p = Promise.attached(f)
    assert(!p.detach())
    assert(p.poll == Some(Return(())))
  }

  test("Detached promises are no longer connected: Success") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setException(e)
    att.setValue(())
    assert(p.poll == Some(Throw(e)))
    assert(att.poll == Some(Return(())))
  }

  test("Detached promises are no longer connected: Failure") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setValue(())
    att.setException(e)
    assert(att.poll == Some(Throw(e)))
    assert(p.poll == Some(Return(())))
  }

  test("become not allowed when already satisfied") {
    val value = "hellohello"
    val p = new Promise[String]()
    p.setValue(value)

    val ex = intercept[IllegalStateException] {
      p.become(new Promise[String]())
    }
    assert(ex.getMessage.contains(value))
  }

  test("Updating a Promise more than once should fail") {
    val p = new Promise[Int]()
    val first = Return(1)
    val second = Return(2)

    p.update(first)
    val ex = intercept[Promise.ImmutableResult] {
      p.update(second)
    }
    assert(ex.message.contains(first.toString))
    assert(ex.message.contains(second.toString))
  }

  // this won't work inline, because we still hold a reference to d
  def detach(d: Promise.Detachable) {
    assert(d != null)
    assert(d.detach())
  }

  test("Promise.attached undone by detach") {
    val p = new Promise[Unit]
    assert(p.waitqLength == 0)
    val q = Promise.attached(p)
    assert(p.waitqLength == 1)
    q.respond(_ => ())
    assert(p.waitqLength == 1)
    q.detach()
    assert(p.waitqLength == 0)
  }

  class HandledMonitor extends Monitor {
    var handled = null: Throwable
    def handle(exc: Throwable) = {
      handled = exc
      true
    }
  }

  test("Promise.respond should monitor fatal exceptions") {
    val p = new Promise[Int]
    val m = new HandledMonitor()
    val exc = new ThreadDeath()

    Monitor.using(m) {
      p ensure { throw exc }
    }

    assert(m.handled == null)
    p.update(Return(1))
    assert(m.handled == exc)
  }

  test("Promise.transform should monitor fatal exceptions") {
    val m = new HandledMonitor()
    val exc = new ThreadDeath()
    val p = new Promise[Int]

    Monitor.using(m) {
      p transform { case _ => throw exc }
    }

    assert(m.handled == null)
    val actual = intercept[ThreadDeath] {
      p.update(Return(1))
    }
    assert(actual == exc)
    assert(m.handled == exc)
  }

  test("Promise can only forwardInterruptsTo one other future") {
    val a = new Promise[Int]
    val b = new HandledPromise[Int]
    val c = new HandledPromise[Int]

    a.forwardInterruptsTo(b)
    a.forwardInterruptsTo(c)

    val ex = new Exception("expected")
    a.raise(ex)

    assert(b.handled == None)
    assert(c.handled == Some(ex))
  }

  test("forwardInterruptsTo does not forward to a completed future") {
    val a = new Promise[Int]
    val b = new HandledPromise[Int]

    b.update(Return(3))

    val ex = new Exception("expected")

    a.forwardInterruptsTo(b)
    a.raise(ex)

    assert(b.handled == None)
  }
}
