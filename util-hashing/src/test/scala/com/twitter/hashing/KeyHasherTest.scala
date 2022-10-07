package com.twitter.hashing

import com.twitter.io.TempFile
import java.util.Base64
import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets.UTF_8
import org.scalatest.funsuite.AnyFunSuite

class KeyHasherTest extends AnyFunSuite {
  def readResource(name: String) = {
    var lines = new ListBuffer[String]()
    val src = scala.io.Source.fromFile(TempFile.fromResourcePath(getClass, "/" + name))
    src.getLines()
  }

  val base64 = Base64.getDecoder
  def decode(str: String) = base64.decode(str.getBytes(UTF_8))

  def testHasher(name: String, hasher: KeyHasher) = {
    val sources = readResource(name + "_source") map { decode(_) }
    val hashes = readResource(name + "_hashes")
    assert(sources.size > 0)

    sources zip hashes foreach {
      case (source, hashAsString) =>
        val hash = BigInt(hashAsString).toLong
        assert(hasher.hashKey(source) == hash)
    }
  }

  test("KeyHasher should correctly hash fnv1_32") {
    testHasher("fnv1_32", KeyHasher.FNV1_32)
  }

  test("KeyHasher should correctly hash fnv1_64") {
    testHasher("fnv1_64", KeyHasher.FNV1_64)
  }

  test("KeyHasher should correctly hash fnv1a_32") {
    testHasher("fnv1a_32", KeyHasher.FNV1A_32)
  }

  test("KeyHasher should correctly hash fnv1a_64") {
    testHasher("fnv1a_64", KeyHasher.FNV1A_64)
  }

  test("KeyHasher should correctly hash jenkins") {
    testHasher("jenkins", KeyHasher.JENKINS)
  }

  test("KeyHasher should correctly hash crc32 itu") {
    testHasher("crc32", KeyHasher.CRC32_ITU)
  }
}
