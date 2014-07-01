package com.twitter.collection

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import com.twitter.conversions.time._
import com.twitter.util.{Duration, Time}

@RunWith(classOf[JUnitRunner])
class GenerationalQueueTest extends FunSuite {

  def genericGenerationalQueueTest[A](
    name: String,
    newQueue: () => GenerationalQueue[String],
    timeout: Duration
  ) {

    test("%s: Don't collect fresh data".format(name))  {
      Time.withCurrentTimeFrozen { _ =>
        val queue = newQueue()
        queue.add("foo")
        queue.add("bar")
        val collectedValue = queue.collect(timeout)
        assert(collectedValue.isEmpty)
      }
    }

    test("%s: Don't collect old data recently refreshed".format(name))  {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        val queue = newQueue()
        queue.add("foo")
        t += timeout * 3
        queue.add("bar")
        t += timeout * 3
        queue.touch("foo")
        t += timeout * 3
        val collectedValue = queue.collect(timeout)
        assert(!collectedValue.isEmpty)
        assert(collectedValue.get == "bar")
      }
    }

    test("%s: collect one old data".format(name))  {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        val queue = newQueue()
        queue.add("foo1")
        queue.add("foo2")
        queue.add("foo3")

        t += timeout * 3
        queue.add("bar")

        val collectedData = queue.collect(timeout + 1.millisecond)
        assert(!collectedData.isEmpty)
        assert(collectedData.get.indexOf("foo") != -1)
      }
    }

    test("%s: collectAll old data".format(name))  {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        val queue = newQueue()
        queue.add("foo")
        queue.add("bar")

        t += timeout
        queue.add("foo2")
        queue.add("bar2")

        t += timeout
        val collectedValues = queue.collectAll(timeout + 1.millisecond)
        assert(collectedValues.size == 2)
        assert(collectedValues.toSeq.contains("foo"))
        assert(collectedValues.toSeq.contains("bar"))
      }
    }

    test("%s: generate a long chain of bucket (if applicable)".format(name))  {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        val queue = newQueue()
        queue.add("foo")
        t += timeout * 2
        queue.add("bar")
        t += timeout * 2
        queue.add("foobar")
        t += timeout * 2
        queue.add("barfoo")

        val collectedValues = queue.collect(timeout + 1.millisecond)
        assert(!collectedValues.isEmpty)
        assert(List("foo", "bar").contains(collectedValues.get))
      }
    }

    test("%s: Don't collect old data".format(name)) {
      var t = Time.now
      Time.withTimeFunction(t) { _ =>
        val queue = newQueue()
        t += 3.seconds
        queue.add("1")
        queue.add("2")
        queue.add("3")
        t += 4.seconds
        assert(!queue.collect(2.seconds).isEmpty)
      }
    }
  }

  val generationalQueue = () => new ExactGenerationalQueue[String]()
  genericGenerationalQueueTest(
    "ExactGenerationalQueue",
    generationalQueue, 100.milliseconds)

  val timeout = 100.milliseconds
  val bucketQueue = () => new BucketGenerationalQueue[String](timeout)
  genericGenerationalQueueTest(
    "BucketGenerationalQueue",
    bucketQueue, timeout)

  test("BucketGenerationalQueue check removals") {
    var t = Time.now
    Time.withTimeFunction(t) { _ =>
      val queue = bucketQueue()
      queue.add("1")
      queue.add("2")
      queue.remove("1")
      queue.remove("2")
      t += timeout * 3
      assert(queue.collect(timeout).isEmpty)
    }
  }
}
