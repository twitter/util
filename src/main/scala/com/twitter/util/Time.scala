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
  val epoch = Time(0)

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

  def apply(millis: Long) = new Time(millis)
}

trait TimeControl {
  def advance(delta: Duration)
}

trait TimeLike[+This <: TimeLike[This]] {
  def inMilliseconds: Long

  def inNanoseconds: Long = inMilliseconds * 1000000L
  def inMicroseconds: Long = inNanoseconds / 1000L
  protected def build(nanos: Long): This
  def inDays = (inHours / 24)
  def inHours = (inMinutes / 60)
  def inMinutes = (inSeconds / 60)
  def inSeconds = (inMilliseconds / 1000L).toInt
  def inTimeUnit = (inMilliseconds, TimeUnit.MILLISECONDS)
  def +(delta: Duration): This = build(inNanoseconds + delta.inNanoseconds)
  def -(delta: Duration): This = build(inNanoseconds - delta.inNanoseconds)
  def max[A <: TimeLike[_]](that: A): This = build(this.inNanoseconds max that.inNanoseconds)
  def min[A <: TimeLike[_]](that: A): This = build(this.inNanoseconds min that.inNanoseconds)

  // backward compat:
  def inMillis = inMilliseconds

  override def equals(other: Any) = {
    other match {
      case x: TimeLike[_] =>
        inNanoseconds == x.inNanoseconds
      case x =>
        false
    }
  }
}

class Time(millis: Long) extends TimeLike[Time] with Ordered[Time] {
  protected override def build(nanos: Long) = Time(nanos / 1000000L)

  def inMilliseconds = millis

  override def toString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(toDate)

  def compare(that: Time) = (this.inMilliseconds compare that.inMilliseconds)

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Time, maxDelta: Duration) = (this - other).abs <= maxDelta

  /**
   * Creates a duration between two times.
   */
  def -(that: Time) = new Duration(this.inNanoseconds - that.inNanoseconds)

  def since = Time.now - this

  /**
   * Gets the current time as Duration since epoch
   */
  def fromEpoch = this - Time.epoch

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






    /**
     * Returns how long it took, in milliseconds, to run the function f.
     *
    def duration[T](f: => T): (T, Long) = {
      val start = Time.now
      val rv = f
      val duration = Time.now - start
      (rv, duration.inMilliseconds)
    }

    **
     * Returns how long it took, in microseconds, to run the function f.
     *
    def durationMicros[T](f: => T): (T, Long) = {
      val (rv, duration) = durationNanos(f)
      (rv, duration / 1000)
    }

    **
     * Returns how long it took, in nanoseconds, to run the function f.
     *
    def durationNanos[T](f: => T): (T, Long) = {
      val start = System.nanoTime
      val rv = f
      val duration = System.nanoTime - start
      (rv, duration)
    }
    */
}

class Duration protected(protected val nanos: Long) extends TimeLike[Duration] with Ordered[Duration] {
  override def inNanoseconds = nanos
  def inMilliseconds = nanos / 1000000L

  override def build(nanos: Long) = new Duration(nanos)

  override def toString = {
    if (inNanoseconds < 1000000) {
      inNanoseconds + ".nanoseconds"
    } else if (inMilliseconds < 1000) {
      inMilliseconds + ".milliseconds"
    } else {
      inSeconds + ".seconds"
    }
  }

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Duration, maxDelta: Duration) = (this - other).abs <= maxDelta

  def compare(that: Duration) = (this.nanos compare that.nanos)

  def *(x: Long) = new Duration(nanos * x)
  def fromNow = Time.now + this
  def ago = Time.now - this
  def afterEpoch = Time.epoch + this
  def abs = if (nanos < 0) new Duration(-nanos) else this
}
