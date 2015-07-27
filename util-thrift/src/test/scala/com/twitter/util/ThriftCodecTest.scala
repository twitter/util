package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ThriftCodecTest extends FunSuite {

  private def roundTrip(codec: ThriftCodec[TestThriftStructure, _]): Unit = {
    val struct = new TestThriftStructure("aString", 5)
    val encoded: Array[Byte] = codec.encode(struct)
    val decoded: TestThriftStructure = codec.decode(encoded)

    assert(decoded === struct)
  }

  test("BinaryThriftCodec") {
    val codec = new BinaryThriftCodec[TestThriftStructure]()
    roundTrip(codec)
  }

  test("CompactThriftCodec") {
    val codec = new CompactThriftCodec[TestThriftStructure]()
    roundTrip(codec)
  }

}
