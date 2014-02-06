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
