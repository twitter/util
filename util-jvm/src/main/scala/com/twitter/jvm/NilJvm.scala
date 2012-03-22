package com.twitter.jvm

object NilJvm extends Jvm {
  val opts: Opts = new Opts {
    def compileThresh = None
  }
  def snapCounters: Map[String, String] = Map()
  def snap: Snapshot = Snapshot(Gc(0, 0, Seq()))
}
