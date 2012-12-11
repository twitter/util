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
import org.specs.SpecificationWithJUnit
import com.twitter.conversions.time._

class DurationSpec extends { val ops = Duration } with TimeLikeSpec[Duration] {
  import ops._

  "Duration" should {
    "*" in {
      1.second * 2 mustEqual 2.seconds
      500.milliseconds * 4 mustEqual 2.seconds
    }

    "/" in {
      10.seconds / 2 mustEqual 5.seconds
      1.seconds / 0 mustEqual Top
      (-1).seconds / 0 mustEqual Bottom
      0.seconds / 0 mustEqual Undefined
      Top / 0 mustEqual Undefined
      Top / -1 mustEqual Bottom
      Top / 1 mustEqual Top
    }

    "%" in {
      10.seconds % 3.seconds mustEqual 1.second
      1.second % 300.millis mustEqual 100.millis
      1.second % 0.seconds mustEqual Undefined
      (-1).second % 0.seconds mustEqual Undefined
      0.seconds % 0.seconds mustEqual Undefined
      Top % 123.seconds mustEqual Undefined
      Bottom % 123.seconds mustEqual Undefined
    }

    "unary_-" in {
      -((10.seconds).inSeconds) must be_==(-10)
      -((Long.MinValue+1).nanoseconds) must be_==(Long.MaxValue.nanoseconds)
      -(Long.MinValue.nanoseconds) must be_==(Top)
    }

    "abs" in {
      10.seconds.abs mustEqual 10.seconds
      (-10).seconds.abs mustEqual 10.seconds
    }

    "afterEpoch" in {
      10.seconds.afterEpoch mustEqual Time.fromMilliseconds(10000)
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
      11.seconds must be_>(10.seconds)
      11000.milliseconds must be_>(10.seconds)
      10.seconds must be_>=(10.seconds)
      10.seconds must be_<=(10.seconds)
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

    "moreOrLessEquals" in {
      10.seconds.moreOrLessEquals(9.seconds, 1.second) must beTrue
      10.seconds.moreOrLessEquals(11.seconds, 1.second) must beTrue
      10.seconds.moreOrLessEquals(8.seconds, 1.second) must beFalse
      10.seconds.moreOrLessEquals(12.seconds, 1.second) must beFalse
    }

    "inTimeUnit" in {
      23.nanoseconds.inTimeUnit mustEqual ((23, TimeUnit.NANOSECONDS))
      23.microseconds.inTimeUnit mustEqual ((23000, TimeUnit.NANOSECONDS))
      23.milliseconds.inTimeUnit mustEqual ((23, TimeUnit.MILLISECONDS))
      23.seconds.inTimeUnit mustEqual ((23, TimeUnit.SECONDS))
    }

    "inUnit" in {
      23.seconds.inUnit(TimeUnit.SECONDS) mustEqual(23L)
      23.seconds.inUnit(TimeUnit.MILLISECONDS) mustEqual(23000L)
      2301.millis.inUnit(TimeUnit.SECONDS) mustEqual(2L)
      2301.millis.inUnit(TimeUnit.MICROSECONDS) mustEqual(2301000L)
      4680.nanoseconds.inUnit(TimeUnit.MICROSECONDS) mustEqual(4L)
    }

    "time milliseconds" in {
      val (rv, duration) = Duration.inMilliseconds {
        Thread.sleep(10)
        "Faunts"
      }
      rv mustEqual "Faunts"
      duration must be_>=(10.milliseconds)
    }

    "time nanoseconds" in {
      val (rv, duration) = Duration.inNanoseconds {
        // or 10 grace hoppers, as i prefer to call them. :)
        Thread.sleep(0, 10)
        "M4 (part II)"
      }
      rv mustEqual "M4 (part II)"
      duration must be_>=(10.nanoseconds)
    }

    "be hashable" in {
      val map = new java.util.concurrent.ConcurrentHashMap[Duration, Int]
      map.put(23.millis, 23)
      map.get(23.millis) mustEqual 23
      map.put(44.millis, 44)
      map.get(44.millis) mustEqual 44
      map.get(233) mustEqual 0
    }

    "toString must display as sums" in {
      (9999999.seconds).toString mustEqual "115.days+17.hours+46.minutes+39.seconds"
    }

    "toString must handle negative durations" in {
      (-9999999.seconds).toString mustEqual "-115.days-17.hours-46.minutes-39.seconds"
    }

    "parse" in {
      Duration.parse("321.nanoseconds") must be_==(321.nanoseconds)
      Duration.parse("1.microsecond") must be_==(1.microsecond)
      Duration.parse("876.milliseconds") must be_==(876.milliseconds)
      Duration.parse("98.seconds") must be_==(98.seconds)
      Duration.parse("65.minutes") must be_==(65.minutes)
      Duration.parse("2.hours") must be_==(2.hours)
      Duration.parse("3.days") must be_==(3.days)
    }

    "reject obvious human impostors" in {
      Duration.parse("10.stardates") must throwA[NumberFormatException]
      Duration.parse("98 milliseconds") must throwA[NumberFormatException]
    }
  }

  "Top" should {
    "Be scaling resistant" in {
      Top / 100 must be_==(Top)
      Top * 100 must be_==(Top)
    }

    "-Top == Bottom" in {
      -Top must be_==(Bottom)
    }

    "--Top == Top" in {
      -(-Top) must be_==(Top)
    }
  }
}
