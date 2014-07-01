package com.twitter.jvm

import com.twitter.conversions.storage._
import com.twitter.util.Time

object NilJvm extends Jvm {
  val opts: Opts = new Opts {
    def compileThresh = None
  }
  def forceGc() = System.gc()
  val snapCounters: Map[String, String] = Map()
  val snap: Snapshot = Snapshot(
    Time.epoch,
    Heap(0, 0, Seq()),
    Seq())
  val edenPool = new Pool { def state() = PoolState(0, 0.bytes, 0.bytes) }
}
