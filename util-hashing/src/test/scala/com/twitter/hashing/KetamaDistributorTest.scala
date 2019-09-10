package com.twitter.hashing

import org.scalacheck.Gen
import org.scalatest.WordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.collection.mutable
import java.io.{BufferedReader, InputStreamReader}
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest

class KetamaDistributorTest extends WordSpec with ScalaCheckDrivenPropertyChecks {
  "KetamaDistributor" should {
    val nodes = Seq(
      KetamaNode("10.0.1.1", 600, 1),
      KetamaNode("10.0.1.2", 300, 2),
      KetamaNode("10.0.1.3", 200, 3),
      KetamaNode("10.0.1.4", 350, 4),
      KetamaNode("10.0.1.5", 1000, 5),
      KetamaNode("10.0.1.6", 800, 6),
      KetamaNode("10.0.1.7", 950, 7),
      KetamaNode("10.0.1.8", 100, 8)
    )

    // 160 is the hard coded value for libmemcached, which was this input data is from
    val ketamaDistributor = new KetamaDistributor(nodes, 160)
    val ketamaDistributorInoldLibMemcachedVersionComplianceMode =
      new KetamaDistributor(nodes, 160, true)

    "pick the correct node with ketama hash function" in {
      // Test from Smile's KetamaNodeLocatorSpec.scala

      // Load known good results (key, hash(?), continuum ceiling(?), IP)
      val stream = getClass.getClassLoader.getResourceAsStream("ketama_results")
      val reader = new BufferedReader(new InputStreamReader(stream))
      val expected = new mutable.ListBuffer[Array[String]]
      var line: String = null
      do {
        line = reader.readLine
        if (line != null) {
          val segments = line.split(" ")
          assert(segments.length == 4)
          expected += segments
        }
      } while (line != null)
      assert(expected.size == 99)

      // Test that ketamaClient.clientOf(key) == expected IP
      val handleToIp = nodes.map { n =>
        n.handle -> n.identifier
      }.toMap
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

    "pick the correct node with 64-bit hash values" in {
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

    "hashInt" in {
      val ketama = new KetamaDistributor[Unit](Seq.empty, 0, false)
      forAll(Gen.chooseNum(0, Int.MaxValue)) { i =>
        val md = MessageDigest.getInstance("MD5")
        ketama.hashInt(i, md)
        val array = md.digest()
        assert(array.toSeq == md.digest(i.toString.getBytes("UTF-8")).toSeq)
      }
    }

    "byteArrayToLE" in {
      val ketama = new KetamaDistributor[Unit](Seq.empty, 0, false)
      forAll { s: String =>
        val ba = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"))
        val bb = ByteBuffer.wrap(ba).order(ByteOrder.LITTLE_ENDIAN)

        assert(ketama.byteArrayToLE(ba, 0) == bb.getInt(0))
        assert(ketama.byteArrayToLE(ba, 4) == bb.getInt(4))
        assert(ketama.byteArrayToLE(ba, 8) == bb.getInt(8))
        assert(ketama.byteArrayToLE(ba, 12) == bb.getInt(12))
      }
    }

    // these two methods use slightly different code paths to determine
    // the partitionId for a given hash, but should return equal values
    // for the same input.

    "partitionIdForHash is the same as entryForHash 1st tuple element" in {
      val keyHasher = KeyHasher.MURMUR3

      forAll { s: String =>
        val hash = keyHasher.hashKey(s.getBytes("UTF-8"))
        val (pid1, _) = ketamaDistributor.entryForHash(hash)
        val pid2 = ketamaDistributor.partitionIdForHash(hash)

        assert(pid1 == pid2)
      }
    }
  }
}
