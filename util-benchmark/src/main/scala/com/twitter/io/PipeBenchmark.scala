package com.twitter.io

import com.twitter.util.Future
import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

// ./bazel run //util/util-benchmark/src/main/scala:jmh -- 'PipeBenchmark'
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
class PipeBenchmark extends StdBenchAnnotations {

  private val foo = Buf.Utf8("foo")

  @Benchmark
  def writeAndRead(): Future[Option[Buf]] = {
    val p = new Pipe[Buf]
    p.write(foo)
    p.read()
  }

  @Benchmark
  def readAndWrite(): Future[Unit] = {
    val p = new Pipe[Buf]
    p.read()
    p.write(foo)
  }
}
