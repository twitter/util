package com.twitter.util

import org.specs.Specification
import com.twitter.util.TimeConversions._

object DurationSpec extends Specification {
  "Duration" should {
    "min" in {
      1.second min 2.seconds mustEqual 1.second
      2.second min 1.seconds mustEqual 1.second
    }
    "max" in {
      1.second max 2.seconds mustEqual 2.second
      2.second max 1.seconds mustEqual 2.second
    }
  }
}
