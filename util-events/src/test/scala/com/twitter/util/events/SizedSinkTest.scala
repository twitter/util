package com.twitter.util.events

import com.twitter.util.events.Event._
import com.twitter.util.Time
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SizedSinkTest extends FunSuite {

  test("SizedSink#apply must be given a positive approxSize") {
    intercept[IllegalArgumentException] {
      SizedSink(0)
    }
  }

  test("constructor must be given power of 2 for capacity") {
    intercept[IllegalArgumentException] {
      new SizedSink(3, () => 1L)
    }
  }

  test("event and events") {
    val type1 = Event.nullType
    val type2 = Event.nullType
    val type3 = Event.nullType
    Time.withCurrentTimeFrozen { _ =>
      def event(etype: Type) =
        Event(etype, Time.now, NoLong, NoObject, 1.0f)

      val sink = new SizedSink(2, () => Time.now.inMillis)
      assert(sink.events.size === 0)

      sink.event(type1, doubleVal = 1.0f)
      assert(sink.events.toSeq === Seq(event(type1)))

      sink.event(type2, doubleVal = 1.0f)
      assert(sink.events.toSeq === Seq(event(type1), event(type2)))

      // wrap around
      sink.event(type3, doubleVal = 1.0f)
      assert(sink.events.toSeq === Seq(event(type3), event(type2)))
    }
  }
}
