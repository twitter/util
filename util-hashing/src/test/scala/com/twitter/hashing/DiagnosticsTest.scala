package com.twitter.hashing

import org.scalatest.funsuite.AnyFunSuite

class DiagnosticsTest extends AnyFunSuite {
  test("Diagnostics should print distribution") {
    val hosts = 1 until 500 map { "10.1.1." + _ + ":11211:4" }

    val nodes = hosts.map { s =>
      val Array(host, port, weight) = s.split(":")
      val identifier = host + ":" + port
      HashNode(identifier, weight.toInt, identifier)
    }

    val hashFunctions = List(
      "FNV1_32" -> KeyHasher.FNV1_32,
      "FNV1A_32" -> KeyHasher.FNV1A_32,
      "FNV1_64" -> KeyHasher.FNV1_64,
      "FNV1A_64" -> KeyHasher.FNV1A_64,
      "CRC32-ITU" -> KeyHasher.CRC32_ITU,
      "HSIEH" -> KeyHasher.HSIEH,
      "JENKINS" -> KeyHasher.JENKINS,
      "MURMUR3" -> KeyHasher.MURMUR3
    )

    val keys = (1 until 1000000).map(_.toString).toList
    // Comment this out unless you're running it results
    // hashFunctions foreach { case (s, h) =>
    //   val distributor = new KetamaDistributor(nodes, 160)
    //   val tester = new DistributionTester(distributor)
    //   val start = Time.now
    //   val dev = tester.distributionDeviation(keys map { s => h.hashKey(s.getBytes) })
    //   val duration = (Time.now - start).inMilliseconds
    //   println("%s\n  distribution: %.5f\n  duration: %dms\n".format(s, dev, duration))
    // }
  }
}
