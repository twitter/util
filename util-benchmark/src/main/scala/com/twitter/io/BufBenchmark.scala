package com.twitter.io

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

object BufBenchmark {
  @State(Scope.Benchmark)
  class BufBenchmarkState {
    @Param(Array("1000"))
    var bufSize = 1000

    @Param(Array("1", "5", "10"))
    var sliceSize = 10

    @Param(Array("4", "100"))
    var parts = 4

    @Param(Array("0", "100"))
    var startPositionPercentage = 0

    var buf: Buf = null

    var startIndex = 0
    var endIndex = 0

    @Setup(Level.Trial)
    def setup() {
      buf = (for (i <- 0 until parts) yield Buf.ByteArray.Owned(Array.fill[Byte](bufSize / parts)(i.toByte))).reduce(_ concat _)

      startIndex = (bufSize * (startPositionPercentage.toFloat / 100)).toInt
      endIndex = startIndex + sliceSize

      if (endIndex > bufSize) {
        endIndex = bufSize
        startIndex = math.max(0, endIndex - sliceSize)
      }
    }
  }
}

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class BufBenchmark {
  import BufBenchmark._

  @Benchmark
  def timeSlice(state: BufBenchmarkState) {
    state.buf.slice(state.startIndex, state.endIndex)
  }
}
