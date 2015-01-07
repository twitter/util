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

  def validatePattern(pattern: String): Unit = {
    val stripped = stripSingleQuoted(pattern)
    if (stripped.contains('Y') && !(stripped.contains('w'))) {
      throw new IllegalArgumentException(
        "Invalid date format uses 'Y' for week-of-year without 'w': %s".format(pattern))
    }
  }

  /** Patterns can contain quoted strings 'foo' which we should ignore in checking the pattern */
  private[util] def stripSingleQuoted(pattern: String): String = {
    val buf = new StringBuilder(pattern.size)
    var startIndex = 0
    var endIndex = 0
    while (startIndex >= 0 && startIndex < pattern.size) {
      endIndex = pattern.indexOf('\'', startIndex)
      if (endIndex < 0) {
        buf.append(pattern.substring(startIndex))
        startIndex = -1
      } else {
        buf.append(pattern.substring(startIndex, endIndex))
        startIndex = endIndex+1
        endIndex = pattern.indexOf('\'', startIndex)
        if (endIndex < 0) {
          throw new IllegalArgumentException("Unmatched quote in date format: %s".format(pattern))
        }
        startIndex = endIndex+1
      }
    }
    buf.toString
  }
}
