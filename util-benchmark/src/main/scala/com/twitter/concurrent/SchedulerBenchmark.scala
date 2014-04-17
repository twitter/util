package com.twitter.concurrent

import com.google.caliper.SimpleBenchmark
import java.util.concurrent.atomic.AtomicLong

/**
 * Measure Scheduler.submit/run time when there are multiple threads.  Previous
 * to changing SampleScale from 50 to 1000, there was sometimes contention for
 * the global threads lock in getCurrentThreadCpuTime().
 */
class SchedulerBenchmark extends SimpleBenchmark {
  val NumThreads = 48
 
  private def go(scheduler: Scheduler, n: Int, m: Int) {
    val nop = new Runnable { def run() {} }
    var i = 0
    while (i < n/m) {
      scheduler.submit(new Runnable {
        override def run() {
          var j = 0
          while (j < m) {
            scheduler.submit(nop)
            j += 1
          }
        }
      })
      i += 1
    }

    scheduler.flush()
  }
  
  def timeLocalFifo1(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }

  def timeLocalFifo2(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }

  def timeLocalFifo3(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }
  
  def timeLocalFifo4(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }

  def timeLocalFifo8(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }
  
  def timeLocalFifo16(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }

  def timeLocalFifo32(nreps: Int) {
    go(new LocalScheduler, nreps, 1)
  }

  def timeLocalLifo1(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }

  def timeLocalLifo2(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }

  def timeLocalLifo3(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }
  
  def timeLocalLifo4(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }

  def timeLocalLifo8(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }
  
  def timeLocalLifo16(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }

  def timeLocalLifo32(nreps: Int) {
    go(new LocalScheduler(true), nreps, 1)
  }

  def timeSchedule(nreps: Int) {
    val submissions = new AtomicLong(nreps)
    val executions = new AtomicLong

    val runnable = new Runnable {
      def run() {
        executions.incrementAndGet()
      }
    }

    val threads = for (_ <- 0.until(NumThreads)) yield {
      new Thread {
        override def run() {
          while (submissions.getAndDecrement() > 0) {
            Scheduler.submit(runnable)
          }
        }
      }
    }

    for (thread <- threads)
      thread.start()
    for (thread <- threads)
      thread.join(30000)

    Scheduler.flush()

    assert(executions.get() == nreps)
  }
}
