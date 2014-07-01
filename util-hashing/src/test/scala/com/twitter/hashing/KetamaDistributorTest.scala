package com.twitter.hashing

import _root_.java.io.{BufferedReader, InputStreamReader}

import scala.collection.mutable

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class KetamaDistributorTest extends WordSpec {
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
    val ketamaDistributorInoldLibMemcachedVersionComplianceMode = new KetamaDistributor(nodes, 160, true)
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
          assert(segments.length === 4)
          expected += segments
        }
      } while (line != null)
      assert(expected.size === 99)

      // Test that ketamaClient.clientOf(key) == expected IP
      val handleToIp = nodes.map { n => n.handle -> n.identifier }.toMap
      for (testcase <- expected) {
        val hash = KeyHasher.KETAMA.hashKey(testcase(0).getBytes)

        val handle = ketamaDistributor.nodeForHash(hash)
        val handle2 = ketamaDistributorInoldLibMemcachedVersionComplianceMode.nodeForHash(hash)
        val resultIp = handleToIp(handle)
        val resultIp2 = handleToIp(handle2)
        assert(testcase(3) === resultIp)
        assert(testcase(3) === resultIp2)
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

      knownGoodValues foreach { case (key, node) =>
        val handle = ketamaDistributor.nodeForHash(key)
        assert(handle === node)
      }
    }
  }
}
