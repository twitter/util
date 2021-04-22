package com.twitter.finagle.stats

import com.github.benmanes.caffeine.cache.{Cache, Caffeine, RemovalCause, RemovalListener}
import com.twitter.util.lint._
import java.lang.{Boolean => JBoolean}
import java.util.concurrent.{ConcurrentHashMap, Executor, ForkJoinPool}
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{Function => JFunction}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

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

  private[this] class UnderlyingGauge(val f: () => Float, val metadata: Metadata) extends Gauge {
    def remove(): Unit = self.remove(this)
  }

  private[this] val removals = new RemovalListener[UnderlyingGauge, JBoolean] {
    def onRemoval(key: UnderlyingGauge, value: JBoolean, cause: RemovalCause): Unit =
      self.deregister()
  }

  /**
   * A cache of `UnderlyingGauges` to sentinel values (Boolean.TRUE).
   * The keys are held by `WeakReferences` and cleaned out occasionally,
   * when there are no longer strong references to the key.
   *
   * @see [[cleanup()]] to allow for more predictable testing.
   */
  private[this] val refs: Cache[UnderlyingGauge, JBoolean] =
    Caffeine
      .newBuilder()
      .executor(executor)
      .weakKeys()
      .removalListener(removals)
      .build[UnderlyingGauge, JBoolean]()

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

  /**
   * Returns a gauge unless it is dead, in which case it returns null.
   */
  def addGauge(f: => Float, metadata: Metadata): Gauge = {
    val underlyingGauge = new UnderlyingGauge(() => f, metadata)
    refs.put(underlyingGauge, JBoolean.TRUE)

    if (register()) underlyingGauge else null
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

  // Special care must be taken in implementing these so that they are free
  // of race conditions.
  /**
   * Indicates when the gauge needs to be registered.
   *
   * Returns true until the gauge is dead, after which it returns false.
   */
  def register(): Boolean

  /**
   * Indicates when the gauge needs to be deregistered.
   */
  def deregister(): Unit
}

trait StatsReceiverWithCumulativeGauges extends StatsReceiver { self =>

  private[this] val gauges = new ConcurrentHashMap[Seq[String], CumulativeGauge]()

  def largeGaugeLinterRule: Rule = {
    Rule(
      Category.Performance,
      "Large CumulativeGauges",
      "Identifies CumulativeGauges which are backed by very large numbers (10k+) " +
        "of Gauges. Indicative of a leak or code registering the same gauge more " +
        s"often than expected. (For $toString)"
    ) {
      gauges.asScala.collect {
        case (ks, cg) if (cg.totalSize >= 10000) => Issue(ks.mkString("/") + "=" + cg.totalSize)
      }.toSeq
    }
  }

  /**
   * The StatsReceiver implements these. They provide the cumulative
   * gauges.
   */
  protected[this] def registerGauge(schema: GaugeSchema, f: => Float): Unit
  protected[this] def deregisterGauge(name: Seq[String]): Unit

  private[this] def getWhenNotPresent(schema: GaugeSchema) = whenNotPresent(schema)

  /** The executor that will be used for expiring gauges */
  def executor: Executor = ForkJoinPool.commonPool()

  private[this] def whenNotPresent(schema: GaugeSchema) =
    new JFunction[Seq[String], CumulativeGauge] {
      def apply(key: Seq[String]): CumulativeGauge = new CumulativeGauge(executor) {
        self.registerGauge(schema, getValue)

        // The number of registers starts at `0` because every new gauge will cause a
        // registration, even the one that created the `CumulativeGauge`. Because 0 is
        // a valid value to increment from, we use `-1` to signal the closed state where
        // no more registration activity is possible.
        private[this] val registers = new AtomicInteger(0)

        @tailrec def register(): Boolean = {
          val c = registers.get
          if (c == -1) false
          else if (!registers.compareAndSet(c, c + 1)) register()
          else true
        }

        @tailrec def deregister(): Unit = registers.get match {
          case -1 => () // Already closed
          case 1 =>
            // Attempt to transition to the closed state
            if (!registers.compareAndSet(1, -1)) {
              deregister() // lost a race so try again
            } else {
              // Do the cleanup.
              gauges.remove(key)
              self.deregisterGauge(key)
            }

          case c =>
            // Just a normal decrement
            if (!registers.compareAndSet(c, c - 1)) {
              deregister() // lost a race so try again
            }
        }
      }
    }

  def addGauge(schema: GaugeSchema)(f: => Float): Gauge = {
    var gauge: Gauge = null
    while (gauge == null) {
      val cumulativeGauge =
        gauges.computeIfAbsent(schema.metricBuilder.name, getWhenNotPresent(schema))
      gauge = cumulativeGauge.addGauge(f, schema.metricBuilder)
    }
    gauge
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
