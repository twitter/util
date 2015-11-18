package com.twitter.util

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

// ./sbt 'project util-benchmark' 'run .*TimeBenchmark.*'
@State(Scope.Benchmark)
class TimeBenchmark extends StdBenchAnnotations {

  private[this] val t1 = Time.fromNanoseconds(1)
  private[this] val t2 = Time.fromNanoseconds(2)
  private[this] val t3 = Time.fromNanoseconds(1234567890L)
  private[this] val t4 = Time.fromNanoseconds(9876543210L)
  private[this] val t5 = Time.fromNanoseconds(Long.MinValue + 10)

  @OperationsPerInvocation(7)
  @Benchmark
  def timeEquals: Boolean = {
    t1 == Time.Top &
      t1 == Time.Bottom &
      t1 == Time.Undefined &
      t1 == t2 &
      Time.Top == Time.Top &
      Time.Top == Time.Bottom &
      Time.Top == Time.Undefined
  }

  @Benchmark
  def timeHashCode: Int = t1.hashCode

  @Benchmark
  def timeDiff: Duration = t4.diff(t3)

  @Benchmark
  def timeDiffOverflow: Duration = t5.diff(t4)
}
