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

import com.twitter.util.Local
import java.util.{logging => javalog}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite

class QueueingHandlerTest extends AnyFunSuite with Eventually with IntegrationPatience {

  class MockHandler extends Handler(BareFormatter, None) {
    def publish(record: javalog.LogRecord) = ()
    def close() = ()
    def flush() = ()
  }

  def freshLogger(): Logger = {
    val logger = Logger.get("test")
    logger.clearHandlers()
    logger.setLevel(Logger.INFO)
    logger.setUseParentHandlers(false)
    logger
  }

  test("QueueingHandler should publish") {
    val logger = freshLogger()
    val stringHandler = new StringHandler(BareFormatter, Some(Logger.INFO))
    val queueHandler = new QueueingHandler(stringHandler)
    logger.addHandler(queueHandler)

    logger.warning("oh noes!")
    eventually {
      // let thread log
      assert(stringHandler.get == "oh noes!\n")
    }
  }

  test("QueueingHandler should publish with local context") {
    val logger = freshLogger()
    val local = new Local[String]
    val formatter = new Formatter {
      override def format(record: javalog.LogRecord) =
        local().getOrElse("MISSING!") + ":" + formatText(record) + lineTerminator
    }
    val stringHandler = new StringHandler(formatter, Some(Logger.INFO))
    logger.addHandler(new QueueingHandler(stringHandler))

    local() = "foo"
    logger.warning("oh noes!")

    eventually {
      assert(stringHandler.get == "foo:oh noes!\n")
    }
  }

  test("QueueingHandler should publish, drop on overflow") {
    val logger = freshLogger()
    val blockingHandler = new MockHandler {
      override def publish(record: javalog.LogRecord): Unit = {
        Thread.sleep(100000)
      }
    }
    @volatile var droppedCount = 0

    val queueHandler = new QueueingHandler(blockingHandler, 1) {
      override protected def onOverflow(record: javalog.LogRecord): Unit = {
        droppedCount += 1
      }
    }
    logger.addHandler(queueHandler)

    logger.warning("1")
    logger.warning("2")
    logger.warning("3")

    eventually {
      // let thread log and block
      assert(droppedCount >= 1) // either 1 or 2, depending on race
    }
  }

  test("QueueingHandler should flush") {
    val logger = freshLogger()
    var wasFlushed = false
    val handler = new MockHandler {
      override def flush(): Unit = { wasFlushed = true }
    }
    val queueHandler = new QueueingHandler(handler, 1)
    logger.addHandler(queueHandler)

    // smoke test: thread might write it, flush might write it
    logger.warning("oh noes!")
    queueHandler.flush()
    assert(wasFlushed == true)
  }

  test("QueueingHandler should close") {
    val logger = freshLogger()
    var wasClosed = false
    val handler = new MockHandler {
      override def close(): Unit = { wasClosed = true }
    }
    val queueHandler = new QueueingHandler(handler)
    logger.addHandler(queueHandler)

    logger.warning("oh noes!")
    eventually {
      // let thread log
      queueHandler.close()
      assert(wasClosed == true)
    }
  }

  test("QueueingHandler should handle exceptions in the underlying handler") {
    val logger = freshLogger()
    @volatile var mustError = true
    @volatile var didLog = false
    val handler = new MockHandler {
      override def publish(record: javalog.LogRecord): Unit = {
        if (mustError) {
          mustError = false
          throw new Exception("Unable to log for whatever reason.")
        } else {
          didLog = true
        }
      }
    }

    val queueHandler = new QueueingHandler(handler)
    logger.addHandler(queueHandler)

    logger.info("fizz")
    logger.info("buzz")

    eventually {
      // let thread log
      assert(didLog == true)
    }
  }

  test("QueueingHandler should forward formatter to the underlying handler") {
    val handler = new MockHandler {}

    val queueHandler = new QueueingHandler(handler)
    val formatter = new Formatter()

    queueHandler.setFormatter(formatter)

    assert(handler.getFormatter eq formatter)
  }

  test("QueueingHandler should obey flag to force or not force class and method name inference") {
    val formatter = new Formatter() {
      override def format(record: javalog.LogRecord) = {
        val prefix =
          if (record.getSourceClassName != null) record.getSourceClassName + ": "
          else ""

        prefix + formatText(record) + lineTerminator
      }
    }
    for (infer <- Seq(true, false)) {
      val logger = freshLogger()
      val stringHandler = new StringHandler(formatter, Some(Logger.INFO))
      val queueHandler = new QueueingHandler(stringHandler, Int.MaxValue, infer)
      logger.addHandler(queueHandler)
      val helper = new QueueingHandlerTestHelper()
      helper.logSomething(logger)

      val expectedMessagePrefix = if (infer) helper.getClass.getName + ": " else ""
      val expectedMessage = expectedMessagePrefix + helper.message + "\n"

      eventually {
        // let thread log
        assert(stringHandler.get == expectedMessage)
      }
    }
  }
}

class QueueingHandlerTestHelper {
  def message = "A message"
  def logSomething(logger: Logger) = logger.info(message)
}
