package com.twitter.util

import org.scalatest.WordSpec

class CancellableTest extends WordSpec {
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      def increment: Unit = count += 1
      val s = new CancellableSink(increment)
      s.cancel()
      assert(s.isCancelled)
      assert(count == 1)
      s.cancel()
      assert(s.isCancelled)
      assert(count == 1)
    }
    "linking not supported" in {
      var count = 1
      def multiply: Unit = count *= 2
      val s1 = new CancellableSink(multiply)
      val s2 = new CancellableSink(multiply)
      assertThrows[Exception] {
        s1.linkTo(s2)
      }
      assertThrows[Exception] {
        s2.linkTo(s1)
      }
    }
  }
}
