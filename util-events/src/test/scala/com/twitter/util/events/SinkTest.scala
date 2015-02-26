package com.twitter.util.events

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SinkTest extends FunSuite {

  test("Null") {
    val sink = Sink.Null
    assert(sink.events.size === 0)

    sink.event(Event.nullType, objectVal = "hi")
    assert(sink.events.size === 0)
  }

  test("newDefault") {
    sinkEnabled.let(false) {
      assert(Sink.newDefault === Sink.Null)
    }

    sinkEnabled.let(true) {
      approxNumEvents.let(0) {
        assert(Sink.newDefault === Sink.Null)
      }
      approxNumEvents.let(1) {
        assert(Sink.newDefault !== Sink.Null)
      }
    }
  }

}
