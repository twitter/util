package com.twitter.finagle.stats

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class InMemoryStatsReceiverTest extends FunSuite
  with Eventually
  with IntegrationPatience {

  test("clear") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    inMemoryStatsReceiver.counter("counter").incr()
    inMemoryStatsReceiver.counter("counter").incr(2)
    assert(inMemoryStatsReceiver.counter("counter")() == 3)

    inMemoryStatsReceiver.stat("stat").add(1.0f)
    inMemoryStatsReceiver.stat("stat").add(2.0f)
    assert(inMemoryStatsReceiver.stat("stat")() == Seq(1.0f, 2.0f))

    inMemoryStatsReceiver.addGauge("gauge") { 1 }
    assert(inMemoryStatsReceiver.gauges.contains(Seq("gauge")))

    inMemoryStatsReceiver.clear()

    assert(!inMemoryStatsReceiver.counters.contains(Seq("counter")))
    assert(!inMemoryStatsReceiver.stats.contains(Seq("stat")))
    assert(!inMemoryStatsReceiver.gauges.contains(Seq("gauge")))
  }

  test("threadsafe counter") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.counter("same").incr())
    eventually {
      assert(inMemoryStatsReceiver.counter("same")() == 50)
    }
  }

  test("threadsafe stats") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.stat("same").add(1.0f))
    eventually {
      assert(inMemoryStatsReceiver.stat("same")().size == 50)
    }
  }

  test("ReadableCounter.toString") {
    val stats = new InMemoryStatsReceiver()
    val c = stats.counter("a", "b")
    assert("Counter(a/b=0)" == c.toString)
    c.incr()
    assert("Counter(a/b=1)" == c.toString)
  }

  test("ReadableGauge.toString") {
    var n = 0
    val stats = new InMemoryStatsReceiver()
    val g = stats.addGauge("a", "b") { n }
    assert("Gauge(a/b=0.0)" == g.toString)

    n = 11
    assert("Gauge(a/b=11.0)" == g.toString)
  }

  test("ReadableStat.toString") {
    val stats = new InMemoryStatsReceiver()
    val s = stats.stat("a", "b")
    assert("Stat(a/b=[])" == s.toString)

    s.add(1)
    assert("Stat(a/b=[1.0])" == s.toString)

    s.add(2)
    s.add(3)
    assert("Stat(a/b=[1.0,2.0,3.0])" == s.toString)

    s.add(4)
    assert("Stat(a/b=[1.0,2.0,3.0... (omitted 1 value(s))])" == s.toString)
  }

  test("histogramDetails when empty") {
    val stats = new InMemoryStatsReceiver()
    assert(stats.histogramDetails == Map.empty)
  }

  test("histogramDetails edges") {
    val stats = new InMemoryStatsReceiver()
    val s1 = stats.stat("a", "b")
    val s2 = stats.stat("a", "c")

    // test counting and edge cases
    s1.add(Int.MaxValue)
    s1.add(0)
    s1.add(Int.MaxValue)
    s1.add(0)

    // test other edges cases
    s2.add(-5)
    s2.add(Long.MaxValue)
    assert(stats.histogramDetails("a/b").counts == 
      Seq(BucketAndCount(0, 1, 2), BucketAndCount(Int.MaxValue - 1, Int.MaxValue, 2)))
    assert(stats.histogramDetails("a/c").counts == 
      Seq(BucketAndCount(0, 1, 1), BucketAndCount(Int.MaxValue - 1, Int.MaxValue, 1)))
  }
}
