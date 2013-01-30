package com.twitter.util

import org.specs.SpecificationWithJUnit

class RingBufferSpec extends SpecificationWithJUnit {
  "RingBuffer" should {
    "empty" in {
      val buf = new RingBuffer[String](4)
      buf.length mustEqual 0
      buf.size mustEqual 0
      buf.isEmpty mustEqual true
      buf(0) must throwA[IndexOutOfBoundsException]
      buf.next must throwA[NoSuchElementException]
      buf.iterator.hasNext mustEqual false
    }

    "handle single element" in {
      val buf = new RingBuffer[String](4)
      buf += "a"
      buf.size mustEqual 1
      buf(0) mustEqual "a"
      buf.toList mustEqual List("a")
    }

    "handle multiple element" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c")
      buf.size mustEqual 3
      buf(0) mustEqual "a"
      buf(1) mustEqual "b"
      buf(2) mustEqual "c"
      buf.toList mustEqual List("a", "b", "c")
      buf.next mustEqual "a"
      buf.size mustEqual 2
      buf.next mustEqual "b"
      buf.size mustEqual 1
      buf.next mustEqual "c"
      buf.size mustEqual 0
    }

    "handle overwrite/rollover" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      buf.size mustEqual 4
      buf(0) mustEqual "c"
      buf.toList mustEqual List("c", "d", "e", "f")
    }

    "removeWhere" in {
      val buf = new RingBuffer[Int](6)
      buf ++= (0 until 10)
      buf.toList mustEqual List(4, 5, 6, 7, 8, 9)
      buf.removeWhere(_ % 3 == 0)
      buf.toList mustEqual List(4, 5, 7, 8)
    }
  }
}
