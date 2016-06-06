package com.twitter.concurrent

import com.twitter.util.StdBenchAnnotations
import com.twitter.util.Await
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Threads(10)
class AsyncSemaphoreBenchmark extends StdBenchAnnotations {

  private[this] val semaphore: AsyncSemaphore = new AsyncSemaphore(1)

  @Benchmark
  def acquireAndRelease(): Unit = {
    Await.result(semaphore.acquire().map(_.release()))
  }
}
