package com.twitter.conversions

import scala.language.implicitConversions

/**
 * Implicits for turning x.percent (where x is an Int or Double) into a Double scaled to
 * where 1.0 is 100 percent.
 *
 * @note Negative values, fractional values, and values greater than 100 are permitted.
 *
 * @example
 * {{{
 *    1.percent == 0.01
 *    100.percent == 1.0
 *    99.9.percent == 0.999
 *    500.percent == 5.0
 *    -10.percent == -0.1
 * }}}
 */
@deprecated("Use the AnyVal version `com.twitter.conversions.PercentOps`", "2018-12-05")
object percent {

  private val BigDecimal100 = BigDecimal(100.0)

  class RichDoublePercent private[conversions] (val wrapped: Double) extends AnyVal {
    // convert wrapped to BigDecimal to preserve precision when dividing Doubles
    def percent: Double =
      if (wrapped.equals(Double.NaN)
        || wrapped.equals(Double.PositiveInfinity)
        || wrapped.equals(Double.NegativeInfinity)) wrapped
      else (BigDecimal(wrapped) / BigDecimal100).doubleValue
  }

  implicit def intPercentToFractionalDouble(i: Int): RichDoublePercent =
    new RichDoublePercent(i)
  implicit def doublePercentToFractionalDouble(d: Double): RichDoublePercent =
    new RichDoublePercent(d)
}
