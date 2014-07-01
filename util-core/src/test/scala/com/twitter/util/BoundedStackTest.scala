package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BoundedStackTest extends WordSpec {
  "BoundedStack" should {
    "empty" in {
      val buf = new BoundedStack[String](4)
      assert(buf.length === 0)
      assert(buf.size === 0)
      assert(buf.isEmpty === true)
      intercept[IndexOutOfBoundsException] {
      buf(0)
      }
      intercept[NoSuchElementException] {
      buf.pop
      }
      assert(buf.iterator.hasNext === false)
    }

    "handle single element" in {
      val buf = new BoundedStack[String](4)
      buf += "a"
      assert(buf.size === 1)
      assert(buf(0) === "a")
      assert(buf.toList === List("a"))
    }

    "handle multiple element" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c")
      assert(buf.size === 3)
      assert(buf(0) === "c")
      assert(buf(1) === "b")
      assert(buf(2) === "a")
      assert(buf.toList === List("c", "b", "a"))
      assert(buf.pop === "c")
      assert(buf.size === 2)
      assert(buf.pop === "b")
      assert(buf.size === 1)
      assert(buf.pop === "a")
      assert(buf.size === 0)
    }

    "handle overwrite/rollover" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      assert(buf.size === 4)
      assert(buf(0) === "f")
      assert(buf.toList === List("f", "e", "d", "c"))
    }
    
    "handle update" in {
      val buf = new BoundedStack[String](4)
      buf ++= List("a", "b", "c", "d", "e", "f")
      for (i <- 0 until buf.size) {
        val old = buf(i)
        val updated = old + "2"
        buf(i) = updated
        assert(buf(i) === updated)
      }
      assert(buf.toList === List("f2", "e2", "d2", "c2"))
    }
    
    "insert at 0 is same as +=" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      assert(buf.size === 1)
      assert(buf(0) === "a")
      buf.insert(0, "b")
      assert(buf.size === 2)
      assert(buf(0) === "b")
      assert(buf(1) === "a")
      buf.insert(0, "c")
      assert(buf(0) === "c")
      assert(buf(1) === "b")
      assert(buf(2) === "a")
      buf.insert(0, "d")
    }
    
    "insert at count pushes to bottom" in {
      val buf = new BoundedStack[String](3)
      buf.insert(0, "a")
      buf.insert(1, "b")
      buf.insert(2, "c")
      assert(buf(0) === "a")
      assert(buf(1) === "b")
      assert(buf(2) === "c")
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
