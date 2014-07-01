package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RingBufferTest extends WordSpec {
  "RingBuffer" should {
    "empty" in {
      val buf = new RingBuffer[String](4)
      assert(buf.length === 0)
      assert(buf.size === 0)
      assert(buf.isEmpty === true)
      intercept[IndexOutOfBoundsException] {
      buf(0)
      }
      intercept[NoSuchElementException] {
      buf.next
      }
      assert(buf.iterator.hasNext === false)
    }

    "handle single element" in {
      val buf = new RingBuffer[String](4)
      buf += "a"
      assert(buf.size === 1)
      assert(buf(0) === "a")
      assert(buf.toList === List("a"))
    }

    "handle multiple element" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c")
      assert(buf.size === 3)
      assert(buf(0) === "a")
      assert(buf(1) === "b")
      assert(buf(2) === "c")
      assert(buf.toList === List("a", "b", "c"))
      assert(buf.next === "a")
      assert(buf.size === 2)
      assert(buf.next === "b")
      assert(buf.size === 1)
      assert(buf.next === "c")
      assert(buf.size === 0)
    }

    "handle overwrite/rollover" in {
      val buf = new RingBuffer[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      assert(buf.size === 4)
      assert(buf(0) === "c")
      assert(buf.toList === List("c", "d", "e", "f"))
    }

    "removeWhere" in {
      val buf = new RingBuffer[Int](6)
      buf ++= (0 until 10)
      assert(buf.toList === List(4, 5, 6, 7, 8, 9))
      buf.removeWhere(_ % 3 == 0)
      assert(buf.toList === List(4, 5, 7, 8))
    }
  }
}
