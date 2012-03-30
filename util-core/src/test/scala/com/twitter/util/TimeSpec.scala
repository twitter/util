package com.twitter.util

import scala.math.BigInt
import scala.util.Random
import org.specs.SpecificationWithJUnit
import TimeConversions._

class TimeSpec extends SpecificationWithJUnit {
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

    "withTimeFunction" in {
      val t0 = Time.now
      var t = t0
      Time.withTimeFunction(t) { _ =>
        Time.now mustEqual t0
        Thread.sleep(50)
        Time.now mustEqual t0
        val delta = 100.milliseconds
        t += delta
        Time.now mustEqual t0 + delta
      }
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
      Time.fromMilliseconds(Long.MaxValue) must be_>(Time.now)
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

    "moreOrLessEquals" in {
      val now = Time.now
      now.moreOrLessEquals(now + 1.second, 1.second) must beTrue
      now.moreOrLessEquals(now - 1.seconds, 1.second) must beTrue
      now.moreOrLessEquals(now + 2.seconds, 1.second) must beFalse
      now.moreOrLessEquals(now - 2.seconds, 1.second) must beFalse
    }

    "floor" in {
      val format = new TimeFormat("yyyy-MM-dd HH:mm:ss.SSS")
      val t0 = format.parse("2010-12-24 11:04:07.567")
      t0.floor(1.millisecond) mustEqual t0
      t0.floor(10.milliseconds) mustEqual format.parse("2010-12-24 11:04:07.560")
      t0.floor(1.second) mustEqual format.parse("2010-12-24 11:04:07.000")
      t0.floor(5.second) mustEqual format.parse("2010-12-24 11:04:05.000")
      t0.floor(1.minute) mustEqual format.parse("2010-12-24 11:04:00.000")
      t0.floor(1.hour) mustEqual format.parse("2010-12-24 11:00:00.000")
    }

    "since" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      t1.since(t0) mustEqual 10.seconds
      t0.since(t1) mustEqual (-10).seconds
    }

    "sinceEpoch" in {
      val t0 = Time.epoch + 100.hours
      t0.sinceEpoch mustEqual 100.hours
    }

    "sinceNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now + 100.hours
        t0.sinceNow mustEqual 100.hours
      }
    }

    "fromMillis" in {
      Time.fromMilliseconds(0).inNanoseconds mustEqual 0L
      Time.fromMilliseconds(-1).inNanoseconds mustEqual -1L * 1000000L

      Time.fromMilliseconds(Long.MaxValue).inNanoseconds mustEqual Long.MaxValue
      Time.fromMilliseconds(Long.MaxValue-1) must throwA[TimeOverflowException]

      Time.fromMilliseconds(Long.MinValue) must throwA[TimeOverflowException]
      Time.fromMilliseconds(Long.MinValue+1) must throwA[TimeOverflowException]

      val currentTimeMs = System.currentTimeMillis
      Time.fromMilliseconds(currentTimeMs).inNanoseconds mustEqual(currentTimeMs * 1000000L)
    }

    "until" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      t0.until(t1) mustEqual 10.seconds
      t1.until(t0) mustEqual (-10).seconds
    }

    "untilEpoch" in {
      val t0 = Time.epoch - 100.hours
      t0.untilEpoch mustEqual 100.hours
    }

    "untilNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now - 100.hours
        t0.untilNow mustEqual 100.hours
      }
    }

    "preserve MaxValue" in {
      Long.MaxValue.nanoseconds.inSeconds mustEqual Int.MaxValue
      Long.MaxValue.seconds.inMicroseconds mustEqual Long.MaxValue
      Time.fromMilliseconds(Long.MaxValue).inSeconds mustEqual Int.MaxValue
      Time.fromMilliseconds(Long.MaxValue).inMilliseconds mustEqual Long.MaxValue
      Time.fromMilliseconds(Long.MaxValue).inNanoseconds mustEqual Long.MaxValue
    }
  }

  "TimeMath" should {
    val random = new Random
    val maxSqrt = 3037000499L

    def randLong = {
      if (random.nextInt > 0)
        random.nextLong % maxSqrt
      else
        random.nextLong
    }

    "add" in {
      def test(a: Long, b: Long) {
        val bigC = BigInt(a) + BigInt(b)
        if (bigC.abs > BigInt.MaxLong)
          TimeMath.add(a, b) must throwA[TimeOverflowException]
        else
          TimeMath.add(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong, randLong)
      }
    }

    "sub" in {
      def test(a: Long, b: Long) {
        val bigC = BigInt(a) - BigInt(b)
        if (bigC.abs > BigInt.MaxLong)
          TimeMath.sub(a, b) must throwA[TimeOverflowException]
        else
          TimeMath.sub(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong, randLong)
      }
    }

    "mul" in {
      TimeMath.mul(0L, 10L) mustEqual 0L
      TimeMath.mul(1L, 11L) mustEqual 11L
      TimeMath.mul(-1L, -11L) mustEqual 11L
      TimeMath.mul(-1L, 22L) mustEqual -22L
      TimeMath.mul(22L, -1L) mustEqual -22L

      TimeMath.mul(3456116450671355229L, -986247066L) must throwA[TimeOverflowException]

      TimeMath.mul(Long.MaxValue, 9L) mustEqual Long.MaxValue
      TimeMath.mul(Long.MaxValue, 1L) mustEqual Long.MaxValue
      TimeMath.mul(Long.MaxValue, 0L) mustEqual Long.MaxValue // this is a strange case, huh?
      TimeMath.mul(Long.MaxValue - 1L, 9L) must throwA[TimeOverflowException]

      TimeMath.mul(Long.MinValue, 2L) must throwA[TimeOverflowException]
      TimeMath.mul(Long.MinValue, -2L) must throwA[TimeOverflowException]
      TimeMath.mul(Long.MinValue, 3L) must throwA[TimeOverflowException]
      TimeMath.mul(Long.MinValue, -3L) must throwA[TimeOverflowException]
      TimeMath.mul(Long.MinValue, 1L) mustEqual Long.MinValue
      TimeMath.mul(Long.MinValue, -1L) must throwA[TimeOverflowException]
      TimeMath.mul(1L, Long.MinValue) mustEqual Long.MinValue
      TimeMath.mul(-1L, Long.MinValue) must throwA[TimeOverflowException]
      TimeMath.mul(Long.MinValue, 0L) mustEqual 0L
      TimeMath.mul(Long.MinValue + 1L, 2L) must throwA[TimeOverflowException]

      def test(a: Long, b: Long) {
        val bigC = BigInt(a) * BigInt(b)
        if (bigC.abs > BigInt.MaxLong)
          TimeMath.mul(a, b) must throwA[TimeOverflowException]
        else
          TimeMath.mul(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        val a = randLong
        val b = randLong
        try {
          test(a, b)
        } catch {
          case x => {
            println(a + " * " + b + " failed")
            throw x
          }
        }
      }
    }
  }
}
