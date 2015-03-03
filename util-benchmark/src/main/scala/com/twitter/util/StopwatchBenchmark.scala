package com.twitter.util

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class StopwatchBenchmark {
  import StopwatchBenchmark._

  @Benchmark
  def timeMakeCallback(): () => Duration = {
    Stopwatch.start()
  }

  @Benchmark
  def timeTime(state: StopwatchState): Duration = {
    state.elapsed()
  }
}

object StopwatchBenchmark {
  @State(Scope.Benchmark)
  class StopwatchState {
    val elapsed = Stopwatch.start()
  }
}
