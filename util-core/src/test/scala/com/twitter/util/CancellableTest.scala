package com.twitter.util


import org.scalatest.WordSpec

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CancellableTest extends WordSpec {
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      val s = new CancellableSink { count += 1 }
      s.cancel()
      assert(count === 1)
      s.cancel()
      assert(count === 1)
    }
  }
}
