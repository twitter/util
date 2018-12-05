package com.twitter.conversions

import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class U64OpsTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import com.twitter.conversions.U64Ops._

  test("toU64HextString") {
    forAll { l: Long =>
      assert(l.toU64HexString == "%016x".format(l))
    }
  }

  test("toU64Long") {
    forAll { l: Long =>
      val s = "%016x".format(l)
      assert(s.toU64Long == java.lang.Long.parseUnsignedLong(s, 16))
    }
  }
}
