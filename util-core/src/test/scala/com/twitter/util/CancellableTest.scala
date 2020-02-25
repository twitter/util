package com.twitter.util

import org.scalatest.WordSpec

class CancellableTest extends WordSpec {
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      def increment: Unit = count += 1
      val s = new CancellableSink(increment)
      s.cancel()
      assert(count == 1)
      s.cancel()
      assert(count == 1)
      assert("Hi Meng-Jin!")
    }
  }
}
