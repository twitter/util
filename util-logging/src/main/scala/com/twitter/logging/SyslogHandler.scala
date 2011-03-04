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

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress, SocketAddress}
import java.util.{logging => javalog}
import java.text.SimpleDateFormat
import scala.actors._
import scala.actors.Actor._
import com.twitter.conversions.string._
import config._

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

  val ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  val OLD_SYSLOG_DATE_FORMAT = new SimpleDateFormat("MMM dd HH:mm:ss")
}

class SyslogFormatter(val hostname: String, val serverName: Option[String],
                      val useIsoDateFormat: Boolean, val priority: Int,
                      timezone: Option[String], truncateAt: Int, truncateStackTracesAt: Int)
      extends Formatter(timezone, truncateAt, truncateStackTracesAt, false, "") {
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

class SyslogHandler(val server: String, val port: Int, formatter: Formatter, level: Option[Level])
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
        case e =>
          System.err.println(Formatter.formatStackTrace(e, 30).mkString("\n"))
      }
    }
  }
}

object SyslogFuture {
  private case class Do(action: () => Unit)
  private case object Wait

  def apply(action: => Unit) = writer ! Do(() => action)
  def sync = writer !? Wait

  private lazy val writer = actor {
    while (true) {
      receive {
        case Wait => reply(Wait)
        case Do(action) => action()
      }
    }
  }
}
