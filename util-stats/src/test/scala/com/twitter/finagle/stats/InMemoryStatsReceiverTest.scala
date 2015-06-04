package com.twitter.finagle.stats

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class InMemoryStatsReceiverTest extends FunSuite
  with Eventually
  with IntegrationPatience {

  test("threadsafe counter") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.counter("same").incr())
    eventually {
      assert(inMemoryStatsReceiver.counter("same").apply === 50)
    }
  }

  test("threadsafe stats") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.stat("same").add(1.0f))
    eventually {
      assert(inMemoryStatsReceiver.stat("same").apply.size === 50)
    }
  }
}
