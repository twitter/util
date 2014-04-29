package com.twitter.util

import java.util.concurrent.CancellationException
import java.util.logging.{Logger, Level}
import scala.collection.mutable

/**
 * A "batcher" that takes a function `Seq[In] => Future[Seq[Out]]` and
 * exposes a `In => Future[Out]` interface that batches the underlying
 * asynchronous operations. Thus, one can incrementally submit tasks to be
 * performed when the criteria for batch flushing is met. This class is used
 * internally to the util-core package by `Future.batched` and not intended
 * for public consumption.
 *
 * A batcher's size can be controlled at runtime with the `sizePercentile`
 * function argument. This function returns a float between 0.0 and 1.0,
 * representing the fractional size of the `sizeThreshold` that should be
 * used for the next batch to be collected.
 *
 * TODO: Possible future improvements:
 * - Rather than having separate sizeThreshold and sizePercentile parameters,
 *   just have a single call-by-name sizeThreshold parameter, and let the
 *   caller implement whatever logic they want to compute the next batch size.
 * - Return an instance of a new class (Batcher?) which extends
 *   `In => Future[Out]`. Could support things like querying the queue size,
 *   forcing a flush, attaching callbacks to flush operations, etc.
 */
private[util] class BatchExecutor[In, Out](
  sizeThreshold: Int,
  timeThreshold: Duration = Duration.Top,
  sizePercentile: => Float = 1.0f,
  f: Seq[In] => Future[Seq[Out]]
)(
  implicit timer: Timer
) extends Function1[In, Future[Out]] { batcher =>
  import Level.WARNING

  class ScheduledFlush(after: Duration, timer: Timer) {
    @volatile var cancelled = false
    val task = timer.schedule(after.fromNow) { flush() }

    def cancel() {
      cancelled = true
      task.cancel()
    }

    def flush() {
      val after = batcher.synchronized {
        if (!cancelled)
          flushBatch()
        else
          () => ()
      }
      after()
    }
  }

  val log = Logger.getLogger("Future.batched")

  // operations on these are synchronized on `this`.
  val buf = new mutable.ArrayBuffer[(In, Promise[Out])](sizeThreshold)
  var scheduled: Option[ScheduledFlush] = scala.None
  var currentBufThreshold = newBufThreshold

  def currentBufPercentile = sizePercentile match {
    case tooHigh if tooHigh > 1.0f =>
      log.log(WARNING, "value returned for sizePercentile (%f) was > 1.0f, using 1.0", tooHigh)
      1.0f

    case tooLow if tooLow < 0.0f =>
      log.log(WARNING, "value returned for sizePercentile (%f) was negative, using 0.0f", tooLow)
      0.0f

    case p => p
  }

  def newBufThreshold =
    math.round(currentBufPercentile * sizeThreshold) match {
      case tooLow if tooLow < 1 => 1
      case size =>  math.min(size, sizeThreshold)
    }

  def apply(t: In): Future[Out] = enqueue(t)

  def enqueue(t: In): Future[Out] = {
    val promise = new Promise[Out]
    val after = synchronized {
      buf.append((t, promise))
      if (buf.size >= currentBufThreshold)
        flushBatch()
      else {
        scheduleFlushIfNecessary()
        () => ()
      }
    }

    after()
    promise
  }

  def scheduleFlushIfNecessary() {
    if (timeThreshold < Duration.Top && scheduled.isEmpty)
      scheduled = Some(new ScheduledFlush(timeThreshold, timer))
  }

  def flushBatch(): () => Unit = {
    // this must be executed within a `synchronized` block.
    val prevBatch = new mutable.ArrayBuffer[(In, Promise[Out])](buf.length)
    buf.copyToBuffer(prevBatch)
    buf.clear()

    scheduled foreach { _.cancel() }
    scheduled = scala.None
    currentBufThreshold = newBufThreshold  // set the next batch's size

    () => try {
      executeBatch(prevBatch)
    } catch {
      case e: Throwable =>
        log.log(WARNING, "unhandled exception caught in Future.batched: %s".format(e.toString), e)
    }
  }

  def executeBatch(batch: Seq[(In, Promise[Out])]) {
    val uncancelled = batch filter { case (in, p) =>
      p.isInterrupted match {
        case Some(_cause) =>
          p.setException(new CancellationException)
          false

        case scala.None => true
      }
    }

    val ins = uncancelled map { case (in, _) => in }
    // N.B. intentionally not linking cancellation of these promises to the execution of the batch
    // because it seems that in most cases you would be canceling mostly uncanceled work for an
    // outlier.
    val promises = uncancelled map { case (_, promise) => promise }

    f(ins) respond {
      case Return(outs) =>
        (outs zip promises) foreach { case (out, p) =>
          p() = Return(out)
        }

      case Throw(e) =>
        val t = Throw(e)
        promises foreach { _() = t }
    }
  }
}
