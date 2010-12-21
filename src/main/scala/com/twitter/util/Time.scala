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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Use `Time.now` in your app instead of `System.currentTimeMillis`, and
 * unit tests will be able to adjust the current time to verify timeouts
 * and other time-dependent behavior, without calling `sleep`.
 */
object Time {
  import com.twitter.conversions.time._

  private val defaultFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")
  private val rssFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss Z")

  private[Time] var fn: () => Time = () => new Time(System.currentTimeMillis)

  def now: Time = fn()
  def never: Time = Time(0.seconds)

  def apply(at: Long): Time = new Time(at)
  def apply(at: Duration): Time = new Time(at.inMillis)

  def at(datetime: String) = parse(datetime, defaultFormat)

  def withTimeAt[A](time: Time)(body: TimeControl => A): A = {
    val prevFn = Time.fn
    try {
      val timeControl = new TimeControl {
        def advance(delta: Duration) {
          val newTime = Time.now + delta
          Time.fn = () => newTime
        }
      }
      Time.fn = () => time
      body(timeControl)
    } finally {
      Time.fn = prevFn
    }
  }

  def withCurrentTimeFrozen[A](body: TimeControl => A): A = {
    withTimeAt(Time.now)(body)
  }

  def measure(f: => Unit) = {
    val start = System.currentTimeMillis
    val result = f
    val end = System.currentTimeMillis
    (end - start).millis
  }

  // Wed, 15 Jun 2005 19:00:00 GMT
  def fromRss(rss: String) = parse(rss, rssFormat)

  private def parse(str: String, format: SimpleDateFormat): Time = {
    // SimpleDateFormat is not thread-safe
    val date = format.synchronized(format.parse(str))
    if (date == null) {
      throw new Exception("Unable to parse date-time: " + str)
    } else {
      new Time(date.getTime())
    }
  }
}

trait TimeControl {
  def advance(delta: Duration)
}

class Duration(val at: Long) extends Ordered[Duration] {
  def inDays = (inHours / 24)
  def inHours = (inMinutes / 60)
  def inMinutes = (inSeconds / 60)
  def inSeconds = (at / 1000L).toInt
  def inMillis = at
  def inMilliseconds = at
  def inTimeUnit = (inMilliseconds, TimeUnit.MILLISECONDS)

  def +(delta: Duration) = new Duration(at + delta.inMillis)
  def -(delta: Duration) = new Duration(at - delta.inMillis)
  def *(x: Long) = new Duration(at * x)

  def fromNow = Time(Time.now + this)
  def ago = Time(Time.now - this)

  def max(that: Duration) = if (this.at > that.at) this else that
  def min(that: Duration) = if (this.at < that.at) this else that

  override def toString = inSeconds.toString

  override def equals(other: Any) = {
    other match {
      case other: Duration =>
        inSeconds == other.inSeconds
      case _ =>
        false
    }
  }

  def compare(other: Duration) =
     if (at < other.at)
      -1
    else if (at > other.at)
      1
    else
      0
}

object Duration {
  import com.twitter.conversions.time._

  def fromTimeUnit(value: Long, unit: TimeUnit) =
    unit match {
      case TimeUnit.DAYS         => value.days
      case TimeUnit.HOURS        => value.hours
      case TimeUnit.MINUTES      => value.minutes
      case TimeUnit.SECONDS      => value.seconds
      case TimeUnit.MILLISECONDS => value.milliseconds
      case TimeUnit.MICROSECONDS => value.microseconds
      case TimeUnit.NANOSECONDS  => value.nanoseconds
    }
}

class Time(at: Long) extends Duration(at) {
  override def +(delta: Duration) = new Time(at + delta.inMillis)
  override def -(delta: Duration) = new Time(at - delta.inMillis)
  def toDate = new Date(inMillis)
  override def toString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(toDate)
}
