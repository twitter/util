package com.twitter.jvm

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.conversions.DurationOps._
import com.twitter.util._
import java.lang.management.ManagementFactory
import java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledExecutorService, TimeUnit}
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
 * Access JVM internal performance counters. We maintain a strict
 * interface so that we are decoupled from the actual underlying JVM.
 */
trait Jvm {
  import Jvm.log

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

  def edenPool: com.twitter.jvm.Pool

  /**
   * Gets the current usage of the metaspace, if available.
   */
  def metaspaceUsage: Option[Jvm.MetaspaceUsage]

  /**
   * Gets the applicationTime (total time running application code since the process started)
   * in nanoseconds or 0, if the metric is unavailable.
   */
  def applicationTime: Long

  /**
   * Gets the current tenuringThreshold (times an object must survive GC
   * in order to be promoted) or 0, if the metric is unavailable.
   */
  def tenuringThreshold: Long

  /**
   * Gets the time spent at safepoints (totalTimeMillis), the time getting
   * to safepoints (syncTimeMillis), and safepoints reached (count).
   */
  def safepoint: Safepoint

  def executor: ScheduledExecutorService = Jvm.executor

  /**
   * Invoke `f` for every Gc event in the system. This samples `snap`
   * in order to synthesize a unique stream of Gc events. It's
   * important that `f` is not constructed so that it's likely to, by
   * itself, trigger another Gc event, causing an infinite loop. The
   * same is true of the internal datastructures used by foreachGc,
   * but they are svelte.
   */
  def foreachGc(f: Gc => Unit): Unit = {
    val Period = 1.second
    val LogPeriod = 30.minutes
    @volatile var missedCollections = 0L
    @volatile var lastLog = Time.epoch

    val lastByName = new ConcurrentHashMap[String, java.lang.Long](16, 0.75f, 1)
    def sample(): Unit = {
      val Snapshot(_, _, gcs) = snap

      for (gc @ Gc(count, name, _, _) <- gcs) {
        val lastCount = lastByName.get(name)
        if (lastCount == null) {
          f(gc)
        } else if (lastCount != count) {
          missedCollections += count - 1 - lastCount
          if (missedCollections > 0 && Time.now - lastLog > LogPeriod) {
            if (log.isLoggable(Level.FINE)) {
              log.fine(
                "Missed %d collections for %s due to sampling"
                  .format(missedCollections, name)
              )
            }
            lastLog = Time.now
            missedCollections = 0
          }
          f(gc)
        }

        lastByName.put(name, count)
      }
    }

    executor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = sample() },
      0 /*initial delay*/,
      Period.inMilliseconds,
      TimeUnit.MILLISECONDS
    )
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
    foreachGc {
      case gc @ Gc(_, _, timestamp, _) =>
        val floor = timestamp - bufferFor
        buffer = (gc :: buffer).takeWhile(_.timestamp > floor)
    }

    since: Time =>
      buffer.takeWhile(_.timestamp > since)
  }

  def forceGc(): Unit

  /**
   * Get the main class name for the currently running application.
   * Note that this works only by heuristic, and may not be accurate.
   *
   * TODO: take into account the standard callstack around scala
   * invocations better.
   */
  def mainClassName: String = {
    val mainClass = for {
      (_, stack) <- Thread.getAllStackTraces.asScala.find { case (t, s) => t.getName == "main" }
      frame <- stack.reverse.find { elem =>
        !elem.getClassName.startsWith("scala.tools.nsc.MainGenericRunner")
      }
    } yield frame.getClassName

    mainClass.getOrElse("unknown")
  }
}

/**
 * See [[Jvms]] for Java compatibility.
 */
object Jvm {

  /**
   * Return the current process id.
   *
   * @note this is fragile as the RuntimeMXBean doesn't specify the name format.
   */
  lazy val ProcessId: Option[Int] =
    try {
      ManagementFactory.getRuntimeMXBean.getName.split("@").headOption.map(_.toInt)
    } catch {
      case NonFatal(t) =>
        log.log(Level.WARNING, "failed to find process id", t)
        None
    }

  private lazy val executor =
    Executors.newScheduledThreadPool(1, new NamedPoolThreadFactory("util-jvm-timer", true))

  private lazy val _jvm =
    try new Hotspot
    catch {
      case NonFatal(_) | _: UnsatisfiedLinkError =>
        log.warning("failed to create Hotspot JVM interface, using NilJvm instead")
        NilJvm
    }

  private val log = Logger.getLogger(getClass.getName)

  /**
   * Return an instance of the [[Jvm]] for this runtime.
   *
   * See [[Jvms.apply()]] for Java compatibility.
   */
  def apply(): Jvm = _jvm

  /**
   * Usage of the JVM's metaspace.
   *
   * @param used how much is currently in use
   * @param capacity the current capacity of the metaspace.
   *        It can grow beyond this, up to `maxCapacity`, if needed.
   * @param maxCapacity the maximum size that the metaspace can grow to.
   */
  case class MetaspaceUsage(used: StorageUnit, capacity: StorageUnit, maxCapacity: StorageUnit)

}

/**
 * Java compatibility for [[Jvm]].
 */
object Jvms {

  /**
   * Java compatibility for [[Jvm.ProcessId]].
   */
  def processId(): Option[Int] =
    Jvm.ProcessId

  /**
   * Java compatibility for [[Jvm.apply()]].
   */
  def get(): Jvm = Jvm()

}
