package com.twitter.jvm

import org.scalatest.FunSuite

class EstimatorTest extends FunSuite {
  test("LoadAverage") {
    // This makes LoadAverage.a = 1/2 for easy testing.
    val interval = -1D / math.log(0.5)
    val e = new LoadAverage(interval)
    assert(e.estimate.isNaN)

    e.measure(0)
    assert(e.estimate == 0)
    e.measure(1)
    assert(e.estimate == 0.5)
    e.measure(1)
    assert(e.estimate == 0.75)
    e.measure(-0.75)
    assert(e.estimate == 0)
  }
}
