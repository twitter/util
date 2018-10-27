package com.twitter.io

import com.twitter.util.{Future, StdBenchAnnotations}
import org.openjdk.jmh.annotations.{Benchmark, Measurement, Scope, State, Warmup}

// sbt 'project util-benchmark' 'jmh:run PipeBenchmark'
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
