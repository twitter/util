package com.twitter.jvm

import com.twitter.conversions.storage._
import com.twitter.jvm.Jvm.MetaspaceUsage
import com.twitter.util.{StorageUnit, Time}

object NilJvm extends Jvm {
  val opts: Opts = new Opts {
    def compileThresh = None
  }
  def forceGc(): Unit = System.gc()
  val snapCounters: Map[String, String] = Map()
  val snap: Snapshot = Snapshot(
    Time.epoch,
    Heap(0, 0, Seq()),
    Seq())

  val edenPool: Pool = new Pool { def state() = PoolState(0, 0.bytes, 0.bytes) }

  val metaspaceUsage: Option[Jvm.MetaspaceUsage] = None

  val safepoint: Safepoint = Safepoint(0, 0, 0)

}
