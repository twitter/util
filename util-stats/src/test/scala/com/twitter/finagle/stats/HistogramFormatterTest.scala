package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class HistogramFormatterTest extends AnyFunSuite {

  test("Ostrich - formats as expected") {
    assert(HistogramFormatter.Ostrich.labelAverage == "average")
    assert(HistogramFormatter.Ostrich.labelMax == "maximum")
    assert(HistogramFormatter.Ostrich.labelMin == "minimum")
    assert(HistogramFormatter.Ostrich.labelCount == "count")
    assert(HistogramFormatter.Ostrich.labelSum == "sum")
    assert(HistogramFormatter.Ostrich.labelPercentile(0.50) == "p50")
    assert(HistogramFormatter.Ostrich.labelPercentile(0.99) == "p99")
    assert(HistogramFormatter.Ostrich.labelPercentile(0.999) == "p999")
    assert(HistogramFormatter.Ostrich.labelPercentile(0.9999) == "p9999")
  }

  test("CommonMetrics - formats as expected") {
    assert(HistogramFormatter.CommonsMetrics.labelAverage == "avg")
    assert(HistogramFormatter.CommonsMetrics.labelMax == "max")
    assert(HistogramFormatter.CommonsMetrics.labelMin == "min")
    assert(HistogramFormatter.CommonsMetrics.labelCount == "count")
    assert(HistogramFormatter.CommonsMetrics.labelSum == "sum")
    assert(HistogramFormatter.CommonsMetrics.labelPercentile(0.50) == "p50")
    assert(HistogramFormatter.CommonsMetrics.labelPercentile(0.99) == "p99")
    assert(HistogramFormatter.CommonsMetrics.labelPercentile(0.999) == "p9990")
    assert(HistogramFormatter.CommonsMetrics.labelPercentile(0.9999) == "p9999")
  }

  test("CommonStats - formats as expected") {
    assert(HistogramFormatter.CommonsStats.labelAverage == "avg")
    assert(HistogramFormatter.CommonsStats.labelMax == "max")
    assert(HistogramFormatter.CommonsStats.labelMin == "min")
    assert(HistogramFormatter.CommonsStats.labelCount == "count")
    assert(HistogramFormatter.CommonsStats.labelSum == "sum")
    assert(HistogramFormatter.CommonsStats.labelPercentile(0.50) == "50_0_percentile")
    assert(HistogramFormatter.CommonsStats.labelPercentile(0.99) == "99_0_percentile")
    assert(HistogramFormatter.CommonsStats.labelPercentile(0.999) == "99_9_percentile")
    assert(HistogramFormatter.CommonsStats.labelPercentile(0.9999) == "99_99_percentile")
  }

}
