package com.twitter.hashing

import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.collection.mutable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.scalatest.funsuite.AnyFunSuite

class ConsistentHashingDistributorTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {
  val nodes = Seq(
    HashNode("10.0.1.1", 600, 1),
    HashNode("10.0.1.2", 300, 2),
    HashNode("10.0.1.3", 200, 3),
    HashNode("10.0.1.4", 350, 4),
    HashNode("10.0.1.5", 1000, 5),
    HashNode("10.0.1.6", 800, 6),
    HashNode("10.0.1.7", 950, 7),
    HashNode("10.0.1.8", 100, 8)
  )

  // 160 is the hard coded value for libmemcached, which was this input data is from
  val ketamaDistributor = new ConsistentHashingDistributor(nodes, 160)
  val ketamaDistributorInoldLibMemcachedVersionComplianceMode =
    new ConsistentHashingDistributor(nodes, 160, true)

  test("KetamaDistributor should pick the correct node with ketama hash function") {
    // Test from Smile's KetamaNodeLocatorSpec.scala

    // Load known good results (key, hash(?), continuum ceiling(?), IP)
    val stream = getClass.getClassLoader.getResourceAsStream("ketama_results")
    val reader = new BufferedReader(new InputStreamReader(stream))
    val expected = new mutable.ListBuffer[Array[String]]
    var line: String = reader.readLine()
    while (line != null) {
      val segments = line.split(" ")
      assert(segments.length == 4)
      expected += segments
      line = reader.readLine()
    }
    assert(expected.size == 99)

    // Test that ketamaClient.clientOf(key) == expected IP
    val handleToIp = nodes.map { n => n.handle -> n.identifier }.toMap
    for (testcase <- expected) {
      val hash = KeyHasher.KETAMA.hashKey(testcase(0).getBytes)

      val handle = ketamaDistributor.nodeForHash(hash)
      val handle2 = ketamaDistributorInoldLibMemcachedVersionComplianceMode.nodeForHash(hash)
      val resultIp = handleToIp(handle)
      val resultIp2 = handleToIp(handle2)
      assert(testcase(3) == resultIp)
      assert(testcase(3) == resultIp2)
    }
  }

  test("KetamaDistributor should pick the correct node with 64-bit hash values") {
    val knownGoodValues = Map(
      -166124121512512L -> 5,
      8796093022208L -> 3,
      4312515125124L -> 2,
      -8192481414141L -> 1,
      -9515121512312L -> 5
    )

    knownGoodValues foreach {
      case (key, node) =>
        val handle = ketamaDistributor.nodeForHash(key)
        assert(handle == node)
    }
  }

  test("KetamaDistributor should hashInt") {
    val ketama = new ConsistentHashingDistributor[Unit](Seq.empty, 0, false)
    forAll(Gen.chooseNum(0, Int.MaxValue)) { i =>
      val md = MessageDigest.getInstance("MD5")
      ketama.hashInt(i, md)
      val array = md.digest()
      assert(array.toSeq == md.digest(i.toString.getBytes("UTF-8")).toSeq)
    }
  }

  test("KetamaDistributor should byteArrayToLE") {
    val ketama = new ConsistentHashingDistributor[Unit](Seq.empty, 0, false)
    forAll { (s: String) =>
      val ba = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"))
      val bb = ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN)

      assert(ketama.byteArrayToLE(ba, 0) == bb.getInt(0))
      assert(ketama.byteArrayToLE(ba, 4) == bb.getInt(4))
      assert(ketama.byteArrayToLE(ba, 8) == bb.getInt(8))
      assert(ketama.byteArrayToLE(ba, 12) == bb.getInt(12))
    }
  }

  test(
    "KetamaDistributor should partitionIdForHash is the same as entryForHash 1st tuple element") {
    val keyHasher = KeyHasher.MURMUR3

    {
      // Special case this unicode string since this test has failed
      // on it in the past.
      val s = "ퟲ狂✟풻棇埲蟂덧➀缘็滀佳黟韕숻᩿볯箿䜐㫌홻ᖛ磌ᡎ油"
      val hash = keyHasher.hashKey(s.getBytes("UTF-8"))
      val (pid1, _) = ketamaDistributor.entryForHash(hash)
      val pid2 = ketamaDistributor.partitionIdForHash(hash)
      assert(pid1 == pid2)
    }

    forAll { (s: String) =>
      val hash = keyHasher.hashKey(s.getBytes("UTF-8"))
      val (pid1, _) = ketamaDistributor.entryForHash(hash)
      val pid2 = ketamaDistributor.partitionIdForHash(hash)
      assert(pid1 == pid2)
    }
  }
}
