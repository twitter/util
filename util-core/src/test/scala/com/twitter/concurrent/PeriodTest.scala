package com.twitter.concurrent

import com.twitter.conversions.time._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class PeriodTest extends FunSuite {
  test("Period#numPeriods should behave reasonably") {
    val period = new Period(1.second)
    val dur = 1.second
    assert(period.numPeriods(dur) == 1)
  }

  test("Period#numPeriods should be correct for Period#interval > `dur`") {
    val period = new Period(1.second)
    val dur = 1.milliseconds
    assert(math.abs(period.numPeriods(dur)) == 0.001)
  }

  test("Period#numPeriods should be correct for Period#interval < `dur`") {
    val period = new Period(1.second)
    val dur = 2.seconds
    assert(period.numPeriods(dur) == 2)
  }

  test("Period#realInterval should release on MinimumInterval for `interval` == MinimumInterval") {
    val period = new Period(AsyncMeter.MinimumInterval)
    assert(period.realInterval == AsyncMeter.MinimumInterval)
  }

  test("Period#realInterval should release on MinimumInterval for `interval` < MinimumInterval") {
    val period = new Period(AsyncMeter.MinimumInterval / 2)
    assert(period.realInterval == AsyncMeter.MinimumInterval)
  }

  test("Period#realInterval should release on `interval` for `interval` > MinimumInterval") {
    val period = new Period(AsyncMeter.MinimumInterval * 2)
    assert(period.realInterval == AsyncMeter.MinimumInterval * 2)
  }

  test("Period.fromBurstiness should make sensible intervals") {
    assert(Period.fromBurstiness(1, 1.second) == new Period(1.second))
    assert(Period.fromBurstiness(2, 2.seconds) == new Period(1.second))
    assert(Period.fromBurstiness(1, 2.seconds) == new Period(2.seconds))
    assert(Period.fromBurstiness(2, 1.second) == new Period(500.milliseconds))
  }
}
