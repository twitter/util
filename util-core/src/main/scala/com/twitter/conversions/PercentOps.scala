package com.twitter.conversions

/**
 * Implicits for turning `x.percent` (where `x` is an `Int` or `Double`) into a `Double`
 * scaled to where 1.0 is 100 percent.
 *
 * @note Negative values, fractional values, and values greater than 100 are permitted.
 *
 * @example
 * {{{
 *    import com.twitter.conversions.PercentOps._
 *
 *    1.percent == 0.01
 *    100.percent == 1.0
 *    99.9.percent == 0.999
 *    500.percent == 5.0
 *    -10.percent == -0.1
 * }}}
 */
object PercentOps {

  private val BigDecimal100 = BigDecimal(100.0)

  implicit class RichPercent(val value: Double) extends AnyVal {
    // convert wrapped to BigDecimal to preserve precision when dividing Doubles
    def percent: Double =
      if (value.equals(Double.NaN)
        || value.equals(Double.PositiveInfinity)
        || value.equals(Double.NegativeInfinity)) value
      else (BigDecimal(value) / BigDecimal100).doubleValue
  }

}
