package com.twitter.util.yaml

import org.specs.Specification
import java.net.InetAddress

object NetUtilSpec extends Specification {
  "NetUtil" should {
    "isIpv4Address" in {
      for (i <- 0.to(255)) {
        NetUtil.isIpv4Address("%d.0.0.0".format(i)) must beTrue
        NetUtil.isIpv4Address("0.%d.0.0".format(i)) must beTrue
        NetUtil.isIpv4Address("0.0.%d.0".format(i)) must beTrue
        NetUtil.isIpv4Address("0.0.0.%d".format(i)) must beTrue
        NetUtil.isIpv4Address("%d.%d.%d.%d".format(i, i, i, i)) must beTrue
      }

      NetUtil.isIpv4Address("")            must beFalse
      NetUtil.isIpv4Address("no")          must beFalse
      NetUtil.isIpv4Address("::127.0.0.1") must beFalse
      NetUtil.isIpv4Address("-1.0.0.0")    must beFalse
      NetUtil.isIpv4Address("256.0.0.0")   must beFalse
      NetUtil.isIpv4Address("0.256.0.0")   must beFalse
      NetUtil.isIpv4Address("0.0.256.0")   must beFalse
      NetUtil.isIpv4Address("0.0.0.256")   must beFalse
    }

    "isPrivate" in {
      NetUtil.isPrivateAddress(InetAddress.getByName("0.0.0.0"))         must beFalse
      NetUtil.isPrivateAddress(InetAddress.getByName("199.59.148.13"))   must beFalse
      NetUtil.isPrivateAddress(InetAddress.getByName("10.0.0.0"))        must beTrue
      NetUtil.isPrivateAddress(InetAddress.getByName("10.255.255.255"))  must beTrue
      NetUtil.isPrivateAddress(InetAddress.getByName("172.16.0.0"))      must beTrue
      NetUtil.isPrivateAddress(InetAddress.getByName("172.31.255.255"))  must beTrue
      NetUtil.isPrivateAddress(InetAddress.getByName("192.168.0.0"))     must beTrue
      NetUtil.isPrivateAddress(InetAddress.getByName("192.168.255.255")) must beTrue
    }

    "ipToInt" in {
      NetUtil.ipToInt("0.0.0.0")         must_== 0
      NetUtil.ipToInt("255.255.255.255") must_== 0xFFFFFFFF
      NetUtil.ipToInt("255.255.255.0")   must_== 0xFFFFFF00
      NetUtil.ipToInt("255.0.255.0")     must_== 0xFF00FF00
      NetUtil.ipToInt("256.0.255.0")     must throwA[IllegalArgumentException]
    }

    "cidrToIpBlock" in {
      NetUtil.cidrToIpBlock("127")          must_== (0x7F000000, 0xFF000000)
      NetUtil.cidrToIpBlock("127.0.0")      must_== (0x7F000000, 0xFFFFFF00)
      NetUtil.cidrToIpBlock("127.0.0.1")    must_== (0x7F000001, 0xFFFFFFFF)
      NetUtil.cidrToIpBlock("127.0.0.1/1")  must_== (0x7F000001, 0x80000000)
      NetUtil.cidrToIpBlock("127.0.0.1/4")  must_== (0x7F000001, 0xF0000000)
      NetUtil.cidrToIpBlock("127.0.0.1/32") must_== (0x7F000001, 0xFFFFFFFF)
      NetUtil.cidrToIpBlock("127/24")       must_== (0x7F000000, 0xFFFFFF00)
    }

    "isIpInBlocks" in {
      val blocks = Seq(NetUtil.cidrToIpBlock("127"),
                       NetUtil.cidrToIpBlock("10.1.1.0/24"),
                       NetUtil.cidrToIpBlock("192.168.0.0/16"),
                       NetUtil.cidrToIpBlock("200.1.1.1"),
                       NetUtil.cidrToIpBlock("200.1.1.2/32"))

      NetUtil.isIpInBlocks("127.0.0.1", blocks)       must beTrue
      NetUtil.isIpInBlocks("128.0.0.1", blocks)       must beFalse
      NetUtil.isIpInBlocks("127.255.255.255", blocks) must beTrue

      NetUtil.isIpInBlocks("10.1.1.1", blocks)        must beTrue
      NetUtil.isIpInBlocks("10.1.1.255", blocks)      must beTrue
      NetUtil.isIpInBlocks("10.1.0.255", blocks)      must beFalse
      NetUtil.isIpInBlocks("10.1.2.0", blocks)        must beFalse

      NetUtil.isIpInBlocks("192.168.0.1", blocks)     must beTrue
      NetUtil.isIpInBlocks("192.168.255.255", blocks) must beTrue
      NetUtil.isIpInBlocks("192.167.255.255", blocks) must beFalse
      NetUtil.isIpInBlocks("192.169.0.0", blocks)     must beFalse
      NetUtil.isIpInBlocks("200.168.0.0", blocks)     must beFalse

      NetUtil.isIpInBlocks("200.1.1.1", blocks)       must beTrue
      NetUtil.isIpInBlocks("200.1.3.1", blocks)       must beFalse
      NetUtil.isIpInBlocks("200.1.1.2", blocks)       must beTrue
      NetUtil.isIpInBlocks("200.1.3.2", blocks)       must beFalse

      NetUtil.isIpInBlocks("", blocks)                must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("no", blocks)              must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("::127.0.0.1", blocks)     must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("-1.0.0.0", blocks)        must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("256.0.0.0", blocks)       must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("0.256.0.0", blocks)       must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("0.0.256.0", blocks)       must throwA[IllegalArgumentException]
      NetUtil.isIpInBlocks("0.0.0.256", blocks)       must throwA[IllegalArgumentException]
    }
  }
}
