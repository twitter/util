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
import com.twitter.conversions.string._

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

/**
 * Mostly useful for unit tests: logging goes directly into a string buffer.
 */
class StringHandler(formatter: Formatter, level: Option[Level]) extends Handler(formatter, level) {
  def this() = this(BasicFormatter, None)

  private var buffer = new StringBuilder()

  def publish(record: javalog.LogRecord) = {
    buffer append getFormatter().format(record)
  }

  def close() = { }

  def flush() = { }

  def get = buffer.toString

  def clear() = {
    buffer.clear
  }
}

/**
 * Log things to the console.
 */
class ConsoleHandler(formatter: Formatter, level: Option[Level]) extends Handler(formatter, level) {
  def this() = this(BasicFormatter, None)

  def publish(record: javalog.LogRecord) = {
    System.err.print(getFormatter().format(record))
  }

  def close() = { }

  def flush() = Console.flush
}
