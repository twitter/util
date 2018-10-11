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

import com.twitter.conversions.time._
import com.twitter.io.TempFolder
import com.twitter.util.Time
import org.scalatest.{BeforeAndAfter, WordSpec}

class ThrottledHandlerTest extends WordSpec with BeforeAndAfter with TempFolder {
  private var handler: StringHandler = _

  "ThrottledHandler" should {
    before {
      Logger.clearHandlers()
      handler = new StringHandler(BareFormatter, None)
    }

    after {
      Logger.clearHandlers()
    }

    "throttle keyed log messages" in {
      val log = Logger()
      val throttledLog = new ThrottledHandler(handler, 1.second, 3)
      Time.withCurrentTimeFrozen { timeCtrl =>
        log.addHandler(throttledLog)

        log.error("apple: %s", "help!")
        log.error("apple: %s", "help 2!")
        log.error("orange: %s", "orange!")
        log.error("orange: %s", "orange!")
        log.error("apple: %s", "help 3!")
        log.error("apple: %s", "help 4!")
        log.error("apple: %s", "help 5!")
        timeCtrl.advance(2.seconds)
        log.error("apple: %s", "done.")

        assert(
          handler.get.split("\n").toList == List(
            "apple: help!",
            "apple: help 2!",
            "orange: orange!",
            "orange: orange!",
            "apple: help 3!",
            "(swallowed 2 repeating messages)",
            "apple: done."
          )
        )
      }
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

        assert(
          handler.get.split("\n").toList == List(
            "apple: help!",
            "apple: help!",
            "apple: help!",
            "(swallowed 2 repeating messages)",
            "hello."
          )
        )
      }
    }
  }
}
