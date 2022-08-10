package com.twitter.finagle.stats

import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.StdBenchAnnotations
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

// ./bazel run //util/util-benchmark/src/main/scala:jmh -- 'StatBenchmark'
@State(Scope.Benchmark)
class StatBenchmark extends StdBenchAnnotations {

  private[this] val nullStat = NullStatsReceiver.stat("null")

  private[this] val aFuture = Future.value("hello")

  @Benchmark
  def time: String = {
    Stat.time(nullStat, TimeUnit.MILLISECONDS) {
      "hello"
    }
  }

  @Benchmark
  def timeFuture: String = {
    Await.result(Stat.timeFuture(nullStat, TimeUnit.MILLISECONDS) {
      aFuture
    })
  }

}
