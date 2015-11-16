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
}
