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

import com.twitter.util.{TempFolder, Time}
import com.twitter.conversions.time._
import org.specs.SpecificationWithJUnit
import config._

class ThrottledHandlerSpec extends SpecificationWithJUnit with TempFolder {
  private var handler: StringHandler = null

  "ThrottledHandler" should {
    doBefore {
      Logger.clearHandlers
      handler = new StringHandler(BareFormatter, None)
    }

    doAfter {
      Logger.clearHandlers
    }

    "throttle keyed log messages" in {
      val log = Logger()
      val throttledLog = new ThrottledHandler(handler, 1.second, 3)
      log.addHandler(throttledLog)

      log.error("apple: %s", "help!")
      log.error("apple: %s", "help 2!")
      log.error("orange: %s", "orange!")
      log.error("orange: %s", "orange!")
      log.error("apple: %s", "help 3!")
      log.error("apple: %s", "help 4!")
      log.error("apple: %s", "help 5!")
      throttledLog.reset()
      log.error("apple: %s", "done.")

      handler.get.split("\n").toList mustEqual List("apple: help!", "apple: help 2!", "orange: orange!", "orange: orange!", "apple: help 3!", "(swallowed 2 repeating messages)", "apple: done.")
    }

    "log the summary even if nothing more is logged with that name" in {
      Time.withCurrentTimeFrozen { time =>
        val log = Logger()
        val throttledLog = new ThrottledHandler(handler, 1.second, 3)
        log.addHandler(throttledLog)
        log.error("apple: %s", "help!")
        log.error("apple: %s", "help!")
        log.error("apple: %s", "help!")
        log.error("apple: %s", "help!")
        log.error("apple: %s", "help!")

        time.advance(2.seconds)
        log.error("hello.")

        handler.get.split("\n").toList mustEqual List("apple: help!", "apple: help!", "apple: help!", "(swallowed 2 repeating messages)", "hello.")
      }
    }
  }
}
