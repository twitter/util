package com.twitter.util

import java.text.{ParsePosition, SimpleDateFormat}
import java.util.Date
import java.util.concurrent.TimeUnit

object TimeConversions {
  class RichWholeNumber(wrapped: Long) {
    def seconds = new Duration(wrapped * 1000)
    def second = seconds
    def milliseconds = new Duration(wrapped)
    def millisecond = milliseconds
    def microseconds = new Duration(wrapped * 1000)
    def nanoseconds = new Duration(wrapped * 1000000)
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

/**
 * Use `Time.now` in your app instead of `System.currentTimeMillis`, and
 * unit tests will be able to adjust the current time to verify timeouts
 * and other time-dependent behavior, without calling `sleep`.
 */
object Time {
  import TimeConversions._

  private val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")

  private[Time] var fn: () => Time = () => new Time(System.currentTimeMillis)

  def now: Time = fn()
  def never: Time = Time(0.seconds)

  def apply(at: Long): Time = new Time(at)
  def apply(at: Duration): Time = new Time(at.inMillis)

  def at(datetime: String) = {
    val date = formatter.parse(datetime, new ParsePosition(0))
    if (date == null) {
      throw new Exception("Unable to parse date-time: " + datetime)
    }
    new Time(date.getTime())
  }

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
  import TimeConversions._

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
