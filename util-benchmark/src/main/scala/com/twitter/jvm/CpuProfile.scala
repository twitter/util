package com.twitter.jvm

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.util.Random
import java.lang.management.ThreadMXBean

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class CpuProfileBenchmark {
  import CpuProfileBenchmark._

  @Benchmark
  def timeThreadGetStackTraces(state: ThreadState): Unit = {
    Thread.getAllStackTraces()
  }

  @Benchmark
  def timeThreadInfoBare(state: ThreadState): Unit = {
    state.bean.dumpAllThreads(false, false)
  }

  // Note: we should actually simulate some contention, too.
  @Benchmark
  def timeThreadInfoFull(state: ThreadState): Unit = {
    state.bean.dumpAllThreads(true, true)
  }
}

object CpuProfileBenchmark {
  @State(Scope.Benchmark)
  class ThreadState {
    val bean: ThreadMXBean = ManagementFactory.getThreadMXBean()

    // TODO: change dynamically.  bounce up & down
    // the stack.  μ and σ are for *this* stack.
    class Stack(rng: Random, μ: Int, σ: Int) {
      def apply(): Int = {
        val depth = math.max(1, μ + (rng.nextGaussian * σ).toInt)
        dive(depth)
      }

      private def dive(depth: Int): Int = {
        if (depth == 0) {
          while (true) {
            Thread.sleep(10 << 20)
          }
          1
        } else
          1 + dive(depth - 1) // make sure we don't tail recurse
      }
    }

    val stackMeanSize = 40
    val stackStddev = 10
    val nthreads = 16
    val rngSeed = 131451732492626L

    @Setup(Level.Iteration)
    def setUp(): Unit = {
      val stack = new Stack(new Random(rngSeed), stackMeanSize, stackStddev)
      threads = for (_ <- 0 until nthreads)
        yield
          new Thread {
            override def run(): Unit = {
              try stack()
              catch {
                case _: InterruptedException =>
              }
            }
          }

      threads foreach { t =>
        t.start()
      }
    }

    @TearDown(Level.Iteration)
    def tearDown(): Unit = {
      threads foreach { t =>
        t.interrupt()
      }
      threads foreach { t =>
        t.join()
      }
      threads = Seq()
    }
    var threads = Seq[Thread]()
  }
}
