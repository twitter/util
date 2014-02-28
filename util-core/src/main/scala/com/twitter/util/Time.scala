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
import java.util.{Locale, Date, TimeZone}
import java.util.concurrent.TimeUnit

trait TimeLikeOps[This <: TimeLike[This]] {
  /** The top value is the greatest possible value. It is akin to an infinity. */
  val Top: This
  /** The bottom value is the smallest possible value. */
  val Bottom: This
  /** An undefined value: behaves like Double.NaN */
  val Undefined: This

  /** The zero value */
  val Zero: This = fromNanoseconds(0)

  /**
   * An extractor for finite `This`, yielding its value in nanoseconds.
   *
   * {{{
   *   duration match {
   *     case Duration.Nanoseconds(ns) => ...
   *     case Duration.Top => ...
   *   }
   * }}}
   */
  object Nanoseconds {
    def unapply(x: This): Option[Long] =
      if (x.isFinite) Some(x.inNanoseconds) else None
  }

  /**
   * An extractor for finite TimeLikes; eg.:
   *
   * {{{
   *   duration match {
   *     case Duration.Finite(d) => ...
   *     case Duration.Top => ..
   * }}}
   */
  object Finite {
    def unapply(x: This): Option[This] =
      if (x.isFinite) Some(x) else None
  }

  /** Make a new `This` from the given number of nanoseconds */
  def fromNanoseconds(nanoseconds: Long): This

  def fromSeconds(seconds: Int): This = fromMilliseconds(1000L * seconds)

  def fromMilliseconds(millis: Long): This =
    if (millis > 9223372036854L) Top
    else if (millis < -9223372036854L) Bottom
    else fromNanoseconds(TimeUnit.MILLISECONDS.toNanos(millis))

  def fromMicroseconds(micros: Long): This =
    if (micros > 9223372036854775L) Top
    else if (micros < -9223372036854775L) Bottom
    else fromNanoseconds(TimeUnit.MICROSECONDS.toNanos(micros))
}

/**
 * A common trait for time-like values. It requires a companion
 * `TimeLikeOps` module. `TimeLike`s are finite, but they must always
 * have two sentinels: `Top` and `Bottom`. These act like positive
 * and negative infinities: Arithmetic involving them preserves their
 * values, and so on.
 *
 * `TimeLike`s are `Long`-valued nanoseconds, but have different
 * interpretations: `Duration`s measure the number of nanoseconds
 * between two points in time, while `Time` measure the number of
 * nanoseconds since the Unix epoch (1 January 1970 00:00:00 UTC).
 *
 * TimeLike behave like '''boxed''' java Double values with respect
 * to infinity and undefined values. In particular, this means that a
 * `TimeLike`'s `Undefined` value is comparable to other values. In
 * turn this means it can be used as keys in maps, etc.
 *
 * Overflows are also handled like doubles.
 */
trait TimeLike[This <: TimeLike[This]] extends Ordered[This] { self: This =>
  protected val ops: TimeLikeOps[This]
  import ops._

  /** The `TimeLike`'s value in nanoseconds. */
  def inNanoseconds: Long

  def inMicroseconds: Long = inNanoseconds / Duration.NanosPerMicrosecond
  def inMilliseconds: Long = inNanoseconds / Duration.NanosPerMillisecond
  def inLongSeconds: Long  = inNanoseconds / Duration.NanosPerSecond
  def inSeconds: Int       =
    if (inLongSeconds > Int.MaxValue) Int.MaxValue
    else if (inLongSeconds < Int.MinValue) Int.MinValue
    else inLongSeconds.toInt
  // Units larger than seconds safely fit into 32-bits when converting from a 64-bit nanosecond basis
  def inMinutes: Int       = (inNanoseconds / Duration.NanosPerMinute) toInt
  def inHours: Int         = (inNanoseconds / Duration.NanosPerHour) toInt
  def inDays: Int          = (inNanoseconds / Duration.NanosPerDay) toInt
  def inMillis: Long       = inMilliseconds // (Backwards compat)

  /**
   * Returns a value/`TimeUnit` pair; attempting to return coarser
   * grained values if possible (specifically: `TimeUnit.SECONDS` or
   * `TimeUnit.MILLISECONDS`) before resorting to the default
   * `TimeUnit.NANOSECONDS`.
   */
  def inTimeUnit: (Long, TimeUnit) = {
    // allow for APIs that may treat TimeUnit differently if measured in very tiny units.
    if (inNanoseconds % Duration.NanosPerSecond == 0) {
      (inSeconds, TimeUnit.SECONDS)
    } else if (inNanoseconds % Duration.NanosPerMillisecond == 0) {
      (inMilliseconds, TimeUnit.MILLISECONDS)
    } else {
      (inNanoseconds, TimeUnit.NANOSECONDS)
    }
  }

  /**
   * Adds `delta` to this `TimeLike`. Adding `Duration.Top` results
   * in the `TimeLike`'s `Top`, adding `Duration.Bottom` results in
   * the `TimeLike`'s `Bottom`.
   */
  def +(delta: Duration): This = delta match {
    case Duration.Top => Top
    case Duration.Bottom => Bottom
    case Duration.Undefined => Undefined
    case Duration.Nanoseconds(ns) =>
      try
        fromNanoseconds(LongOverflowArith.add(inNanoseconds, ns))
      catch {
        case _: LongOverflowException if ns < 0 => Bottom
        case _: LongOverflowException => Top
      }
  }

  def -(delta: Duration): This = this.+(-delta)

  /** Is this a finite TimeLike value? */
  def isFinite: Boolean

  /** The difference between the two `TimeLike`s */
  def diff(that: This): Duration

  /**
   * Rounds down to the nearest multiple of the given duration.  For example:
   * 127.seconds.floor(1.minute) => 2.minutes.  Taking the floor of a
   * Time object with duration greater than 1.hour can have unexpected
   * results because of timezones.
   */
  def floor(x: Duration): This = (this, x) match {
    case (Nanoseconds(0), Duration.Nanoseconds(0)) => Undefined
    case (Nanoseconds(num), Duration.Nanoseconds(0)) => if (num < 0) Bottom else Top
    case (Nanoseconds(num), Duration.Nanoseconds(denom)) => fromNanoseconds((num/denom) * denom)
    case (self, Duration.Nanoseconds(_)) => self
    case (_, _) => Undefined
  }

  def max(that: This): This =
    if ((this compare that) < 0) that else this

  def min(that: This): This =
    if ((this compare that) < 0) this else that

  def compare(that: This) =
    if ((that eq Top) || (that eq Undefined)) -1
    else if (that eq Bottom) 1
    else if (inNanoseconds < that.inNanoseconds) -1
    else if (inNanoseconds > that.inNanoseconds) 1
    else 0

  /** Equality within `maxDelta` */
  def moreOrLessEquals(other: This, maxDelta: Duration) =
    (other ne Undefined) && ((this == other) || (this diff other).abs <= maxDelta)
}

/**
 * Boxes for serialization. This has to be its own
 * toplevel object to remain serializable
 */
private[util] object TimeBox {
  case class Finite(nanos: Long) extends Serializable {
    private def readResolve(): Object = Time.fromNanoseconds(nanos)
  }

  case class Top() extends Serializable {
    private def readResolve(): Object = Time.Top
  }

  case class Bottom() extends Serializable {
    private def readResolve(): Object = Time.Bottom
  }

  case class Undefined() extends Serializable {
    private def readResolve(): Object = Time.Undefined
  }
}

/**
 * Use `Time.now` in your program instead of
 * `System.currentTimeMillis`, and unit tests will be able to adjust
 * the current time to verify timeouts and other time-dependent
 * behavior, without calling `sleep`.
 *
 * If you import the [[com.twitter.conversions.time]] implicits you
 * can write human-readable values such as `1.minute` or
 * `250.millis`.
 */
object Time extends TimeLikeOps[Time] {
  def fromNanoseconds(nanoseconds: Long): Time = new Time(nanoseconds)

  // This is needed for Java compatibility.
  override def fromSeconds(seconds: Int): Time = super.fromSeconds(seconds)
  override def fromMilliseconds(millis: Long): Time = super.fromMilliseconds(millis)

  /**
   * Time `Top` is greater than any other definable time, and is
   * equal only to itself. It may be used as a sentinel value,
   * representing a time infinitely far into the future.
   */
  val Top: Time = new Time(Long.MaxValue) {
    override def toString = "Time.Top"

    override def compare(that: Time) =
      if (that eq Undefined) -1
      else if (that eq Top) 0
      else 1

    override def +(delta: Duration) = delta match {
      case Duration.Bottom | Duration.Undefined => Undefined
      case _ => this  // Top or finite.
    }

    override def diff(that: Time) = that match {
      case Top | Undefined => Duration.Undefined
      case other => Duration.Top
    }

    override def isFinite = false

    private def writeReplace(): Object = TimeBox.Top()
  }

  /**
   * Time `Bottom` is smaller than any other time, and is equal only
   * to itself. It may be used as a sentinel value, representing a
   * time infinitely far in the past.
   */
  val Bottom: Time = new Time(Long.MinValue) {
    override def toString = "Time.Bottom"

    override def compare(that: Time) = if (this eq that) 0 else -1

    override def +(delta: Duration) = delta match {
      case Duration.Top | Duration.Undefined => Undefined
      case _ => this
    }

    override def diff(that: Time) = that match {
      case Bottom | Undefined => Duration.Undefined
      case other => Duration.Bottom
    }

    override def isFinite = false

    private def writeReplace(): Object = TimeBox.Bottom()
  }

  val Undefined: Time = new Time(0) {
    override def toString = "Time.Undefined"

    override def compare(that: Time) = if (this eq that) 0 else 1
    override def +(delta: Duration) = this
    override def diff(that: Time) = Duration.Undefined
    override def isFinite = false

    private def writeReplace(): Object = TimeBox.Undefined()
  }

  def now: Time = {
    localGetTime() match {
      case None =>
        Time.fromMilliseconds(System.currentTimeMillis())
      case Some(f) =>
        f()
    }
  }

  /**
   * The unix epoch. Times are measured relative to this.
   */
  val epoch = fromNanoseconds(0L)

  /**
   * A time larger than any other finite time. Synonymous to `Top`.
   */
  @deprecated("Use Time.Top", "5.4.0")
  val never = Top

  private val defaultFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  private val rssFormat = new TimeFormat("E, dd MMM yyyy HH:mm:ss Z")

  /**
   * On some systems (os x), nanoTime is just epoch time with greater
   * precision. On others (linux), it can be based on system uptime.
   *
   * TODO: This isn't always accurate, an NTP daemon may change at
   * runtime, and so the offset effectively changes.
   */
  @deprecated("nanoTimeOffset may be dangerous to use", "5.4.0")
  val nanoTimeOffset = (System.currentTimeMillis * 1000000) - System.nanoTime

  /**
   * Note, this should only ever be updated by methods used for testing.
   */
  private[util] val localGetTime = new Local[()=>Time]

  @deprecated("use Time.fromMilliseconds(...) instead", "2011-09-12") // date is a guess
  def apply(millis: Long) = fromMilliseconds(millis)
  def apply(date: Date): Time = fromMilliseconds(date.getTime)

  def at(datetime: String) = defaultFormat.parse(datetime)

  /**
   * Execute body with the time function replaced by `timeFunction`
   * WARNING: This is only meant for testing purposes.  You can break it
   * with nested calls if you have an outstanding Future executing in a worker pool.
   */
  def withTimeFunction[A](timeFunction: => Time)(body: TimeControl => A): A = {
    @volatile var tf = () => timeFunction
    val save = Local.save()
    try {
      val timeControl = new TimeControl {
        def set(time: Time) {
          tf = () => time
        }
        def advance(delta: Duration) {
          val newTime = tf() + delta
          /* Modifying the var here instead of resetting the local allows this method
            to work inside filters or between the creation and fulfillment of Promises.
            See BackupRequestFilterTest in Finagle for an example. */
          tf = () => newTime
        }
      }
      Time.localGetTime() = () => tf()
      body(timeControl)
    } finally {
      Local.restore(save)
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

  @deprecated("Use Stopwatch", "5.4.0")
  def measure(f: => Unit): Duration = {
    val begin = Time.now
    f
    Time.now - begin
  }

  @deprecated("Use Stopwatch", "5.4.0")
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
 *
 * The timezone used will be UTC.
 */
class TimeFormat(pattern: String, locale: Option[Locale]) {
  private[this] val format = locale map {
    new SimpleDateFormat(pattern, _)
  } getOrElse new SimpleDateFormat(pattern)
  format.setTimeZone(TimeZone.getTimeZone("UTC"))

  // jdk6 and jdk7 pick up the default locale differently in SimpleDateFormat
  // so we can't rely on Locale.getDefault here.
  // instead we let SimpleDateFormat do the work for us above
  /** Create a new TimeFormat with the default locale. **/
  def this(pattern: String) = this(pattern, None)

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

/**
 * An absolute point in time, represented as the number of
 * nanoseconds since the Unix epoch.
 */
sealed class Time private[util] (protected val nanos: Long) extends {
  protected val ops = Time
} with TimeLike[Time] with Serializable {
  import ops._

  def inNanoseconds = nanos

  /**
   * Renders this time using the default format.
   */
  override def toString = defaultFormat.format(this)

  override def equals(other: Any) = other match {
    case t: Time => (this compare t) == 0
    case _ => false
  }

  override def hashCode = nanos.hashCode

  /**
   * Formats this Time according to the given SimpleDateFormat pattern.
   */
  def format(pattern: String) = new TimeFormat(pattern).format(this)

  /**
   * Formats this Time according to the given SimpleDateFormat pattern and locale.
   */
  def format(pattern: String, locale: Locale) = new TimeFormat(pattern, Some(locale)).format(this)

  /**
   * Creates a duration between two times.
   */
  def -(that: Time) = diff(that)

  override def isFinite = true

  def diff(that: Time) =  that match {
    case Undefined => Duration.Undefined
    case Top => Duration.Bottom
    case Bottom => Duration.Top
    case other =>
      try
        new Duration(LongOverflowArith.sub(this.inNanoseconds, other.inNanoseconds))
      catch {
        case _: LongOverflowException if other.inNanoseconds < 0 => Duration.Top
        case _: LongOverflowException => Duration.Bottom
      }
  }

  /**
   * Duration that has passed between the given time and the current time.
   */
  def since(that: Time) = this - that

  /**
   * Duration that has passed between the epoch and the current time.
   */
  def sinceEpoch = since(epoch)

  /**
   * Gets the current time as Duration since now
   */
  def sinceNow = since(now)

  /**
   * Duration that has passed between the epoch and the current time.
   */
  @deprecated("use sinceEpoch", "2011-05-23") // date is a guess
  def fromEpoch = this - epoch

  /**
   * Duration between current time and the givne time.
   */
  def until(that: Time) = that - this

  /**
   * Gets the duration between this time and the epoch.
   */
  def untilEpoch = until(epoch)

  /**
   * Gets the duration between this time and now.
   */
  def untilNow = until(now)

  /**
   * Converts this Time object to a java.util.Date
   */
  def toDate = new Date(inMillis)

  private def writeReplace(): Object = TimeBox.Finite(inNanoseconds)
}
