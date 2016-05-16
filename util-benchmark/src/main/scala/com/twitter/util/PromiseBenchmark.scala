package com.twitter.util

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class PromiseBenchmark extends StdBenchAnnotations {

  private[this] val StringFuture = Future.value("hi")

  private[this] val Value = Return("okok")

  @Benchmark
  def attached(): Promise[String] = {
    Promise.attached(StringFuture)
  }

  // used to isolate the work in the `updateIfEmpty` benchmark
  @Benchmark
  def newUnsatisfiedPromise(): Promise[String] = {
    new Promise[String]()
  }

  @Benchmark
  def updateIfEmpty(): Boolean = {
    val p = new Promise[String]()
    p.updateIfEmpty(Value)
  }

}
