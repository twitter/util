package com.twitter.util


import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CancellableTest extends WordSpec with ShouldMatchers {
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
