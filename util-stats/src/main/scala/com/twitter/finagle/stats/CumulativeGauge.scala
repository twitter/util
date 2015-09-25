package com.twitter.finagle.stats

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import java.lang.ref.WeakReference

/**
 * `CumulativeGauge` provides a [[Gauge gauge]] that is composed of the (addition)
 * of several underlying gauges. It follows the weak reference
 * semantics of [[Gauge Gauges]] as outlined in [[StatsReceiver]].
 */
private[finagle] abstract class CumulativeGauge {
  private[this] case class UnderlyingGauge(f: () => Float) extends Gauge {
    def remove(): Unit = removeGauge(this)
  }

  @volatile private[this] var underlying =
    IndexedSeq.empty[WeakReference[UnderlyingGauge]]

  /**
   * Returns a buffered version of the current gauges
   */
  private[this] def get(): IndexedSeq[UnderlyingGauge] = {
    removeGauge(null) // clean up weakrefs

    val gs = new mutable.ArrayBuffer[UnderlyingGauge](underlying.size)
    underlying.foreach { weakRef =>
      val g = weakRef.get()
      if (g != null)
        gs += g
    }
    gs
  }

  /** The number of active gauges */
  private[stats] def size: Int =
    get().size

  private[this] def removeGauge(underlyingGauge: UnderlyingGauge): Unit = synchronized {
    // first, clean up weakrefs
    val newUnderlying = mutable.IndexedSeq.newBuilder[WeakReference[UnderlyingGauge]]
    underlying.foreach { weakRef =>
      val g = weakRef.get()
      if (g != null && (g ne underlyingGauge))
        newUnderlying += weakRef
    }
    underlying = newUnderlying.result()
    if (underlying.isEmpty)
      deregister()
  }

  def addGauge(f: => Float): Gauge = synchronized {
    val shouldRegister = underlying.isEmpty
    if (!shouldRegister)
      removeGauge(null) // there is at least 1 gauge that may need to be cleaned
    val underlyingGauge = UnderlyingGauge(() => f)
    underlying :+= new WeakReference(underlyingGauge)
    if (shouldRegister)
      register()
    underlyingGauge
  }

  def getValue: Float = {
    var sum = 0f
    get().foreach { g =>
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

  /**
   * The StatsReceiver implements these. They provide the cumulated
   * gauges.
   */
  protected[this] def registerGauge(name: Seq[String], f: => Float): Unit
  protected[this] def deregisterGauge(name: Seq[String]): Unit

  def addGauge(name: String*)(f: => Float): Gauge = {
    var cumulativeGauge = gauges.get(name)
    if (cumulativeGauge == null) {
      val insert = new CumulativeGauge {
        override def register(): Unit = synchronized {
          self.registerGauge(name, getValue)
        }

        override def deregister(): Unit = synchronized {
          gauges.remove(name)
          self.deregisterGauge(name)
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
