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

  override def toString: String = {
    "<%s level=%s formatter=%s>".format(getClass.getName, getLevel, formatter.toString)
  }
}

/**
 * A log handler class which delegates to another handler. This allows to implement filtering
 * log handlers.
 */
abstract class ProxyHandler(val handler: Handler)
    extends Handler(handler.formatter, handler.level) {
  override def close(): Unit = handler.close()

  override def flush(): Unit = handler.flush()

  override def getEncoding(): String = handler.getEncoding()

  override def getErrorManager(): javalog.ErrorManager = handler.getErrorManager()

  override def getFilter(): javalog.Filter = handler.getFilter()

  override def getFormatter(): javalog.Formatter = handler.getFormatter()

  override def getLevel(): javalog.Level = handler.getLevel()

  override def isLoggable(record: javalog.LogRecord): Boolean = handler.isLoggable(record)

  override def publish(record: javalog.LogRecord): Unit = handler.publish(record)

  override def setEncoding(encoding: String): Unit = handler.setEncoding(encoding)

  override def setErrorManager(errorManager: javalog.ErrorManager): Unit =
    handler.setErrorManager(errorManager)

  override def setFilter(filter: javalog.Filter): Unit = handler.setFilter(filter)

  override def setFormatter(formatter: javalog.Formatter): Unit = handler.setFormatter(formatter)

  override def setLevel(level: javalog.Level): Unit = handler.setLevel(level)
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
  ): () => StringHandler =
    () => new StringHandler(formatter, level)

  /**
   * for java compatibility
   */
  def apply(): () => StringHandler = () => new StringHandler()
}

/**
 * Mostly useful for unit tests: logging goes directly into a string buffer.
 */
class StringHandler(formatter: Formatter = new Formatter(), level: Option[Level] = None)
    extends Handler(formatter, level) {

  // thread-safe logging
  private val buffer = new StringBuffer()

  def publish(record: javalog.LogRecord): Unit = {
    buffer append getFormatter().format(record)
  }

  def close(): Unit = {}

  def flush(): Unit = {}

  def get: String = {
    buffer.toString
  }

  def clear(): Unit = {
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
  ): () => ConsoleHandler =
    () => new ConsoleHandler(formatter, level)

  /**
   * for java compatibility
   */
  def apply(): () => ConsoleHandler = () => new ConsoleHandler()
}

/**
 * Log things to the console.
 */
class ConsoleHandler(formatter: Formatter = new Formatter(), level: Option[Level] = None)
    extends Handler(formatter, level) {

  def publish(record: javalog.LogRecord): Unit = {
    System.err.print(getFormatter().format(record))
  }

  def close(): Unit = {}

  def flush(): Unit = Console.flush
}
