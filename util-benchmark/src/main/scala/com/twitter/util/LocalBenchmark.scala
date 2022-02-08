package com.twitter.util

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@Threads(1)
class LocalBenchmark extends StdBenchAnnotations {
  // We've sampled some service instances and get the result that usually
  // 10 Locals are constructed and 3 Locals are set.
  val size = 10
  private[this] val locals = Array.fill(size)(new Local[String])
  private[this] val getter = locals(1).threadLocalGetter()

  @Benchmark
  def let1(): String =
    locals(0).let("foo") { "bar" }

  @Benchmark
  def let2(): String =
    locals(0).let("foo") {
      locals(1).let("foo") { "bar" }
    }

  @Benchmark
  def let3(): String =
    locals(0).let("foo") {
      locals(1).let("foo") {
        locals(2).let("foo") { "bar" }
      }
    }

  @Setup
  def prepare(): Unit = {
    locals(0).update("foo")
    locals(1).update("foo")
    locals(2).update("foo")
  }

  @TearDown
  def clear(): Unit = {
    Local.clear()
  }

  @Benchmark
  def get(): Option[String] = locals(1)()

  @Benchmark
  def getThreadLocal(): Option[String] = getter()
}
