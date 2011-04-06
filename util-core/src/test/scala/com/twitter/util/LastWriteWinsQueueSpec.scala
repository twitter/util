package com.twitter.util
import org.specs.Specification

object LastWriteWinsQueueSpec extends Specification {
  "LastWriteWinsQueue" should {
    val queue = new LastWriteWinsQueue[String]

    "add & remove items" in {
      queue.size mustBe 0
      queue.add("1")
      queue.size mustBe 1
      queue.remove() mustBe "1"
      queue.size mustBe 0
    }

    "last write wins" in {
      queue.add("1")
      queue.add("2")
      queue.size mustBe 1
      queue.poll() mustBe "2"
      queue.size mustBe 0
      queue.poll() must beNull
    }
  }
}