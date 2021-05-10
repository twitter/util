package com.twitter.concurrent

import com.twitter.util.StdBenchAnnotations
import com.twitter.util.Await
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Threads(10)
class AsyncSemaphoreBenchmark extends StdBenchAnnotations {

  private[this] val noWaitersSemaphore: AsyncSemaphore = new AsyncSemaphore(10)
  private[this] val waitersSemaphore: AsyncSemaphore = new AsyncSemaphore(1)
  private[this] val mixedSemaphore: AsyncSemaphore = new AsyncSemaphore(5)

  @Benchmark
  def noWaiters(): Unit = {
    Await.result(noWaitersSemaphore.acquire().map(_.release()))
  }

  @Benchmark
  def waiters(): Unit = {
    Await.result(waitersSemaphore.acquire().map(_.release()))
  }

  @Benchmark
  def mixed(): Unit = {
    Await.result(mixedSemaphore.acquire().map(_.release()))
  }
}
