package com.twitter.jvm

import com.twitter.conversions.time._
import com.twitter.util.{Timer, Duration, Time}
import java.lang.management.ManagementFactory
import java.util.logging.Logger
import scala.collection.mutable
import java.util.concurrent.{ScheduledExecutorService, Executors, TimeUnit}

case class Heap(
  // Number of bytes allocated so far.
  allocated: Long,
  // Tenuring threshold: How many times an
  // object needs to be copied before being
  // tenured.
  tenuringThreshold: Long,
  // Histogram of the number of bytes that have
  // been copied as many times. Note: 0-indexed.
  ageHisto: Seq[Long]
)

case class Gc(
  count: Long,
  name: String,
  timestamp: Time,
  duration: Duration
)

case class Snapshot(
  timestamp: Time,
  heap: Heap,
  lastGcs: Seq[Gc]
)


/**
 * Access JVM internal performance counters. We maintain a strict
 * interface so that we are decoupled from the actual underlying JVM.
 */
trait Jvm {
  trait Opts {
    def compileThresh: Option[Int]
  }

  /**
   * Current VM-specific options.
   */
  val opts: Opts

  /**
   * Get a snapshot of all performance counters.
   */
  def snapCounters: Map[String, String]

  /**
   * Snapshot of JVM state.
   */
  def snap: Snapshot

  def executor: ScheduledExecutorService = Jvm.executor

  /**
   * Invoke `f` for every Gc event in the system. This samples `snap`
   * in order to synthesize a unique stream of Gc events. It's
   * important that `f` is not constructed so that it's likely to, by
   * itself, trigger another Gc event, causing an infinite loop. The
   * same is true of the internal datastructures used by foreachGc,
   * but they are svelte.
   */
  def foreachGc(f: Gc => Unit) {
    val Period = 1.second
    val log = Logger.getLogger(getClass.getName)

    val lastByName = mutable.HashMap[String, Long]()
    def sample() {
      val Snapshot(timestamp, _, gcs) = snap

      for (gc@Gc(count, name, _, _) <- gcs) {
        lastByName.get(name) match {
          case Some(`count`) => // old
          case Some(lastCount) =>
            if (lastCount != count-1)
              log.warning("Missed %d collections for %s due to sampling".format(count-1-lastCount, name))
            f(gc)
          case None =>
            f(gc)
        }

        lastByName(name) = count
      }
    }

    executor.scheduleAtFixedRate(
      new Runnable { def run() = sample() }, 0/*initial delay*/,
      Period.inMilliseconds, TimeUnit.MILLISECONDS)
  }

  /**
   * Monitors Gcs using `foreachGc`, and returns a function to query
   * its timeline (up to `buffer` in the past). Querying is cheap, linear
   * to the number of Gcs that happened since the queried time. The
   * result is returned in reverse chronological order.
   */
  def monitorGcs(bufferFor: Duration): Time => Seq[Gc] = {
    require(bufferFor > 0.seconds)
    @volatile var buffer = Nil: List[Gc]

    // We assume that timestamps from foreachGc are monotonic.
    foreachGc { case gc@Gc(_, _, timestamp, _) =>
      val floor = timestamp - bufferFor
      buffer = (gc :: buffer) takeWhile(_.timestamp > floor)
    }

    (since: Time) => buffer takeWhile(_.timestamp > since)
  }
}

object Jvm {
  private lazy val executor = Executors.newScheduledThreadPool(1)

  private[this] lazy val _jvm = {
    val name = ManagementFactory.getRuntimeMXBean.getVmName
    // Is there a better way to detect HotSpot?
    //
    // TODO: also check that we can _actually_ create a Hotspot
    // instance without exceptions
    if (name startsWith "Java HotSpot(TM)")
      Some(new Hotspot: Jvm)
    else
      None
  } getOrElse NilJvm

  def apply(): Jvm = _jvm
}
