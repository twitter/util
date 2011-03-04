package com.twitter.util

import org.specs.Specification

object BoundedStackSpec extends Specification {
  "BoundedStack" should {
    "empty" in {
      val buf = new BoundedStack[String](4)
      buf.length mustEqual 0
      buf.size mustEqual 0
      buf.isEmpty mustEqual true
      buf(0) must throwA[IndexOutOfBoundsException]
      buf.pop must throwA[NoSuchElementException]
      buf.elements.hasNext mustEqual false
    }

    "handle single element" in {
      val buf = new BoundedStack[String](4)
      buf += "a"
      buf.size mustEqual 1
      buf(0) mustEqual "a"
      buf.toList mustEqual List("a")
    }

    "handle multiple element" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c")
      buf.size mustEqual 3
      buf(0) mustEqual "c"
      buf(1) mustEqual "b"
      buf(2) mustEqual "a"
      buf.toList mustEqual List("c", "b", "a")
      buf.pop mustEqual "c"
      buf.size mustEqual 2
      buf.pop mustEqual "b"
      buf.size mustEqual 1
      buf.pop mustEqual "a"
      buf.size mustEqual 0
    }

    "handle overwrite/rollover" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      buf.size mustEqual 4
      buf(0) mustEqual "f"
      buf.toList mustEqual List("f", "e", "d", "c")
    }
    
    "handle update" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      for (i <- 0 until buf.size) {
        val old = buf(i)
        val updated = old + "2"
        buf(i) = updated
        buf(i) mustEqual updated
      }
      buf.toList mustEqual List("f2", "e2", "d2", "c2")
    }
    
    "insert at 0 is same as +=" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      buf.size mustEqual 1
      buf(0) mustEqual "a"
      buf.insert(0, "b")
      buf.size mustEqual 2
      buf(0) mustEqual "b"
      buf(1) mustEqual "a"
      buf.insert(0, "c")
      buf(0) mustEqual "c"
      buf(1) mustEqual "b"
      buf(2) mustEqual "a"
      buf.insert(0, "d")
    }
    
    "insert at count pushes to bottom" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      buf.insert(1, "b")
      buf.insert(2, "c")
      buf(0) mustEqual "a"
      buf(1) mustEqual "b"
      buf(2) mustEqual "c"
    }
    
    "insert > count throws exception" in {
      val buf = new BoundedStack[String](3)
      buf.insert(1, "a") must throwA[IndexOutOfBoundsException]
      buf.insert(0, "a")
      buf.insert(2, "b") must throwA[IndexOutOfBoundsException]
    }
  }
}
