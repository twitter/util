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
import com.twitter.util.{Duration, Time}
import java.util.{logging => javalog}
import scala.collection.mutable

object ThrottledHandler {
  /**
   * Generates a HandlerFactory that returns a ThrottledHandler
   *
   * @param handler
   * Wrapped handler.
   *
   * @param duration
   * Timespan to consider duplicates. After this amount of time, duplicate entries will be logged
   * again.
   *
   * @param maxToDisplay
   * Maximum duplicate log entries to pass before suppressing them.
   */
  def apply(
    handler: HandlerFactory,
    duration: Duration = 0.seconds,
    maxToDisplay: Int = Int.MaxValue
  ): HandlerFactory =
    () => new ThrottledHandler(handler(), duration, maxToDisplay)
}

/**
 * @param handler
 * Wrapped handler.
 *
 * @param duration
 * Timespan to consider duplicates. After this amount of time, duplicate entries will be logged
 * again.
 *
 * @param maxToDisplay
 * Maximum duplicate log entries to pass before suppressing them.
 */
class ThrottledHandler(
    val handler: Handler,
    val duration: Duration,
    val maxToDisplay: Int)
  extends Handler(handler.formatter, handler.level) {

  private class Throttle(now: Time, name: String, level: javalog.Level) {
    var startTime: Time = now
    var count: Int = 0

    override def toString = "Throttle: startTime=" + startTime + " count=" + count

    final def checkFlush(now: Time) {
      if (now - startTime >= duration) {
        if (count > maxToDisplay) {
          val throttledRecord = new javalog.LogRecord(level, "(swallowed %d repeating messages)".format(count - maxToDisplay))
          throttledRecord.setLoggerName(name)
          handler.publish(throttledRecord)
        }
        startTime = now
        count = 0
      }
    }
  }

  private val throttleMap = new mutable.HashMap[String, Throttle]

  def reset() {
    throttleMap.synchronized {
      for ((k, throttle) <- throttleMap) {
        throttle.startTime = Time.epoch
      }
    }
  }

  def close() = handler.close()
  def flush() = handler.flush()

  @volatile var lastFlushCheck = Time.epoch

  /**
   * Log a message, with sprintf formatting, at the desired level, and
   * attach an exception and stack trace.
   */
  def publish(record: javalog.LogRecord) = {
    val now = Time.now
    if (now - lastFlushCheck > 1.second) {
      lastFlushCheck = now
      throttleMap.synchronized {
        throttleMap.values.foreach { t => t.checkFlush(now) }
      }
    }
    val throttle = throttleMap.synchronized {
      throttleMap.getOrElseUpdate(record.getMessage(),
                                  new Throttle(now, record.getLoggerName(), record.getLevel()))
    }
    throttle.synchronized {
      throttle.checkFlush(now)
      throttle.count += 1
      if (throttle.count <= maxToDisplay) {
        handler.publish(record)
      }
    }
  }
}
