/*
 * Copyright 2011 Twitter, Inc.
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

import java.util.{logging => jlogging}

import org.scalatest.{BeforeAndAfter, WordSpec}

/**
 * Specify logging during unit tests via system property, defaulting to FATAL only.
 */
trait TestLogging extends BeforeAndAfter { self: WordSpec =>
  val logLevel =
    Logger.levelNames(Option[String](System.getenv("log")).getOrElse("FATAL").toUpperCase)

  private val logger = Logger.get("")
  private var oldLevel: jlogging.Level = _

  before {
    oldLevel = logger.getLevel()
    logger.setLevel(logLevel)
    logger.addHandler(new ConsoleHandler(new Formatter(), None))
  }

  after {
    logger.clearHandlers()
    logger.setLevel(oldLevel)
  }

  private var traceHandler = new StringHandler(BareFormatter, None)

  /**
   * Set up logging to record messages at the given level, and not send them to the console.
   *
   * This is meant to be used in a `before` block.
   */
  def traceLogger(level: Level): Unit = {
    traceLogger("", level)
  }

  /**
   * Set up logging to record messages sent to the given logger at the given level, and not send
   * them to the console.
   *
   * This is meant to be used in a `before` block.
   */
  def traceLogger(name: String, level: Level): Unit = {
    traceHandler.clear()
    val logger = Logger.get(name)
    logger.setLevel(level)
    logger.clearHandlers()
    logger.addHandler(traceHandler)
  }

  def logLines(): Seq[String] = traceHandler.get.split("\n").toSeq

  /**
   * Verify that the logger set up with `traceLogger` has received a log line with the given
   * substring somewhere inside it.
   */
  def mustLog(substring: String) = {
    assert(logLines().filter { _ contains substring }.size > 0)
  }
}
