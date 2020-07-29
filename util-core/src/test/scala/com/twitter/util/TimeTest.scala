package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.{Locale, TimeZone}
import java.util.concurrent.TimeUnit

import org.scalatest.WordSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import com.twitter.conversions.DurationOps._

trait TimeLikeSpec[T <: TimeLike[T]] extends WordSpec with ScalaCheckDrivenPropertyChecks {
  val ops: TimeLikeOps[T]
  import ops._

  "Top, Bottom, Undefined, Nanoseconds(_), Finite(_)" should {
    val easyVs = Seq(Zero, Top, Bottom, Undefined, fromNanoseconds(1), fromNanoseconds(-1))
    val vs = easyVs ++ Seq(fromNanoseconds(Long.MaxValue - 1), fromNanoseconds(Long.MinValue + 1))

    "behave like boxed doubles" in {
      assert((Top compare Undefined) < 0)
      assert((Bottom compare Top) < 0)
      assert((Undefined compare Undefined) == 0)
      assert((Top compare Top) == 0)
      assert((Bottom compare Bottom) == 0)

      assert(Top + Duration.Top == Top)
      assert(Bottom - Duration.Bottom == Undefined)
      assert(Top - Duration.Top == Undefined)
      assert(Bottom + Duration.Bottom == Bottom)
    }

    "complementary diff" in {
      // Note that this doesn't always hold because of two's
      // complement arithmetic.
      for (a <- easyVs; b <- easyVs)
        assert((a diff b) == -(b diff a))

    }

    "complementary compare" in {
      for (a <- vs; b <- vs) {
        val x = a compare b
        val y = b compare a
        assert(((x == 0 && y == 0) || (x < 0 != y < 0)) == true)
      }
    }

    "commutative max" in {
      for (a <- vs; b <- vs)
        assert((a max b) == (b max a))
    }

    "commutative min" in {
      for (a <- vs; b <- vs)
        assert((a min b) == (b min a))
    }

    "handle underflows" in {
      assert(fromNanoseconds(Long.MinValue) - 1.nanosecond == Bottom)
      assert(fromMicroseconds(Long.MinValue) - 1.nanosecond == Bottom)
    }

    "handle overflows" in {
      assert(fromNanoseconds(Long.MaxValue) + 1.nanosecond == Top)
      assert(fromMicroseconds(Long.MaxValue) + 1.nanosecond == Top)
    }

    "Nanoseconds(_) extracts only finite values, in nanoseconds" in {
      for (t <- Seq(Top, Bottom, Undefined))
        assert(t match {
          case Nanoseconds(_) => false
          case _ => true
        })

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        assert(t match {
          case Nanoseconds(`ns`) => true
          case _ => false
        })
    }

    "Finite(_) extracts only finite values" in {
      for (t <- Seq(Top, Bottom, Undefined))
        assert(t match {
          case Finite(_) => false
          case _ => true
        })

      for (ns <- Seq(Long.MinValue, -1, 0, 1, Long.MaxValue); t = fromNanoseconds(ns))
        assert(t match {
          case Finite(`t`) => true
          case _ => false
        })
    }

    "roundtrip through serialization" in {
      for (v <- vs) {
        val bytes = new ByteArrayOutputStream
        val out = new ObjectOutputStream(bytes)
        out.writeObject(v)
        val in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
        assert(in.readObject() == v)
      }
    }

    "has correct isZero behaviour" in {
      for (t <- Seq(Top, Bottom, Undefined, fromNanoseconds(1L))) {
        assert(t.isZero == false)
      }

      for (z <- Seq(
          Zero,
          fromNanoseconds(0),
          fromMicroseconds(0),
          fromFractionalSeconds(0),
          fromMilliseconds(0),
          fromSeconds(0),
          fromMinutes(0)
        )) {
        assert(z.isZero == true)
      }
    }
  }

  "Top" should {
    "be impermeable to finite arithmetic" in {
      assert(Top - 0.seconds == Top)
      assert(Top - 100.seconds == Top)
      assert(Top - Duration.fromNanoseconds(Long.MaxValue) == Top)
    }

    "become undefined when subtracted from itself, or added to bottom" in {
      assert(Top - Duration.Top == Undefined)
      assert(Top + Duration.Bottom == Undefined)
    }

    "not be equal to the maximum value" in {
      assert(fromNanoseconds(Long.MaxValue) != Top)
    }

    "always be max" in {
      assert((Top max fromSeconds(1)) == Top)
      assert((Top max fromFractionalSeconds(1.0)) == Top)
      assert((Top max fromNanoseconds(Long.MaxValue)) == Top)
      assert((Top max Bottom) == Top)
    }

    "greater than everything else" in {
      assert(fromSeconds(0) < Top)
      assert(fromFractionalSeconds(Double.MaxValue) < Top)
      assert(fromNanoseconds(Long.MaxValue) < Top)
    }

    "equal to itself" in {
      assert(Top == Top)
    }

    "more or less equals only to itself" in {
      assert(Top.moreOrLessEquals(Top, Duration.Top) == true)
      assert(Top.moreOrLessEquals(Top, Duration.Zero) == true)
      assert(Top.moreOrLessEquals(Bottom, Duration.Top) == true)
      assert(Top.moreOrLessEquals(Bottom, Duration.Zero) == false)
      assert(Top.moreOrLessEquals(fromSeconds(0), Duration.Top) == true)
      assert(Top.moreOrLessEquals(fromSeconds(0), Duration.Bottom) == false)
    }

    "Undefined diff to Top" in {
      assert((Top diff Top) == Duration.Undefined)
    }
  }

  "Bottom" should {
    "be impermeable to finite arithmetic" in {
      assert(Bottom + 0.seconds == Bottom)
      assert(Bottom + 100.seconds == Bottom)
      assert(Bottom + Duration.fromNanoseconds(Long.MaxValue) == Bottom)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      assert(Bottom + Duration.Top == Undefined)
      assert(Bottom - Duration.Bottom == Undefined)
    }

    "always be min" in {
      assert((Bottom min Top) == Bottom)
      assert((Bottom min fromNanoseconds(0)) == Bottom)
    }

    "less than everything else" in {
      assert(Bottom < fromSeconds(0))
      assert(Bottom < fromNanoseconds(Long.MaxValue))
      assert(Bottom < fromNanoseconds(Long.MinValue))
    }

    "less than Top" in {
      assert(Bottom < Top)
    }

    "equal to itself" in {
      assert(Bottom == Bottom)
    }

    "more or less equals only to itself" in {
      assert(Bottom.moreOrLessEquals(Bottom, Duration.Top) == true)
      assert(Bottom.moreOrLessEquals(Bottom, Duration.Zero) == true)
      assert(Bottom.moreOrLessEquals(Top, Duration.Bottom) == false)
      assert(Bottom.moreOrLessEquals(Top, Duration.Zero) == false)
      assert(Bottom.moreOrLessEquals(fromSeconds(0), Duration.Top) == true)
      assert(Bottom.moreOrLessEquals(fromSeconds(0), Duration.Bottom) == false)
    }

    "Undefined diff to Bottom" in {
      assert((Bottom diff Bottom) == Duration.Undefined)
    }
  }

  "Undefined" should {
    "be impermeable to any arithmetic" in {
      assert(Undefined + 0.seconds == Undefined)
      assert(Undefined + 100.seconds == Undefined)
      assert(Undefined + Duration.fromNanoseconds(Long.MaxValue) == Undefined)
    }

    "become undefined when added with Top or subtracted by bottom" in {
      assert(Undefined + Duration.Top == Undefined)
      assert(Undefined - Duration.Undefined == Undefined)
    }

    "always be max" in {
      assert((Undefined max Top) == Undefined)
      assert((Undefined max fromNanoseconds(0)) == Undefined)
    }

    "greater than everything else" in {
      assert(fromSeconds(0) < Undefined)
      assert(Top < Undefined)
      assert(fromNanoseconds(Long.MaxValue) < Undefined)
    }

    "equal to itself" in {
      assert(Undefined == Undefined)
    }

    "not more or less equal to anything" in {
      assert(Undefined.moreOrLessEquals(Undefined, Duration.Top) == false)
      assert(Undefined.moreOrLessEquals(Undefined, Duration.Zero) == false)
      assert(Undefined.moreOrLessEquals(Top, Duration.Undefined) == true)
      assert(Undefined.moreOrLessEquals(Top, Duration.Zero) == false)
      assert(Undefined.moreOrLessEquals(fromSeconds(0), Duration.Top) == false)
      assert(Undefined.moreOrLessEquals(fromSeconds(0), Duration.Undefined) == true)
    }

    "Undefined on diff" in {
      assert((Undefined diff Top) == Duration.Undefined)
      assert((Undefined diff Bottom) == Duration.Undefined)
      assert((Undefined diff fromNanoseconds(123)) == Duration.Undefined)
    }
  }

  "values" should {
    "reflect their underlying value" in {
      val nss = Seq(
        2592000000000000000L, // 30000.days
        1040403005001003L, // 12.days+1.hour+3.seconds+5.milliseconds+1.microsecond+3.nanoseconds
        123000000000L, // 123.seconds
        1L
      )

      for (ns <- nss) {
        val t = fromNanoseconds(ns)
        assert(t.inNanoseconds == ns)
        assert(t.inMicroseconds == ns / 1000L)
        assert(t.inMilliseconds == ns / 1000000L)
        assert(t.inLongSeconds == ns / 1000000000L)
        assert(t.inMinutes == ns / 60000000000L)
        assert(t.inHours == ns / 3600000000000L)
        assert(t.inDays == ns / 86400000000000L)
      }
    }
  }

  "inSeconds" should {
    "equal inLongSeconds when in 32-bit range" in {
      val nss = Seq(
        315370851000000000L, // 3650.days+3.hours+51.seconds
        1040403005001003L, // 12.days+1.hour+3.seconds+5.milliseconds+1.microsecond+3.nanoseconds
        1L
      )
      for (ns <- nss) {
        val t = fromNanoseconds(ns)
        assert(t.inLongSeconds == t.inSeconds)
      }
    }
    "clamp value to Int.MinValue or MaxValue when out of range" in {
      val longNs = 2160000000000000000L // 25000.days
      assert(fromNanoseconds(longNs).inSeconds == Int.MaxValue)
      assert(fromNanoseconds(-longNs).inSeconds == Int.MinValue)
    }
  }

  "rounding" should {

    "maintain top and bottom" in {
      assert(Top.floor(1.hour) == Top)
      assert(Bottom.floor(1.hour) == Bottom)
    }

    "divide by zero" in {
      assert(Zero.floor(Duration.Zero) == Undefined)
      assert(fromSeconds(1).floor(Duration.Zero) == Top)
      assert(fromSeconds(-1).floor(Duration.Zero) == Bottom)
    }

    "deal with undefineds" in {
      assert(Undefined.floor(0.seconds) == Undefined)
      assert(Undefined.floor(Duration.Top) == Undefined)
      assert(Undefined.floor(Duration.Bottom) == Undefined)
      assert(Undefined.floor(Duration.Undefined) == Undefined)
    }

    "round to itself" in {
      for (s <- Seq(Long.MinValue, -1, 1, Long.MaxValue); t = s.nanoseconds)
        assert(t.floor(t.inNanoseconds.nanoseconds) == t)
    }
  }

  "floor" should {
    "round down" in {
      assert(60.seconds.floor(1.minute) == 60.seconds)
      assert(100.seconds.floor(1.minute) == 60.seconds)
      assert(119.seconds.floor(1.minute) == 60.seconds)
      assert(120.seconds.floor(1.minute) == 120.seconds)
    }
  }

  "ceiling" should {
    "round up" in {
      assert(60.seconds.ceil(1.minute) == 60.seconds)
      assert(100.seconds.ceil(1.minute) == 120.seconds)
      assert(119.seconds.ceil(1.minute) == 120.seconds)
      assert(120.seconds.ceil(1.minute) == 120.seconds)
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
        case Nanoseconds(ns) => assert(ns == millis * 1e6)
      }
      assert(fromMilliseconds(millis + 1) == Top)
    }

    "underflow millis" in {
      val millis = TimeUnit.NANOSECONDS.toMillis(Long.MinValue)
      fromMilliseconds(millis) match {
        case Nanoseconds(ns) => assert(ns == millis * 1e6)
      }
      assert(fromMilliseconds(millis - 1) == Bottom)
    }
  }
}

class TimeFormatTest extends WordSpec {
  "TimeFormat" should {
    "format correctly with non US locale" in {
      val locale = Locale.GERMAN
      val format = "EEEE"
      val timeFormat = new TimeFormat(format, Some(locale))
      val day = "Donnerstag"
      assert(timeFormat.parse(day).format(format, locale) == day)
    }

    "set UTC timezone as default" in {
      val format = "HH:mm"
      val timeFormat = new TimeFormat(format)
      assert(timeFormat.parse("16:04").format(format) == "16:04")
    }

    "set non-UTC timezone correctly" in {
      val format = "HH:mm"
      val timeFormat = new TimeFormat(format, TimeZone.getTimeZone("EST"))
      assert(timeFormat.parse("16:04").format(format) == "21:04")
    }
  }
}

class TimeTest extends { val ops: Time.type = Time } with TimeLikeSpec[Time] with Eventually
with IntegrationPatience {

  "Time" should {
    "work in collections" in {
      val t0 = Time.fromSeconds(100)
      val t1 = Time.fromSeconds(100)
      assert(t0 == t1)
      assert(t0.hashCode == t1.hashCode)
      val pairs = List((t0, "foo"), (t1, "bar"))
      assert(pairs.groupBy { case (time: Time, value: String) => time } == Map(t0 -> pairs))
    }

    "now should be now" in {
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "withTimeAt" in {
      val t0 = new Time(123456789L)
      Time.withTimeAt(t0) { _ =>
        assert(Time.now == t0)
        Thread.sleep(50)
        assert(Time.now == t0)
      }
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "withTimeAt nested" in {
      val t0 = new Time(123456789L)
      val t1 = t0 + 10.minutes
      Time.withTimeAt(t0) { _ =>
        assert(Time.now == t0)
        Time.withTimeAt(t1) { _ => assert(Time.now == t1) }
        assert(Time.now == t0)
      }
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "withTimeAt threaded" in {
      val t0 = new Time(314159L)
      val t1 = new Time(314160L)
      Time.withTimeAt(t0) { tc =>
        assert(Time.now == t0)
        Thread.sleep(50)
        assert(Time.now == t0)
        tc.advance(Duration.fromNanoseconds(1))
        assert(Time.now == t1)
        tc.set(t0)
        assert(Time.now == t0)
        @volatile var threadTime: Option[Time] = None
        val thread = new Thread {
          override def run(): Unit = {
            threadTime = Some(Time.now)
          }
        }
        thread.start()
        thread.join()
        assert(threadTime.get != t0)
      }
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "withTimeFunction" in {
      val t0 = Time.now
      var t = t0
      Time.withTimeFunction(t) { _ =>
        assert(Time.now == t0)
        Thread.sleep(50)
        assert(Time.now == t0)
        val delta = 100.milliseconds
        t += delta
        assert(Time.now == t0 + delta)
      }
    }

    "withCurrentTimeFrozen" in {
      val t0 = new Time(123456789L)
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now
        Thread.sleep(50)
        assert(Time.now == t0)
      }
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "advance" in {
      val t0 = new Time(123456789L)
      val delta = 5.seconds
      Time.withTimeAt(t0) { tc =>
        assert(Time.now == t0)
        tc.advance(delta)
        assert(Time.now == (t0 + delta))
      }
      assert((Time.now.inMillis - System.currentTimeMillis).abs < 20L)
    }

    "sleep" in {
      Time.withCurrentTimeFrozen { ctl =>
        val ctx = Local.save()
        val r = new Runnable {
          def run(): Unit = {
            Local.restore(ctx)
            Time.sleep(5.seconds)
          }
        }
        @volatile var x = 0
        val t = new Thread(r)
        t.start()
        assert(t.isAlive == true)
        eventually { assert(t.getState == Thread.State.TIMED_WAITING) }
        ctl.advance(5.seconds)
        t.join()
        assert(t.isAlive == false)
      }
    }

    "compare" in {
      assert(10.seconds.afterEpoch < 11.seconds.afterEpoch)
      assert(10.seconds.afterEpoch == 10.seconds.afterEpoch)
      assert(11.seconds.afterEpoch > 10.seconds.afterEpoch)
      assert(Time.fromMilliseconds(Long.MaxValue) > Time.now)
    }

    "equals" in {
      assert(Time.Top == Time.Top)
      assert(Time.Top != Time.Bottom)
      assert(Time.Top != Time.Undefined)

      assert(Time.Bottom != Time.Top)
      assert(Time.Bottom == Time.Bottom)
      assert(Time.Bottom != Time.Undefined)

      assert(Time.Undefined != Time.Top)
      assert(Time.Undefined != Time.Bottom)
      assert(Time.Undefined == Time.Undefined)

      val now = Time.now
      assert(now == now)
      assert(now == Time.fromNanoseconds(now.inNanoseconds))
      assert(now != now + 1.nanosecond)
    }

    "+ delta" in {
      assert(10.seconds.afterEpoch + 5.seconds == 15.seconds.afterEpoch)
    }

    "- delta" in {
      assert(10.seconds.afterEpoch - 5.seconds == 5.seconds.afterEpoch)
    }

    "- time" in {
      assert(10.seconds.afterEpoch - 5.seconds.afterEpoch == 5.seconds)
    }

    "max" in {
      assert((10.seconds.afterEpoch max 5.seconds.afterEpoch) == 10.seconds.afterEpoch)
      assert((5.seconds.afterEpoch max 10.seconds.afterEpoch) == 10.seconds.afterEpoch)
    }

    "min" in {
      assert((10.seconds.afterEpoch min 5.seconds.afterEpoch) == 5.seconds.afterEpoch)
      assert((5.seconds.afterEpoch min 10.seconds.afterEpoch) == 5.seconds.afterEpoch)
    }

    "moreOrLessEquals" in {
      val now = Time.now
      assert(now.moreOrLessEquals(now + 1.second, 1.second) == true)
      assert(now.moreOrLessEquals(now - 1.seconds, 1.second) == true)
      assert(now.moreOrLessEquals(now + 2.seconds, 1.second) == false)
      assert(now.moreOrLessEquals(now - 2.seconds, 1.second) == false)
    }

    "floor" in {
      val format = new TimeFormat("yyyy-MM-dd HH:mm:ss.SSS")
      val t0 = format.parse("2010-12-24 11:04:07.567")
      assert(t0.floor(1.millisecond) == t0)
      assert(t0.floor(10.milliseconds) == format.parse("2010-12-24 11:04:07.560"))
      assert(t0.floor(1.second) == format.parse("2010-12-24 11:04:07.000"))
      assert(t0.floor(5.second) == format.parse("2010-12-24 11:04:05.000"))
      assert(t0.floor(1.minute) == format.parse("2010-12-24 11:04:00.000"))
      assert(t0.floor(1.hour) == format.parse("2010-12-24 11:00:00.000"))
    }

    "since" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      assert(t1.since(t0) == 10.seconds)
      assert(t0.since(t1) == (-10).seconds)
    }

    "sinceEpoch" in {
      val t0 = Time.epoch + 100.hours
      assert(t0.sinceEpoch == 100.hours)
    }

    "sinceNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now + 100.hours
        assert(t0.sinceNow == 100.hours)
      }
    }

    "fromFractionalSeconds" in {
      val tolerance = 2.microseconds // we permit 1us slop

      forAll { i: Int =>
        assert(
          Time.fromSeconds(i).moreOrLessEquals(Time.fromFractionalSeconds(i.toDouble), tolerance)
        )
      }

      forAll { d: Double =>
        val magic = 9223372036854775L // cribbed from Time.fromMicroseconds
        val microseconds = d * 1.second.inMicroseconds
        whenever(microseconds > -magic && microseconds < magic) {
          assert(
            Time
              .fromMicroseconds(microseconds.toLong)
              .moreOrLessEquals(Time.fromFractionalSeconds(d), tolerance)
          )
        }
      }

      forAll { l: Long =>
        val seconds: Double = l.toDouble / 1.second.inNanoseconds
        assert(
          Time.fromFractionalSeconds(seconds).moreOrLessEquals(Time.fromNanoseconds(l), tolerance)
        )
      }
    }

    "fromMicroseconds" in {
      assert(Time.fromMicroseconds(0).inNanoseconds == 0L)
      assert(Time.fromMicroseconds(-1).inNanoseconds == -1L * 1000L)

      assert(Time.fromMicroseconds(Long.MaxValue).inNanoseconds == Long.MaxValue)
      assert(Time.fromMicroseconds(Long.MaxValue - 1) == Time.Top)

      assert(Time.fromMicroseconds(Long.MinValue) == Time.Bottom)
      assert(Time.fromMicroseconds(Long.MinValue + 1) == Time.Bottom)

      val currentTimeMicros = System.currentTimeMillis() * 1000
      assert(
        Time
          .fromMicroseconds(currentTimeMicros)
          .inNanoseconds == currentTimeMicros.microseconds.inNanoseconds
      )
    }

    "fromMillis" in {
      assert(Time.fromMilliseconds(0).inNanoseconds == 0L)
      assert(Time.fromMilliseconds(-1).inNanoseconds == -1L * 1000000L)

      assert(Time.fromMilliseconds(Long.MaxValue).inNanoseconds == Long.MaxValue)
      assert(Time.fromMilliseconds(Long.MaxValue - 1) == Time.Top)

      assert(Time.fromMilliseconds(Long.MinValue) == Time.Bottom)
      assert(Time.fromMilliseconds(Long.MinValue + 1) == Time.Bottom)

      val currentTimeMs = System.currentTimeMillis
      assert(Time.fromMilliseconds(currentTimeMs).inNanoseconds == currentTimeMs * 1000000L)
    }

    "fromMinutes" in {
      assert(Time.fromMinutes(0).inNanoseconds == 0L)
      assert(Time.fromMinutes(-1).inNanoseconds == -60L * 1000000000L)

      assert(Time.fromMinutes(Int.MaxValue).inNanoseconds == Long.MaxValue)
      assert(Time.fromMinutes(Int.MaxValue) == Time.Top)
      assert(Time.fromMinutes(Int.MinValue) == Time.Bottom)
    }

    "fromHours" in {
      assert(Time.fromHours(1).inNanoseconds == Time.fromMinutes(60).inNanoseconds)

      assert(Time.fromHours(0).inNanoseconds == 0L)
      assert(Time.fromHours(-1).inNanoseconds == -3600L * 1000000000L)

      assert(Time.fromHours(Int.MaxValue).inNanoseconds == Long.MaxValue)
      assert(Time.fromHours(Int.MaxValue) == Time.Top)
      assert(Time.fromHours(Int.MinValue) == Time.Bottom)
    }

    "fromDays" in {
      assert(Time.fromDays(1).inNanoseconds == Time.fromHours(24).inNanoseconds)

      assert(Time.fromDays(0).inNanoseconds == 0L)
      assert(Time.fromDays(-1).inNanoseconds == -3600L * 24L * 1000000000L)

      assert(Time.fromDays(Int.MaxValue).inNanoseconds == Long.MaxValue)
      assert(Time.fromDays(Int.MaxValue) == Time.Top)
      assert(Time.fromDays(Int.MinValue) == Time.Bottom)
    }

    "until" in {
      val t0 = Time.now
      val t1 = t0 + 10.seconds
      assert(t0.until(t1) == 10.seconds)
      assert(t1.until(t0) == (-10).seconds)
    }

    "untilEpoch" in {
      val t0 = Time.epoch - 100.hours
      assert(t0.untilEpoch == 100.hours)
    }

    "untilNow" in {
      Time.withCurrentTimeFrozen { _ =>
        val t0 = Time.now - 100.hours
        assert(t0.untilNow == 100.hours)
      }
    }
  }
}
