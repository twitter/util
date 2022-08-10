package com.twitter.util

import org.openjdk.jmh.annotations._

// ./bazel run //util/util-benchmark/src/main/scala:jmh -- 'TryBenchmark'
@State(Scope.Benchmark)
class TryBenchmark extends StdBenchAnnotations {

  private[this] val retHello = Return("hello")

  private[this] val mapFn: String => Int =
    str => str.length

  private[this] val rescuePf: PartialFunction[Throwable, Try[String]] = {
    case _: IllegalArgumentException => Return("bye")
  }

  @Benchmark
  def returnMap(): Try[Int] =
    retHello.map(mapFn)

  @Benchmark
  def returnRescue(): Try[String] =
    retHello.rescue(rescuePf)

}
