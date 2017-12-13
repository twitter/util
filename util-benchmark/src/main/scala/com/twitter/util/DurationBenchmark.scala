package com.twitter.util

import org.openjdk.jmh.annotations._

// ./sbt 'project util-benchmark' 'jmh:run DurationBenchmark'
@State(Scope.Benchmark)
class DurationBenchmark extends StdBenchAnnotations {

  private[this] val d1 = Duration.fromNanoseconds(1)
  private[this] val d2 = Duration.fromNanoseconds(2)
  private[this] val d3 = Duration.fromNanoseconds(1234567890L)
  private[this] val d4 = Duration.fromNanoseconds(9876543210L)
  private[this] val d5 = Duration.fromNanoseconds(Long.MaxValue - 10)

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
  def durationMultiplyLong: Duration = d3 * 123456L

  @Benchmark
  def durationMultiplyLongOverflow: Duration = d3 * Long.MaxValue

  @Benchmark
  def durationAddDelta: Duration = d3 + d4

  @Benchmark
  def durationAddDeltaOverflow: Duration = d3 + d5

  @Benchmark
  def durationMod: Duration = d4 % d3

  @Benchmark
  def durationFloor: Duration = d4.floor(d3)
}
