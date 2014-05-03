package com.twitter.util

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LastWriteWinsQueueTest extends WordSpec with ShouldMatchers {
  "LastWriteWinsQueue" should {
    val queue = new LastWriteWinsQueue[String]

    "add & remove items" in {
      queue.size shouldEqual 0
      queue.add("1")
      queue.size shouldEqual 1
      queue.remove() shouldEqual "1"
      queue.size shouldEqual 0
    }

    "last write wins" in {
      queue.add("1")
      queue.add("2")
      queue.size shouldEqual 1
      queue.poll() shouldEqual "2"
      queue.size shouldEqual 0
      queue.poll() shouldEqual null
    }
  }
}
