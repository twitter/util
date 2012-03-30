package com.twitter.hashing

import org.specs.SpecificationWithJUnit
import scala.collection.mutable.ListBuffer
import org.apache.commons.codec.binary.Base64

class KeyHasherSpec extends SpecificationWithJUnit {
  def readResource(name: String) = {
    var lines = new ListBuffer[String]()
    val src = scala.io.Source.fromFile(getClass.getResource("/" + name).getPath)
    src.getLines
  }

  val base64 = new Base64()
  def decode(str: String) = base64.decode(str)

  def testHasher(name: String, hasher: KeyHasher) {
    val sources = readResource(name + "_source") map { decode(_) }
    val hashes = readResource(name + "_hashes")
    sources.size must beGreaterThan(0)

    sources zip hashes foreach { case (source, hashAsString) =>
      val hash = BigInt(hashAsString).toLong
      hasher.hashKey(source) mustEqual hash
    }
  }

  "KeyHasher" should {
    "correctly hash fnv1_32" in {
      testHasher("fnv1_32", KeyHasher.FNV1_32)
    }

    "correctly hash fnv1_64" in {
      testHasher("fnv1_64", KeyHasher.FNV1_64)
    }

    "correctly hash fnv1a_32" in {
      testHasher("fnv1a_32", KeyHasher.FNV1A_32)
    }

    "correctly hash fnv1a_64" in {
      testHasher("fnv1a_64", KeyHasher.FNV1A_64)
    }

    "correctly hash jenkins" in {
      testHasher("jenkins", KeyHasher.JENKINS)
    }

    "correctly hash crc32 itu" in {
      testHasher("crc32", KeyHasher.CRC32_ITU)
    }

  }
}
