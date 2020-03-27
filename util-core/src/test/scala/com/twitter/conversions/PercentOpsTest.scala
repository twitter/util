package com.twitter.conversions

import com.twitter.conversions.PercentOps._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class PercentOpsTest extends FunSuite with ScalaCheckDrivenPropertyChecks {

  private[this] val Precision = 0.0000000001

  private[this] def doubleEq(d1: Double, d2: Double): Boolean = {
    Math.abs(d1 - d2) <= Precision
  }

  test("percent can be fractional and precision is preserved") {
    assert(99.9.percent == 0.999)
    assert(99.99.percent == 0.9999)
    assert(99.999.percent == 0.99999)
    assert(12.3456.percent == 0.123456)
  }

  test("percent can be > 100") {
    assert(101.percent == 1.01)
    assert(500.percent == 5.0)
  }

  test("percent can be < 0") {
    assert(-0.1.percent == -0.001)
    assert(-500.percent == -5.0)
  }

  test("assorted percentages") {
    forAll(arbitrary[Int]) { i => assert(new RichPercent(i).percent == i / 100.0) }

    // We're not as accurate when we get into high double-digit exponents, but that's acceptable.
    forAll(Gen.choose(-10000d, 10000d)) { d =>
      assert(doubleEq(new RichPercent(d).percent, d / 100.0))
    }
  }

  test("doesn't blow up on edge cases") {
    assert(Double.NaN.percent.equals(Double.NaN))
    assert(Double.NegativeInfinity.percent.equals(Double.NegativeInfinity))
    assert(Double.PositiveInfinity.percent.equals(Double.PositiveInfinity))
  }
}
