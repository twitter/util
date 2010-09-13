package com.twitter.util

import java.text.{ParsePosition, SimpleDateFormat}

object TimeConversions {
  class RichWholeNumber(wrapped: Long) {
    def seconds = new Duration(wrapped * 1000)
    def second = seconds
    def milliseconds = new Duration(wrapped)
    def millisecond = milliseconds
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
}


/**
 * Use `Time.now` in your app instead of `System.currentTimeMillis`, and
 * unit tests will be able to adjust the current time to verify timeouts
 * and other time-dependent behavior, without calling `sleep`.
 */
object Time {
  import TimeConversions._

  private val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")

  private var fn: () => Time = null

  reset()

  /**
   * Freeze the clock. Time will not pass until reset.
   */
  def freeze() {
    Time.now = new Time(System.currentTimeMillis)
  }

  def now: Time = fn()
  def never: Time = Time(0.seconds)

  def now_=(at: Time) {
    fn = () => at
  }

  def reset() {
    fn = { () => new Time(System.currentTimeMillis) }
  }

  def apply(at: Duration) = new Time(at.inMillis)

  def advance(delta: Duration) {
    now = now + delta
  }

  def at(datetime: String) = {
    val date = formatter.parse(datetime, new ParsePosition(0))
    if (date == null) {
      throw new Exception("Unable to parse date-time: " + datetime)
    }
    new Time(date.getTime())
  }
}

class Duration(val at: Long) {
  def inDays = (inHours / 24)
  def inHours = (inMinutes / 60)
  def inMinutes = (inSeconds / 60)
  def inSeconds = (at / 1000L).toInt
  def inMillis = at
  def inMilliseconds = at

  def +(delta: Duration) = new Duration(at + delta.inMillis)
  def -(delta: Duration) = new Duration(at - delta.inMillis)

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

  def >(other: Duration) = at > other.at
  def <(other: Duration) = at < other.at
  def >=(other: Duration) = at >= other.at
  def <=(other: Duration) = at <= other.at
}

class Time(at: Long) extends Duration(at) {
  override def +(delta: Duration) = new Time(at + delta.inMillis)
  override def -(delta: Duration) = new Time(at - delta.inMillis)
}
