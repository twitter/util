package com.twitter.finagle.stats

import java.util.concurrent.ConcurrentHashMap
import scala.ref.WeakReference

/**
 * CumulativeGauge provides a gauge that is composed of the (addition)
 * of several underlying gauges. It follows the weak reference
 * semantics of Gauges as outlined in StatsReceiver.
 */
private[finagle] trait CumulativeGauge {
  private[this] case class UnderlyingGauge(f: () => Float) extends Gauge {
    def remove(): Unit = removeGauge(this)
  }

  @volatile private[this] var underlying: List[WeakReference[UnderlyingGauge]] = Nil

  /**
   * Returns a buffered version of the current gauges
   */
  private[this] def get(): Seq[UnderlyingGauge] = {
    removeGauge(null) // clean up weakrefs
    underlying.flatMap(_.get)
  }

  private[this] def removeGauge(underlyingGauge: UnderlyingGauge): Unit = synchronized {
    // This cleans up weakrefs
    underlying = underlying.filter { weakRef =>
      weakRef.get match {
        case Some(g) => g ne underlyingGauge
        case None => false
      }
    }
    if (underlying.isEmpty)
      deregister()
  }

  def addGauge(f: => Float): Gauge = synchronized {
    val shouldRegister = underlying.isEmpty
    val underlyingGauge = UnderlyingGauge(() => f)
    underlying ::= new WeakReference(underlyingGauge)
    if (shouldRegister)
      register()
    underlyingGauge
  }

  def getValue: Float =
    get().map(_.f()).sum

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

}
