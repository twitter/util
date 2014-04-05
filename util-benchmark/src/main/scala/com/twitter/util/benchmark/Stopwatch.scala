package com.twitter.util.benchmark

import com.google.caliper.SimpleBenchmark
import com.twitter.util.Stopwatch

class StopwatchBenchmark extends SimpleBenchmark {
  // Multiplier required to do enough work
  // to be able to measure anything at all.
  val N = 100

  def timeMakeCallback(reps: Int) {
    var i = 0
    while (i < reps) {
      Stopwatch.start()
      i += 1
    }
  }

  def timeTime(reps: Int) {
    var i = 0
    while (i < reps) {
      Stopwatch.start()()
      i += 1
    }
  }
}
