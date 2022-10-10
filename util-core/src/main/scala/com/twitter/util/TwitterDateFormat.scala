package com.twitter.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A `java.text.SimpleDateFormat` that avoids confusion between YYYY and yyyy.
 * One should never use YYYY (week year) unless the format also includes the week of year.
 */
object TwitterDateFormat {
  def apply(pattern: String): SimpleDateFormat = {
    validatePattern(pattern)
    new SimpleDateFormat(pattern)
  }

  def apply(pattern: String, locale: Locale): SimpleDateFormat = {
    validatePattern(pattern)
    new SimpleDateFormat(pattern, locale)
  }

  // Returns a quadruple of booleans, each signifying whether the pattern has
  // a symbol for year-of-era (y), month-of-year (M), day-of-month (d), and
  // hour-of-day (H), respectively. Throws an exception if week-year (Y)
  // is used without week-in-year (w).
  def validatePattern(
    pattern: String,
    newFormat: Boolean = false
  ): (Boolean, Boolean, Boolean, Boolean) = {
    var quoteOff = true
    var optionalLevel = 0
    var has_Y = false
    var has_w = false
    var has_y = false
    var has_M = false
    var has_d = false
    var has_H = false
    val chars = pattern.toCharArray
    var i = 0
    while (i < chars.length) {
      chars(i) match {
        case '\'' => quoteOff = !quoteOff
        case '[' if newFormat && quoteOff => optionalLevel += 1
        case ']' if newFormat && quoteOff => optionalLevel -= 1
        case 'y' if !has_y && quoteOff && optionalLevel == 0 => has_y = true
        case 'M' if !has_M && quoteOff && optionalLevel == 0 => has_M = true
        case 'd' if !has_d && quoteOff && optionalLevel == 0 => has_d = true
        case 'H' if !has_H && quoteOff && optionalLevel == 0 => has_H = true
        case 'Y' if !has_Y && quoteOff && optionalLevel == 0 => has_Y = true
        case 'w' if !has_w && quoteOff && optionalLevel == 0 => has_w = true
        case _ => ()
      }
      i += 1
    }
    if (has_Y && !has_w) {
      throw new IllegalArgumentException(
        "Invalid date format uses 'Y' for week-of-year without 'w': %s".format(pattern)
      )
    }
    (has_y, has_M, has_d, has_H)
  }
}
