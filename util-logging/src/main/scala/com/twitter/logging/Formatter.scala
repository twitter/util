/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import java.text.SimpleDateFormat
import java.util.{Date, GregorianCalendar, TimeZone, logging => javalog}
import java.util.regex.Pattern
import scala.collection.mutable
import com.twitter.conversions.string._
import config._

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

  val dateFormatRegex = Pattern.compile("<([^>]+)>")
}

/**
 * A standard log formatter for scala. This extends the java built-in log formatter.
 *
 * Truncation, exception formatting, multi-line logging, and time zones
 * are handled in this class. Subclasses are called for formatting the
 * line prefix, formatting the date, and determining the line terminator.
 */
class Formatter(val timezone: Option[String], val truncateAt: Int, val truncateStackTracesAt: Int,
                val useFullPackageNames: Boolean, val prefix: String)
      extends javalog.Formatter {

  // for default console logging.
  def this() = this(None, 0, 30, false, "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: ")

  private val matcher = Formatter.dateFormatRegex.matcher(prefix)

  private val DATE_FORMAT = new SimpleDateFormat(if (matcher.find()) matcher.group(1) else "yyyyMMdd-HH:mm:ss.SSS")
  private val FORMAT = matcher.replaceFirst("%3\\$s")

  /**
   * Return the date formatter to use for log messages.
   */
  def dateFormat: SimpleDateFormat = DATE_FORMAT

  /**
   * Calendar to use for time zone display in date-time formatting.
   */
  val calendar = if (timezone.isDefined) {
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
    val levelName = level match {
      // if it maps to one of our levels, use our name.
      case x: Level =>
        x.name
      case x: javalog.Level =>
        Logger.levelsMap.get(x.intValue) match {
          case None => "%03d".format(x.intValue)
          case Some(level) => level.name
        }
    }

    FORMAT.format(levelName, name, date)
  }

  /**
   * Return the line terminator (if any) to use at the end of each log
   * message.
   */
  def lineTerminator: String = "\n"

  /**
   * Return formatted text from a java LogRecord.
   */
  def formatText(record: javalog.LogRecord): String = {
    record match {
      case null =>
        ""
      case r: LazyLogRecord =>
        r.generate.toString
      case r: javalog.LogRecord =>
        r.getParameters match {
          case null =>
            r.getMessage
          case formatArgs =>
            String.format(r.getMessage, formatArgs: _*)
        }
    }
  }

  override def format(record: javalog.LogRecord): String = {
    val name = record.getLoggerName match {
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

    var message = formatText(record)

    if ((truncateAt > 0) && (message.length > truncateAt)) {
      message = message.substring(0, truncateAt) + "..."
    }

    // allow multi-line log entries to be atomic:
    var lines = new mutable.ArrayBuffer[String]
    lines ++= message.split("\n")

    if (record.getThrown ne null) {
      val traceLines = Formatter.formatStackTrace(record.getThrown, truncateStackTracesAt)
      if (traceLines.size > 0) {
        lines += record.getThrown.toString
        lines ++= traceLines
      }
    }
    val prefix = formatPrefix(record.getLevel, dateFormat.format(new Date(record.getMillis)), name)
    lines.mkString(prefix, lineTerminator + prefix, lineTerminator)
  }
}

/**
 * Formatter that logs only the text of a log message, with no prefix (no date, etc).
 */
object BareFormatter extends Formatter {
  override def format(record: javalog.LogRecord) = formatText(record) + lineTerminator
}
