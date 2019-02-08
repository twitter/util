package com.twitter.finagle.stats

import org.scalatest.FunSuite

class DenylistStatsReceiverTest extends FunSuite {
  // scalafix:off StoreGaugesAsMemberVariables
  test("DenylistStatsReceiver denylists properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new DenylistStatsReceiver(inmemory, { case _ => true })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters.isEmpty)
    assert(inmemory.gauges.isEmpty)
    assert(inmemory.stats.isEmpty)
  }

  test("DenylistStatsReceiver allows through properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new DenylistStatsReceiver(inmemory, { case _ => false })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "baz"))() == 3.0)
    assert(inmemory.stats == Map(Seq("qux") -> Seq(3.0)))
  }

  test("DenylistStatsReceiver can go both ways properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new DenylistStatsReceiver(inmemory, { case seq => seq.length != 2 })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "baz"))() == 3.0)
    assert(inmemory.stats.isEmpty)
  }

  test("DenylistStatsReceiver works scoped") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr =
      new DenylistStatsReceiver(inmemory, { case seq => seq == Seq("foo", "bar") }).scope("foo")
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("bar")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "foo", "baz"))() == 3.0)
    assert(inmemory.stats.isEmpty)
  }
  // scalafix:on StoreGaugesAsMemberVariables
}
