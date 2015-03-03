package com.twitter.util.events

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class SinkBenchmark {
  import SinkBenchmark._

  @Benchmark
  def timeEventSizedSink(state: SinkState): Unit = {
    import state._
    sizedSink.event(eventType, doubleVal = 2.5d)
  }
}

object SinkBenchmark {
  @State(Scope.Benchmark)
  class SinkState {
    val sizedSink = SizedSink(10000)
    val eventType = Event.nullType
  }
}
