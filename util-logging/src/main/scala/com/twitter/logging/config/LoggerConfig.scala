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
package config

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Config, Duration, NetUtil}

@deprecated("use LoggerFactory", "6.12.1")
class LoggerConfig extends Config[Logger] {

  /**
   * Name of the logging node. The default ("") is the top-level logger.
   */
  var node: String = ""

  /**
   * Log level for this node. Leaving it None is java's secret signal to use the parent logger's
   * level.
   */
  var level: Option[Level] = None

  /**
   * Where to send log messages.
   */
  var handlers: List[HandlerConfig] = Nil

  /**
   * Override to have log messages stop at this node. Otherwise they are passed up to parent
   * nodes.
   */
  var useParents = true

  def apply(): Logger = {
    val logger = Logger.get(node)
    level.foreach { x =>
      logger.setLevel(x)
    }
    handlers.foreach { h =>
      logger.addHandler(h())
    }
    logger.setUseParentHandlers(useParents)
    logger
  }
}

@deprecated("use Formatter directly", "6.12.1")
class FormatterConfig extends Config[Formatter] {

  /**
   * Should dates in log messages be reported in a different time zone rather than local time?
   * If set, the time zone name must be one known by the java `TimeZone` class.
   */
  var timezone: Option[String] = None

  /**
   * Truncate log messages after N characters. 0 = don't truncate (the default).
   */
  var truncateAt: Int = 0

  /**
   * Truncate stack traces in exception logging (line count).
   */
  var truncateStackTracesAt: Int = 30

  /**
   * Use full package names like "com.example.thingy" instead of just the toplevel name like
   * "thingy"?
   */
  var useFullPackageNames: Boolean = false

  /**
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
  var prefix: String = "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: "

  def apply() =
    new Formatter(timezone, truncateAt, truncateStackTracesAt, useFullPackageNames, prefix)
}

@deprecated("use BareFormatter directly", "6.12.1")
object BareFormatterConfig extends FormatterConfig {
  override def apply() = BareFormatter
}

@deprecated("use SyslogFormatter directly", "6.12.1")
class SyslogFormatterConfig extends FormatterConfig {

  /**
   * Hostname to prepend to log lines.
   */
  var hostname: String = NetUtil.getLocalHostName()

  /**
   * Optional server name to insert before log entries.
   */
  var serverName: Option[String] = None

  /**
   * Use new standard ISO-format timestamps instead of old BSD-format?
   */
  var useIsoDateFormat: Boolean = true

  /**
   * Priority level in syslog numbers.
   */
  var priority: Int = SyslogHandler.PRIORITY_USER

  def serverName_=(name: String): Unit = { serverName = Some(name) }

  override def apply() =
    new SyslogFormatter(
      hostname,
      serverName,
      useIsoDateFormat,
      priority,
      timezone,
      truncateAt,
      truncateStackTracesAt
    )
}

@deprecated("use Formatter directly", "6.12.1")
trait HandlerConfig extends Config[Handler] {
  var formatter: FormatterConfig = new FormatterConfig

  var level: Option[Level] = None
}

@deprecated("use HandlerFactory", "6.12.1")
class ConsoleHandlerConfig extends HandlerConfig {
  def apply() = new ConsoleHandler(formatter(), level)
}

@deprecated("use HandlerFactory", "6.12.1")
class ThrottledHandlerConfig extends HandlerConfig {

  /**
   * Timespan to consider duplicates. After this amount of time, duplicate entries will be logged
   * again.
   */
  var duration: Duration = 0.seconds

  /**
   * Maximum duplicate log entries to pass before suppressing them.
   */
  var maxToDisplay: Int = Int.MaxValue

  /**
   * Wrapped handler.
   */
  var handler: HandlerConfig = null

  def apply() = new ThrottledHandler(handler(), duration, maxToDisplay)
}

@deprecated("use HandlerFactory", "6.12.1")
class QueuingHandlerConfig extends HandlerConfig {
  def apply() = new QueueingHandler(handler(), maxQueueSize)

  /**
   * Maximum queue size.  Records are dropped when queue overflows.
   */
  var maxQueueSize: Int = Int.MaxValue

  /**
   * Wrapped handler.
   */
  var handler: HandlerConfig = null
}

@deprecated("use HandlerFactory", "6.12.1")
class FileHandlerConfig extends HandlerConfig {

  /**
   * Filename to log to.
   */
  var filename: String = null

  /**
   * When to roll the logfile.
   */
  var roll: Policy = Policy.Never

  /**
   * Append to an existing logfile, or truncate it?
   */
  var append: Boolean = true

  /**
   * How many rotated logfiles to keep around, maximum. -1 means to keep them all.
   */
  var rotateCount: Int = -1

  def apply() = new FileHandler(filename, roll, append, rotateCount, formatter(), level)
}

@deprecated("use HandlerFactory", "6.12.1")
class SyslogHandlerConfig extends HandlerConfig {

  /**
   * Syslog server hostname.
   */
  var server: String = "localhost"

  /**
   * Syslog server port.
   */
  var port: Int = SyslogHandler.DEFAULT_PORT

  def apply() = new SyslogHandler(server, port, formatter(), level)
}

@deprecated("use HandlerFactory", "6.12.1")
class ScribeHandlerConfig extends HandlerConfig {
  // send a scribe message no more frequently than this:
  var bufferTime = 100.milliseconds

  // don't connect more frequently than this (when the scribe server is down):
  var connectBackoff = 15.seconds

  var maxMessagesPerTransaction = 1000
  var maxMessagesToBuffer = 10000

  var hostname = "localhost"
  var port = 1463
  var category = "scala"

  def apply() =
    new ScribeHandler(
      hostname,
      port,
      category,
      bufferTime,
      connectBackoff,
      maxMessagesPerTransaction,
      maxMessagesToBuffer,
      formatter(),
      level
    )
}
