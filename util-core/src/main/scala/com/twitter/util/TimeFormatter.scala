/*
 * Copyright 2022 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

object TimeFormatter {

  /**
   * Creates a new [[TimeFormatter]] based on a pattern and optionally a locale and a time zone.
   * @param pattern the [[DateTimeFormatter]] compatible pattern
   * @param locale locale, defaults to platform default locale for formatting
   * @param zoneId time zone, defaults to UTC
   * @return a new [[TimeFormatter]] for the specified pattern, locale, and time zone.
   */
  def apply(
    pattern: String,
    locale: Locale = Locale.getDefault(Locale.Category.FORMAT),
    zoneId: ZoneId = ZoneOffset.UTC
  ): TimeFormatter = {
    val (has_y, has_M, has_d, has_H) = TwitterDateFormat.validatePattern(pattern, newFormat = true)
    val builder = new DateTimeFormatterBuilder()
      .parseLenient()
      .parseCaseInsensitive()
      .appendPattern(pattern)
    // These defaults match those that SimpleDateFormat would use during
    // parsing when these fields are not specified. [[Instant.from]] can
    // produce an Instant from parse results if at least all the fields up to
    // HOUR_OF_DAY are present.
    if (!has_y) builder.parseDefaulting(ChronoField.YEAR_OF_ERA, 1970)
    if (!has_M) builder.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
    if (!has_d) builder.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
    if (!has_H) builder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
    new TimeFormatter(builder.toFormatter(locale).withZone(zoneId))
  }
}

/**
 * A parser and formatter for Time instances. Instances of this class are immutable and threadsafe.
 * Use [[TimeFormatter$.apply]] to construct instances from [[DateTimeFormatter]]-compatible pattern
 * strings.
 */
class TimeFormatter private[util] (format: DateTimeFormatter) {

  /**
   * Parses a string into a [[Time]].
   * @param str the string to parse
   * @return the parsed time object
   * @throws java.time.format.DateTimeParseException if the string can not be parsed
   */
  def parse(str: String): Time =
    Time.fromInstant(Instant.from(format.parse(str)))

  /**
   * Formats a [[Time]] object into a string.
   * @param time the time object to format
   * @return a string with formatted representation of the time object
   */
  def format(time: Time): String =
    format.format(time.toInstant)
}
