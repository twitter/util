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

import java.util.concurrent.TimeUnit

import com.twitter.conversions.time._

class DurationSpec extends { val ops = Duration } with TimeLikeSpec[Duration] {
  import ops._

  "Duration" should  {
    "*" in {
      1.second * 2 shouldEqual 2.seconds
      500.milliseconds * 4 shouldEqual 2.seconds
    }

    "/" in {
      10.seconds / 2 shouldEqual 5.seconds
      1.seconds / 0 shouldEqual Top
      (-1).seconds / 0 shouldEqual Bottom
      0.seconds / 0 shouldEqual Undefined
      Top / 0 shouldEqual Undefined
      Top / -1 shouldEqual Bottom
      Top / 1 shouldEqual Top
    }

    "%" in {
      10.seconds % 3.seconds shouldEqual 1.second
      1.second % 300.millis shouldEqual 100.millis
      1.second % 0.seconds shouldEqual Undefined
      (-1).second % 0.seconds shouldEqual Undefined
      0.seconds % 0.seconds shouldEqual Undefined
      Top % 123.seconds shouldEqual Undefined
      Bottom % 123.seconds shouldEqual Undefined
    }

    "unary_-" in {
      -((10.seconds).inSeconds) shouldEqual(-10)
      -((Long.MinValue+1).nanoseconds) shouldEqual(Long.MaxValue.nanoseconds)
      -(Long.MinValue.nanoseconds) shouldEqual(Top)
    }

    "abs" in {
      10.seconds.abs shouldEqual 10.seconds
      (-10).seconds.abs shouldEqual 10.seconds
    }

    "afterEpoch" in {
      10.seconds.afterEpoch shouldEqual Time.fromMilliseconds(10000)
    }

    "fromNow" in {
      Time.withCurrentTimeFrozen { _ =>
        10.seconds.fromNow shouldEqual (Time.now + 10.seconds)
      }
    }

    "ago" in {
      Time.withCurrentTimeFrozen { _ =>
        10.seconds.ago shouldEqual (Time.now - 10.seconds)
      }
    }

    "compare" in {
      10.seconds should be <(11.seconds)
      10.seconds should be <(11000.milliseconds)
      11.seconds should be >(10.seconds)
      11000.milliseconds should be >(10.seconds)
      10.seconds should be >=(10.seconds)
      10.seconds should be <=(10.seconds)
      new Duration(Long.MaxValue) should be >(0.seconds)
    }

    "+ delta" in {
      10.seconds + 5.seconds shouldEqual 15.seconds
    }

    "- delta" in {
      10.seconds - 5.seconds shouldEqual 5.seconds
    }

    "max" in {
      10.seconds max 5.seconds shouldEqual 10.seconds
      5.seconds max 10.seconds shouldEqual 10.seconds
    }

    "min" in {
      10.seconds min 5.seconds shouldEqual 5.seconds
      5.seconds min 10.seconds shouldEqual 5.seconds
    }

    "moreOrLessEquals" in {
      10.seconds.moreOrLessEquals(9.seconds, 1.second) shouldBe true
      10.seconds.moreOrLessEquals(11.seconds, 1.second) shouldBe true
      10.seconds.moreOrLessEquals(8.seconds, 1.second) shouldBe false
      10.seconds.moreOrLessEquals(12.seconds, 1.second) shouldBe false
    }

    "inTimeUnit" in {
      23.nanoseconds.inTimeUnit shouldEqual ((23, TimeUnit.NANOSECONDS))
      23.microseconds.inTimeUnit shouldEqual ((23000, TimeUnit.NANOSECONDS))
      23.milliseconds.inTimeUnit shouldEqual ((23, TimeUnit.MILLISECONDS))
      23.seconds.inTimeUnit shouldEqual ((23, TimeUnit.SECONDS))
    }

    "inUnit" in {
      23.seconds.inUnit(TimeUnit.SECONDS) shouldEqual(23L)
      23.seconds.inUnit(TimeUnit.MILLISECONDS) shouldEqual(23000L)
      2301.millis.inUnit(TimeUnit.SECONDS) shouldEqual(2L)
      2301.millis.inUnit(TimeUnit.MICROSECONDS) shouldEqual(2301000L)
      4680.nanoseconds.inUnit(TimeUnit.MICROSECONDS) shouldEqual(4L)
    }

    "time milliseconds" in {
      val (rv, duration) = Duration.inMilliseconds {
        Thread.sleep(10)
        "Faunts"
      }
      rv shouldEqual "Faunts"
      duration should be >=(10.milliseconds)
    }

    "time nanoseconds" in {
      val (rv, duration) = Duration.inNanoseconds {
        // or 10 grace hoppers, as i prefer to call them. :)
        Thread.sleep(0, 10)
        "M4 (part II)"
      }
      rv shouldEqual "M4 (part II)"
      duration should be >=(10.nanoseconds)
    }

    "be hashable" in {
      val map = new java.util.concurrent.ConcurrentHashMap[Duration, Int]
      map.put(23.millis, 23)
      map.get(23.millis) shouldEqual 23
      map.put(44.millis, 44)
      map.get(44.millis) shouldEqual 44
      //map.get(233) shouldEqual 0
    }

    "toString should display as sums" in {
      (9999999.seconds).toString shouldEqual "115.days+17.hours+46.minutes+39.seconds"
    }

    "toString should handle negative durations" in {
      (-9999999.seconds).toString shouldEqual "-115.days-17.hours-46.minutes-39.seconds"
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
        Duration.parse(d.toString) shouldEqual d
      }
    }

    "parse" in {
      Seq(
        " 1.second"                   -> 1.second,
        "+1.second"                   -> 1.second,
        "-1.second"                   -> -1.second,
        "1.SECOND"                    -> 1.second,
        "1.day - 1.second"            -> (1.day - 1.second),
        "1.day"                       -> 1.day,
        "1.microsecond"               -> 1.microsecond,
        "1.millisecond"               -> 1.millisecond,
        "1.second"                    -> 1.second,
        "1.second+1.minute  +  1.day" -> (1.second + 1.minute + 1.day),
        "1.second+1.second"           -> 2.seconds,
        "2.hours"                     -> 2.hours,
        "3.days"                      -> 3.days,
        "321.nanoseconds"             -> 321.nanoseconds,
        "65.minutes"                  -> 65.minutes,
        "876.milliseconds"            -> 876.milliseconds,
        "98.seconds"                  -> 98.seconds,
        "Duration.Bottom"             -> Duration.Bottom,
        "Duration.Top"                -> Duration.Top,
        "Duration.Undefined"          -> Duration.Undefined,
        "duration.TOP"                -> Duration.Top
      ) foreach { case (s, d) =>
        Duration.parse(s) shouldEqual(d)
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

  "Top" should  {
    "Be scaling resistant" in {
      Top / 100 shouldEqual(Top)
      Top * 100 shouldEqual(Top)
    }

    "-Top == Bottom" in {
      -Top shouldEqual(Bottom)
    }

    "--Top == Top" in {
      -(-Top) shouldEqual(Top)
    }
  }
}
