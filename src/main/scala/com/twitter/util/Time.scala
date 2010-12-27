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

  private val defaultFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  private val rssFormat = new TimeFormat("E, dd MMM yyyy HH:mm:ss Z")

  private[Time] var fn: () => Time = () => new Time(System.currentTimeMillis)

  def now: Time = fn()

  /**
   * The unix epoch.  Times are measured relative to this.
   */
  val epoch = Time(0)

  def apply(date: Date): Time = Time(date.getTime)

  def at(datetime: String) = defaultFormat.parse(datetime)

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

  def measure(f: => Unit): Duration = {
    val start = now
    val result = f
    val end = now
    end - start
  }

  // Wed, 15 Jun 2005 19:00:00 GMT
  def fromRss(rss: String) = rssFormat.parse(rss)
}

trait TimeControl {
  def advance(delta: Duration)
}

/**
 * A thread-safe wrapper around a SimpleDateFormat object.
 */
class TimeFormat(pattern: String) {
  private val format = new SimpleDateFormat(pattern)

  def parse(str: String): Time = {
    // SimpleDateFormat is not thread-safe
    val date = format.synchronized(format.parse(str))
    if (date == null) {
      throw new Exception("Unable to parse date-time: " + str)
    } else {
      Time(date.getTime())
    }
  }

  def format(time: Time): String = {
    format.synchronized(format.format(time.toDate))
  }
}

trait TimeLike[+This <: TimeLike[This]] {
  def inMillis: Long
  protected def build(inMillis: Long): This
  def inDays = (inHours / 24)
  def inHours = (inMinutes / 60)
  def inMinutes = (inSeconds / 60)
  def inSeconds = (inMillis / 1000L).toInt
  def inMilliseconds = inMillis
  def inTimeUnit = (inMillis, TimeUnit.MILLISECONDS)
  def +(delta: Duration): This = build(inMillis + delta.inMillis)
  def -(delta: Duration): This = build(inMillis - delta.inMillis)
  def max[A <: TimeLike[_]](that: A): This = build(this.inMillis max that.inMillis)
  def min[A <: TimeLike[_]](that: A): This = build(this.inMillis min that.inMillis)

  /**
   * Rounds down to the nearest multiple of the given duration.  For example:
   * 127.seconds.floor(1.minute) => 2.minutes.  Taking the floor of a
   * Time object with duration greater than 1.hour can have unexpected
   * results because of timezones.
   */
  def floor(x: Duration) = build((inMillis / x.inMillis) * x.inMillis)
}

case class Time(inMillis: Long) extends TimeLike[Time] with Ordered[Time] {
  protected override def build(inMillis: Long) = Time(inMillis)

  /**
   * Renders this time using the default format.
   */
  override def toString = Time.defaultFormat.format(this)

  /**
   * Formats this Time according to the given SimpleDateFormat pattern.
   */
  def format(pattern: String) = new TimeFormat(pattern).format(this)

  def compare(that: Time) = (this.inMillis compare that.inMillis)

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Time, maxDelta: Duration) = (this - other).abs <= maxDelta

  /**
   * Creates a duration between two times.
   */
  def -(that: Time) = new Duration(this.inMillis - that.inMillis)

  /**
   * Duration that has passed between the given time and the current time.
   */
  def since(that: Time) = this - that

  /**
   * Duration that has passed between the epoch and the current time.
   */
  def sinceEpoch = since(Time.epoch)

  /**
   * Gets the current time as Duration since now
   */
  def sinceNow = since(Time.now)

  /**
   * Duration that has passed between the epoch and the current time.
   */
  @deprecated("use sinceEpoch")
  def fromEpoch = this - Time.epoch

  /**
   * Duration between current time and the givne time.
   */
  def until(that: Time) = that - this

  /**
   * Gets the duration between this time and the epoch.
   */
  def untilEpoch = until(Time.epoch)

  /**
   * Gets the duration between this time and now.
   */
  def untilNow = until(Time.now)

  /**
   * Converts this Time object to a java.util.Date
   */
  def toDate = new Date(inMillis)
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

  def since(time: Time) = Time.now.since(time)
}

case class Duration(inMillis: Long) extends TimeLike[Duration] with Ordered[Duration] {
  protected def build(inMillis: Long) = Duration(inMillis)

  override def toString = {
    if (inMillis < 1000) inMillis + ".milliseconds"
    else inSeconds + ".seconds"
  }

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Duration, maxDelta: Duration) = (this - other).abs <= maxDelta

  def compare(that: Duration) = (this.inMillis compare that.inMillis)

  def *(x: Long) = Duration(inMillis * x)
  def /(x: Long) = Duration(inMillis / x)
  def %(x: Duration) = Duration(inMillis % x.inMillis)

  /**
   * Converts negative durations to positive durations.
   */
  def abs = if (inMillis < 0) Duration(-inMillis) else this

  def fromNow = Time.now + this
  def ago = Time.now - this
  def afterEpoch = Time.epoch + this
}
