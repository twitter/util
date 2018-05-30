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

import java.util.concurrent.atomic.AtomicReference
import java.util.{logging => javalog}

import scala.annotation.tailrec
import scala.collection.mutable

import com.twitter.conversions.time._
import com.twitter.util.{Duration, Time}

object ThrottledHandler {

  /**
   * Generates a HandlerFactory that returns a ThrottledHandler
   * NOTE: ThrottledHandler emits plain-text messages regarding any throttling it does.
   * This means that using it to wrap any logger which you expect to produce easily parseable,
   * well-structured logs (as opposed to just plain text logs) will break your format.
   * Specifically, wrapping ScribeHandler with ThrottledHandler is usually a bug.
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
  ) = () => new ThrottledHandler(handler(), duration, maxToDisplay)
}

/**
 * NOTE: ThrottledHandler emits plain-text messages regarding any throttling it does.
 * This means that using it to wrap any logger which you expect to produce easily parseable,
 * well-structured logs (as opposed to just plain text logs) will break your format.
 * Specifically, DO NOT wrap Thrift Scribing loggers with ThrottledHandler.
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
  handler: Handler,
  val duration: Duration,
  val maxToDisplay: Int
) extends ProxyHandler(handler) {

  private class Throttle(startTime: Time, name: String, level: javalog.Level) {
    private[this] var expired = false
    private[this] var count = 0

    override def toString = "Throttle: startTime=" + startTime + " count=" + count

    final def add(record: javalog.LogRecord, now: Time): Boolean = {
      val (shouldPublish, added) = synchronized {
        if (!expired) {
          count += 1
          (count <= maxToDisplay, true)
        } else {
          (false, false)
        }
      }

      if (shouldPublish) doPublish(record)
      added
    }

    final def removeIfExpired(now: Time): Boolean = {
      val didExpire = synchronized {
        expired = (now - startTime >= duration)
        expired
      }

      if (didExpire && count > maxToDisplay) publishSwallowed()

      didExpire
    }

    private[this] def publishSwallowed(): Unit = {
      val throttledRecord = new javalog.LogRecord(
        level,
        "(swallowed %d repeating messages)".format(count - maxToDisplay)
      )
      throttledRecord.setLoggerName(name)
      doPublish(throttledRecord)
    }
  }

  private val lastFlushCheck = new AtomicReference(Time.epoch)
  private val throttleMap = new mutable.HashMap[String, Throttle]

  @deprecated("Use flushThrottled() instead", "5.3.13")
  def reset(): Unit = {
    flushThrottled()
  }

  /**
   * Force printing any "swallowed" messages.
   */
  def flushThrottled(): Unit = {
    synchronized {
      val now = Time.now
      throttleMap retain {
        case (_, throttle) => !throttle.removeIfExpired(now)
      }
    }
  }

  /**
   * Log a message, with sprintf formatting, at the desired level, and
   * attach an exception and stack trace.
   */
  override def publish(record: javalog.LogRecord): Unit = {
    val now = Time.now
    val last = lastFlushCheck.get

    if (now - last > 1.second && lastFlushCheck.compareAndSet(last, now)) {
      flushThrottled()
    }

    val key = record match {
      case r: LazyLogRecordUnformatted => r.preformatted
      case _ => record.getMessage
    }
    @tailrec def tryPublish(): Unit = {
      val throttle = synchronized {
        throttleMap.getOrElseUpdate(
          key,
          new Throttle(now, record.getLoggerName(), record.getLevel())
        )
      }

      // catch the case where throttle is removed before we had a chance to add
      if (!throttle.add(record, now)) tryPublish()
    }

    tryPublish()
  }

  private def doPublish(record: javalog.LogRecord) = {
    super.publish(record)
  }
}
