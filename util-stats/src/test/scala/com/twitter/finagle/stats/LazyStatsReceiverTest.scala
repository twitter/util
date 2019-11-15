package com.twitter.finagle.stats

import org.scalatest.FunSuite

class LazyStatsReceiverTest extends FunSuite {
  test("Doesn't eagerly initialize counters") {
    val underlying = new InMemoryStatsReceiver()
    val wrapped = new LazyStatsReceiver(underlying)
    val counter = wrapped.counter("foo")
    assert(underlying.counters.isEmpty)
    counter.incr()
    assert(underlying.counters == Map(Seq("foo") -> 1))
  }

  test("Doesn't eagerly initialize histograms") {
    val underlying = new InMemoryStatsReceiver()
    val wrapped = new LazyStatsReceiver(underlying)
    val histo = wrapped.stat("foo")
    assert(underlying.stats.isEmpty)
    histo.add(5)
    assert(underlying.stats == Map(Seq("foo") -> Seq(5)))
  }
}
