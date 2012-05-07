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

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Use `Time.now` in your app instead of `System.currentTimeMillis`, and
 * unit tests will be able to adjust the current time to verify timeouts
 * and other time-dependent behavior, without calling `sleep`.
 *
 * If you import the [[com.twitter.conversions.time]] implicits you can
 * write human-readable values such as `1.minute` or `250.millis`.
 */
object Time {
  import com.twitter.conversions.time._

  private val defaultFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  private val rssFormat = new TimeFormat("E, dd MMM yyyy HH:mm:ss Z")

  /**
   * on some systems (os x), nanoTime is just epoch time with greater precision.
   * on others (linux), it can be based on system uptime.
   */
  val nanoTimeOffset = (System.currentTimeMillis * 1000000) - System.nanoTime

  /**
   * Note, this should only ever be updated by methods used for testing.
   * Even there, since this is shared global state, there are risks about memory visibility
   * with multi-threaded tests.
   */
  private[Time] var fn: () => Time = () => Time.fromNanoseconds(System.nanoTime + nanoTimeOffset)

  @deprecated("use Time.fromMilliseconds(...) instead")
  def apply(millis: Long) = fromMilliseconds(millis)

  def fromMilliseconds(millis: Long): Time = {
    val nanos = TimeUnit.MILLISECONDS.toNanos(millis)
    // handle some overflow, but let Long.MaxValue pass thru unchanged
    if (nanos == Long.MaxValue && millis != Long.MaxValue) {
      throw new TimeOverflowException(millis.toString)
    } else if (nanos == Long.MinValue) {
      throw new TimeOverflowException(millis.toString)
    } else {
      new Time(nanos)
    }
  }

  def fromSeconds(seconds: Int) = fromMilliseconds(1000L * seconds)

  def fromNanoseconds(nanoseconds: Long) = new Time(nanoseconds)

  def now: Time = fn()

  /**
   * The unix epoch. Times are measured relative to this.
   */
  val epoch = Time.fromMilliseconds(0)

  def apply(date: Date): Time = Time.fromMilliseconds(date.getTime)

  def at(datetime: String) = defaultFormat.parse(datetime)

  /**
   * Execute body with the time function replaced by `timeFunction`
   * WARNING: This functionality isn't thread safe, as Time.fn is a global!
   *          It must be used only for testing purpose.
   */
  def withTimeFunction[A](timeFunction: => Time)(body: TimeControl => A): A = {
    val prevFn = Time.fn
    try {
      val timeControl = new TimeControl {
        def set(time: Time) {
          Time.fn = () => time
        }
        def advance(delta: Duration) {
          val newTime = Time.now + delta
          Time.fn = () => newTime
        }
      }
      Time.fn = () => timeFunction
      body(timeControl)
    } finally {
      Time.fn = prevFn
    }
  }

  /**
   * Runs the given body at a specified time.
   * Makes for simple, fast, predictable unit tests that are dependent on time.
   *
   * Note, this intended for use in tests.
   *
   * Since this updates shared global state, there are risks about memory visibility
   * with multi-threaded tests.
   */
  def withTimeAt[A](time: Time)(body: TimeControl => A): A =
    withTimeFunction(time)(body)

  /**
   * Runs the given body at the current time.
   * Makes for simple, fast, predictable unit tests that are dependent on time.
   *
   * Note, this intended for use in tests.
   *
   * Since this updates shared global state, there are risks about memory visibility
   * with multi-threaded tests.
   */
  def withCurrentTimeFrozen[A](body: TimeControl => A): A = {
    withTimeAt(Time.now)(body)
  }

  def measure(f: => Unit): Duration = {
    val start = now
    f
    val end = now
    end - start
  }

  def measureMany(n: Int)(f: => Unit): Duration = {
    require(n > 0)
    val d = measure {
      var i = 0
      while (i < n) {
        f
        i += 1
      }
    }
    d/n
  }

  /** Returns the Time parsed from a string in RSS format. Eg: "Wed, 15 Jun 2005 19:00:00 GMT" */
  def fromRss(rss: String) = rssFormat.parse(rss)
}

trait TimeControl {
  def set(time: Time)
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
      Time.fromMilliseconds(date.getTime())
    }
  }

  def format(time: Time): String = {
    format.synchronized(format.format(time.toDate))
  }
}

trait TimeLike[+This <: TimeLike[This]] {
  import Duration._

  def inNanoseconds: Long
  protected def build(nanos: Long): This

  def inMicroseconds: Long = TimeMath.div(inNanoseconds, NanosPerMicrosecond)
  def inMilliseconds: Long = TimeMath.div(inNanoseconds, NanosPerMillisecond)
  def inSeconds: Int       = TimeMath.divInt(inNanoseconds, NanosPerSecond).toInt
  def inMinutes: Int       = TimeMath.divInt(inNanoseconds, NanosPerMinute).toInt
  def inHours: Int         = TimeMath.divInt(inNanoseconds, NanosPerHour).toInt
  def inDays: Int          = TimeMath.divInt(inNanoseconds, NanosPerDay).toInt

  def inTimeUnit: (Long, TimeUnit) = {
    // allow for APIs that may treat TimeUnit differently if measured in very tiny units.
    if (inNanoseconds % NanosPerSecond == 0) {
      (inSeconds, TimeUnit.SECONDS)
    } else if (inNanoseconds % NanosPerMillisecond == 0) {
      (inMilliseconds, TimeUnit.MILLISECONDS)
    } else {
      (inNanoseconds, TimeUnit.NANOSECONDS)
    }
  }

  def +(delta: Duration): This = build(TimeMath.add(inNanoseconds, delta.inNanoseconds))
  def -(delta: Duration): This = build(TimeMath.sub(inNanoseconds, delta.inNanoseconds))
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

  /**
   * Rounds down to the nearest multiple of the given duration.  For example:
   * 127.seconds.floor(1.minute) => 2.minutes.  Taking the floor of a
   * Time object with duration greater than 1.hour can have unexpected
   * results because of timezones.
   */
  def floor(x: Duration) = build((inNanoseconds / x.inNanoseconds) * x.inNanoseconds)
}

class Time private[util] (protected val nanos: Long) extends TimeLike[Time] with Ordered[Time] with Serializable {
  protected override def build(nanos: Long) = new Time(nanos)

  def inNanoseconds = nanos

  /**
   * Renders this time using the default format.
   */
  override def toString = Time.defaultFormat.format(this)

  /**
   * Formats this Time according to the given SimpleDateFormat pattern.
   */
  def format(pattern: String) = new TimeFormat(pattern).format(this)

  def compare(that: Time) = (this.nanos compare that.nanos)

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Time, maxDelta: Duration) = (this - other).abs <= maxDelta

  /**
   * Creates a duration between two times.
   */
  def -(that: Time) = new Duration(TimeMath.sub(this.inNanoseconds, that.inNanoseconds))

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

  val NanosPerMicrosecond = 1000L
  val NanosPerMillisecond = NanosPerMicrosecond * 1000L
  val NanosPerSecond = NanosPerMillisecond * 1000L
  val NanosPerMinute = NanosPerSecond * 60
  val NanosPerHour = NanosPerMinute * 60
  val NanosPerDay = NanosPerHour * 24

  def fromTimeUnit(value: Long, unit: TimeUnit) = apply(value, unit)

  /**
   * Returns the scaling factor for going from nanoseconds to the given TimeUnit.
   */
  private def nanosecondsPerUnit(unit: TimeUnit): Long = {
    unit match {
      case TimeUnit.DAYS         => NanosPerDay
      case TimeUnit.HOURS        => NanosPerHour
      case TimeUnit.MINUTES      => NanosPerMinute
      case TimeUnit.SECONDS      => NanosPerSecond
      case TimeUnit.MILLISECONDS => NanosPerMillisecond
      case TimeUnit.MICROSECONDS => NanosPerMicrosecond
      case TimeUnit.NANOSECONDS  => 1L
    }
  }

  def apply(value: Long, unit: TimeUnit): Duration = {
    val factor = nanosecondsPerUnit(unit)
    new Duration(TimeMath.mul(value, factor))
  }

  @deprecated("use time.untilNow")
  def since(time: Time) = Time.now.since(time)

  val MaxValue = Long.MaxValue.nanoseconds
  val MinValue = 0.nanoseconds
  val zero = MinValue
  val forever = MaxValue
  val eternity = MaxValue

  /**
   * Returns how long it took, in millisecond granularity, to run the function f.
   */
  def inMilliseconds[T](f: => T): (T, Duration) = {
    val start = Time.now
    val rv = f
    val duration = Time.now - start
    (rv, duration)
  }

  /**
   * Returns how long it took, in nanosecond granularity, to run the function f.
   */
  def inNanoseconds[T](f: => T): (T, Duration) = {
    val start = System.nanoTime
    val rv = f
    val duration = new Duration(System.nanoTime - start)
    (rv, duration)
  }
}

class Duration private[util] (protected val nanos: Long) extends TimeLike[Duration] with Ordered[Duration] with Serializable {
  import Duration._

  def inNanoseconds = nanos

  /**
   * Returns the length of the duration in the given TimeUnit.
   *
   * In general, a simpler approach is to use the named methods (eg. inSeconds)
   * However, this is useful for more programmatic call sites.
   */
  def inUnit(unit: TimeUnit): Long = {
    TimeMath.div(inNanoseconds, nanosecondsPerUnit(unit))
  }

  def build(nanos: Long) = new Duration(nanos)

  override def hashCode = nanos.hashCode

  override def equals(that: Any) = that match {
    case t: Duration => this.nanos == t.nanos
    case _ => false
  }

  override def toString = {
    if (nanos == Long.MaxValue) {
      "never"
    } else if (inNanoseconds % NanosPerMinute == 0) {
      inMinutes + ".minutes"
    } else if (inNanoseconds % NanosPerSecond == 0) {
      inSeconds + ".seconds"
    } else if (inNanoseconds % NanosPerMillisecond == 0) {
      inMilliseconds + ".milliseconds"
    } else {
      inNanoseconds + ".nanoseconds"
    }
  }

  /**
   * Equality within a delta.
   */
  def moreOrLessEquals(other: Duration, maxDelta: Duration) = (this - other).abs <= maxDelta

  def compare(that: Duration) = (this.nanos compare that.nanos)

  def *(x: Long) = new Duration(TimeMath.mul(nanos, x))
  def /(x: Long) = new Duration(nanos / x)
  def %(x: Duration) = new Duration(nanos % x.nanos)

  /**
   * Converts negative durations to positive durations.
   */
  def abs = if (nanos < 0) new Duration(-nanos) else this

  def fromNow = Time.now + this
  def ago = Time.now - this
  def afterEpoch = Time.epoch + this
}

/**
 * Checks for overflow, and maintains sentinel (MaxValue)
 * times.
 */
object TimeMath {
  def add(a: Long, b: Long) = {
    if (a == Long.MaxValue || b == Long.MaxValue)
      Long.MaxValue
    else {
      val c = a + b
      if (((a ^ c) & (b ^ c)) < 0)
        throw new TimeOverflowException(a + " + " + b)
      else
        c
    }
  }

  def sub(a: Long, b: Long) = {
    if (a == Long.MaxValue)
      Long.MaxValue
    else {
      val c = a - b
      if (((a ^ c) & (-b ^ c)) < 0)
        throw new TimeOverflowException(a + " - " + b)
      else
        c
    }
  }

  def mul(a: Long, b: Long): Long = {
    if (a > b) {
      // normalize so that a <= b to keep conditionals to a minimum
      mul(b, a)
    } else {
      // a and b are such that a <= b
      if (b == Long.MaxValue) {
        b
      } else {
        if (a < 0L) {
          if (b < 0L) {
            if (a < Long.MaxValue / b) throw new TimeOverflowException(a + " * " + b)
          } else if (b > 0L) {
            if (Long.MinValue / b > a) throw new TimeOverflowException(a + " * " + b)
          }
        } else if (a > 0L) {
          // and b > 0L
          if (a > Long.MaxValue / b) throw new TimeOverflowException(a + " * " + b)
        }
        a * b
      }
    }
  }

  def div(a: Long, b: Long) = {
    if (a == Long.MaxValue) {
      Long.MaxValue
    } else {
      a / b
    }
  }

  def divInt(a: Long, b: Long) = {
    if (a == Long.MaxValue) {
      Int.MaxValue
    } else {
      a / b
    }
  }
}

class TimeOverflowException(msg: String) extends Exception(msg)
