package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class MetricBuilderTest extends AnyFunSuite {

  test("metadataScopeSeparator affects stringification") {
    val mb = new MetricBuilder(name = Seq("foo", "bar"), statsReceiver = new InMemoryStatsReceiver)
    assert(mb.toString.contains("foo/bar"))
    metadataScopeSeparator.setSeparator("-")
    assert(mb.toString.contains("foo-bar"))
    metadataScopeSeparator.setSeparator("_aTerriblyLongStringForSomeReason_")
    assert(mb.toString.contains("foo_aTerriblyLongStringForSomeReason_bar"))
  }

}
