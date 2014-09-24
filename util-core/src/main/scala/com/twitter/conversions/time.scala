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

import java.util.concurrent.TimeUnit

import com.twitter.util.Duration

object time {
  class RichWholeNumber(wrapped: Long) {
    def nanoseconds = Duration(wrapped, TimeUnit.NANOSECONDS)
    def nanosecond = nanoseconds
    def microseconds = Duration(wrapped, TimeUnit.MICROSECONDS)
    def microsecond = microseconds
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

  private val ZeroRichWholeNumber = new RichWholeNumber(0) {
    override def nanoseconds = Duration.Zero
    override def microseconds = Duration.Zero
    override def milliseconds = Duration.Zero
    override def seconds = Duration.Zero
    override def minutes = Duration.Zero
    override def hours = Duration.Zero
    override def days = Duration.Zero
  }

  implicit def intToTimeableNumber(i: Int): RichWholeNumber =
    if (i == 0) ZeroRichWholeNumber else new RichWholeNumber(i)
  implicit def longToTimeableNumber(l: Long): RichWholeNumber =
    if (l == 0) ZeroRichWholeNumber else new RichWholeNumber(l)
}
