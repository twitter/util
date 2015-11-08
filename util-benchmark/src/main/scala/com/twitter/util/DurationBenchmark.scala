package com.twitter.util

import org.openjdk.jmh.annotations._

// ./sbt 'project util-benchmark' 'run .*DurationBenchmark.*'
@State(Scope.Benchmark)
class DurationBenchmark extends StdBenchAnnotations {

  private[this] val d1 = Duration.fromNanoseconds(1)
  private[this] val d2 = Duration.fromNanoseconds(2)

  @OperationsPerInvocation(7)
  @Benchmark
  def durationEquals: Boolean = {
    d1 == Duration.Top &
      d1 == Duration.Bottom &
      d1 == Duration.Undefined &
      d1 == d2 &
      Duration.Top == Duration.Top &
      Duration.Top == Duration.Bottom &
      Duration.Top == Duration.Undefined
  }

  @Benchmark
  def durationMultiplyLong: Duration = d2 * 123456L

  @Benchmark
  def durationMultiplyLongOverflow: Duration = d2 * Long.MaxValue
}
