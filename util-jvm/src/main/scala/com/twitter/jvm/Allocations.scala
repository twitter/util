package com.twitter.jvm

import com.twitter.finagle.stats.StatsReceiver
import java.lang.management.{MemoryPoolMXBean, MemoryUsage, ManagementFactory}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.{List => juList}
import javax.management.openmbean.{CompositeData, TabularData}
import javax.management.{
  ListenerNotFoundException,
  Notification,
  NotificationListener,
  NotificationEmitter
}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private[jvm] object Allocations {
  val Unknown: Long = -1L
}

/**
 * Provides visibility into object allocations.
 *
 * @param statsReceiver typically scoped to /jvm/gc/
 */
private[jvm] class Allocations(statsReceiver: StatsReceiver) {

  import Allocations.Unknown

  private[this] val edenPool: Option[MemoryPoolMXBean] =
    ManagementFactory.getMemoryPoolMXBeans.asScala.find { bean =>
      // todo: see if we can support the g1 collector
      bean.getName == "Par Eden Space" || bean.getName == "PS Eden Space"
    }

  private[this] val edenSizeAfterLastGc = new AtomicLong()

  private[this] val edenAllocated = new AtomicLong()

  private[this] val beanAndListeners =
    new LinkedBlockingQueue[(NotificationEmitter, NotificationListener)]()

  private[this] val edenGcPauses = statsReceiver.scope("eden").stat("pause_msec")

  private[jvm] def start(): Unit = {
    edenPool
      .flatMap { bean => Option(bean.getUsage) }
      .foreach { _ =>
        ManagementFactory.getGarbageCollectorMXBeans.asScala.foreach {
          case bean: NotificationEmitter =>
            // skip CMS because it does not collect objects from the eden
            if (bean.getName != "ConcurrentMarkSweep") {
              val listener = newEdenGcListener()
              beanAndListeners.add((bean, listener))
              bean.addNotificationListener(listener, null, null)
            }
          case _ =>
        }
      }
  }

  private[jvm] def stop(): Unit = {
    while (!beanAndListeners.isEmpty) {
      Option(beanAndListeners.poll()).foreach {
        case (bean, listener) =>
          try {
            bean.removeNotificationListener(listener)
          } catch {
            case _: ListenerNotFoundException => // ignore
          }
      }
    }
  }

  private[jvm] def trackingEden: Boolean = !beanAndListeners.isEmpty

  /**
   * Estimation, in bytes, of allocations to the eden.
   *
   * It may miss allocations where large objects are eagerly allocated into the
   * old generation along with some other cases.
   *
   * Note: due to race conditions, the number reported is NOT a
   * monotonically increasing value.
   *
   * @return the approximate number, in bytes, that have been allocated to the eden.
   *         Returns [[com.twitter.jvm.Allocations.Unknown]] in the
   *         case where allocations are not being tracked.
   */
  private[jvm] def eden: Long = {
    if (!trackingEden)
      return Unknown

    edenPool match {
      case None =>
        Unknown
      case Some(pool) =>
        val usage = pool.getUsage
        if (usage == null) {
          Unknown
        } else {
          // there is a race here, where a gc has completed but we have not yet
          // been notified. so `edenAllocated` has not yet been updated which in
          // turn means our math is off.
          // in the interest of keeping this simple, if the eden's current used is
          // less than the last edenAllocated we will only return the number of
          // bytes we have gc-ed from the eden.
          val usedSinceLastGc = math.max(0, usage.getUsed - edenSizeAfterLastGc.get)
          usedSinceLastGc + edenAllocated.get
        }
    }
  }

  private[this] def newEdenGcListener() = new NotificationListener {
    def edenMemoryUsageFrom(any: Any): Option[MemoryUsage] = {
      if (!any.isInstanceOf[TabularData])
        return None

      val tabData = any.asInstanceOf[TabularData]
      val edenKeys: mutable.Set[juList[_]] = tabData.keySet.asScala.collect {
        case ks: juList[_] if ks.asScala.headOption.exists {
              case s: String => s.contains("Eden")
              case _ => false
            } =>
          ks
      }

      val memoryUsages = edenKeys.flatMap { k =>
        tabData.get(k.toArray) match {
          case cd: CompositeData if cd.containsKey("value") =>
            cd.get("value") match {
              case vcd: CompositeData => Some(MemoryUsage.from(vcd))
              case _ => None
            }
          case _ => None
        }
      }
      memoryUsages.headOption
    }

    // the Notification's userData correspond to
    // `com.sun.management.GarbageCollectionNotificationInfo`s.
    override def handleNotification(notification: Notification, handback: Any): Unit = {
      val userData = notification.getUserData match {
        case cd: CompositeData if cd.containsKey("gcInfo") => cd
        case _ => return
      }
      val gcInfo = userData.get("gcInfo") match {
        case cd: CompositeData => cd
        case _ => return
      }

      if (gcInfo.containsKey("duration")) {
        gcInfo.get("duration") match {
          case duration: java.lang.Long => edenGcPauses.add(duration.floatValue)
          case _ =>
        }
      }

      if (!gcInfo.containsKey("memoryUsageBeforeGc") || !gcInfo.containsKey("memoryUsageAfterGc"))
        return

      for {
        beforeGc <- edenMemoryUsageFrom(gcInfo.get("memoryUsageBeforeGc")).map(_.getUsed)
        afterGc <- edenMemoryUsageFrom(gcInfo.get("memoryUsageAfterGc")).map(_.getUsed)
      } {
        edenAllocated.addAndGet(beforeGc - afterGc)
        edenSizeAfterLastGc.set(afterGc)
      }
    }
  }

}
