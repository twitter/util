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
import scala.collection.mutable

/**
 * A base log handler for scala. This extends the java built-in handler and connects it with a
 * formatter automatically.
 */
abstract class Handler(val formatter: Formatter, val level: Option[Level]) extends javalog.Handler {
  setFormatter(formatter)

  level.foreach { x => setLevel(x) }

  override def toString = {
    "<%s level=%s formatter=%s>".format(getClass.getName, getLevel, formatter.toString)
  }
}

object NullHandler extends Handler(BareFormatter, None) {
  def publish(record: javalog.LogRecord) {}
  def close() {}
  def flush() {}
}

object StringHandler {
  /**
   * Generates a HandlerFactory that returns a StringHandler
   */
  def apply(
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) = () => new StringHandler(formatter, level)
}

/**
 * Mostly useful for unit tests: logging goes directly into a string buffer.
 */
class StringHandler(
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None)
  extends Handler(formatter, level) {

  // thread-safe logging
  private val buffer = new StringBuffer()

  def publish(record: javalog.LogRecord) = {
    buffer append getFormatter().format(record)
  }

  def close() = { }

  def flush() = { }

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
}

/**
 * Log things to the console.
 */
class ConsoleHandler(
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None)
  extends Handler(formatter, level) {

  def publish(record: javalog.LogRecord) = {
    System.err.print(getFormatter().format(record))
  }

  def close() = { }

  def flush() = Console.flush
}
