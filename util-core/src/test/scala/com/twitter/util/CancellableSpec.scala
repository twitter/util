package com.twitter.util

import org.specs.SpecificationWithJUnit

class CancellableSpec extends SpecificationWithJUnit {
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      val s = new CancellableSink { count += 1 }
      s.cancel()
      count must be_==(1)
      s.cancel()
      count must be_==(1)
    }
  }
}
