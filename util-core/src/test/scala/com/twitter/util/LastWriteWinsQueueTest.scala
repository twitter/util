package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LastWriteWinsQueueTest extends WordSpec {
  "LastWriteWinsQueue" should {
    val queue = new LastWriteWinsQueue[String]

    "add & remove items" in {
      assert(queue.size == 0)
      queue.add("1")
      assert(queue.size == 1)
      assert(queue.remove() == "1")
      assert(queue.size == 0)
    }

    "last write wins" in {
      queue.add("1")
      queue.add("2")
      assert(queue.size == 1)
      assert(queue.poll() == "2")
      assert(queue.size == 0)
      assert(queue.poll() == null)
    }
  }
}
