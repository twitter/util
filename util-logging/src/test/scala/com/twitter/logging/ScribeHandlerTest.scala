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

package com.twitter
package logging

import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.util.{RandomSocket, Time}
import java.util.concurrent.TimeUnit
import java.util.{logging => javalog}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, WordSpec}

@RunWith(classOf[JUnitRunner])
class ScribeHandlerTest extends WordSpec with BeforeAndAfter {
  val record1 = new javalog.LogRecord(Level.INFO, "This is a message.")
  record1.setMillis(1206769996722L)
  record1.setLoggerName("hello")
  val record2 = new javalog.LogRecord(Level.INFO, "This is another message.")
  record2.setLoggerName("hello")
  record2.setMillis(1206769996722L)

  // Ensure we get a free port. This assumes this port won't be reallocated
  // while test is running even though SO_REUSEADDR is true
  val portWithoutListener = RandomSocket.nextPort()

  "ScribeHandler" should {

    "build a scribe RPC call" in {
      Time.withCurrentTimeFrozen { _ =>
        val scribe = ScribeHandler(
          // Hack to make sure that the buffer doesn't get flushed.
          port = portWithoutListener,
          bufferTime = 100.milliseconds,
          maxMessagesToBuffer = 10000,
          formatter = new Formatter(timezone = Some("UTC")),
          category = "test",
          level = Some(Level.DEBUG)
        ).apply()

        scribe.updateLastTransmission()
        scribe.publish(record1)
        scribe.publish(record2)

        assert(scribe.queue.size === 2)
        assert(scribe.makeBuffer(2).array.hexlify === (
          "000000b080010001000000034c6f67000000000f0001" +
          "0c000000020b000100000004746573740b0002000000" +
          "36494e46205b32303038303332392d30353a35333a31" +
          "362e3732325d2068656c6c6f3a205468697320697320" +
          "61206d6573736167652e0a000b000100000004746573" +
          "740b00020000003c494e46205b32303038303332392d" +
          "30353a35333a31362e3732325d2068656c6c6f3a2054" +
          "68697320697320616e6f74686572206d657373616765" +
          "2e0a0000"))
      }
    }

    "be able to log binary data" in {
      Time.withCurrentTimeFrozen { _ =>
        val scribe = ScribeHandler(
          port = portWithoutListener,
          bufferTime = 100.milliseconds,
          maxMessagesToBuffer = 10000,
          formatter = new Formatter(timezone = Some("UTC")),
          category = "test",
          level = Some(Level.DEBUG)
        ).apply()

        val bytes = Array[Byte](1,2,3,4,5)

        scribe.updateLastTransmission()
        scribe.publish(bytes)

        assert(scribe.queue.peek() === bytes)
      }
    }

    "throw away log messages if scribe is too busy" in {
      val statsReceiver = new InMemoryStatsReceiver

      val scribe = ScribeHandler(
        port = portWithoutListener,
        bufferTime = 5.seconds,
        maxMessagesToBuffer = 1,
        formatter = BareFormatter,
        category = "test",
        statsReceiver = statsReceiver
      ).apply()

      scribe.updateLastTransmission()
      scribe.publish(record1)
      scribe.publish(record2)

      assert(statsReceiver.counter("dropped_records")() === 1l)
      assert(statsReceiver.counter("sent_records")() === 0l)
    }

    "have backoff on connection errors" in {
      val statsReceiver = new InMemoryStatsReceiver

      val scribe = ScribeHandler(
        port = portWithoutListener,
        bufferTime = 5.seconds,
        maxMessagesToBuffer = 1,
        formatter = BareFormatter,
        category = "test",
        statsReceiver = statsReceiver
      ).apply()

      scribe.publish(record1)
      scribe.publish(record2)
      scribe.publish(record1)
      scribe.publish(record2)

      scribe.flusher.shutdown()
      scribe.flusher.awaitTermination(5, TimeUnit.SECONDS)

      assert(statsReceiver.counter("connection_failed")() === 1l)
      assert(statsReceiver.counter("connection_skipped")() === 3l)
    }

    // TODO rewrite deterministically when we rewrite ScribeHandler
    "avoid unbounded flush queue growth" in {
      val statsReceiver = new InMemoryStatsReceiver

      val scribe = ScribeHandler(
        port = portWithoutListener,
        bufferTime = 5.seconds,
        maxMessagesToBuffer = 1,
        formatter = BareFormatter,
        category = "test",
        statsReceiver = statsReceiver
      ).apply()

      // crude form to allow all 100 submission and get predictable dropping of tasks
      scribe.synchronized {
        for (i <- 0 until 100) {
          scribe.publish(record1)
        }
      }

      scribe.flusher.shutdown()
      scribe.flusher.awaitTermination(15, TimeUnit.SECONDS)

      assert(statsReceiver.counter("published")() === 100l)
      assert(statsReceiver.counter("connection_failed")() === 1l)
      // 4 if the first job didn't start executing until after synchronized block above ended
      assert(statsReceiver.counter("connection_skipped")() === 4l || statsReceiver.counter("connection_skipped")() === 5l)
    }
  }
}
