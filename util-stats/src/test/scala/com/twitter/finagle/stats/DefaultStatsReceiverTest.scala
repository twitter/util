package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class DefaultStatsReceiverTest extends AnyFunSuite {
  test("DefaultStatsReceiver initializes dimensional scope and label") {
    LoadedStatsReceiver.self = new InMemoryStatsReceiver
    val fooCounter = DefaultStatsReceiver.counter("foo")
    val identity = fooCounter.metadata.toMetricBuilder.get.identity

    assert(identity.dimensionalName == Seq("foo"))
    assert(identity.hierarchicalName == Seq("foo"))
    assert(identity.labels == Map("implementation" -> "app"))
  }
}
