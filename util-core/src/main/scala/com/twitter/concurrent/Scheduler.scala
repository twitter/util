package com.twitter.concurrent

import java.util.ArrayDeque
import management.ManagementFactory
import scala.util.Random


trait Scheduler {
  /**
   * Schedule `r` to be run at some time in the future.
   */
  def submit(r: Runnable)

  /**
   * Flush the schedule. Returns when there is no more
   * work to do.
   */
  def flush()
  
  // A note on Hotspot's ThreadMXBean's CPU time. On Linux, this
  // uses clock_gettime[1] which should both be fast and accurate.
  //
  // On OSX, the Mach thread_info call is used.
  //
  // [1] http://linux.die.net/man/3/clock_gettime

  /** The amount of User time that's been scheduled as per ThreadMXBean. */
  def usrTime: Long
  
  /** The amount of CPU time that's been scheduled as per ThreadMXBean */
  def cpuTime: Long
  
  /** Number of dispatches performed by this scheduler. */
  def numDispatches: Long
}

/**
 * An efficient thread-local continuation scheduler.
 */
object Scheduler extends Scheduler {
  private val SampleScale = 50

  private[this] val bean = ManagementFactory.getThreadMXBean()

  @volatile var schedulers = Set[Scheduler]()

  private val local = new ThreadLocal[Scheduler] {
    override def initialValue = null
  }
  
  def apply(): Scheduler = {
    val s = local.get()
    if (s != null)
      return s
    
    local.set(new LocalScheduler)
    synchronized { schedulers += local.get() }
    local.get()
  }

  def submit(r: Runnable) = Scheduler().submit(r)
  def flush() = Scheduler().flush()
  def usrTime = Scheduler().usrTime
  def cpuTime = Scheduler().cpuTime
  def numDispatches = Scheduler().numDispatches

  private class LocalScheduler extends Scheduler {
    private[this] var r0, r1, r2: Runnable = null
    private[this] val rs = new ArrayDeque[Runnable]
    private[this] var running = false
    private[this] val rng = new Random

    // This is safe: there's only one updater.
    @volatile var usrTime = 0L
    @volatile var cpuTime = 0L
    @volatile var numDispatches = 0L

    def submit(r: Runnable) {
      assert(r != null)
      if (r0 == null) r0 = r
      else if (r1 == null) r1 = r
      else if (r2 == null) r2 = r
      else rs.addLast(r)
      if (!running) {
        if (rng.nextInt(SampleScale) == 0) {
          numDispatches += SampleScale
          val cpu0 = bean.getCurrentThreadCpuTime()
          val usr0 = bean.getCurrentThreadUserTime()
          run()
          cpuTime += (bean.getCurrentThreadCpuTime() - cpu0)*SampleScale
          usrTime += (bean.getCurrentThreadUserTime() - usr0)*SampleScale
        } else {
          run()
        }
      }
    }

    def flush() {
      if (running) run()
    }

    private[this] def run() {
      val save = running
      running = true
      // via moderately silly benchmarking, the
      // queue unrolling gives us a ~50% speedup
      // over pure Queue usage for common
      // situations.
      try {
        while (r0 != null) {
          val r = r0
          r0 = r1
          r1 = r2
          r2 = if (rs.isEmpty) null else rs.removeFirst()
          r.run()
        }
      } finally {
        running = save
      }
    }
  }
}
