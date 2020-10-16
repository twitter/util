package com.twitter.logging

import com.twitter.concurrent.{NamedPoolThreadFactory, AsyncQueue}
import com.twitter.util._
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{logging => javalog}

object QueueingHandler {

  private[this] val executor = Executors.newCachedThreadPool(
    new NamedPoolThreadFactory("QueueingHandlerPool", makeDaemons = true)
  )

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
  // The type parameter exists to ease java interop
  def apply[H <: Handler](
    handler: () => H,
    maxQueueSize: Int = Int.MaxValue,
    inferClassNames: Boolean = false
  ): () => QueueingHandler =
    () => new QueueingHandler(handler(), maxQueueSize, inferClassNames)

  def apply(handler: HandlerFactory, maxQueueSize: Int): () => QueueingHandler =
    apply(handler, maxQueueSize, false)

  // java interop
  def apply[H <: Handler](handler: () => H): () => QueueingHandler =
    apply(handler, Int.MaxValue)

  private case class RecordWithLocals(record: javalog.LogRecord, locals: Local.Context)

}

/**
 * Proxy handler that queues log records and publishes them in another thread to
 * a nested handler. Useful for when a handler may block.
 *
 * @param handler Wrapped handler that publishing is proxied to.
 *
 * @param maxQueueSize Maximum queue size. Records are sent to
 * [[onOverflow]] when it is at capacity.
 *
 * @param inferClassNames [[com.twitter.logging.LogRecord]] and
 * `java.util.logging.LogRecord` both attempt to infer the class and
 * method name of the caller, but the inference needs the stack trace at
 * the time that the record is logged. QueueingHandler breaks the
 * inference because the log record is rendered out of band, so the stack
 * trace is gone. Setting this option to true will cause the
 * introspection to happen before the log record is queued, which means
 * that the class name and method name will be available when the log
 * record is passed to the underlying handler. This defaults to false
 * because it loses some of the latency improvements of deferring
 * logging by getting the stack trace synchronously.
 */
class QueueingHandler(handler: Handler, val maxQueueSize: Int, inferClassNames: Boolean)
    extends ProxyHandler(handler) {

  import QueueingHandler._

  def this(handler: Handler, maxQueueSize: Int) =
    this(handler, maxQueueSize, false)

  def this(handler: Handler) =
    this(handler, Int.MaxValue)

  protected val dropLogNode: String = ""
  protected val log: Logger = Logger(dropLogNode)

  private[this] val queue = new AsyncQueue[RecordWithLocals](maxQueueSize)

  private[this] val closed = new AtomicBoolean(false)

  override def publish(record: javalog.LogRecord): Unit = {
    // Calling getSourceClassName has the side-effect of inspecting the
    // stack and filling in the class and method names if they have not
    // already been set. See the description of inferClassNames for why
    // we might do this here.
    if (inferClassNames) record.getSourceClassName

    DefaultFuturePool {
      // We run this in a FuturePool to avoid satisfying pollers
      // (which flush the record) inline.
      if (!queue.offer(RecordWithLocals(record, Local.save())))
        onOverflow(record)
    }
  }

  private[this] def doPublish(record: RecordWithLocals): Unit = Local.let(record.locals) {
    super.publish(record.record)
  }

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
    queue.drain().map { records => records.foreach(doPublish) }

    // Propagate flush
    super.flush()
  }

  /**
   * Called when record dropped.  Default is to log to console.
   */
  protected def onOverflow(record: javalog.LogRecord): Unit = {
    Console.err.println(
      String.format("[%s] log queue overflow - record dropped", Time.now.toString)
    )
  }
}
