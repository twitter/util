package com.twitter.util.benchmark

import java.lang.management.ManagementFactory

import scala.util.Random

import com.google.caliper.SimpleBenchmark

class CpuProfileBenchmark extends SimpleBenchmark {

  // TODO: change dynamically.  bounce up & down
  // the stack.  μ and σ are for *this* stack.
  class Stack(rng: Random, μ: Int, σ: Int) {
    def apply() = {
      val depth = math.max(1, μ + (rng.nextGaussian*σ).toInt)
      dive(depth)
    }

    private def dive(depth: Int): Int = {
      if (depth == 0) {
        while (true) {
          Thread.sleep(10<<20)
        }
        1
      } else
        1+dive(depth-1)  // make sure we don't tail recurse
    }
  }

  var stackMeanSize = 40
  var stackStddev = 10
  var nthreads = 16
  var threads = Seq[Thread]()
  var rngSeed = 131451732492626L

  override protected def setUp() {
    val stack = new Stack(new Random(rngSeed), stackMeanSize, stackStddev)
    threads = for (_ <- 0 until nthreads) yield new Thread {
      override def run() {
        try stack() catch {
          case _: InterruptedException =>
        }
      }
    }

    threads foreach { t => t.start() }
  }

  override protected def tearDown() {
    threads foreach { t => t.interrupt() }
    threads foreach { t => t.join() }
    threads = Seq()
  }

  def timeThreadGetStackTraces(nreps: Int) {
    var i = 0
    while (i < nreps) {
      Thread.getAllStackTraces()
      i += 1
    }
  }

  def timeThreadInfoBare(nreps: Int) {
    val bean = ManagementFactory.getThreadMXBean()
    var i = 0
    while (i < nreps) {
      bean.dumpAllThreads(false, false)
      i += 1
    }
  }

  // Note: we should actually simulate some contention, too.
  def timeThreadInfoFull(nreps: Int) {
    val bean = ManagementFactory.getThreadMXBean()
    var i = 0
    while (i < nreps) {
      bean.dumpAllThreads(true, true)
      i += 1
    }
  }

}
