package com.twitter.util

import com.twitter.util.TimeConversions.intToTimeableNumber
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class ClosableTest extends FunSpec {
  describe("Closable") {
    it("should close within a timeout if it's passed in") {
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
  }
}
