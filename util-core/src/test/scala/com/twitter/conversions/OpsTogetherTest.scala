package com.twitter.conversions

import com.twitter.util.{Duration, StorageUnit}
import org.scalatest.funsuite.AnyFunSuite

class OpsTogetherTest extends AnyFunSuite {
  test("multiple wildcard imports") {
    import com.twitter.conversions.DurationOps._
    import com.twitter.conversions.PercentOps._
    import com.twitter.conversions.StorageUnitOps._

    assert(5.percent == 0.05)
    assert(1.seconds == Duration.fromSeconds(1))
    assert(1.byte == StorageUnit.fromBytes(1))
  }
}
