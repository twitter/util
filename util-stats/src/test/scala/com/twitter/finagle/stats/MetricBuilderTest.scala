package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.CounterishGaugeType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
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

  test("the .gauge() api builds CounterishGauge vs Gauge where appropriate") {
    val mb = MetricBuilder(
      name = Seq("c_gauge"),
      metricType = GaugeType,
      statsReceiver = new InMemoryStatsReceiver
    ).withCounterishGauge

    assert(mb.metricType == CounterishGaugeType)

    val g = mb.gauge("c_gauge") { 1.0.toFloat }

    assert(g.metadata.toMetricBuilder.get.metricType == CounterishGaugeType)
  }
}
