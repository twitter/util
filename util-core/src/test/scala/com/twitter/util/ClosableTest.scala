package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

import com.twitter.util.TimeConversions.intToTimeableNumber

@RunWith(classOf[JUnitRunner])
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
      assert(time === Some(Time.now + dur))
    }
  }

  test("Closable.closeOnCollect") {
    @volatile var closed = false
    Closable.closeOnCollect(
      Closable.make { t =>
        closed = true
        Future.Done
      },
      new Object{}
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

  test("Closable.all,sequence are eager") {
    assert((Future.value(1).map(_ =>
      Closable.sequence(Closable.nop, Closable.nop).close().isDone)).poll == Some(Return(true)))
    assert((Future.value(1).map(_ =>
      Closable.all(Closable.nop, Closable.nop).close().isDone)).poll == Some(Return(true)))
  }
}
