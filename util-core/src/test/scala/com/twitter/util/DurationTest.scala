/*
 * Copyright 2010 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import com.twitter.conversions.time._
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DurationTest extends { val ops = Duration } with TimeLikeSpec[Duration] {

  "Duration" should {
    "*" in {
      assert(1.second * 2 == 2.seconds)
      assert(1.second * Long.MaxValue == Duration.Top)
      assert(1.second * Long.MinValue == Duration.Bottom)
      assert(-10.seconds * Long.MinValue == Duration.Top)
      assert(-10.seconds * Long.MaxValue == Duration.Bottom)
      assert(500.milliseconds * 4 == 2.seconds)
      assert(1.nanosecond * Long.MaxValue == Long.MaxValue.nanoseconds)
      assert(1.nanosecond * Long.MinValue == Long.MinValue.nanoseconds)

      assert(1.second * 2.0 == 2.seconds)
      assert(500.milliseconds * 4.0 == 2.seconds)
      assert(1.second * 0.5 == 500.milliseconds)
      assert(1.second * Double.PositiveInfinity == Duration.Top)
      assert(1.second * Double.NegativeInfinity == Duration.Bottom)
      assert(1.second * Double.NaN == Duration.Undefined)
      assert(1.nanosecond * (Long.MaxValue.toDouble + 1) == Duration.Top)
      assert(1.nanosecond * (Long.MinValue.toDouble - 1) == Duration.Bottom)

      assert(Duration.Undefined * Double.NaN == Duration.Undefined)
      assert(Duration.Top * Double.NaN == Duration.Undefined)
      assert(Duration.Bottom * Double.NaN == Duration.Undefined)

      forAll { (a: Long, b: Long) =>
        val c = BigInt(a) * BigInt(b)
        val d = a.nanoseconds * b

        assert(
          (c >= Long.MaxValue && d == Duration.Top) ||
            (c <= Long.MinValue && d == Duration.Bottom) ||
            (d == c.toLong.nanoseconds)
        )
      }
    }

    "/" in {
      assert(10.seconds / 2 == 5.seconds)
      assert(1.seconds / 0 == Duration.Top)
      assert((-1).seconds / 0 == Duration.Bottom)
      assert(0.seconds / 0 == Duration.Undefined)
      assert(Duration.Top / 0 == Duration.Undefined)
      assert(Duration.Top / -1 == Duration.Bottom)
      assert(Duration.Top / 1 == Duration.Top)

      assert(10.seconds / 2.0 == 5.seconds)
      assert(1.seconds / 0.0 == Duration.Top)
      assert((-1).seconds / 0.0 == Duration.Bottom)
      assert(0.seconds / 0.0 == Duration.Undefined)
      assert(Duration.Top / 0.0 == Duration.Undefined)
      assert(Duration.Top / -1.0 == Duration.Bottom)
      assert(Duration.Top / 1.0 == Duration.Top)

      assert(Duration.Undefined / Double.NaN == Duration.Undefined)
      assert(Duration.Top / Double.NaN == Duration.Undefined)
      assert(Duration.Bottom / Double.NaN == Duration.Undefined)
    }

    "%" in {
      assert(10.seconds % 3.seconds == 1.second)
      assert(1.second % 300.millis == 100.millis)
      assert(1.second % 0.seconds == Duration.Undefined)
      assert((-1).second % 0.seconds == Duration.Undefined)
      assert(0.seconds % 0.seconds == Duration.Undefined)
      assert(Duration.Top % 123.seconds == Duration.Undefined)
      assert(Duration.Bottom % 123.seconds == Duration.Undefined)
    }

    "unary_-" in {
      assert(-((10.seconds).inSeconds) == -10)
      assert(-((Long.MinValue + 1).nanoseconds) == Long.MaxValue.nanoseconds)
      assert(-(Long.MinValue.nanoseconds) == Duration.Top)
    }

    "abs" in {
      assert(10.seconds.abs == 10.seconds)
      assert((-10).seconds.abs == 10.seconds)
    }

    "afterEpoch" in {
      assert(10.seconds.afterEpoch == Time.fromMilliseconds(10000))
    }

    "fromNow" in {
      Time.withCurrentTimeFrozen { _ =>
        assert(10.seconds.fromNow == (Time.now + 10.seconds))
      }
    }

    "ago" in {
      Time.withCurrentTimeFrozen { _ =>
        assert(10.seconds.ago == (Time.now - 10.seconds))
      }
    }

    "compare" in {
      assert(10.seconds < 11.seconds)
      assert(10.seconds < 11000.milliseconds)
      assert(11.seconds > 10.seconds)
      assert(11000.milliseconds > 10.seconds)
      assert(10.seconds >= 10.seconds)
      assert(10.seconds <= 10.seconds)
      assert(new Duration(Long.MaxValue) > 0.seconds)
    }

    "equals" in {
      assert(Duration.Top == Duration.Top)
      assert(Duration.Top != Duration.Bottom)
      assert(Duration.Top != Duration.Undefined)

      assert(Duration.Bottom != Duration.Top)
      assert(Duration.Bottom == Duration.Bottom)
      assert(Duration.Bottom != Duration.Undefined)

      assert(Duration.Undefined != Duration.Top)
      assert(Duration.Undefined != Duration.Bottom)
      assert(Duration.Undefined == Duration.Undefined)

      val tenSecs = 10.seconds
      assert(tenSecs == tenSecs)
      assert(tenSecs == 10.seconds)
      assert(tenSecs != 11.seconds)
    }

    "+ delta" in {
      forAll { (a: Long, b: Long) =>
        val c = BigInt(a) + BigInt(b)
        val d = a.nanoseconds + b.nanosecond

        assert(
          (c >= Long.MaxValue && d == Duration.Top) ||
            (c <= Long.MinValue && d == Duration.Bottom) ||
            (d == c.toLong.nanoseconds)
        )
      }
    }

    "- delta" in {
      assert(10.seconds - 5.seconds == 5.seconds)
    }

    "max" in {
      assert((10.seconds max 5.seconds) == 10.seconds)
      assert((5.seconds max 10.seconds) == 10.seconds)
    }

    "min" in {
      assert((10.seconds min 5.seconds) == 5.seconds)
      assert((5.seconds min 10.seconds) == 5.seconds)
    }

    "moreOrLessEquals" in {
      assert(10.seconds.moreOrLessEquals(9.seconds, 1.second) == true)
      assert(10.seconds.moreOrLessEquals(11.seconds, 1.second) == true)
      assert(10.seconds.moreOrLessEquals(8.seconds, 1.second) == false)
      assert(10.seconds.moreOrLessEquals(12.seconds, 1.second) == false)
    }

    "inTimeUnit" in {
      assert(23.nanoseconds.inTimeUnit == ((23, TimeUnit.NANOSECONDS)))
      assert(23.microseconds.inTimeUnit == ((23000, TimeUnit.NANOSECONDS)))
      assert(23.milliseconds.inTimeUnit == ((23, TimeUnit.MILLISECONDS)))
      assert(23.seconds.inTimeUnit == ((23, TimeUnit.SECONDS)))
    }

    "inUnit" in {
      assert(23.seconds.inUnit(TimeUnit.SECONDS) == 23L)
      assert(23.seconds.inUnit(TimeUnit.MILLISECONDS) == 23000L)
      assert(2301.millis.inUnit(TimeUnit.SECONDS) == 2L)
      assert(2301.millis.inUnit(TimeUnit.MICROSECONDS) == 2301000L)
      assert(4680.nanoseconds.inUnit(TimeUnit.MICROSECONDS) == 4L)
    }

    "be hashable" in {
      val map = new java.util.concurrent.ConcurrentHashMap[Duration, Int]
      map.put(23.millis, 23)
      assert(map.get(23.millis) == 23)
      map.put(44.millis, 44)
      assert(map.get(44.millis) == 44)
    }

    "toString should display as sums" in {
      assert((9999999.seconds).toString == "115.days+17.hours+46.minutes+39.seconds")
    }

    "toString should handle negative durations" in {
      assert((-9999999.seconds).toString == "-115.days-17.hours-46.minutes-39.seconds")
    }

    "parse the format from toString" in {
      Seq(
        -10.minutes,
        -9999999.seconds,
        1.day + 3.hours,
        1.day,
        1.nanosecond,
        42.milliseconds,
        9999999.seconds,
        Duration.Bottom,
        Duration.Top,
        Duration.Undefined
      ) foreach { d =>
        assert(Duration.parse(d.toString) == d)
      }
    }

    "parse" in {
      Seq(
        " 1.second" -> 1.second,
        "+1.second" -> 1.second,
        "-1.second" -> -1.second,
        "1.SECOND" -> 1.second,
        "1.day - 1.second" -> (1.day - 1.second),
        "1.day" -> 1.day,
        "1.microsecond" -> 1.microsecond,
        "1.millisecond" -> 1.millisecond,
        "1.second" -> 1.second,
        "1.second+1.minute  +  1.day" -> (1.second + 1.minute + 1.day),
        "1.second+1.second" -> 2.seconds,
        "2.hours" -> 2.hours,
        "3.days" -> 3.days,
        "321.nanoseconds" -> 321.nanoseconds,
        "65.minutes" -> 65.minutes,
        "876.milliseconds" -> 876.milliseconds,
        "98.seconds" -> 98.seconds,
        "Duration.Bottom" -> Duration.Bottom,
        "Duration.Top" -> Duration.Top,
        "Duration.Undefined" -> Duration.Undefined,
        "duration.TOP" -> Duration.Top
      ) foreach {
        case (s, d) =>
          assert(Duration.parse(s) == d)
      }
    }

    "reject obvious human impostors" in {
      intercept[NumberFormatException] {
        Seq(
          "",
          "++1.second",
          "1. second",
          "1.milli",
          "1.s",
          "10.stardates",
          "2.minutes 1.second",
          "98 milliseconds",
          "98 millisecons",
          "99.minutes +"
        ) foreach { s =>
          Duration.parse(s)
        }
      }
    }
  }

  "Top" should {
    "Be scaling resistant" in {
      assert(Duration.Top / 100 == Duration.Top)
      assert(Duration.Top * 100 == Duration.Top)
    }

    "-Top == Bottom" in {
      assert(-Duration.Top == Duration.Bottom)
    }

    "--Top == Top" in {
      assert(-(-Duration.Top) == Duration.Top)
    }
  }
}
