package com.twitter.util

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class PromiseBenchmark extends StdBenchAnnotations {

  private[this] val StringFuture = Future.value("hi")

  @Benchmark
  def attached(): Promise[String] = {
    Promise.attached(StringFuture)
  }

}
