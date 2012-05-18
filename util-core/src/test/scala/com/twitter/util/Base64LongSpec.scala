package com.twitter.util

import org.specs.SpecificationWithJUnit
import com.twitter.util.Base64Long.toBase64
import util.Random

class Base64LongSpec extends SpecificationWithJUnit {
  "toBase64" should {
    "properly convert zero" in {
      toBase64(0) must be_==("A")
    }

    "properly convert a large number" in {
      toBase64(202128261025763330L) must be_==("LOGpUdghAC")
    }

    "Use the expected number of digits" in {
      val expectedLength: Long => Int = {
        case 0          => 1 // Special case in the implementation
        case n if n < 0 => 11 // High bit set, treated as unsigned
        case n          => (math.log(n + 1)/math.log(64)).ceil.toInt
      }
      val checkExpectedLength = (n: Long) => toBase64(n).length must be_==(expectedLength(n))
      Seq(0L, 1L, 63L, 64L, 4095L, 4096L, -1L) foreach checkExpectedLength
      (1 to 200) foreach { _ => checkExpectedLength(Random.nextLong) }
    }
  }
}