package com.twitter.concurrent

import java.util.ArrayDeque

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
}

/**
 * An efficient thread-local continuation scheduler.
 */
object Scheduler extends Scheduler {
  private val local = new ThreadLocal[Scheduler] {
    override def initialValue = new LocalScheduler
  }

  def submit(r: Runnable) = local.get().submit(r)
  def flush() = local.get().flush()

  private class LocalScheduler extends Scheduler {
    private[this] var r0, r1, r2: Runnable = null
    private[this] val rs = new ArrayDeque[Runnable]
    private[this] var running = false

    def submit(r: Runnable) {
      assert(r != null)
      if (r0 == null) r0 = r
      else if (r1 == null) r1 = r
      else if (r2 == null) r2 = r
      else rs.addLast(r)
      if (!running) run()
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
