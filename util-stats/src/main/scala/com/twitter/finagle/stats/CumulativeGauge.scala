package com.twitter.finagle.stats

import com.github.benmanes.caffeine.cache.{Cache, Caffeine, RemovalCause, RemovalListener}
import com.twitter.util.lint._
import java.lang.Boolean
import java.util.concurrent.{ConcurrentHashMap, Executor, ForkJoinPool}
import scala.collection.JavaConverters._

/**
 * `CumulativeGauge` provides a [[Gauge gauge]] that is composed of the (addition)
 * of several underlying gauges. It follows the weak reference
 * semantics of [[Gauge Gauges]] as outlined in [[StatsReceiver]].
 *
 * @param executor exposed for testing purposes so that a tests can be
 *                 run deterministically using a same thread `Executor`.
 */
private[finagle] abstract class CumulativeGauge(executor: Executor) { self =>

  // uses the default `Executor` for the cache.
  def this() = this(ForkJoinPool.commonPool())

  private[this] class UnderlyingGauge(val f: () => Float) extends Gauge {
    def remove(): Unit = self.remove(this)
  }

  private[this] val removals = new RemovalListener[UnderlyingGauge, java.lang.Boolean] {
    def onRemoval(key: UnderlyingGauge, value: Boolean, cause: RemovalCause): Unit =
      self.deregister()
  }

  /**
   * A cache of `UnderlyingGauges` to sentinel values (Boolean.TRUE).
   * The keys are held by `WeakReferences` and cleaned out occasionally,
   * when there are no longer strong references to the key.
   *
   * @see [[cleanup()]] to allow for more predictable testing.
   */
  private[this] val refs: Cache[UnderlyingGauge, java.lang.Boolean] =
    Caffeine.newBuilder()
      .executor(executor)
      .weakKeys()
      .removalListener(removals)
      .build[UnderlyingGauge, java.lang.Boolean]()

  /** Removes expired gauges. Exposed for testing. */
  protected def cleanup(): Unit =
    refs.cleanUp()

  /** The number of active gauges */
  private[stats] def size: Int = {
    cleanup()
    totalSize
  }

  /** Total number of gauges, including inactive */
  private[stats] def totalSize: Int =
    refs.estimatedSize().toInt

  private def remove(underlyingGauge: UnderlyingGauge): Unit =
    refs.invalidate(underlyingGauge)

  def addGauge(f: => Float): Gauge = {
    val underlyingGauge = new UnderlyingGauge(() => f)
    refs.put(underlyingGauge, java.lang.Boolean.TRUE)
    register()

    underlyingGauge
  }

  def getValue: Float = {
    var sum = 0f
    val iter = refs.asMap().keySet().iterator()
    while (iter.hasNext) {
      val g = iter.next()
      sum += g.f()
    }
    sum
  }

  /**
   * These need to be implemented by the gauge provider. They indicate
   * when the gauge needs to be registered & deregistered.
   *
   * Special care must be taken in implementing these so that they are free
   * of race conditions.
   */
  def register(): Unit
  def deregister(): Unit
}

trait StatsReceiverWithCumulativeGauges extends StatsReceiver { self =>

  private[this] val gauges = new ConcurrentHashMap[Seq[String], CumulativeGauge]()

  def largeGaugeLinterRule: Rule = {
    Rule(
      Category.Performance,
      "Large CumulativeGauges",
      "Identifies CumulativeGauges which are backed by very large numbers (100k+) " +
        "of Gauges. Indicative of a leak or code registering the same gauge more " +
        s"often than expected. (For $toString)"
    ) {
      val largeCgs = gauges.asScala.flatMap { case (ks, cg) =>
        if (cg.totalSize >= 100000) Some(ks -> cg.totalSize)
        else None
      }
      if (largeCgs.isEmpty) {
        Nil
      } else {
        largeCgs.map { case (ks, size) =>
          Issue(ks.mkString("/") + "=" + size)
        }.toSeq
      }
    }
  }

  /**
   * The StatsReceiver implements these. They provide the cumulated
   * gauges.
   */
  protected[this] def registerGauge(name: Seq[String], f: => Float): Unit
  protected[this] def deregisterGauge(name: Seq[String]): Unit

  def addGauge(name: String*)(f: => Float): Gauge = {
    var cumulativeGauge = gauges.get(name)
    if (cumulativeGauge == null) {
      val insert = new CumulativeGauge { cg =>
        // thread safety provided by synchronization on `this/cg`
        private[this] var registers = 0

        def register(): Unit = cg.synchronized {
          registers += 1
          if (registers == 1) {
            self.registerGauge(name, getValue)
            // in order to avoid races with `deregister`, we make sure
            // we are always in `gauges`.
            gauges.putIfAbsent(name, this)
          }
        }

        def deregister(): Unit = cg.synchronized {
          registers -= 1
          if (registers == 0) {
            gauges.remove(name)
            self.deregisterGauge(name)
          }
        }
      }
      val prev = gauges.putIfAbsent(name, insert)
      cumulativeGauge = if (prev == null) insert else prev
    }
    cumulativeGauge.addGauge(f)
  }

  /**
   * The number of gauges that are cumulatively represented
   * and still have a reference to them.
   *
   * Exposed for testing purposes.
   *
   * @return 0 if no active gauges are found.
   */
  protected def numUnderlying(name: String*): Int = {
    val cumulativeGauge = gauges.get(name)
    if (cumulativeGauge == null) 0
    else cumulativeGauge.size
  }

}
