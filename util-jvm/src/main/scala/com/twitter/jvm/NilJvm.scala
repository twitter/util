package com.twitter.jvm

import com.twitter.util.Time

object NilJvm extends Jvm {
  val opts: Opts = new Opts {
    def compileThresh = None
  }
  def snapCounters: Map[String, String] = Map()
  def snap: Snapshot = Snapshot(
    Time.epoch,
    Heap(0, 0, Seq()),
    Seq())
}
