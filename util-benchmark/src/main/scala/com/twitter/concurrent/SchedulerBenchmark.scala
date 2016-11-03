package com.twitter.concurrent

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

// Run this via:
// ./sbt 'project util-benchmark' 'jmh:run SchedulerBenchmark -i 10 -wi 5 -f 1 -t 8 -bm avgt -tu ns'
//
// Notes:
//  - threads, -t, should be 2x number of logical cores.
//  - to run a specific benchmark, use: SchedulerBenchmark.timeSubmit
//  - to send specific params, use: -p lifo=false -p m=1,8,16
/**
 * Measure Scheduler.submit/run time when there are multiple threads.  Previous
 * to changing SampleScale from 50 to 1000, there was sometimes contention for
 * the global threads lock in getCurrentThreadCpuTime().
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class SchedulerBenchmark {
  import SchedulerBenchmark._

  @Benchmark
  def timeSubmit(state: SchedulerState): Unit = {
    state.scheduler.submit(state.cpuRunnable)
    state.scheduler.flush()
  }

  @Benchmark
  def timeLocalQueue(state: SchedulerState): Unit = {
    state.scheduler.submit(state.runnable)
    state.scheduler.flush()
  }

}

object SchedulerBenchmark {

  @State(Scope.Benchmark)
  class SchedulerState {
    @Param(Array("1", "2", "4", "8", "16", "32"))
    var m: Int = 0

    @Param(Array("false"))
    var lifo: Boolean = false

    var cpuRunnable: Runnable = null

    var scheduler: LocalScheduler = _

    var runnable: Runnable = null

    @Setup(Level.Trial)
    def setup(): Unit = {
      scheduler = new LocalScheduler(lifo)
      cpuRunnable = new Runnable {
        def run(): Unit = Blackhole.consumeCPU(1)
      }
      runnable = new Runnable {
        def run(): Unit = {
          var j = 0
          while (j < m) {
            scheduler.submit(cpuRunnable)
            j += 1
          }
        }
      }
    }
  }

}
