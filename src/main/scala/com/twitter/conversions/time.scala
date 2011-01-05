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

package com.twitter
package conversions

import com.twitter.util.{Duration, TimeLike}
import java.util.concurrent.TimeUnit

object time {
  class RichWholeNumber(wrapped: Long) {
    def nanoseconds = Duration(wrapped, TimeUnit.NANOSECONDS)
    def microseconds = Duration(wrapped, TimeUnit.MICROSECONDS)
    def milliseconds = Duration(wrapped, TimeUnit.MILLISECONDS)
    def millisecond = milliseconds
    def millis = milliseconds
    def seconds = Duration(wrapped, TimeUnit.SECONDS)
    def second = seconds
    def minutes = Duration(wrapped, TimeUnit.MINUTES)
    def minute = minutes
    def hours = Duration(wrapped, TimeUnit.HOURS)
    def hour = hours
    def days = Duration(wrapped, TimeUnit.DAYS)
    def day = days
  }

  implicit def intToTimeableNumber(i: Int) = new RichWholeNumber(i)
  implicit def longToTimeableNumber(l: Long) = new RichWholeNumber(l)

  implicit def timelikeToLong(time: TimeLike[_]) = time.inMillis
}
