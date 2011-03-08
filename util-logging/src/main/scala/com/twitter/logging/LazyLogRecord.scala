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

class LazyLogRecord(level: javalog.Level, messageGenerator: => AnyRef) extends javalog.LogRecord(level, "") {
  // for each logged line, generate this string only once, regardless of how many handlers there are:
  var cached: Option[AnyRef] = None

  def generate = {
    cached match {
      case Some(value) =>
        value
      case None =>
        cached = Some(messageGenerator)
        cached.get
    }
  }
}
