package com.twitter.util

import org.specs.Specification
import com.twitter.util.TimeConversions._

object DurationSpec extends Specification {
  "Duration" should {
    "*" in {
      1.second * 2 mustEqual 2.seconds
      500.milliseconds * 4 mustEqual 2.seconds
    }

    "afterEpoch" in {
      10.seconds.afterEpoch mustEqual Time(10000)
    }

    "fromNow" in {
      Time.withCurrentTimeFrozen { _ =>
        10.seconds.fromNow mustEqual (Time.now + 10.seconds)
      }
    }

    "ago" in {
      Time.withCurrentTimeFrozen { _ =>
        10.seconds.ago mustEqual (Time.now - 10.seconds)
      }
    }

    "compare" in {
      10.seconds must be_<(11.seconds)
      10.seconds must be_<(11000.milliseconds)
      new Duration(Long.MaxValue) must be_>(0.seconds)
    }

    "+ delta" in {
      10.seconds + 5.seconds mustEqual 15.seconds
    }

    "- delta" in {
      10.seconds - 5.seconds mustEqual 5.seconds
    }

    "max" in {
      10.seconds max 5.seconds mustEqual 10.seconds
      5.seconds max 10.seconds mustEqual 10.seconds
    }

    "min" in {
      10.seconds min 5.seconds mustEqual 5.seconds
      5.seconds min 10.seconds mustEqual 5.seconds
    }

    "abs" in {
      10.seconds.abs mustEqual 10.seconds
      (-10).seconds.abs mustEqual 10.seconds
    }

    "moreOrLessEquals" in {
      10.seconds.moreOrLessEquals(9.seconds, 1.second) must beTrue
      10.seconds.moreOrLessEquals(11.seconds, 1.second) must beTrue
      10.seconds.moreOrLessEquals(8.seconds, 1.second) must beFalse
      10.seconds.moreOrLessEquals(12.seconds, 1.second) must beFalse
    }
  }
}
