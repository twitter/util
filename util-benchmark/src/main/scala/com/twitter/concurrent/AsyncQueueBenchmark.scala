package com.twitter.concurrent

import com.twitter.util.{Future, StdBenchAnnotations}
import org.openjdk.jmh.annotations.{Benchmark, Scope, State, Threads}

@State(Scope.Benchmark)
@Threads(10)
class AsyncQueueBenchmark extends StdBenchAnnotations {

  private[this] val q = new AsyncQueue[String]

  @Benchmark
  def offersThenPolls: Future[String] = {
    q.offer("hello")
    q.offer("hello")
    q.offer("hello")
    q.offer("hello")
    q.offer("hello")
    q.poll()
    q.poll()
    q.poll()
    q.poll()
    q.poll()
  }

  @Benchmark
  def alternateOfferThenPoll: Future[String] = {
    q.offer("hello")
    q.poll()
    q.offer("hello")
    q.poll()
    q.offer("hello")
    q.poll()
    q.offer("hello")
    q.poll()
    q.offer("hello")
    q.poll()
  }

  @Benchmark
  def pollsThenOffers: Future[String] = {
    q.poll()
    q.poll()
    q.poll()
    q.poll()
    q.poll()
    val polled = q.poll()
    q.offer("hello")
    q.offer("how")
    q.offer("are")
    q.offer("you")
    q.offer("doing")
    q.offer("today")
    polled
  }

  @Benchmark
  def alternatePollThenOffer: Future[String] = {
    q.poll()
    q.offer("hello")
    q.poll()
    q.offer("how")
    q.poll()
    q.offer("are")
    q.poll()
    q.offer("you")
    q.poll()
    q.offer("doing")
    q.poll()
    q.offer("today")
    q.poll()
  }
}
