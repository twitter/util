package com.twitter.finagle.stats

import com.github.benmanes.caffeine.cache.{Cache, Caffeine, RemovalCause, RemovalListener}
import com.twitter.util.lint._
import java.lang.{Boolean => JBoolean}
import java.util.concurrent.{ConcurrentHashMap, Executor, ForkJoinPool}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.StampedLock
import java.util.function.{Function => JFunction}
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
    Caffeine.newBuilder()
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
  def addGauge(f: => Float): Gauge = {
    val underlyingGauge = new UnderlyingGauge(() => f)
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
      val largeCgs = gauges.asScala.flatMap { case (ks, cg) =>
        if (cg.totalSize >= 10000) Some(ks -> cg.totalSize)
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

  private[this] val gaugeFn: JFunction[Seq[String], CumulativeGauge] =
    new JFunction[Seq[String], CumulativeGauge] {
      // the gauge's state machine only goes Alive => Dead to simplify trickiness
      // around garbage collection and races
      def apply(key: Seq[String]): CumulativeGauge = new CumulativeGauge {

        self.registerGauge(key, getValue)

        // we grab the read lock on increment and decrement, which can race each
        // other.  we only grab the write lock when we tear down the cumulative
        // gauge, so that we can ensure no more increments happen after death.
        private[this] val lock = new StampedLock()
        private[this] val registers = new AtomicInteger(0)

        // protected by `lock`
        private[this] var dead = false

        def register(): Boolean = {
          val stamp = lock.readLock()
          try {
            if (dead) return false
            registers.incrementAndGet()
          } finally {
            lock.unlockRead(stamp)
          }
          true
        }

        def deregister(): Unit = {
          val readStamp = lock.readLock()
          if (registers.decrementAndGet() == 0) {
            // we could skip a cas by using tryConvertToWriteLock, but it makes
            // the code much uglier.
            lock.unlockRead(readStamp)
            val writeStamp = lock.writeLock()

            try {
              if (!dead && registers.get == 0) {
                dead = true
                gauges.remove(key)
                self.deregisterGauge(key)
              }
            } finally {
              lock.unlockWrite(writeStamp)
            }
          } else {
            lock.unlockRead(readStamp)
          }
        }
      }
    }

  def addGauge(name: String*)(f: => Float): Gauge = {
    var gauge: Gauge = null
    while (gauge == null) {
      var cumulativeGauge = gauges.computeIfAbsent(name, gaugeFn)
      gauge = cumulativeGauge.addGauge(f)
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
