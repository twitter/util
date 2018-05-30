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

import java.util.{logging => javalog}

/**
 * A base log handler for scala. This extends the java built-in handler and connects it with a
 * formatter automatically.
 */
abstract class Handler(val formatter: Formatter, val level: Option[Level]) extends javalog.Handler {
  setFormatter(formatter)

  level.foreach { x =>
    setLevel(x)
  }

  override def toString = {
    "<%s level=%s formatter=%s>".format(getClass.getName, getLevel, formatter.toString)
  }
}

/**
 * A log handler class which delegates to another handler. This allows to implement filtering
 * log handlers.
 */
abstract class ProxyHandler(val handler: Handler)
    extends Handler(handler.formatter, handler.level) {
  override def close() = handler.close()

  override def flush() = handler.flush()

  override def getEncoding() = handler.getEncoding()

  override def getErrorManager() = handler.getErrorManager()

  override def getFilter() = handler.getFilter()

  override def getFormatter() = handler.getFormatter()

  override def getLevel() = handler.getLevel()

  override def isLoggable(record: javalog.LogRecord) = handler.isLoggable(record)

  override def publish(record: javalog.LogRecord) = handler.publish(record)

  override def setEncoding(encoding: String) = handler.setEncoding(encoding)

  override def setErrorManager(errorManager: javalog.ErrorManager) =
    handler.setErrorManager(errorManager)

  override def setFilter(filter: javalog.Filter) = handler.setFilter(filter)

  override def setFormatter(formatter: javalog.Formatter) = handler.setFormatter(formatter)

  override def setLevel(level: javalog.Level) = handler.setLevel(level)
}

object NullHandler extends Handler(BareFormatter, None) {
  def publish(record: javalog.LogRecord): Unit = {}
  def close(): Unit = {}
  def flush(): Unit = {}

  // for java compatibility
  def get(): this.type = this
}

object StringHandler {

  /**
   * Generates a HandlerFactory that returns a StringHandler
   */
  def apply(
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) = () => new StringHandler(formatter, level)

  /**
   * for java compatibility
   */
  def apply() = () => new StringHandler()
}

/**
 * Mostly useful for unit tests: logging goes directly into a string buffer.
 */
class StringHandler(formatter: Formatter = new Formatter(), level: Option[Level] = None)
    extends Handler(formatter, level) {

  // thread-safe logging
  private val buffer = new StringBuffer()

  def publish(record: javalog.LogRecord) = {
    buffer append getFormatter().format(record)
  }

  def close() = {}

  def flush() = {}

  def get = {
    buffer.toString
  }

  def clear() = {
    buffer.setLength(0)
    buffer.trimToSize()
  }
}

object ConsoleHandler {

  /**
   * Generates a HandlerFactory that returns a ConsoleHandler
   */
  def apply(
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) = () => new ConsoleHandler(formatter, level)

  /**
   * for java compatibility
   */
  def apply() = () => new ConsoleHandler()
}

/**
 * Log things to the console.
 */
class ConsoleHandler(formatter: Formatter = new Formatter(), level: Option[Level] = None)
    extends Handler(formatter, level) {

  def publish(record: javalog.LogRecord) = {
    System.err.print(getFormatter().format(record))
  }

  def close() = {}

  def flush() = Console.flush
}
