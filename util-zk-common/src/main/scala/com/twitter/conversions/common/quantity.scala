package com.twitter.conversions.common

import java.util.concurrent.TimeUnit

import com.twitter.common.quantity.{Amount, Time => CommonTime}
import com.twitter.conversions.time._
import com.twitter.util.Duration

object quantity {
  val COMMON_FOREVER: Duration = 0.millis

  class CommonDurationAdapter(d: Duration) {
    def toIntAmount = Amount.of(d.inMillis.toInt, CommonTime.MILLISECONDS)
    def toLongAmount = Amount.of(d.inMillis.toLong, CommonTime.MILLISECONDS)
  }

  /** Implicit conversion of Duration to CommonDuration */
  implicit def commonDuration(d: Duration) = new CommonDurationAdapter(d)

  class DurationAmountAdapter(a: Amount[java.lang.Long, CommonTime]) {
    def toDuration: Duration = Duration(a.getValue.longValue, translateUnit(a.getUnit))

    def translateUnit(unit: CommonTime) = unit match {
      case CommonTime.DAYS         => TimeUnit.DAYS
      case CommonTime.HOURS        => TimeUnit.HOURS
      case CommonTime.MINUTES      => TimeUnit.MINUTES
      case CommonTime.MICROSECONDS => TimeUnit.MICROSECONDS
      case CommonTime.MILLISECONDS => TimeUnit.MILLISECONDS
      case CommonTime.NANOSECONDS  => TimeUnit.NANOSECONDS
      case CommonTime.SECONDS      => TimeUnit.SECONDS
    }
  }

  /** Implicit conversion of Amount to DurationAmountAdapter */
  implicit def commonDuration(a: Amount[java.lang.Long, CommonTime]) = new DurationAmountAdapter(a)
}
