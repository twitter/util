package com.twitter.conversions

import com.twitter.util.Duration
import java.util.concurrent.TimeUnit

/**
 * Implicits for writing readable [[Duration]]s.
 *
 * @example
 * {{{
 * import com.twitter.conversions.DurationOps._
 *
 * 2000.nanoseconds
 * 50.milliseconds
 * 1.second
 * 24.hours
 * 40.days
 * }}}
 */
object DurationOps {

  implicit class RichDuration(val numNanos: Long) extends AnyVal {
    def nanoseconds: Duration = Duration(numNanos, TimeUnit.NANOSECONDS)
    def nanosecond: Duration = nanoseconds
    def microseconds: Duration = Duration(numNanos, TimeUnit.MICROSECONDS)
    def microsecond: Duration = microseconds
    def milliseconds: Duration = Duration(numNanos, TimeUnit.MILLISECONDS)
    def millisecond: Duration = milliseconds
    def millis: Duration = milliseconds
    def seconds: Duration = Duration(numNanos, TimeUnit.SECONDS)
    def second: Duration = seconds
    def minutes: Duration = Duration(numNanos, TimeUnit.MINUTES)
    def minute: Duration = minutes
    def hours: Duration = Duration(numNanos, TimeUnit.HOURS)
    def hour: Duration = hours
    def days: Duration = Duration(numNanos, TimeUnit.DAYS)
    def day: Duration = days
  }

}
