package com.twitter.util

import TimeConversions._
import java.io.{ByteArrayOutputStream, ByteArrayInputStream, 
  ObjectOutputStream, ObjectInputStream}
import java.util.concurrent.TimeUnit
import java.util.Locale

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

trait TimeLikeSpec[T <: TimeLike[T]] extends WordSpec with ShouldMatchers {
  val ops: TimeLikeOps[T]
  import ops._

  "Top, Bottom, Undefined, Nanoseconds(_), Finite(_)" should {
    val easyVs = Seq(Zero, Top, Bottom, Undefined,
      fromNanoseconds(1), fromNanoseconds(-1))
    val vs = easyVs ++ Seq(
      fromNanoseconds(Long.MaxValue-1),
      fromNanoseconds(Long.MinValue+1))

    "behave like boxed doubles" in {
      Top compare Undefined should be <(0)
      Bottom compare Top should be <(0)
      Undefined compare Undefined shouldEqual(0)
      Top compare Top shouldEqual(0)
      Bottom compare Bottom shouldEqual(0)

      Top + Duration.Top shouldEqual(Top)
      Bottom - Duration.Bottom shouldEqual(Undefined)
      Top - Duration.Top shouldEqual(Undefined)
      Bottom + Duration.Bottom shouldEqual(Bottom)
    }

    "complementary diff" in {
      // Note that this doesn't always hold because of two's
      // complement arithmetic.
      for (a <- easyVs; b <- easyVs)
        a diff b shouldEqual(-(b diff a))

    }

    "complementary compare" in {
      for (a <- vs; b <- vs) {
        val x = a compare b
        val y = b compare a
        (x == 0 && y == 0) || (x < 0 != y < 0) shouldEqual true
      }
    }

    "commutative max" in {
      for (a <- vs; b <- vs)
        a max b shouldEqual(b max a)
    }

    "commutative min" in {
      for (a <- vs; b <- vs)
        a min b shouldEqual(b min a)
    }

    "handle underflows" in {
      fromNanoseconds(Long.MinValue) - 1.nanosecond shouldEqual(Bottom)
      fromMicroseconds(Long.MinValue) - 1.nanosecond shouldEqual(Bottom)
    }

    "handle overflows" in {
      fromNanoseconds(Long.MaxValue) + 1.nanosecond shouldEqual(Top)
      fromMicroseconds(Long.MaxValue) + 1.nanosecond shouldEqual(Top)
    }

    "Nanoseconds(_) extracts only finite values, in nanoseconds" in {
      for (t <- Seq(Top, Bottom, Undefined))
        assert(t match {
          case Nanoseconds(_) => false
          case _              => true
        })

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        assert(t match {
          case Nanoseconds(`ns`) => true
          case _                 => false
        })
    }

    "Finite(_) extracts only finite values" in {
      for (t <- Seq(Top, Bottom, Undefined))
        assert(t match {
          case Finite(_) => false
          case _         => true
        })

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        assert(t match {
          case Finite(`t`) => true
          case _           => false
        })
    }
    
    "roundtrip through serialization" in {
      for (v <- vs) {
        val bytes = new ByteArrayOutputStream
        val out = new ObjectOutputStream(bytes)
        out.writeObject(v)
        val in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
        in.readObject() shouldEqual(v)
      }
    }
  }

  "Top" should {
    "be impermeable to finite arithmetic" in {
      Top - 0.seconds shouldEqual(Top)
      Top - 100.seconds shouldEqual(Top)
      Top - Duration.fromNanoseconds(Long.MaxValue) shouldEqual(Top)
    }

    "become undefined when subtracted from itself, or added to bottom" in {
      Top - Duration.Top shouldEqual(Undefined)
      Top + Duration.Bottom shouldEqual(Undefined)
    }

    "not be equal to the maximum value" in {
      fromNanoseconds(Long.MaxValue) should not be (Top)
    }

    "always be max" in {
      Top max fromSeconds(1) shouldEqual(Top)
      Top max fromNanoseconds(Long.MaxValue) shouldEqual(Top)
      Top max Bottom shouldEqual(Top)
    }

    "greater than everything else" in {
      fromSeconds(0) should be <(Top)
      fromNanoseconds(Long.MaxValue) should be <(Top)
    }

    "equal to itself" in {
      Top shouldEqual(Top)
    }

    "more or less equals only to itself" in {
      Top.moreOrLessEquals(Top, Duration.Top) shouldEqual true
      Top.moreOrLessEquals(Top, Duration.Zero) shouldEqual true
      Top.moreOrLessEquals(Bottom, Duration.Top) shouldEqual true
      Top.moreOrLessEquals(Bottom, Duration.Zero) shouldEqual false
      Top.moreOrLessEquals(fromSeconds(0), Duration.Top) shouldEqual true
      Top.moreOrLessEquals(fromSeconds(0), Duration.Bottom) shouldEqual false
    }

    "Undefined diff to Top" in {
      Top diff Top shouldEqual(Duration.Undefined)
    }
  }

  "Bottom" should {
    "be impermeable to finite arithmetic" in {
      Bottom + 0.seconds shouldEqual(Bottom)
      Bottom + 100.seconds shouldEqual(Bottom)
      Bottom + Duration.fromNanoseconds(Long.MaxValue) shouldEqual(Bottom)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      Bottom + Duration.Top shouldEqual(Undefined)
      Bottom - Duration.Bottom shouldEqual(Undefined)
    }

    "always be min" in {
      Bottom min Top shouldEqual(Bottom)
      Bottom min fromNanoseconds(0) shouldEqual(Bottom)
    }

    "less than everything else" in {
      Bottom should be <(fromSeconds(0))
      Bottom should be <(fromNanoseconds(Long.MaxValue))
      Bottom should be <(fromNanoseconds(Long.MinValue))
    }

    "less than Top" in {
      Bottom should be <(Top)
    }

    "equal to itself" in {
      Bottom shouldEqual(Bottom)
    }

    "more or less equals only to itself" in {
      Bottom.moreOrLessEquals(Bottom, Duration.Top) shouldEqual true
      Bottom.moreOrLessEquals(Bottom, Duration.Zero) shouldEqual true
      Bottom.moreOrLessEquals(Top, Duration.Bottom) shouldEqual false
      Bottom.moreOrLessEquals(Top, Duration.Zero) shouldEqual false
      Bottom.moreOrLessEquals(fromSeconds(0), Duration.Top) shouldEqual true
      Bottom.moreOrLessEquals(fromSeconds(0), Duration.Bottom) shouldEqual false
    }


    "Undefined diff to Bottom" in {
      Bottom diff Bottom shouldEqual(Duration.Undefined)
    }
  }

  "Undefined" should {
    "be impermeable to any arithmetic" in {
      Undefined + 0.seconds shouldEqual(Undefined)
      Undefined + 100.seconds shouldEqual(Undefined)
      Undefined + Duration.fromNanoseconds(Long.MaxValue) shouldEqual(Undefined)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      Undefined + Duration.Top shouldEqual(Undefined)
      Undefined - Duration.Undefined shouldEqual(Undefined)
    }

    "always be max" in {
      Undefined max Top shouldEqual(Undefined)
      Undefined max fromNanoseconds(0) shouldEqual(Undefined)
    }

    "greater than everything else" in {
      fromSeconds(0) should be <(Undefined)
      Top should be <(Undefined)
      fromNanoseconds(Long.MaxValue) should be <(Undefined)
    }

    "equal to itself" in {
      Undefined shouldEqual(Undefined)
    }

    "not more or less equal to anything" in {
      Undefined.moreOrLessEquals(Undefined, Duration.Top) shouldEqual false
      Undefined.moreOrLessEquals(Undefined, Duration.Zero) shouldEqual false
      Undefined.moreOrLessEquals(Top, Duration.Undefined) shouldEqual true
      Undefined.moreOrLessEquals(Top, Duration.Zero) shouldEqual false
      Undefined.moreOrLessEquals(fromSeconds(0), Duration.Top) shouldEqual false
      Undefined.moreOrLessEquals(fromSeconds(0), Duration.Undefined) shouldEqual true
    }

    "Undefined on diff" in {
      Undefined diff Top shouldEqual(Duration.Undefined)
      Undefined diff Bottom shouldEqual(Duration.Undefined)
      Undefined diff fromNanoseconds(123) shouldEqual(Duration.Undefined)
    }
  }

  "values" should {
    "reflect their underlying value" in {
      val nss = Seq(
        2592000000000000000L, // 30000.days
        1040403005001003L,  // 12.days+1.hour+3.seconds+5.milliseconds+1.microsecond+3.nanoseconds
        123000000000L,  // 123.seconds
        1L
      )

      for (ns <- nss) {
        val t = fromNanoseconds(ns)
        t.inNanoseconds shouldEqual(ns)
        t.inMicroseconds shouldEqual(ns/1000L)
        t.inMilliseconds shouldEqual(ns/1000000L)
        t.inLongSeconds shouldEqual(ns/1000000000L)
        t.inMinutes shouldEqual(ns/60000000000L)
        t.inHours shouldEqual(ns/3600000000000L)
        t.inDays shouldEqual(ns/86400000000000L)
      }
    }
  }

  "inSeconds" should {
    "equal inLongSeconds when in 32-bit range" in {
      val nss = Seq(
        315370851000000000L, // 3650.days+3.hours+51.seconds
        1040403005001003L,  // 12.days+1.hour+3.seconds+5.milliseconds+1.microsecond+3.nanoseconds
        1L
      )
      for (ns <- nss) {
        val t = fromNanoseconds(ns)
        t.inLongSeconds shouldEqual(t.inSeconds)
      }
    }
    "clamp value to Int.MinValue or MaxValue when out of range" in {
      val longNs = 2160000000000000000L // 25000.days
      fromNanoseconds(longNs).inSeconds shouldEqual(Int.MaxValue)
      fromNanoseconds(-longNs).inSeconds shouldEqual(Int.MinValue)
    }
  }

  "floor" should {
    "round down" in {
      fromSeconds(60).floor(1.minute) shouldEqual(fromSeconds(60))
      fromSeconds(100).floor(1.minute) shouldEqual(fromSeconds(60))
      fromSeconds(119).floor(1.minute) shouldEqual(fromSeconds(60))
      fromSeconds(120).floor(1.minute) shouldEqual(fromSeconds(120))
    }

    "maintain top and bottom" in {
      Top.floor(1.hour) shouldEqual(Top)
    }

    "divide by zero" in {
      Zero.floor(Duration.Zero) shouldEqual(Undefined)
      fromSeconds(1).floor(Duration.Zero) shouldEqual(Top)
      fromSeconds(-1).floor(Duration.Zero) shouldEqual(Bottom)
    }

    "deal with undefineds" in {
      Bottom.floor(1.second) shouldEqual(Bottom)
      Undefined.floor(0.seconds) shouldEqual(Undefined)
      Undefined.floor(Duration.Top) shouldEqual(Undefined)
      Undefined.floor(Duration.Bottom) shouldEqual(Undefined)
      Undefined.floor(Duration.Undefined) shouldEqual(Undefined)
    }

    "floor itself" in {
      for (s <- Seq(Long.MinValue, -1, 1, Long.MaxValue); t = fromNanoseconds(s))
        t.floor(Duration.fromNanoseconds(t.inNanoseconds)) shouldEqual(t)
    }
  }

  "from*" should {
    "never over/under flow nanos" in {
      for (v <- Seq(Long.MinValue, Long.MaxValue)) {
        fromNanoseconds(v) match {
          case Nanoseconds(ns) => assert(ns == v)
        }
      }
    }

    "overflow millis" in {
      val millis = TimeUnit.NANOSECONDS.toMillis(Long.MaxValue)
      fromMilliseconds(millis) match {
        case Nanoseconds(ns) => assert(ns == millis*1e6)
      }
      fromMilliseconds(millis+1) shouldEqual(Top)
    }

    "underflow millis" in {
      val millis = TimeUnit.NANOSECONDS.toMillis(Long.MinValue)
      fromMilliseconds(millis) match {
        case Nanoseconds(ns) => assert(ns == millis*1e6)
      }
      fromMilliseconds(millis-1) shouldEqual(Bottom)
    }
  }
}

@RunWith(classOf[JUnitRunner])
class TimeFormatTest extends WordSpec with ShouldMatchers {
  "TimeFormat" should {
    "format correctly with non US locale" in {
      val locale = Locale.GERMAN
      val format = "EEEE"
      val timeFormat = new TimeFormat(format, Some(locale))
      val day = "Donnerstag"
      timeFormat.parse(day).format(format, locale) shouldEqual day
    }
  }
}

@RunWith(classOf[JUnitRunner])
class TimeTest extends  { val ops = Time } with TimeLikeSpec[Time] {
  "Time" should {
    "work in collections" in {
      val t0 = Time.fromSeconds(100)
      val t1 = Time.fromSeconds(100)
      t0 shouldEqual t1
      t0.hashCode shouldEqual t1.hashCode
      val pairs = List((t0, "foo"), (t1, "bar"))
      pairs.groupBy { case (time: Time, value: String) => time } shouldEqual Map(t0 -> pairs)
    }

    "now should be now" in {
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "withTimeAt" in {
      val t0 = new Time(123456789L)
      Time.withTimeAt(t0) { _ =>
        Time.now shouldEqual t0
        Thread.sleep(50)
        Time.now shouldEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "withTimeAt nested" in {
      val t0 = new Time(123456789L)
      val t1 = t0 + 10.minutes
      Time.withTimeAt(t0) { _ =>
        Time.now shouldEqual t0
        Time.withTimeAt(t1) { _ =>
          Time.now shouldEqual t1
        }
        Time.now shouldEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "withTimeAt threaded" in {
      val t0 = new Time(314159L)
      val t1 = new Time(314160L)
      Time.withTimeAt(t0) { tc =>
        Time.now shouldEqual t0
        Thread.sleep(50)
        Time.now shouldEqual t0
        tc.advance(Duration.fromNanoseconds(1))
        Time.now shouldEqual t1
        tc.set(t0)
        Time.now shouldEqual t0
        @volatile var threadTime: Option[Time] = None
        val thread = new Thread {
          override def run() {
            threadTime = Some(Time.now)
          }
        }
        thread.start()
        thread.join()
        threadTime.get should not be t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "withTimeFunction" in {
      val t0 = Time.now
      var t = t0
      Time.withTimeFunction(t) { _ =>
        Time.now shouldEqual t0
        Thread.sleep(50)
        Time.now shouldEqual t0
        val delta = 100.milliseconds
        t += delta
        Time.now shouldEqual t0 + delta
      }
    }

    "withCurrentTimeFrozen" in {
      val t0 = new Time(123456789L)
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now
        Thread.sleep(50)
        Time.now shouldEqual t0
      }
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "advance" in {
      val t0 = new Time(123456789L)
      val delta = 5.seconds
      Time.withTimeAt(t0) { tc =>
        Time.now shouldEqual t0
        tc.advance(delta)
        Time.now shouldEqual (t0 + delta)
      }
      (Time.now.inMillis - System.currentTimeMillis).abs should be <(20L)
    }

    "compare" in {
      10.seconds.afterEpoch should be <(11.seconds.afterEpoch)
      10.seconds.afterEpoch shouldEqual(10.seconds.afterEpoch)
      11.seconds.afterEpoch should be >(10.seconds.afterEpoch)
      Time.fromMilliseconds(Long.MaxValue) should be >(Time.now)
    }

    "+ delta" in {
      10.seconds.afterEpoch + 5.seconds shouldEqual 15.seconds.afterEpoch
    }

    "- delta" in {
      10.seconds.afterEpoch - 5.seconds shouldEqual 5.seconds.afterEpoch
    }

    "- time" in {
      10.seconds.afterEpoch - 5.seconds.afterEpoch shouldEqual 5.seconds
    }

    "max" in {
      10.seconds.afterEpoch max 5.seconds.afterEpoch shouldEqual 10.seconds.afterEpoch
      5.seconds.afterEpoch max 10.seconds.afterEpoch shouldEqual 10.seconds.afterEpoch
    }

    "min" in {
      10.seconds.afterEpoch min 5.seconds.afterEpoch shouldEqual 5.seconds.afterEpoch
      5.seconds.afterEpoch min 10.seconds.afterEpoch shouldEqual 5.seconds.afterEpoch
    }

    "moreOrLessEquals" in {
      val now = Time.now
      now.moreOrLessEquals(now + 1.second, 1.second) shouldEqual true
      now.moreOrLessEquals(now - 1.seconds, 1.second) shouldEqual true
      now.moreOrLessEquals(now + 2.seconds, 1.second) shouldEqual false
      now.moreOrLessEquals(now - 2.seconds, 1.second) shouldEqual false
    }

    "floor" in {
      val format = new TimeFormat("yyyy-MM-dd HH:mm:ss.SSS")
      val t0 = format.parse("2010-12-24 11:04:07.567")
      t0.floor(1.millisecond) shouldEqual t0
      t0.floor(10.milliseconds) shouldEqual format.parse("2010-12-24 11:04:07.560")
      t0.floor(1.second) shouldEqual format.parse("2010-12-24 11:04:07.000")
      t0.floor(5.second) shouldEqual format.parse("2010-12-24 11:04:05.000")
      t0.floor(1.minute) shouldEqual format.parse("2010-12-24 11:04:00.000")
      t0.floor(1.hour) shouldEqual format.parse("2010-12-24 11:00:00.000")
    }

    "since" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      t1.since(t0) shouldEqual 10.seconds
      t0.since(t1) shouldEqual (-10).seconds
    }

    "sinceEpoch" in {
      val t0 = Time.epoch + 100.hours
      t0.sinceEpoch shouldEqual 100.hours
    }

    "sinceNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now + 100.hours
        t0.sinceNow shouldEqual 100.hours
      }
    }

    "fromMicroseconds" in {
      Time.fromMicroseconds(0).inNanoseconds shouldEqual 0L
      Time.fromMicroseconds(-1).inNanoseconds shouldEqual -1L * 1000L

      Time.fromMicroseconds(Long.MaxValue).inNanoseconds shouldEqual Long.MaxValue
      Time.fromMicroseconds(Long.MaxValue-1) shouldEqual(Time.Top)

      Time.fromMicroseconds(Long.MinValue) shouldEqual(Time.Bottom)
      Time.fromMicroseconds(Long.MinValue+1) shouldEqual(Time.Bottom)

      val currentTimeMicros = System.currentTimeMillis()*1000
      Time.fromMicroseconds(currentTimeMicros).inNanoseconds shouldEqual(currentTimeMicros.microseconds.inNanoseconds)
    }

    "fromMillis" in {
      Time.fromMilliseconds(0).inNanoseconds shouldEqual 0L
      Time.fromMilliseconds(-1).inNanoseconds shouldEqual -1L * 1000000L

      Time.fromMilliseconds(Long.MaxValue).inNanoseconds shouldEqual Long.MaxValue
      Time.fromMilliseconds(Long.MaxValue-1) shouldEqual(Time.Top)

      Time.fromMilliseconds(Long.MinValue) shouldEqual(Time.Bottom)
      Time.fromMilliseconds(Long.MinValue+1) shouldEqual(Time.Bottom)

      val currentTimeMs = System.currentTimeMillis
      Time.fromMilliseconds(currentTimeMs).inNanoseconds shouldEqual(currentTimeMs * 1000000L)
    }

    "until" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      t0.until(t1) shouldEqual 10.seconds
      t1.until(t0) shouldEqual (-10).seconds
    }

    "untilEpoch" in {
      val t0 = Time.epoch - 100.hours
      t0.untilEpoch shouldEqual 100.hours
    }

    "untilNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now - 100.hours
        t0.untilNow shouldEqual 100.hours
      }
    }
  }
}
