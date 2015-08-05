package com.twitter.concurrent

import com.twitter.util.{Future, StdBenchAnnotations}
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

@State(Scope.Benchmark)
class AsyncQueueBenchmark extends StdBenchAnnotations {

  private[this] val q = new AsyncQueue[String]

  @Benchmark
  def offerThenPoll: Future[String] = {
    q.offer("hello")
    q.poll()
  }

  @Benchmark
  def pollThenOffer: Future[String] = {
    val polled = q.poll()
    q.offer("hello")
    polled
  }

}
