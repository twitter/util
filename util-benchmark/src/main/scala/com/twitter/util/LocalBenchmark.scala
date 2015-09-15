package com.twitter.util

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class LocalBenchmark extends StdBenchAnnotations {

  private[this] val local = new Local[String]()

  @Benchmark
  def let: String =
    local.let("hi") {
      local() match {
        case Some(v) => v
        case None => "bye"
      }
    }

}
