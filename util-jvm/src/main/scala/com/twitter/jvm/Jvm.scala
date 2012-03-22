package com.twitter.jvm

import java.lang.management.ManagementFactory

/**
 * Access JVM internal performance counters. We maintain a strict
 * interface so that we are decoupled from the actual underlying JVM.
 */
trait Jvm {
  trait Opts {
    def compileThresh: Option[Int]
  }

  /**
   * Current VM-specific options.
   */
  val opts: Opts

  /**
   * Get a snapshot of all performance counters.
   */
  def snapCounters: Map[String, String]

  case class Gc(
    // Number of bytes allocated so far.
    allocated: Long,
    // Tenuring threshold: How many times an
    // object needs to be copied before being
    // tenured.
    tenuringThreshold: Long,
    // Histogram of the number of bytes that have
    // been copied as many times. Note: 0-indexed.
    ageHisto: Seq[Long]
  )

  case class Snapshot(
    gc: Gc
  )

  /**
   * Snapshot of JVM state.
   */
  def snap: Snapshot
}

object Jvm {
  private[this] lazy val _jvm = {
    val name = ManagementFactory.getRuntimeMXBean.getVmName
    // Is there a better way to detect HotSpot?
    //
    // TODO: also check that we can _actually_ create a Hotspot
    // instance without exceptions
    if (name startsWith "Java HotSpot(TM)")
      Some(new Hotspot: Jvm)
    else
      None
  } getOrElse NilJvm

  def apply(): Jvm = _jvm
}
