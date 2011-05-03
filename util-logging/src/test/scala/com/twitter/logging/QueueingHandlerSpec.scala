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
import org.specs.Specification
import org.specs.util.TimeConversions._


class QueueingHandlerSpec extends Specification {
  class NullHandler extends Handler(BareFormatter, None) {
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
      stringHandler.get must_== "oh noes!\n"
    }

    "publish, drop on overflow" in {
      val logger = freshLogger()
      val blockingHandler = new NullHandler {
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
      (() => droppedCount)() must eventually(be_==(2))
    }

    "flush" in {
      val logger = freshLogger()
      var wasFlushed = false
      val handler = new NullHandler {
        override def flush() { wasFlushed = true }
      }
      var queueHandler = new QueueingHandler(handler, 1)
      logger.addHandler(queueHandler)

      // smoke test: thread might write it, flush might write it
      logger.warning("oh noes!")
      queueHandler.flush()
      wasFlushed must beTrue
    }

    "close" in {
      val logger = freshLogger()
      var wasClosed = false
      val handler = new NullHandler {
        override def close() { wasClosed = true }
      }
      var queueHandler = new QueueingHandler(handler)
      logger.addHandler(queueHandler)

      logger.warning("oh noes!")
      Thread.sleep(100) // let thread log
      queueHandler.close()
      wasClosed must beTrue
    }
  }
}
