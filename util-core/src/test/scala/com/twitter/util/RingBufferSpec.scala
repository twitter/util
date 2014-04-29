package com.twitter.util

import org.scalatest.{WordSpec, Matchers}

class RingBufferSpec extends WordSpec with Matchers {
  "RingBuffer" should {
    "empty" in {
      val buf = new RingBuffer[String](4)
      buf.length shouldEqual 0
      buf.size shouldEqual 0
      buf.isEmpty shouldEqual true
      intercept[IndexOutOfBoundsException] {
      buf(0)
      }
      intercept[NoSuchElementException] {
      buf.next
      }
      buf.iterator.hasNext shouldEqual false
    }

    "handle single element" in {
      val buf = new RingBuffer[String](4)
      buf += "a"
      buf.size shouldEqual 1
      buf(0) shouldEqual "a"
      buf.toList shouldEqual List("a")
    }

    "handle multiple element" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c")
      buf.size shouldEqual 3
      buf(0) shouldEqual "a"
      buf(1) shouldEqual "b"
      buf(2) shouldEqual "c"
      buf.toList shouldEqual List("a", "b", "c")
      buf.next shouldEqual "a"
      buf.size shouldEqual 2
      buf.next shouldEqual "b"
      buf.size shouldEqual 1
      buf.next shouldEqual "c"
      buf.size shouldEqual 0
    }

    "handle overwrite/rollover" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      buf.size shouldEqual 4
      buf(0) shouldEqual "c"
      buf.toList shouldEqual List("c", "d", "e", "f")
    }

    "removeWhere" in {
      val buf = new RingBuffer[Int](6)
      buf ++= (0 until 10)
      buf.toList shouldEqual List(4, 5, 6, 7, 8, 9)
      buf.removeWhere(_ % 3 == 0)
      buf.toList shouldEqual List(4, 5, 7, 8)
    }
  }
}
