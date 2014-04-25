package com.twitter.util

import org.scalatest.{WordSpec, Matchers}

class LastWriteWinsQueueSpec extends WordSpec with Matchers {
  "LastWriteWinsQueue" should  {
    val queue = new LastWriteWinsQueue[String]

    "add & remove items" in {
      queue.size shouldBe 0
      queue.add("1")
      queue.size shouldBe 1
      queue.remove() shouldBe "1"
      queue.size shouldBe 0
    }

    "last write wins" in {
      queue.add("1")
      queue.add("2")
      queue.size shouldBe 1
      queue.poll() shouldBe "2"
      queue.size shouldBe 0
      queue.poll() shouldEqual null
    }
  }
}
