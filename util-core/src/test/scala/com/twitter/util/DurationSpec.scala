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

class DurationSpec extends SpecificationWithJUnit {
  "Duration" should {
    "*" in {
      1.second * 2 mustEqual 2.seconds
      500.milliseconds * 4 mustEqual 2.seconds
    }

    "/" in {
      10.seconds / 2 mustEqual 5.seconds
    }

    "%" in {
      10.seconds % 3.seconds mustEqual 1.second
      1.second % 300.millis mustEqual 100.millis
    }

    "floor" in {
      60.seconds.floor(1.minute) mustEqual 1.minute
      100.seconds.floor(1.minute) mustEqual 1.minute
      119.seconds.floor(1.minute) mustEqual 1.minute
      120.seconds.floor(1.minute) mustEqual 2.minutes
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
  }
}
