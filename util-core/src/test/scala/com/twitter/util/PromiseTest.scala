package com.twitter.util

import java.lang.ref.WeakReference

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PromiseTest extends FunSuite with Eventually {
  test("Promise.attached should detach via interruption") {
    val p = new HandledPromise[Unit]()
    val f = Promise.attached(p)
    f.setInterruptHandler { case t: Throwable =>
      if (f.detach())
        f.update(Throw(t))
    }
    f.raise(new Exception())
    assert(p.handled === None)
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
    assert(Await.result(f) === ())
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
    assert(actual === e)
  }

  test("Promise.attached should detach properly for futures") {
    val f = Future.Unit
    val p = Promise.attached(f)
    assert(!p.detach())
    assert(p.poll === Some(Return(())))
  }

  test("Detached promises are no longer connected: Success") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setException(e)
    att.setValue(())
    assert(p.poll === Some(Throw(e)))
    assert(att.poll === Some(Return(())))
  }

  test("Detached promises are no longer connected: Failure") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setValue(())
    att.setException(e)
    assert(att.poll === Some(Throw(e)))
    assert(p.poll === Some(Return(())))
  }

  // this won't work inline, because we still hold a reference to d
  def detach(d: Promise.Detachable) {
    assert(d != null)
    assert(d.detach())
  }

  // System.gc is advisory, and isn't guaranteed to run
  if (!Option(System.getProperty("SKIP_FLAKY")).isDefined) test("Promise.attached should properly detach on gc") {
    val p = Promise[Unit]
    val attachedRef: WeakReference[Promise[Unit] with Promise.Detachable] =
      new WeakReference(Promise.attached(p))

    detach(attachedRef.get())

    System.gc()
    eventually(assert(attachedRef.get() === null))
  }
}
