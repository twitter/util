package com.twitter.hashing

import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class HashableTest extends FunSuite with GeneratorDrivenPropertyChecks {

  private[this] val algorithms = Seq(
    Hashable.CRC32_ITU,
    Hashable.FNV1_32,
    Hashable.FNV1_64,
    Hashable.FNV1A_32,
    Hashable.FNV1A_64,
    Hashable.HSIEH,
    Hashable.JENKINS,
    Hashable.MD5_LEInt,
    Hashable.MURMUR3
  )

  def testConsistency[T](algo: Hashable[Array[Byte], T]): Unit = {
    forAll { input: Array[Byte] =>
      assert(algo(input) == algo(input))
    }
  }

  algorithms foreach { algo =>
    test(s"$algo hashing algorithm should be consistent") {
      testConsistency(algo)
    }
  }

  test("MD5_LEInt properly hashes") {
    val h = Hashable.MD5_LEInt
    assert(h(Array[Byte]()) == -645128748)
    assert(h(Array[Byte](0)) == -1383745389)
    assert(h(Array[Byte](1, 2, 3, 4)) == 1522587144)
    assert(h("lunch money".getBytes("UTF-8")) == -2099949960)
  }
}
