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

import com.twitter.util.{Duration, Time}

object time {
  class RichWholeNumber(wrapped: Long) {
    def seconds = new Duration(wrapped * 1000)
    def second = seconds
    def milliseconds = new Duration(wrapped)
    def millisecond = milliseconds
    def microseconds = new Duration(wrapped / 1000)
    def nanoseconds = new Duration(wrapped / 1000000)
    def millis = milliseconds
    def minutes = new Duration(wrapped * 1000 * 60)
    def minute = minutes
    def hours = new Duration(wrapped * 1000 * 60 * 60)
    def hour = hours
    def days = new Duration(wrapped * 1000 * 60 * 60 * 24)
    def day = days
  }

  implicit def intToTimeableNumber(i: Int) = new RichWholeNumber(i)
  implicit def longToTimeableNumber(l: Long) = new RichWholeNumber(l)

  implicit def durationToLong(duration: Duration) = duration.inMillis
}
