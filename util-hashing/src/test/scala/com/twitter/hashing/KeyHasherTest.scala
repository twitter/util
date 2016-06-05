package com.twitter.hashing

import com.twitter.io.{TempFile, Charsets}
import java.util.Base64
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.ListBuffer

@RunWith(classOf[JUnitRunner])
class KeyHasherTest extends WordSpec {
  def readResource(name: String) = {
    var lines = new ListBuffer[String]()
    val src = scala.io.Source.fromFile(TempFile.fromResourcePath(getClass, "/"+name))
    src.getLines
  }

  val base64 = Base64.getDecoder
  def decode(str: String) = base64.decode(str.getBytes(Charsets.Utf8))

  def testHasher(name: String, hasher: KeyHasher) = {
    val sources = readResource(name + "_source") map { decode(_) }
    val hashes = readResource(name + "_hashes")
    assert(sources.size > 0)

    sources zip hashes foreach { case (source, hashAsString) =>
      val hash = BigInt(hashAsString).toLong
      assert(hasher.hashKey(source) == hash)
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
