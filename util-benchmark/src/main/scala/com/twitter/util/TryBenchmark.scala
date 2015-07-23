package com.twitter.util

import org.openjdk.jmh.annotations._

// ./sbt 'project util-benchmark' 'run .*TryBenchmark.*'
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
