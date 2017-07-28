package com.twitter.finagle.stats

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlacklistStatsReceiverTest extends FunSuite {
  test("BlacklistStatsReceiver blacklists properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new BlacklistStatsReceiver(inmemory, { case _ => true })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters.isEmpty)
    assert(inmemory.gauges.isEmpty)
    assert(inmemory.stats.isEmpty)
  }

  test("BlacklistStatsReceiver allows through properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new BlacklistStatsReceiver(inmemory, { case _ => false })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "baz"))() == 3.0)
    assert(inmemory.stats == Map(Seq("qux") -> Seq(3.0)))
  }

  test("BlacklistStatsReceiver can go both ways properly") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr = new BlacklistStatsReceiver(inmemory, { case seq => seq.length != 2 })
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("qux")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "baz"))() == 3.0)
    assert(inmemory.stats.isEmpty)
  }

  test("BlacklistStatsReceiver works scoped") {
    val inmemory = new InMemoryStatsReceiver()
    val bsr =
      new BlacklistStatsReceiver(inmemory, { case seq => seq == Seq("foo", "bar") }).scope("foo")
    val ctr = bsr.counter("foo", "bar")
    ctr.incr()
    val gauge = bsr.addGauge("foo", "baz") { 3.0f }
    val stat = bsr.stat("bar")
    stat.add(3)

    assert(inmemory.counters == Map(Seq("foo", "foo", "bar") -> 1))
    assert(inmemory.gauges(Seq("foo", "foo", "baz"))() == 3.0)
    assert(inmemory.stats.isEmpty)
  }

}
