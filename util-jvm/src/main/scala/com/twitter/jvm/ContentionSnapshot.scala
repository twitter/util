package com.twitter.jvm

import java.lang.management.{ManagementFactory, ThreadInfo}
import java.lang.Thread.State._
import scala.collection.mutable

/**
 * A thread contention summary. This provides a brief overview of threads
 * that are blocked or otherwise waiting.
 *
 * While this could be an object, we use instantiation as a signal of intent
 * and enable contention monitoring.
 */
class ContentionSnapshot {
  ManagementFactory.getThreadMXBean.setThreadContentionMonitoringEnabled(true)

  case class Snapshot(
    blockedThreads: Seq[String],
    lockOwners: Seq[String])

  private[this] object Blocked {
    def unapply(t: ThreadInfo): Option[ThreadInfo] = {
      t.getThreadState match {
        case BLOCKED | WAITING | TIMED_WAITING => Some(t)
        case _ => None
      }
    }
  }

  def snap(): Snapshot = {
    val bean = ManagementFactory.getThreadMXBean
    val lockOwners = mutable.Set[Long]()

    val blocked = bean.getThreadInfo(bean.getAllThreadIds, true, true).collect {
      case Blocked(t) => t
    }
    val ownerIds = blocked map(_.getLockOwnerId) filter(_ != -1)

    Snapshot(
      blockedThreads = blocked.map(_.toString).toSeq,
      lockOwners = bean.getThreadInfo(ownerIds.toArray, true, true).map(_.toString).toSeq)
  }
}
