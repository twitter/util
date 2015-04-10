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

package com.twitter.conversions

import com.twitter.util.Duration
import java.util.concurrent.TimeUnit
import scala.language.implicitConversions

object time {
  class RichWholeNumber(wrapped: Long) {
    def nanoseconds: Duration = Duration(wrapped, TimeUnit.NANOSECONDS)
    def nanosecond: Duration = nanoseconds
    def microseconds: Duration = Duration(wrapped, TimeUnit.MICROSECONDS)
    def microsecond: Duration = microseconds
    def milliseconds: Duration = Duration(wrapped, TimeUnit.MILLISECONDS)
    def millisecond: Duration = milliseconds
    def millis: Duration = milliseconds
    def seconds: Duration = Duration(wrapped, TimeUnit.SECONDS)
    def second: Duration = seconds
    def minutes: Duration = Duration(wrapped, TimeUnit.MINUTES)
    def minute: Duration = minutes
    def hours: Duration = Duration(wrapped, TimeUnit.HOURS)
    def hour: Duration = hours
    def days: Duration = Duration(wrapped, TimeUnit.DAYS)
    def day: Duration = days
  }

  private val ZeroRichWholeNumber = new RichWholeNumber(0) {
    override def nanoseconds: Duration = Duration.Zero
    override def microseconds: Duration = Duration.Zero
    override def milliseconds: Duration = Duration.Zero
    override def seconds: Duration = Duration.Zero
    override def minutes: Duration = Duration.Zero
    override def hours: Duration = Duration.Zero
    override def days: Duration = Duration.Zero
  }

  implicit def intToTimeableNumber(i: Int): RichWholeNumber =
    if (i == 0) ZeroRichWholeNumber else new RichWholeNumber(i)
  implicit def longToTimeableNumber(l: Long): RichWholeNumber =
    if (l == 0) ZeroRichWholeNumber else new RichWholeNumber(l)
}
