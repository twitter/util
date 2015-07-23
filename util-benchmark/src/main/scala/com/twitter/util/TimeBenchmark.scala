package com.twitter.util

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

// ./sbt 'project util-benchmark' 'run .*TimeBenchmark.*'
@State(Scope.Benchmark)
class TimeBenchmark extends StdBenchAnnotations {

  private[this] val time1 = Time.fromNanoseconds(1)
  private[this] val time2 = Time.fromNanoseconds(2)

  @OperationsPerInvocation(7)
  @Benchmark
  def timeEquals: Boolean = {
    time1 == Time.Top &
      time1 == Time.Bottom &
      time1 == Time.Undefined &
      time1 == time2 &
      Time.Top == Time.Top &
      Time.Top == Time.Bottom &
      Time.Top == Time.Undefined
  }

  @Benchmark
  def timeHashCode: Int =
    time1.hashCode

}
