package com.twitter.hashing

import org.specs.mock.Mockito
import org.specs.SpecificationWithJUnit
import scala.collection.mutable
import _root_.java.io.{BufferedReader, InputStreamReader}



class KetamaDistributorSpec extends SpecificationWithJUnit with Mockito {
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
    val ketamaClient = new KetamaDistributor(nodes, 160)

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
          segments.length mustEqual 4
          expected += segments
        }
      } while (line != null)
      expected.size mustEqual 99

      // Test that ketamaClient.clientOf(key) == expected IP
      val handleToIp = nodes.map { n => n.handle -> n.identifier }.toMap
      for (testcase <- expected) {
        val handle = ketamaClient.nodeForHash(KeyHasher.KETAMA.hashKey(testcase(0).getBytes))
        val resultIp = handleToIp(handle)
        testcase(3) must be_==(resultIp)
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
        val handle = ketamaClient.nodeForHash(key)
        handle mustEqual node
      }
    }
  }
}
