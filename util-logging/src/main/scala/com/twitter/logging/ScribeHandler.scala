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

import java.io.IOException
import java.net._
import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{Executors, LinkedBlockingQueue}
import java.util.{Arrays, logging => javalog}

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.conversions.time._
import com.twitter.util.{Duration, Time}

private class Retry extends Exception("retry")

object ScribeHandler {
  val OK = 0
  val TRY_LATER = 1

  val DefaultHostname = "localhost"
  val DefaultPort = 1463
  val DefaultCategory = "scala"
  val DefaultBufferTime = 100.milliseconds
  val DefaultConnectBackoff = 15.seconds
  val DefaultMaxMessagesPerTransaction = 1000
  val DefaultMaxMessagesToBuffer = 10000
  val DefaultStatsReportPeriod = 5.minutes
  val log = Logger.get(getClass)

  /**
   * Generates a HandlerFactory that returns a ScribeHandler
   *
   * @param bufferTime
   * send a scribe message no more frequently than this:
   *
   * @param connectBackoff
   * don't connect more frequently than this (when the scribe server is down):
   */
  def apply(
    hostname: String = DefaultHostname,
    port: Int = DefaultPort,
    category: String = DefaultCategory,
    bufferTime: Duration = DefaultBufferTime,
    connectBackoff: Duration = DefaultConnectBackoff,
    maxMessagesPerTransaction: Int = DefaultMaxMessagesPerTransaction,
    maxMessagesToBuffer: Int = DefaultMaxMessagesToBuffer,
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) =
    () => new ScribeHandler(
      hostname,
      port,
      category,
      bufferTime,
      connectBackoff,
      maxMessagesPerTransaction,
      maxMessagesToBuffer,
      formatter,
      level)
}

class ScribeHandler(
    hostname: String,
    port: Int,
    category: String,
    bufferTime: Duration,
    connectBackoff: Duration,
    maxMessagesPerTransaction: Int,
    maxMessagesToBuffer: Int,
    formatter: Formatter,
    level: Option[Level])
  extends Handler(formatter, level) {
  import ScribeHandler._

  // it may be necessary to log errors here if scribe is down:
  private val loggerName = getClass.toString

  private var lastConnectAttempt = Time.epoch

  private var _lastLogStats = Time.epoch
  // visible for testing
  private[logging] def updateLastLogStats(): Unit = synchronized {
    _lastLogStats = Time.now
  }

  private var _lastTransmission = Time.epoch
  // visible for testing
  private[logging] def updateLastTransmission(): Unit = synchronized {
    _lastTransmission = Time.now
  }

  private var socket: Option[Socket] = None
  private var archaicServer = false
  private[logging] val flusher =
    Executors.newSingleThreadExecutor(
      new NamedPoolThreadFactory("ScribeFlusher-" + category, true)
    )

  private[logging] val queue = new LinkedBlockingQueue[Array[Byte]](maxMessagesToBuffer)
  private[logging] val sentRecords = new AtomicLong()
  private[logging] val droppedRecords = new AtomicLong()
  private[logging] val reconnectionFailure = new AtomicLong()
  private[logging] val reconnectionSkipped = new AtomicLong()

  override def flush() {

    def connect() {
      if (!socket.isDefined) {
        if (Time.now.since(lastConnectAttempt) > connectBackoff) {
          try {
            lastConnectAttempt = Time.now
            socket = Some(new Socket(hostname, port))
          } catch {
            case e: Exception =>
              log.error("Unable to open socket to scribe server at %s:%d: %s", hostname, port, e)
              reconnectionFailure.incrementAndGet()
          }
        } else {
          reconnectionSkipped.incrementAndGet()
        }
      }
    }

    // report stats
    def logStats() {
      val period = Time.now.since(_lastLogStats)
      if (period > DefaultStatsReportPeriod) {
        val sent = sentRecords.getAndSet(0)
        val dropped = droppedRecords.getAndSet(0)
        val failed = reconnectionFailure.getAndSet(0)
        val skipped = reconnectionSkipped.getAndSet(0)
        log.info("sent records: %d, per second: %d, dropped records: %d, reconnection failures: %d, reconnection skipped: %d",
                 sent, sent/period.inSeconds, dropped, failed, skipped)
        updateLastLogStats()
      }
    }

    def sendBatch() {
      synchronized {
        connect()
        socket.foreach { s =>
          val outStream = s.getOutputStream()
          val inStream = s.getInputStream()
          var remaining = queue.size

          // try to send the log records in batch
          // need to check if socket is closed due to exception
          while (remaining > 0 && socket.isDefined) {
            val count = maxMessagesPerTransaction min remaining
            val buffer = makeBuffer(count)
            var offset = 0

            try {
              outStream.write(buffer.array)
              val expectedReply = if (archaicServer) OLD_SCRIBE_REPLY else SCRIBE_REPLY

              // read response:
              val response = new Array[Byte](expectedReply.length)
              while (offset < response.length) {
                val n = inStream.read(response, offset, response.length - offset)
                if (n < 0) {
                  throw new IOException("End of stream")
                }
                offset += n
                if (!archaicServer && (offset > 0) && (response(0) == 0)) {
                  archaicServer = true
                  lastConnectAttempt = Time.epoch
                  log.warning("Scribe server is archaic; changing to old protocol for future requests.")
                  throw new Retry
                }
              }
              if (!Arrays.equals(response, expectedReply)) {
                throw new IOException("Error response from scribe server: " + response.toList.toString)
              }
              sentRecords.getAndAdd(count)
              remaining -= count
            } catch {
              case _: Retry => closeSocket()
              case e: Exception =>
                log.error(e, "Failed to send %s %d log entries to scribe server at %s:%d",
                          category, count, hostname, port)
                closeSocket()
            }
          }
          updateLastTransmission()
        }
        logStats()
      }
    }

    flusher.execute( new Runnable {
      def run() { sendBatch() }
    })
  }

  // should be private, make it visible to tests
  private[logging] def makeBuffer(count: Int): ByteBuffer = {
    val texts = for (i <- 0 until count) yield queue.poll()

    val recordHeader = ByteBuffer.wrap(new Array[Byte](10 + category.length))
    recordHeader.order(ByteOrder.BIG_ENDIAN)
    recordHeader.put(11: Byte)
    recordHeader.putShort(1)
    recordHeader.putInt(category.length)
    recordHeader.put(category.getBytes("ISO-8859-1"))
    recordHeader.put(11: Byte)
    recordHeader.putShort(2)

    val prefix = if (archaicServer) OLD_SCRIBE_PREFIX else SCRIBE_PREFIX
    val messageSize = (count * (recordHeader.capacity + 5)) + texts.foldLeft(0) { _ + _.length } + prefix.length + 5
    val buffer = ByteBuffer.wrap(new Array[Byte](messageSize + 4))
    buffer.order(ByteOrder.BIG_ENDIAN)
    // "framing":
    buffer.putInt(messageSize)
    buffer.put(prefix)
    buffer.putInt(count)
    for (text <- texts) {
      buffer.put(recordHeader.array)
      buffer.putInt(text.length)
      buffer.put(text)
      buffer.put(0: Byte)
    }
    buffer.put(0: Byte)
    buffer
  }

  private def closeSocket() {
    synchronized {
      socket.foreach { s =>
        try {
          s.close()
        } catch {
          case _: Throwable =>
        }
      }
      socket = None
    }

  }

  override def close() {
    closeSocket()
    flusher.shutdown()
  }

  override def publish(record: javalog.LogRecord) {
    if (record.getLoggerName == loggerName) return
    publish(getFormatter.format(record).getBytes("UTF-8"))
  }

  def publish(record: Array[Byte]) {
    if (!queue.offer(record)) droppedRecords.incrementAndGet()
    if (Time.now.since(_lastTransmission) >= bufferTime) flush()
  }

  override def toString = {
    ("<%s level=%s hostname=%s port=%d scribe_buffer=%s " +
     "scribe_backoff=%s scribe_max_packet_size=%d formatter=%s>").format(getClass.getName, getLevel,
      hostname, port, bufferTime, connectBackoff, maxMessagesPerTransaction, formatter.toString)
  }

  private[this] val SCRIBE_PREFIX: Array[Byte] = Array(
    // version 1, call, "Log", reqid=0
    0x80.toByte, 1, 0, 1, 0, 0, 0, 3, 'L'.toByte, 'o'.toByte, 'g'.toByte, 0, 0, 0, 0,
    // list of structs
    15, 0, 1, 12
  )
  private[this] val OLD_SCRIBE_PREFIX: Array[Byte] = Array(
    // (no version), "Log", reply, reqid=0
    0, 0, 0, 3, 'L'.toByte, 'o'.toByte, 'g'.toByte, 1, 0, 0, 0, 0,
    // list of structs
    15, 0, 1, 12
  )

  private[this] val SCRIBE_REPLY: Array[Byte] = Array(
    // version 1, reply, "Log", reqid=0
    0x80.toByte, 1, 0, 2, 0, 0, 0, 3, 'L'.toByte, 'o'.toByte, 'g'.toByte, 0, 0, 0, 0,
    // int, fid 0, 0=ok
    8, 0, 0, 0, 0, 0, 0, 0
  )
  private[this] val OLD_SCRIBE_REPLY: Array[Byte] = Array(
    0, 0, 0, 20,
    // (no version), "Log", reply, reqid=0
    0, 0, 0, 3, 'L'.toByte, 'o'.toByte, 'g'.toByte, 2, 0, 0, 0, 0,
    // int, fid 0, 0=ok
    8, 0, 0, 0, 0, 0, 0, 0
  )
}
