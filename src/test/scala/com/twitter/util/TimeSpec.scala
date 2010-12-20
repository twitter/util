package com.twitter.util

import org.specs.Specification
import TimeConversions._

object TimeSpec extends Specification {
  "Time" should {
    "now should be now" in {
      (Time.now.inMillis - System.currentTimeMillis).abs must beLessThan(20L)
    }

    "withTimeAt" in {
      val t0 = new Time(123456789L)
      Time.withTimeAt(t0) { _ =>
        Time.now mustEqual t0
        Thread.sleep(50)
        Time.now mustEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs must beLessThan(20L)
    }

    "withTimeAt nested" in {
      val t0 = new Time(123456789L)
      val t1 = t0 + 10.minutes
      Time.withTimeAt(t0) { _ =>
        Time.now mustEqual t0
        Time.withTimeAt(t1) { _ =>
          Time.now mustEqual t1
        }
        Time.now mustEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs must beLessThan(20L)
    }

    "withCurrentTimeFrozen" in {
      val t0 = new Time(123456789L)
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now
        Thread.sleep(50)
        Time.now mustEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs must beLessThan(20L)
    }

    "advance" in {
      val t0 = new Time(123456789L)
      val delta = 5.seconds
      Time.withTimeAt(t0) { tc =>
        Time.now mustEqual t0
        tc.advance(delta)
        Time.now mustEqual (t0 + delta)
      }
      (Time.now.inMillis - System.currentTimeMillis).abs must beLessThan(20L)
    }

    "compare" in {
      10.seconds.afterEpoch must be_<(11.seconds.afterEpoch)
      10.seconds.afterEpoch must be_==(10.seconds.afterEpoch)
      11.seconds.afterEpoch must be_>(10.seconds.afterEpoch)
      Time(Long.MaxValue) must be_>(Time.now)
    }

    "+ delta" in {
      10.seconds.afterEpoch + 5.seconds mustEqual 15.seconds.afterEpoch
    }

    "- delta" in {
      10.seconds.afterEpoch - 5.seconds mustEqual 5.seconds.afterEpoch
    }

    "- time" in {
      10.seconds.afterEpoch - 5.seconds.afterEpoch mustEqual 5.seconds
    }

    "max" in {
      10.seconds.afterEpoch max 5.seconds.afterEpoch mustEqual 10.seconds.afterEpoch
      5.seconds.afterEpoch max 10.seconds.afterEpoch mustEqual 10.seconds.afterEpoch
    }

    "min" in {
      10.seconds.afterEpoch min 5.seconds.afterEpoch mustEqual 5.seconds.afterEpoch
      5.seconds.afterEpoch min 10.seconds.afterEpoch mustEqual 5.seconds.afterEpoch
    }
  }

  "Duration" should {
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
  }
}
