package com.twitter.util


import org.scalatest.{WordSpec, Matchers}

class CancellableSpec extends WordSpec with Matchers {
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      val s = new CancellableSink { count += 1 }
      s.cancel()
      count shouldEqual(1)
      s.cancel()
      count shouldEqual(1)
    }
  }
}
