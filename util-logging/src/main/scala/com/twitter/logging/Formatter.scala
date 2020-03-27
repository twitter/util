/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.logging

import com.twitter.util.TwitterDateFormat
import java.text.{MessageFormat, DateFormat}
import java.util.regex.Pattern
import java.util.{Date, GregorianCalendar, TimeZone, logging => javalog}
import scala.collection.mutable
import java.{util => ju}

private[logging] object Formatter {
  // FIXME: might be nice to unmangle some scala names here.
  private[logging] def formatStackTrace(t: Throwable, limit: Int): List[String] = {
    var out = new mutable.ListBuffer[String]
    if (limit > 0) {
      out ++= t.getStackTrace.map { elem => "    at %s".format(elem.toString) }
      if (out.length > limit) {
        out.trimEnd(out.length - limit)
        out += "    (...more...)"
      }
    }
    if ((t.getCause ne null) && (t.getCause ne t)) {
      out += "Caused by %s".format(t.getCause.toString)
      out ++= formatStackTrace(t.getCause, limit)
    }
    out.toList
  }

  val DateFormatRegex: Pattern = Pattern.compile("<([^>]+)>")
  val DefaultStackTraceSizeLimit = 30
  val DefaultPrefix = "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: "
}

/**
 * A standard log formatter for scala. This extends the java built-in log formatter.
 *
 * Truncation, exception formatting, multi-line logging, and time zones
 * are handled in this class. Subclasses are called for formatting the
 * line prefix, formatting the date, and determining the line terminator.
 *
 * @param timezone
 * Should dates in log messages be reported in a different time zone rather than
 * local time? If set, the time zone name must be one known by the java `TimeZone` class.
 *
 * @param truncateAt
 * Truncate log messages after N characters. 0 = don't truncate (the default).
 *
 * @param truncateStackTracesAt
 * Truncate stack traces in exception logging (line count).
 *
 * @param useFullPackageNames
 * Use full package names like "com.example.thingy" instead of just the
 * toplevel name like "thingy"?
 *
 * @param prefix
 * Format for the log-line prefix, if any.
 *
 * There are two positional format strings (printf-style): the name of the level being logged
 * (for example, "ERROR") and the name of the package that's logging (for example, "jobs").
 *
 * A string in `<` angle brackets `>` will be used to format the log entry's timestamp, using
 * java's `SimpleDateFormat`.
 *
 * For example, a format string of:
 *
 *     "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: "
 *
 * will generate a log line prefix of:
 *
 *     "ERR [20080315-18:39:05.033] jobs: "
 */
class Formatter(
  val timezone: Option[String] = None,
  val truncateAt: Int = 0,
  val truncateStackTracesAt: Int = Formatter.DefaultStackTraceSizeLimit,
  val useFullPackageNames: Boolean = false,
  val prefix: String = Formatter.DefaultPrefix)
    extends javalog.Formatter {

  private val matcher = Formatter.DateFormatRegex.matcher(prefix)

  private val DATE_FORMAT = TwitterDateFormat(
    if (matcher.find()) matcher.group(1) else "yyyyMMdd-HH:mm:ss.SSS"
  )
  private val FORMAT = matcher.replaceFirst("%3\\$s")

  /**
   * Return the date formatter to use for log messages.
   */
  def dateFormat: DateFormat = DATE_FORMAT

  /**
   * Calendar to use for time zone display in date-time formatting.
   */
  val calendar: ju.GregorianCalendar = if (timezone.isDefined) {
    new GregorianCalendar(TimeZone.getTimeZone(timezone.get))
  } else {
    new GregorianCalendar
  }
  dateFormat.setCalendar(calendar)

  /**
   * Return the string to prefix each log message with, given a log level,
   * formatted date string, and package name.
   */
  def formatPrefix(level: javalog.Level, date: String, name: String): String = {
    FORMAT.format(formatLevelName(level), name, date)
  }

  /**
   * Return the string representation of a given log level's name
   */
  def formatLevelName(level: javalog.Level): String = {
    level match {
      case x: Level =>
        x.name
      case x: javalog.Level =>
        Logger.levels.get(x.intValue) match {
          case None => "%03d".format(x.intValue)
          case Some(level) => level.name
        }
    }
  }

  /**
   * Return the line terminator (if any) to use at the end of each log
   * message.
   */
  def lineTerminator: String = "\n"

  def formatMessageLines(record: javalog.LogRecord): Array[String] = {
    val message = truncateText(formatText(record))

    val containsNewLine = message.indexOf('\n') >= 0
    if (!containsNewLine && record.getThrown == null) {
      Array(message)
    } else {
      val splitOnNewlines = message.split("\n")
      val numThrowLines = if (record.getThrown == null) 0 else 20

      val lines = new mutable.ArrayBuffer[String](splitOnNewlines.length + numThrowLines)
      lines ++= splitOnNewlines

      if (record.getThrown ne null) {
        val traceLines = Formatter.formatStackTrace(record.getThrown, truncateStackTracesAt)
        lines += record.getThrown.toString
        if (traceLines.nonEmpty)
          lines ++= traceLines
      }
      lines.toArray
    }
  }

  /**
   * Return formatted text from a java LogRecord.
   */
  def formatText(record: javalog.LogRecord): String = {
    record match {
      case null => ""
      case r: LogRecord => {
        r.getParameters match {
          case null => r.getMessage
          case formatArgs => String.format(r.getMessage, formatArgs: _*)
        }
      }
      case r: javalog.LogRecord => {
        r.getParameters match {
          case null => r.getMessage
          case formatArgs => MessageFormat.format(r.getMessage, formatArgs: _*)
        }
      }
    }
  }

  override def format(record: javalog.LogRecord): String = {
    val name = formatName(record)
    val prefix = formatPrefix(record.getLevel, dateFormat.format(new Date(record.getMillis)), name)
    formatMessageLines(record).mkString(prefix, lineTerminator + prefix, lineTerminator)
  }

  /**
   * Returns the formatted name of the node given a LogRecord
   */
  def formatName(record: javalog.LogRecord): String = {
    record.getLoggerName match {
      case null => "(root)"
      case "" => "(root)"
      case n => {
        val nameSegments = n.split("\\.")
        if (nameSegments.length >= 2) {
          if (useFullPackageNames) {
            nameSegments.slice(0, nameSegments.length - 1).mkString(".")
          } else {
            nameSegments(nameSegments.length - 2)
          }
        } else {
          n
        }
      }
    }
  }

  /**
   * Truncates the text from a java LogRecord, if necessary
   */
  def truncateText(message: String): String =
    if ((truncateAt > 0) && (message.length > truncateAt))
      message.take(truncateAt) + "..."
    else
      message
}

/**
 * Formatter that logs only the text of a log message, with no prefix (no date, etc).
 */
object BareFormatter extends Formatter {
  override def format(record: javalog.LogRecord): String = formatText(record) + lineTerminator
}
