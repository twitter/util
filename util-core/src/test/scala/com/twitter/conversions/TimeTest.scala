package com.twitter.conversions

import org.scalatest.FunSuite

import com.twitter.util.Duration

class TimeTest extends FunSuite {
  import time._

  test("converts Duration.Zero") {
    assert(0.seconds eq Duration.Zero)
    assert(0.milliseconds eq Duration.Zero)
    assert(0.seconds eq 0.seconds)
  }

  test("converts nonzero durations") {
    assert(1.seconds == Duration.fromSeconds(1))
    assert(123.milliseconds == Duration.fromMilliseconds(123))
  }
}
