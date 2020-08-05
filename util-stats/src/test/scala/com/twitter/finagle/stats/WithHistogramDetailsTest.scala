package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class WithHistogramDetailsTest extends AnyFunSuite {
  test("AggregateWithHistogramDetails rejects empties") {
    intercept[IllegalArgumentException] {
      AggregateWithHistogramDetails(Nil)
    }
  }

  test("AggregateWithHistogramDetails makes passing one arg a nop") {
    val details = new InMemoryStatsReceiver()
    assert(AggregateWithHistogramDetails(Seq(details)) == details)
  }

  test("AggregateWithHistogramDetails merges stats") {
    val details1 = new InMemoryStatsReceiver()
    details1.stat("foo").add(10)
    val details2 = new InMemoryStatsReceiver()
    details2.stat("bar").add(15)
    val details = AggregateWithHistogramDetails(Seq(details1, details2))
    val map = details.histogramDetails
    assert(map("foo").counts == Seq(BucketAndCount(10, 11, 1)))
    assert(map("bar").counts == Seq(BucketAndCount(15, 16, 1)))
    assert(map.size == 2)
  }

  test("AggregateWithHistogramDetails prefers first keys when deduplicating") {
    val details1 = new InMemoryStatsReceiver()
    details1.stat("foo").add(10)
    val details2 = new InMemoryStatsReceiver()
    details2.stat("foo").add(15)
    val details = AggregateWithHistogramDetails(Seq(details1, details2))
    val map = details.histogramDetails
    assert(map("foo").counts == Seq(BucketAndCount(10, 11, 1)))
    assert(map.size == 1)
  }
}
