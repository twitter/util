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

import java.util.{logging => javalog}
import org.scalatest.{WordSpec, Matchers}


class QueueingHandlerSpec extends WordSpec with Matchers {
  class MockHandler extends Handler(BareFormatter, None) {
    def publish(record: javalog.LogRecord) {}
    def close() {}
    def flush() {}
  }

  def freshLogger():Logger = {
    val logger = Logger.get("test")
    logger.clearHandlers()
    logger.setLevel(Logger.INFO)
    logger.setUseParentHandlers(false)
    logger
  }

  "QueueingHandler" should {
    "publish" in {
      val logger = freshLogger()
      val stringHandler = new StringHandler(BareFormatter, Some(Logger.INFO))
      var queueHandler = new QueueingHandler(stringHandler)
      logger.addHandler(queueHandler)

      logger.warning("oh noes!")
      Thread.sleep(100) // let thread log
      stringHandler.get should be ("oh noes!\n")
    }

    "publish, drop on overflow" in {
      val logger = freshLogger()
      val blockingHandler = new MockHandler {
        override def publish(record: javalog.LogRecord) {
          Thread.sleep(100000)
        }
      }
      var droppedCount = 0
      var queueHandler = new QueueingHandler(blockingHandler, 1) {
        override protected def onOverflow(record: javalog.LogRecord) {
          droppedCount += 1
        }
      }
      logger.addHandler(queueHandler)

      logger.warning("1")
      logger.warning("2")
      logger.warning("3")
      Thread.sleep(100) // let thread log and block
      droppedCount should be >=(1) // either 1 or 2, depending on race
    }

    "flush" in {
      val logger = freshLogger()
      var wasFlushed = false
      val handler = new MockHandler {
        override def flush() { wasFlushed = true }
      }
      var queueHandler = new QueueingHandler(handler, 1)
      logger.addHandler(queueHandler)

      // smoke test: thread might write it, flush might write it
      logger.warning("oh noes!")
      queueHandler.flush()
      wasFlushed shouldBe true
    }

    "close" in {
      val logger = freshLogger()
      var wasClosed = false
      val handler = new MockHandler {
        override def close() { wasClosed = true }
      }
      var queueHandler = new QueueingHandler(handler)
      logger.addHandler(queueHandler)

      logger.warning("oh noes!")
      Thread.sleep(100) // let thread log
      queueHandler.close()
      wasClosed shouldBe true
    }

    "handle exceptions in the underlying handler" in {
      val logger = freshLogger()
      var mustError = true
      var didLog = false
      val handler = new MockHandler {
        override def publish(record: javalog.LogRecord) {
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
      Thread.sleep(100) // let thread log
      didLog shouldBe true
    }
  }
}
