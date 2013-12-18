package com.twitter.util

import com.twitter.util.TimeConversions.intToTimeableNumber
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually._ 
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClosableTest extends FunSuite {
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
}
