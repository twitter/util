package com.twitter.concurrent

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.SampleTime))
class OnceBenchmark {
  import OnceBenchmark._

  @Benchmark
  def timeApply(state: OnceState): Unit = {
    state.once()
  }
}

object OnceBenchmark {
  @State(Scope.Benchmark)
  class OnceState {
    val once: () => Unit = Once(())
  }
}
