package com.twitter.conversions

import com.twitter.util.Duration
import org.scalatest.FunSuite
import com.twitter.conversions.DurationOps._

class DurationOpsTest extends FunSuite {
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

}
