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

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress}
import java.util.concurrent.Executors
import java.util.{logging => javalog}

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.util.{TwitterDateFormat, NetUtil}

object SyslogHandler {
  val DEFAULT_PORT = 514

  val PRIORITY_USER = 8
  val PRIORITY_DAEMON = 24
  val PRIORITY_LOCAL0 = 128
  val PRIORITY_LOCAL1 = 136
  val PRIORITY_LOCAL2 = 144
  val PRIORITY_LOCAL3 = 152
  val PRIORITY_LOCAL4 = 160
  val PRIORITY_LOCAL5 = 168
  val PRIORITY_LOCAL6 = 176
  val PRIORITY_LOCAL7 = 184

  private val SEVERITY_EMERGENCY = 0
  private val SEVERITY_ALERT = 1
  private val SEVERITY_CRITICAL = 2
  private val SEVERITY_ERROR = 3
  private val SEVERITY_WARNING = 4
  private val SEVERITY_NOTICE = 5
  private val SEVERITY_INFO = 6
  private val SEVERITY_DEBUG = 7

  /**
   * Convert the java/scala log level into its closest syslog-ng severity.
   */
  private[logging] def severityForLogLevel(level: Int): Int = {
    if (level >= Level.FATAL.value) {
      SEVERITY_ALERT
    } else if (level >= Level.CRITICAL.value) {
      SEVERITY_CRITICAL
    } else if (level >= Level.ERROR.value) {
      SEVERITY_ERROR
    } else if (level >= Level.WARNING.value) {
      SEVERITY_WARNING
    } else if (level >= Level.INFO.value) {
      SEVERITY_INFO
    } else {
      SEVERITY_DEBUG
    }
  }

  val ISO_DATE_FORMAT = TwitterDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  val OLD_SYSLOG_DATE_FORMAT = TwitterDateFormat("MMM dd HH:mm:ss")

  /**
   * Generates a HandlerFactory that returns a SyslogHandler.
   *
   * @param server
   * Syslog server hostname.
   *
   * @param port
   * Syslog server port.
   */
  def apply(
    server: String = "localhost",
    port: Int = SyslogHandler.DEFAULT_PORT,
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) = () => new SyslogHandler(server, port, formatter, level)
}

class SyslogHandler(
    val server: String,
    val port: Int,
    formatter: Formatter,
    level: Option[Level])
  extends Handler(formatter, level) {

  private val socket = new DatagramSocket
  private[logging] val dest = new InetSocketAddress(server, port)

  def flush() = { }
  def close() = { }

  def publish(record: javalog.LogRecord) = {
    val data = formatter.format(record).getBytes
    val packet = new DatagramPacket(data, data.length, dest)
    SyslogFuture {
      try {
        socket.send(packet)
      } catch {
        case e: Throwable =>
          System.err.println(Formatter.formatStackTrace(e, 30).mkString("\n"))
      }
    }
  }
}

/**
 * @param hostname
 * Hostname to prepend to log lines.
 *
 * @param serverName
 * Optional server name to insert before log entries.
 *
 * @param useIsoDateFormat
 * Use new standard ISO-format timestamps instead of old BSD-format?
 *
 * @param priority
 * Priority level in syslog numbers.
 *
 * @param timezone
 * Should dates in log messages be reported in a different time zone rather than local time?
 * If set, the time zone name must be one known by the java `TimeZone` class.
 *
 * @param truncateAt
 * Truncate log messages after N characters. 0 = don't truncate (the default).
 *
 * @param truncateStackTracesAt
 * Truncate stack traces in exception logging (line count).
 */
class SyslogFormatter(
    val hostname: String = NetUtil.getLocalHostName(),
    val serverName: Option[String] = None,
    val useIsoDateFormat: Boolean = true,
    val priority: Int = SyslogHandler.PRIORITY_USER,
    timezone: Option[String] = None,
    truncateAt: Int = 0,
    truncateStackTracesAt: Int = Formatter.DefaultStackTraceSizeLimit)
  extends Formatter(
    timezone,
    truncateAt,
    truncateStackTracesAt,
    useFullPackageNames = false,
    prefix = "") {

  override def dateFormat = if (useIsoDateFormat) {
    SyslogHandler.ISO_DATE_FORMAT
  } else {
    SyslogHandler.OLD_SYSLOG_DATE_FORMAT
  }

  override def lineTerminator = ""

  override def formatPrefix(level: javalog.Level, date: String, name: String): String = {
    val syslogLevel = level match {
      case x: Level => SyslogHandler.severityForLogLevel(x.value)
      case x: javalog.Level => SyslogHandler.severityForLogLevel(x.intValue)
    }
    serverName match {
      case None =>
        "<%d>%s %s %s: ".format(priority | syslogLevel, date, hostname, name)
      case Some(serverName) =>
        "<%d>%s %s [%s] %s: ".format(priority | syslogLevel, date, hostname, serverName, name)
    }
  }
}

object SyslogFuture {
  private val executor = Executors.newSingleThreadExecutor(
    new NamedPoolThreadFactory("TWITTER-UTIL-SYSLOG", true/*daemon*/))
  private val noop = new Runnable { def run() {} }

  def apply(action: => Unit) = executor.submit(new Runnable {
    def run() { action }
  })

  def sync() {
    val f = executor.submit(noop)
    f.get()
  }
}
