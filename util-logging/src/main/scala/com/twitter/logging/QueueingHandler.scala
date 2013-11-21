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

import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue}
import java.util.{logging => javalog}
import com.twitter.util.Time

object QueueingHandler {
  /**
   * Generates a HandlerFactory that returns a QueueingHandler
   *
   * @param handler
   * Wrapped handler.
   *
   * @param maxQueueSize
   * Maximum queue size.  Records are dropped when queue overflows.
   */
  def apply(
    handler: HandlerFactory,
    maxQueueSize: Int = Int.MaxValue
  ) = () => new QueueingHandler(handler(), maxQueueSize)
}

/**
 * Proxy handler that queues log records and publishes them in another thread to
 * a nested handler. Useful for when a handler may block.
 *
 * @param handler
 * Wrapped handler.
 *
 * @param maxQueueSize
 * Maximum queue size.  Records are dropped when queue overflows.
 */
class QueueingHandler(val handler: Handler, val maxQueueSize: Int = Int.MaxValue)
  extends Handler(handler.formatter, handler.level) {

  protected val dropLogNode: String = ""
  protected val log: Logger = Logger(dropLogNode)

  private[this] val queue = new LinkedBlockingQueue[javalog.LogRecord](maxQueueSize)

  private[this] val thread = new Thread {
    override def run() {
      try {
        while (true) {
          val record = queue.take()
          try {
            handler.publish(record)
          } catch {
            case e: InterruptedException =>
              throw e // re-raise
            case e: Throwable =>
              e.printStackTrace()
          }

          if (Thread.interrupted()) throw new InterruptedException
        }
      } catch {
        case _: InterruptedException => // done
      }
      closeLatch.countDown() // signal closed
    }
  }

  private[this] val closeLatch = new CountDownLatch(1)

  thread.setDaemon(true)
  thread.start()

  def publish(record: javalog.LogRecord) = {
    if (queue.offer(record) == false)
      onOverflow(record)
  }

  def close() {
    // Stop thread
    thread.interrupt()
    closeLatch.await()
    // Propagate close
    handler.close()
  }

  def flush() {
    // Publish all records in queue
    var record = queue.poll()
    while (record ne null) {
      handler.publish(record)
      record = queue.poll()
    }
    // Propagate flush
    handler.flush()
  }

  /**
   * Called when record dropped.  Default is to log to console.
   */
  protected def onOverflow(record: javalog.LogRecord) {
    Console.err.println(String.format("[%s] log queue overflow - record dropped", Time.now.toString))
  }
}
