/*
 * Copyright 2011 Twitter, Inc.
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

import org.specs.Specification

/**
 * Specify logging during unit tests via system property, defaulting to FATAL only.
 */
trait TestLogging { self: Specification =>
  val logLevel = Logger.levelNames(Option[String](System.getenv("log")).getOrElse("FATAL").toUpperCase)

  new SpecContext {
    beforeSpec {
      Logger.get("").setLevel(logLevel)
    }
  }

  private var traceHandler = new StringHandler(BareFormatter, None)

  /**
   * Set up logging to record messages at the given level, and not send them to the console.
   *
   * This is meant to be used in a `doBefore` block.
   */
  def traceLogger(level: Level) {
    traceLogger("", level)
  }

  /**
   * Set up logging to record messages sent to the given logger at the given level, and not send
   * them to the console.
   *
   * This is meant to be used in a `doBefore` block.
   */
  def traceLogger(name: String, level: Level) {
    traceHandler.clear()
    val logger = Logger.get(name)
    logger.setLevel(level)
    logger.clearHandlers()
    logger.addHandler(traceHandler)
  }

  /**
   * Verify that the logger set up with `traceLogger` has received a log line with the given
   * substring somewhere inside it.
   */
  def mustLog(substring: String) = {
    val lines = traceHandler.get.split("\n")
    lines.filter { _ contains substring }.size must be_>(0)
  }
}
