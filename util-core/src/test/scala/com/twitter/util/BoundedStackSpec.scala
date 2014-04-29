package com.twitter.util

import org.scalatest.{WordSpec, Matchers}

class BoundedStackSpec extends WordSpec with Matchers {
  "BoundedStack" should {
    "empty" in {
      val buf = new BoundedStack[String](4)
      buf.length shouldEqual 0
      buf.size shouldEqual 0
      buf.isEmpty shouldEqual true
      intercept[IndexOutOfBoundsException] {
      buf(0)
      }
      intercept[NoSuchElementException] {
      buf.pop
      }
      buf.iterator.hasNext shouldEqual false
    }

    "handle single element" in {
      val buf = new BoundedStack[String](4)
      buf += "a"
      buf.size shouldEqual 1
      buf(0) shouldEqual "a"
      buf.toList shouldEqual List("a")
    }

    "handle multiple element" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c")
      buf.size shouldEqual 3
      buf(0) shouldEqual "c"
      buf(1) shouldEqual "b"
      buf(2) shouldEqual "a"
      buf.toList shouldEqual List("c", "b", "a")
      buf.pop shouldEqual "c"
      buf.size shouldEqual 2
      buf.pop shouldEqual "b"
      buf.size shouldEqual 1
      buf.pop shouldEqual "a"
      buf.size shouldEqual 0
    }

    "handle overwrite/rollover" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      buf.size shouldEqual 4
      buf(0) shouldEqual "f"
      buf.toList shouldEqual List("f", "e", "d", "c")
    }
    
    "handle update" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      for (i <- 0 until buf.size) {
        val old = buf(i)
        val updated = old + "2"
        buf(i) = updated
        buf(i) shouldEqual updated
      }
      buf.toList shouldEqual List("f2", "e2", "d2", "c2")
    }
    
    "insert at 0 is same as +=" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      buf.size shouldEqual 1
      buf(0) shouldEqual "a"
      buf.insert(0, "b")
      buf.size shouldEqual 2
      buf(0) shouldEqual "b"
      buf(1) shouldEqual "a"
      buf.insert(0, "c")
      buf(0) shouldEqual "c"
      buf(1) shouldEqual "b"
      buf(2) shouldEqual "a"
      buf.insert(0, "d")
    }
    
    "insert at count pushes to bottom" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      buf.insert(1, "b")
      buf.insert(2, "c")
      buf(0) shouldEqual "a"
      buf(1) shouldEqual "b"
      buf(2) shouldEqual "c"
    }
    
    "insert > count throws exception" in {
      val buf = new BoundedStack[String](3)
      intercept[IndexOutOfBoundsException] {
        buf.insert(1, "a")
      }
      buf.insert(0, "a")
      intercept[IndexOutOfBoundsException] {
        buf.insert(2, "b")
      }
    }
  }
}
