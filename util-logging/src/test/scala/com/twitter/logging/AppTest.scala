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

import com.twitter.app.App
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterEach, FunSuite}

@RunWith(classOf[JUnitRunner])
class AppTest extends FunSuite with BeforeAndAfterEach {

  object TestLoggingApp extends App with Logging {
    override def handlers = ScribeHandler() :: super.handlers
  }

  object TestLoggingWithConfigureApp extends App with Logging {
    override def handlers = ScribeHandler() :: super.handlers
    override def configureLoggerFactories(): Unit = {}
  }

  test("TestLoggingApp should have one factory with two log handlers") {
    TestLoggingApp.main(Array.empty)
    assert(TestLoggingApp.loggerFactories.size == 1)
    assert(TestLoggingApp.loggerFactories.head.handlers.size == 2)
    // Logger is configured with two handlers/loggers
    assert(Logger.iterator.size == 2)
  }

  test("TestLoggingWithConfigureApp should set up a Logger with no Loggers") {
    TestLoggingWithConfigureApp.main(Array.empty)
    assert(TestLoggingApp.loggerFactories.size == 1)
    assert(TestLoggingWithConfigureApp.loggerFactories.head.handlers.size == 2)
    // Logger is not configured with any handler/logger
    assert(Logger.iterator.isEmpty)
  }

  override protected def afterEach(): Unit = {
    Logger.reset()
  }
}
