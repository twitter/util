package com.twitter.util

import TimeConversions._
import java.io.{ByteArrayOutputStream, ByteArrayInputStream, 
  ObjectOutputStream, ObjectInputStream}
import java.util.concurrent.TimeUnit
import java.util.Locale
import org.specs.SpecificationWithJUnit

trait TimeLikeSpec[T <: TimeLike[T]] extends SpecificationWithJUnit {
  val ops: TimeLikeOps[T]
  import ops._

  "Top, Bottom, Undefined, Nanoseconds(_), Finite(_)" should {
    val easyVs = Seq(Zero, Top, Bottom, Undefined,
      fromNanoseconds(1), fromNanoseconds(-1))
    val vs = easyVs ++ Seq(
      fromNanoseconds(Long.MaxValue-1),
      fromNanoseconds(Long.MinValue+1))

    "behave like boxed doubles" in {
      Top compare Undefined must be_<(0)
      Bottom compare Top must be_<(0)
      Undefined compare Undefined must be_==(0)
      Top compare Top must be_==(0)
      Bottom compare Bottom must be_==(0)

      Top + Duration.Top must be_==(Top)
      Bottom - Duration.Bottom must be_==(Undefined)
      Top - Duration.Top must be_==(Undefined)
      Bottom + Duration.Bottom must be_==(Bottom)
    }

    "complementary diff" in {
      // Note that this doesn't always hold because of two's
      // complement arithmetic.
      for (a <- easyVs; b <- easyVs)
        a diff b must be_==(-(b diff a))

    }

    "complementary compare" in {
      for (a <- vs; b <- vs) {
        val x = a compare b
        val y = b compare a
        (x == 0 && y == 0) || (x < 0 != y < 0) must beTrue
      }
    }

    "commutative max" in {
      for (a <- vs; b <- vs)
        a max b must be_==(b max a)
    }

    "commutative min" in {
      for (a <- vs; b <- vs)
        a min b must be_==(b min a)
    }

    "handle underflows" in {
      fromNanoseconds(Long.MinValue) - 1.nanosecond must be_==(Bottom)
      fromMicroseconds(Long.MinValue) - 1.nanosecond must be_==(Bottom)
    }

    "handle overflows" in {
      fromNanoseconds(Long.MaxValue) + 1.nanosecond must be_==(Top)
      fromMicroseconds(Long.MaxValue) + 1.nanosecond must be_==(Top)
    }

    "Nanoseconds(_) extracts only finite values, in nanoseconds" in {
      for (t <- Seq(Top, Bottom, Undefined))
        t mustNot beLike { case Nanoseconds(_) => true }

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        t must beLike { case Nanoseconds(`ns`) => true }
    }

    "Finite(_) extracts only finite values" in {
      for (t <- Seq(Top, Bottom, Undefined))
        t mustNot beLike { case Finite(_) => true }

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        t must beLike { case Finite(`t`) => true }
    }
    
    "roundtrip through serialization" in {
      for (v <- vs) {
        val bytes = new ByteArrayOutputStream
        val out = new ObjectOutputStream(bytes)
        out.writeObject(v)
        val in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
        in.readObject() must be_==(v)
      }
    }
  }

  "Top" should {
    "be impermeable to finite arithmetic" in {
      Top - 0.seconds must be_==(Top)
      Top - 100.seconds must be_==(Top)
      Top - Duration.fromNanoseconds(Long.MaxValue) must be_==(Top)
    }

    "become undefined when subtracted from itself, or added to bottom" in {
      Top - Duration.Top must be_==(Undefined)
      Top + Duration.Bottom must be_==(Undefined)
    }

    "not be equal to the maximum value" in {
      fromNanoseconds(Long.MaxValue) mustNot be_==(Top)
    }

    "always be max" in {
      Top max fromSeconds(1) must be_==(Top)
      Top max fromNanoseconds(Long.MaxValue) must be_==(Top)
      Top max Bottom must be_==(Top)
    }

    "greater than everything else" in {
      fromSeconds(0) must be_<(Top)
      fromNanoseconds(Long.MaxValue) must be_<(Top)
    }

    "equal to itself" in {
      Top must be_==(Top)
    }

    "more or less equals only to itself" in {
      Top.moreOrLessEquals(Top, Duration.Top) must beTrue
      Top.moreOrLessEquals(Top, Duration.Zero) must beTrue
      Top.moreOrLessEquals(Bottom, Duration.Top) must beTrue
      Top.moreOrLessEquals(Bottom, Duration.Zero) must beFalse
      Top.moreOrLessEquals(fromSeconds(0), Duration.Top) must beTrue
      Top.moreOrLessEquals(fromSeconds(0), Duration.Bottom) must beFalse
    }

    "Undefined diff to Top" in {
      Top diff Top must be_==(Duration.Undefined)
    }
  }

  "Bottom" should {
    "be impermeable to finite arithmetic" in {
      Bottom + 0.seconds must be_==(Bottom)
      Bottom + 100.seconds must be_==(Bottom)
      Bottom + Duration.fromNanoseconds(Long.MaxValue) must be_==(Bottom)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      Bottom + Duration.Top must be_==(Undefined)
      Bottom - Duration.Bottom must be_==(Undefined)
    }

    "always be min" in {
      Bottom min Top must be_==(Bottom)
      Bottom min fromNanoseconds(0) must be_==(Bottom)
    }

    "less than everything else" in {
      Bottom must be_<(fromSeconds(0))
      Bottom must be_<(fromNanoseconds(Long.MaxValue))
      Bottom must be_<(fromNanoseconds(Long.MinValue))
    }

    "less than Top" in {
      Bottom must be_<(Top)
    }

    "equal to itself" in {
      Bottom must be_==(Bottom)
    }

    "more or less equals only to itself" in {
      Bottom.moreOrLessEquals(Bottom, Duration.Top) must beTrue
      Bottom.moreOrLessEquals(Bottom, Duration.Zero) must beTrue
      Bottom.moreOrLessEquals(Top, Duration.Bottom) must beFalse
      Bottom.moreOrLessEquals(Top, Duration.Zero) must beFalse
      Bottom.moreOrLessEquals(fromSeconds(0), Duration.Top) must beTrue
      Bottom.moreOrLessEquals(fromSeconds(0), Duration.Bottom) must beFalse
    }


    "Undefined diff to Bottom" in {
      Bottom diff Bottom must be_==(Duration.Undefined)
    }
  }

  "Undefined" should {
    "be impermeable to any arithmetic" in {
      Undefined + 0.seconds must be_==(Undefined)
      Undefined + 100.seconds must be_==(Undefined)
      Undefined + Duration.fromNanoseconds(Long.MaxValue) must be_==(Undefined)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      Undefined + Duration.Top must be_==(Undefined)
      Undefined - Duration.Undefined must be_==(Undefined)
    }

    "always be max" in {
      Undefined max Top must be_==(Undefined)
      Undefined max fromNanoseconds(0) must be_==(Undefined)
    }

    "greater than everything else" in {
      fromSeconds(0) must be_<(Undefined)
      Top must be_<(Undefined)
      fromNanoseconds(Long.MaxValue) must be_<(Undefined)
    }

    "equal to itself" in {
      Undefined must be_==(Undefined)
    }

    "not more or less equal to anything" in {
      Undefined.moreOrLessEquals(Undefined, Duration.Top) must beFalse
      Undefined.moreOrLessEquals(Undefined, Duration.Zero) must beFalse
      Undefined.moreOrLessEquals(Top, Duration.Undefined) must beTrue
      Undefined.moreOrLessEquals(Top, Duration.Zero) must beFalse
      Undefined.moreOrLessEquals(fromSeconds(0), Duration.Top) must beFalse
      Undefined.moreOrLessEquals(fromSeconds(0), Duration.Undefined) must beTrue
    }

    "Undefined on diff" in {
      Undefined diff Top must be_==(Duration.Undefined)
      Undefined diff Bottom must be_==(Duration.Undefined)
      Undefined diff fromNanoseconds(123) must be_==(Duration.Undefined)
    }
  }

  "values" should {
    "reflect their underlying value" in {
      val nss = Seq(
        1040403005001003L,  // 12.days+1.hour+3.seconds+5.milliseconds+1.microsecond+3.nanoseconds
        123000000000L,  // 123.seconds
        1L
      )

      for (ns <- nss) {
        val t = fromNanoseconds(ns)
        t.inNanoseconds must be_==(ns)
        t.inMicroseconds must be_==(ns/1000L)
        t.inMilliseconds must be_==(ns/1000000L)
        t.inSeconds must be_==(ns/1000000000L)
        t.inMinutes must be_==(ns/60000000000L)
        t.inHours must be_==(ns/3600000000000L)
        t.inDays must be_==(ns/86400000000000L)
      }
    }
  }

  "floor" should {
    "round down" in {
      fromSeconds(60).floor(1.minute) must be_==(fromSeconds(60))
      fromSeconds(100).floor(1.minute) must be_==(fromSeconds(60))
      fromSeconds(119).floor(1.minute) must be_==(fromSeconds(60))
      fromSeconds(120).floor(1.minute) must be_==(fromSeconds(120))
    }

    "maintain top and bottom" in {
      Top.floor(1.hour) must be_==(Top)
    }

    "divide by zero" in {
      Zero.floor(Duration.Zero) must be_==(Undefined)
      fromSeconds(1).floor(Duration.Zero) must be_==(Top)
      fromSeconds(-1).floor(Duration.Zero) must be_==(Bottom)
    }

    "deal with undefineds" in {
      Bottom.floor(1.second) must be_==(Bottom)
      Undefined.floor(0.seconds) must be_==(Undefined)
      Undefined.floor(Duration.Top) must be_==(Undefined)
      Undefined.floor(Duration.Bottom) must be_==(Undefined)
      Undefined.floor(Duration.Undefined) must be_==(Undefined)
    }

    "floor itself" in {
      for (s <- Seq(Long.MinValue, -1, 1, Long.MaxValue); t = fromNanoseconds(s))
        t.floor(Duration.fromNanoseconds(t.inNanoseconds)) must be_==(t)
    }
  }

  "from*" should {
    "never over/under flow nanos" in {
      for (v <- Seq(Long.MinValue, Long.MaxValue)) {
        fromNanoseconds(v) must beLike {
          case Nanoseconds(ns) => ns == v
        }
      }
    }

    "overflow millis" in {
      val millis = TimeUnit.NANOSECONDS.toMillis(Long.MaxValue)
      fromMilliseconds(millis) must beLike {
        case Nanoseconds(ns) => ns == millis*1e6
      }
      fromMilliseconds(millis+1) must be_==(Top)
    }

    "underflow millis" in {
      val millis = TimeUnit.NANOSECONDS.toMillis(Long.MinValue)
      fromMilliseconds(millis) must beLike {
        case Nanoseconds(ns) => ns == millis*1e6
      }
      fromMilliseconds(millis-1) must be_==(Bottom)
    }
  }
}

class TimeFormatSpec extends SpecificationWithJUnit {
  "TimeFormat" should {
    "format correctly with non US locale" in {
      val locale = Locale.GERMAN
      val format = "EEEE"
      val timeFormat = new TimeFormat(format, Some(locale))
      val day = "Donnerstag"
      timeFormat.parse(day).format(format, locale) mustEqual day
    }
  }
}

class TimeSpec extends  { val ops = Time } with TimeLikeSpec[Time] {
  "Time" should {
    "work in collections" in {
      val t0 = Time.fromSeconds(100)
      val t1 = Time.fromSeconds(100)
      t0 mustEqual t1
      t0.hashCode mustEqual t1.hashCode
      val pairs = List((t0, "foo"), (t1, "bar"))
      pairs.groupBy { case (time: Time, value: String) => time } mustEqual Map(t0 -> pairs)
    }

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

    "withTimeAt threaded" in {
      val t0 = new Time(314159L)
      val t1 = new Time(314160L)
      Time.withTimeAt(t0) { tc =>
        Time.now mustEqual t0
        Thread.sleep(50)
        Time.now mustEqual t0
        tc.advance(Duration.fromNanoseconds(1))
        Time.now mustEqual t1
        tc.set(t0)
        Time.now mustEqual t0
        @volatile var threadTime: Option[Time] = None
        val thread = new Thread {
          override def run() {
            threadTime = Some(Time.now)
          }
        }
        thread.start()
        thread.join()
        threadTime.get mustNotEq t0
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

    "fromMicroseconds" in {
      Time.fromMicroseconds(0).inNanoseconds mustEqual 0L
      Time.fromMicroseconds(-1).inNanoseconds mustEqual -1L * 1000L

      Time.fromMicroseconds(Long.MaxValue).inNanoseconds mustEqual Long.MaxValue
      Time.fromMicroseconds(Long.MaxValue-1) must be_==(Time.Top)

      Time.fromMicroseconds(Long.MinValue) must be_==(Time.Bottom)
      Time.fromMicroseconds(Long.MinValue+1) must be_==(Time.Bottom)

      val currentTimeMicros = System.currentTimeMillis()*1000
      Time.fromMicroseconds(currentTimeMicros).inNanoseconds mustEqual(currentTimeMicros.microseconds.inNanoseconds)
    }

    "fromMillis" in {
      Time.fromMilliseconds(0).inNanoseconds mustEqual 0L
      Time.fromMilliseconds(-1).inNanoseconds mustEqual -1L * 1000000L

      Time.fromMilliseconds(Long.MaxValue).inNanoseconds mustEqual Long.MaxValue
      Time.fromMilliseconds(Long.MaxValue-1) must be_==(Time.Top)

      Time.fromMilliseconds(Long.MinValue) must be_==(Time.Bottom)
      Time.fromMilliseconds(Long.MinValue+1) must be_==(Time.Bottom)

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
  }
}
