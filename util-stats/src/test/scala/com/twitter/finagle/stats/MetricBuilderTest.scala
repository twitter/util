package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class MetricBuilderTest extends AnyFunSuite {

  test("metadataScopeSeparator affects stringification") {
    val mb = MetricBuilder(name = Seq("foo", "bar"), statsReceiver = new InMemoryStatsReceiver)
    assert(mb.toString.contains("foo/bar"))
    metadataScopeSeparator.setSeparator("-")
    assert(mb.toString.contains("foo-bar"))
    metadataScopeSeparator.setSeparator("_aTerriblyLongStringForSomeReason_")
    assert(mb.toString.contains("foo_aTerriblyLongStringForSomeReason_bar"))
  }

  test("kernels are object reference hashCode") {
    val sr = new InMemoryStatsReceiver
    val mb1 = MetricBuilder(name = Seq("a"), statsReceiver = sr).withKernel
    val mb2 = MetricBuilder(name = Seq("a"), statsReceiver = sr).withKernel
    assert(mb2.kernel != mb1.kernel)
  }

  test("kernels remain the same after copy") {
    val mb1 = MetricBuilder(name = Seq("a"), statsReceiver = new InMemoryStatsReceiver).withKernel
    val mb2 = mb1.withDescription("I am a description").withName("a", "b")
    assert(mb1.kernel == mb2.kernel)
  }
}
