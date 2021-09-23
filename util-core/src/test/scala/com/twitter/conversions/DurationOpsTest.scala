package com.twitter.conversions

import com.twitter.util.Duration
import com.twitter.conversions.DurationOps._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class DurationOpsTest extends AnyFunSuite with ScalaCheckPropertyChecks {
  test("converts Duration.Zero") {
    assert(0.seconds eq Duration.Zero)
    assert(0.milliseconds eq Duration.Zero)
    assert(0.seconds eq 0.seconds)
  }

  test("converts nonzero durations") {
    assert(1.seconds == Duration.fromSeconds(1))
    assert(123.milliseconds == Duration.fromMilliseconds(123))
    assert(100L.nanoseconds == Duration.fromNanoseconds(100L))
  }

  test("conversion works for Int values") {
    forAll { (intValue: Int) =>
      assert(intValue.seconds == intValue.toLong.seconds)
    }
  }

}
