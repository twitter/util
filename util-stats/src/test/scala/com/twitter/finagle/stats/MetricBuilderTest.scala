package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.CounterType
import org.scalatest.funsuite.AnyFunSuite

class MetricBuilderTest extends AnyFunSuite {

  test("metadataScopeSeparator affects stringification") {
    val mb = MetricBuilder(
      name = Seq("foo", "bar"),
      metricType = CounterType,
      statsReceiver = new InMemoryStatsReceiver)
    assert(mb.toString.contains("foo/bar"))
    metadataScopeSeparator.setSeparator("-")
    assert(mb.toString.contains("foo-bar"))
    metadataScopeSeparator.setSeparator("_aTerriblyLongStringForSomeReason_")
    assert(mb.toString.contains("foo_aTerriblyLongStringForSomeReason_bar"))
  }
}
