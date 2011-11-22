package com.twitter.util

import com.twitter.util.TimeConversions._
import org.specs.Specification
import org.specs.mock.Mockito

object GenerationalQueueSpec extends Specification with Mockito {

  def genericGenerationalQueueTest[A](queue: GenerationalQueue[String], timeout: Duration) {

    "Don't collect fresh data" in {
      Time.withCurrentTimeFrozen { _ =>
        queue.add("foo")
        queue.add("bar")
        val collectedValue = queue.collect(timeout)
        collectedValue mustBe None
      }
    }

    "Don't collect old data recently refreshed" in {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        queue.add("foo")
        t += timeout * 3
        queue.add("bar")
        t += timeout * 3
        queue.touch("foo")
        t += timeout * 3
        val collectedValue = queue.collect(timeout)
        collectedValue mustNotBe None
        collectedValue mustEqual Some("bar")
      }
    }

    "collect one old data" in {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        queue.add("foo1")
        queue.add("foo2")
        queue.add("foo3")

        t += timeout * 3
        queue.add("bar")

        val collectedData = queue.collect(timeout + 1.millisecond)
        collectedData mustNotEq None
        collectedData.get.indexOf("foo") mustNotEq -1
      }
    }

    "collectAll old data" in {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        queue.add("foo")
        queue.add("bar")

        t += timeout
        queue.add("foo2")
        queue.add("bar2")

        t += timeout
        val collectedValues = queue.collectAll(timeout + 1.millisecond)
        collectedValues.size mustEqual 2
        collectedValues mustContain "foo"
        collectedValues mustContain "bar"
      }
    }

    "generate a long chain of bucket (if applicable)" in {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        queue.add("foo")
        t += timeout * 2
        queue.add("bar")
        t += timeout * 2
        queue.add("foobar")
        t += timeout * 2
        queue.add("barfoo")

        val collectedValues = queue.collect(timeout + 1.millisecond)
        collectedValues mustNotEq None
        List("foo", "bar") mustContain collectedValues.get
      }
    }
  }

  "ExactGenerationalQueue" should {
    val queue = new ExactGenerationalQueue[String]()
    genericGenerationalQueueTest(queue, 100.milliseconds)
  }

  "BucketGenerationalQueue" should {
    val timeout = 100.milliseconds
    val queue = new BucketGenerationalQueue[String](timeout)
    genericGenerationalQueueTest(queue, timeout)
  }
}
