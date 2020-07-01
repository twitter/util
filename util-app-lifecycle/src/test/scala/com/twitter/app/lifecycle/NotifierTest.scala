package com.twitter.app.lifecycle

import org.scalatest.FunSuite
import Event._

class NotifierTest extends FunSuite {

  test("emits success") {
    var success = false
    var observedEvent: Event = null
    val observer = new Observer {
      def onSuccess(event: Event): Unit = {
        observedEvent = event
        success = true
      }

      def onFailure(event: Event, throwable: Throwable): Unit = {
        observedEvent = event
        success = false
      }
    }
    val notifier = new Notifier(Seq(observer))
    notifier(PreMain) {
      // success
      ()
    }
    assert(observedEvent == PreMain, "Observer did not see PreMain event")
    assert(success, "Observer did not see onSuccess")
  }

  test("emits failure and throws") {
    var failed = false
    var observedEvent: Event = null
    var thrown: Throwable = null

    val observer = new Observer {
      def onSuccess(event: Event): Unit = {
        observedEvent = event
        failed = false
      }

      def onFailure(event: Event, throwable: Throwable): Unit = {
        observedEvent = event
        failed = true
        thrown = throwable
      }
    }
    val notifier = new Notifier(Seq(observer))

    intercept[IllegalStateException] {
      notifier(PostMain) {
        throw new IllegalStateException("BOOM!")
      }
    }
  }

  test("continues to emit after failure") {
    var failed = false
    var observedEvent: Event = null
    var thrown: Throwable = null

    val observer = new Observer {
      def onSuccess(event: Event): Unit = {
        observedEvent = event
        failed = false
      }

      def onFailure(event: Event, throwable: Throwable): Unit = {
        observedEvent = event
        failed = true
        thrown = throwable
      }
    }
    val notifier = new Notifier(Seq(observer))

    intercept[IllegalStateException] {
      notifier(PostMain) {
        throw new IllegalStateException("BOOM!")
      }
    }
    assert(observedEvent == PostMain, "Observer did not see PostMain event")
    assert(failed, "Observer did not see onFailure")
    assert(thrown.isInstanceOf[IllegalStateException] && thrown.getMessage == "BOOM!")

    // our next lifecycle event will succeed
    notifier(Close) {
      ()
    }

    assert(observedEvent == Close)
    assert(failed == false)
  }

}
