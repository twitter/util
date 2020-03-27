package com.twitter.util

import com.twitter.conversions.DurationOps._
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import scala.language.reflectiveCalls

class ClosableTest extends FunSuite with Eventually with IntegrationPatience {
  test("Closable.close(Duration)") {
    Time.withCurrentTimeFrozen { _ =>
      var time: Option[Time] = None
      val c = Closable.make { t =>
        time = Some(t)
        Future.Done
      }
      val dur = 1.minute
      c.close(dur)
      assert(time.contains(Time.now + dur))
    }
  }

  test("Closable.closeOnCollect") {
    @volatile var closed = false
    Closable.closeOnCollect(
      Closable.make { t =>
        closed = true
        Future.Done
      },
      new Object {}
    )
    System.gc()
    eventually { assert(closed) }
  }

  test("Closable.all") {
    val p1, p2 = new Promise[Unit]
    var n1, n2 = 0
    val c1 = Closable.make(_ => { n1 += 1; p1 })
    val c2 = Closable.make(_ => { n2 += 1; p2 })

    val c = Closable.all(c1, c2)
    assert(n1 == 0)
    assert(n2 == 0)

    val f = c.close()
    assert(n1 == 1)
    assert(n2 == 1)

    assert(!f.isDone)
    p1.setDone()
    assert(!f.isDone)
    p2.setDone()
    assert(f.isDone)
  }

  test("Closable.all with exceptions") {
    val throwing = Closable.make(_ => sys.error("lolz"))
    val tracking = new Closable {
      @volatile
      var calledClose = false
      override def close(deadline: Time): Future[Unit] = {
        calledClose = true
        Future.Done
      }
    }

    val f = Closable.all(throwing, tracking).close()
    intercept[Exception] {
      Await.result(f, 5.seconds)
    }
    assert(tracking.calledClose)
  }

  test("Closable.all is eager") {
    assert(
      Future
        .value(1)
        .map(_ => Closable.all(Closable.nop, Closable.nop).close().isDone)
        .poll
        .contains(Return.True)
    )
  }

  test("Closable.sequence") {
    val p1, p2 = new Promise[Unit]
    var n1, n2 = 0
    val c1 = Closable.make(_ => { n1 += 1; p1 })
    val c2 = Closable.make(_ => { n2 += 1; p2 })

    val c = Closable.sequence(c1, c2)
    assert(n1 == 0)
    assert(n2 == 0)

    val f = c.close()
    assert(n1 == 1)
    assert(n2 == 0)
    assert(!f.isDone)

    p1.setDone()
    assert(n1 == 1)
    assert(n2 == 1)
    assert(!f.isDone)

    p2.setDone()
    assert(n1 == 1)
    assert(n2 == 1)
    assert(f.isDone)
  }

  test("Closable.sequence is resilient to failed closes") {
    var n1, n2 = 0
    val c1 = Closable.make { _ =>
      n1 += 1
      Future.exception(new RuntimeException(s"n1=$n1"))
    }
    val c2 = Closable.make { _ =>
      n2 += 1
      Future.Done
    }

    // test with a failed close first
    val f1 = Closable.sequence(c1, c2).close()
    assert(n1 == 1)
    assert(n2 == 1)
    val x1 = intercept[RuntimeException] {
      Await.result(f1, 5.seconds)
    }
    assert(x1.getMessage.contains("n1=1"))

    // then test in reverse order, failed close last
    val f2 = Closable.sequence(c2, c1).close()
    assert(n1 == 2)
    assert(n2 == 2)
    val x2 = intercept[RuntimeException] {
      Await.result(f2, 5.seconds)
    }
    assert(x2.getMessage.contains("n1=2"))

    // multiple failures, returns the first failure
    val f3 = Closable.sequence(c1, c1).close()
    assert(n1 == 4)
    val x3 = intercept[RuntimeException] {
      Await.result(f3, 5.seconds)
    }
    assert(x3.getMessage.contains("n1=3"))

    // verify that first failure is returned, but only after the
    // last closable is satisfied
    val p = new Promise[Unit]()
    val c3 = Closable.make(_ => p)
    val f4 = Closable.sequence(c1, c3).close()
    assert(n1 == 5)
    assert(!f4.isDefined)
    p.setDone()
    val x4 = intercept[RuntimeException] {
      Await.result(f4, 5.seconds)
    }
    assert(x4.getMessage.contains("n1=5"))
  }

  test("Closable.sequence lifts synchronous exceptions") {
    val throwing = Closable.make(_ => sys.error("lolz"))
    val f = Closable.sequence(Closable.nop, throwing).close()
    val ex = intercept[Exception] {
      Await.result(f, 5.seconds)
    }
    assert(ex.getMessage.contains("lolz"))
  }

  test("Closable.sequence is eager") {
    assert(
      Future
        .value(1)
        .map(_ => Closable.sequence(Closable.nop, Closable.nop).close().isDone)
        .poll
        .contains(Return.True)
    )
  }

  test("Cloasable.make catches NonFatals and translates to failed Futures") {
    val throwing = Closable.make { _ => throw new Exception("lolz") }

    var failed = 0
    val f = throwing.close().respond {
      case Throw(_) => failed = -1
      case _ => failed = 1
    }

    val ex = intercept[Exception] {
      Await.result(f, 2.seconds)
    }
    assert(ex.getMessage.contains("lolz"))
    assert(failed == -1)
  }
}
