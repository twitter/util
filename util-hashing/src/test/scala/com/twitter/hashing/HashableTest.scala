package com.twitter.hashing

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class HashableTest extends FunSuite with GeneratorDrivenPropertyChecks {

  private[this] val algorithms = Seq(
    Hashable.CRC32_ITU, Hashable.FNV1_32, Hashable.FNV1_64, Hashable.FNV1A_32,
    Hashable.FNV1A_64, Hashable.HSIEH, Hashable.JENKINS, Hashable.MD5_LEInt
  )

  def testConsistency[T](algo: Hashable[Array[Byte], T]) {
    forAll { input: Array[Byte] =>
      assert(algo(input) === algo(input))
    }
  }

  algorithms foreach { algo =>
    test(s"$algo hashing algorithm should be consistent") {
      testConsistency(algo)
    }
  }
}
