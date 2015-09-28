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

import com.twitter.concurrent.{NamedPoolThreadFactory, AsyncQueue}
import com.twitter.util._
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{logging => javalog}

object QueueingHandler {

  private[this] val executor = Executors.newCachedThreadPool(
    new NamedPoolThreadFactory("QueueingHandlerPool", makeDaemons = true))

  private val DefaultFuturePool = new ExecutorServiceFuturePool(executor)

  private object QueueClosedException extends RuntimeException

  /**
   * Generates a HandlerFactory that returns a QueueingHandler
   *
   * @param handler Wrapped handler that publishing is proxied to.
   *
   * @param maxQueueSize Maximum queue size. Records are sent to
   * [[QueueingHandler.onOverflow]] when it is at capacity.
   */
  def apply(
    handler: HandlerFactory,
    maxQueueSize: Int = Int.MaxValue
  ): () => QueueingHandler =
    () => new QueueingHandler(handler(), maxQueueSize)
}

/**
 * Proxy handler that queues log records and publishes them in another thread to
 * a nested handler. Useful for when a handler may block.
 *
 * @param handler Wrapped handler that publishing is proxied to.
 *
 * @param maxQueueSize Maximum queue size. Records are sent to
 * [[onOverflow]] when it is at capacity.
 */
class QueueingHandler(handler: Handler, val maxQueueSize: Int = Int.MaxValue)
  extends ProxyHandler(handler) {

  import QueueingHandler._

  protected val dropLogNode: String = ""
  protected val log: Logger = Logger(dropLogNode)

  private[this] val queue = new AsyncQueue[javalog.LogRecord](maxQueueSize)

  private[this] val closed = new AtomicBoolean(false)

  override def publish(record: javalog.LogRecord): Unit = {
    DefaultFuturePool {
      // We run this in a FuturePool to avoid satisfying pollers
      // (which flush the record) inline.
      if (!queue.offer(record))
        onOverflow(record)
    }
  }

  private[this] def doPublish(record: javalog.LogRecord): Unit =
    super.publish(record)

  private[this] def loop(): Future[Unit] = {
    queue.poll().map(doPublish).respond {
      case Return(_) => loop()
      case Throw(QueueClosedException) => // indicates we should shutdown
      case Throw(e) =>
        // `doPublish` can throw, and we want to keep on publishing...
        e.printStackTrace()
        loop()
    }
  }

  // begin polling for log records
  DefaultFuturePool {
    loop()
  }

  override def close(): Unit = {
    if (closed.compareAndSet(false, true)) {
      queue.fail(QueueClosedException, discard = true)

      // Propagate close
      super.close()
    }
  }

  override def flush(): Unit = {
    // Publish all records in queue
    queue.drain().map { records =>
      records.foreach(doPublish)
    }

    // Propagate flush
    super.flush()
  }

  /**
   * Called when record dropped.  Default is to log to console.
   */
  protected def onOverflow(record: javalog.LogRecord): Unit = {
    Console.err.println(String.format("[%s] log queue overflow - record dropped", Time.now.toString))
  }
}
