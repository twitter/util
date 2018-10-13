package com.twitter.io.exp

import com.twitter.io.{Writer, Buf, Reader}
import com.twitter.util._
import scala.util.control.NoStackTrace

/**
 * [[Reader Readers]] and [[Writer Writers]] that have minimum bytes
 * per second throughput limits.
 */
object MinimumThroughput {

  private val BpsNoElapsed = -1d

  private val MinDeadline = Duration.fromSeconds(1)

  /** internal marker used to raise on read/write timeouts. */
  private case object MinThroughputTimeoutException extends Exception with NoStackTrace

  private abstract class Min(minBps: Double) {
    assert(minBps >= 0, s"minBps must be non-negative: $minBps")

    // we rely on the `reader.read` and `writer.write` contract that only
    // one read or write can ever be outstanding, so volatile provides
    // us thread-safety.
    @volatile protected[this] var bytes = 0L
    @volatile protected[this] var elapsed = Duration.Zero

    /** Calculate and return the current bps */
    def bps: Double = {
      val elapsedSecs = elapsed.inSeconds
      if (elapsedSecs == 0) {
        BpsNoElapsed
      } else {
        bytes.toDouble / elapsedSecs.toDouble
      }
    }

    /**
     * Return a cap on how long we're willing to wait for the operation to
     * complete processing `numBytes` while staying above the min bps requirements.
     */
    def nextDeadline(numBytes: Int): Duration =
      if (minBps == 0)
        Duration.Top
      else
        Duration.fromSeconds(((bytes + numBytes) / minBps).toInt).max(MinDeadline)

    /** Verify if we are over the min bps requirements. */
    def throughputOk: Boolean = {
      val newBps = bps
      newBps >= minBps || newBps == BpsNoElapsed
    }

    def newBelowThroughputException: BelowThroughputException =
      BelowThroughputException(elapsed, bps, minBps)

  }

  private class MinReader(reader: Reader[Buf], minBps: Double, timer: Timer)
      extends Min(minBps)
      with Reader[Buf] {
    def discard(): Unit = reader.discard()

    def read(n: Int): Future[Option[Buf]] = {
      val deadline = nextDeadline(n)

      val start = Time.now
      val read: Future[Option[Buf]] =
        reader.read(n).raiseWithin(timer, deadline, MinThroughputTimeoutException)

      read.transform { res =>
        elapsed += Time.now - start
        res match {
          case Throw(MinThroughputTimeoutException) =>
            discard()
            Future.exception(newBelowThroughputException)
          case Throw(_) =>
            read // pass through other failures untouched
          case Return(None) =>
            read // hit EOF, so we're fine
          case Return(Some(buf)) =>
            // read some bytes, see if we're still good in terms of bps
            bytes += buf.length
            if (throughputOk) {
              read
            } else {
              discard()
              Future.exception(newBelowThroughputException)
            }
        }
      }
    }
  }

  private class MinWriter(writer: Writer[Buf], minBps: Double, timer: Timer)
      extends Min(minBps)
      with Writer[Buf] {
    def fail(cause: Throwable): Unit = writer.fail(cause)

    override def write(buf: Buf): Future[Unit] = {
      val numBytes = buf.length
      val deadline = nextDeadline(numBytes)

      val start = Time.now
      val write: Future[Unit] =
        writer.write(buf).raiseWithin(timer, deadline, MinThroughputTimeoutException)

      write.transform { res =>
        elapsed += Time.now - start
        res match {
          case Throw(MinThroughputTimeoutException) =>
            val ex = newBelowThroughputException
            fail(ex)
            Future.exception(ex)
          case Throw(_) =>
            write // pass through other failures untouched
          case Return(_) =>
            // wrote some bytes, see if we're still good in terms of bps
            bytes += numBytes
            if (throughputOk) {
              write
            } else {
              val ex = newBelowThroughputException
              fail(ex)
              Future.exception(ex)
            }
        }
      }
    }

    def close(deadline: Time): Future[Unit] = writer.close(deadline)

    def onClose: Future[Unit] = writer.onClose
  }

  /**
   * Ensures that the throughput of [[Reader.read reads]] happen
   * above a minimum specified bytes per second rate.
   *
   * Reads will return a Future of a [[BelowThroughputException]] when the
   * throughput is not met.
   *
   * @note the minimum time allotted to any read is 1 second.
   */
  def reader(reader: Reader[Buf], minBps: Double, timer: Timer): Reader[Buf] =
    new MinReader(reader, minBps, timer)

  /**
   * Ensures that the throughput of [[Writer.write writes]] happen
   * above a minimum specified bytes per second rate.
   *
   * Writes will return a Future of a [[BelowThroughputException]] when the
   * throughput is not met.
   *
   * @note the minimum time allotted to any write is 1 second.
   */
  def writer(writer: Writer[Buf], minBps: Double, timer: Timer): Writer[Buf] =
    new MinWriter(writer, minBps, timer)

}
