package com.twitter.util

import java.net.InetAddress
import org.scalatest.{WordSpec, Matchers}

class NetUtilSpec extends WordSpec with Matchers {
  "NetUtil" should {
    "isIpv4Address" in {
      for (i <- 0.to(255)) {
        NetUtil.isIpv4Address("%d.0.0.0".format(i)) shouldBe true
        NetUtil.isIpv4Address("0.%d.0.0".format(i)) shouldBe true
        NetUtil.isIpv4Address("0.0.%d.0".format(i)) shouldBe true
        NetUtil.isIpv4Address("0.0.0.%d".format(i)) shouldBe true
        NetUtil.isIpv4Address("%d.%d.%d.%d".format(i, i, i, i)) shouldBe true
      }

      NetUtil.isIpv4Address("")            shouldBe false
      NetUtil.isIpv4Address("no")          shouldBe false
      NetUtil.isIpv4Address("::127.0.0.1") shouldBe false
      NetUtil.isIpv4Address("-1.0.0.0")    shouldBe false
      NetUtil.isIpv4Address("256.0.0.0")   shouldBe false
      NetUtil.isIpv4Address("0.256.0.0")   shouldBe false
      NetUtil.isIpv4Address("0.0.256.0")   shouldBe false
      NetUtil.isIpv4Address("0.0.0.256")   shouldBe false
      NetUtil.isIpv4Address("x1.2.3.4")    shouldBe false
      NetUtil.isIpv4Address("1.x2.3.4")    shouldBe false
      NetUtil.isIpv4Address("1.2.x3.4")    shouldBe false
      NetUtil.isIpv4Address("1.2.3.x4")    shouldBe false
      NetUtil.isIpv4Address("1.2.3.4x")    shouldBe false
      NetUtil.isIpv4Address(" 1.2.3.4")    shouldBe false
      NetUtil.isIpv4Address("1.2.3.4 ")    shouldBe false
      NetUtil.isIpv4Address(".")           shouldBe false
      NetUtil.isIpv4Address("....")        shouldBe false
      NetUtil.isIpv4Address("1....")       shouldBe false
      NetUtil.isIpv4Address("1.2...")      shouldBe false
      NetUtil.isIpv4Address("1.2.3.")      shouldBe false
      NetUtil.isIpv4Address(".2.3.4")      shouldBe false
    }

    "isPrivate" in {
      NetUtil.isPrivateAddress(InetAddress.getByName("0.0.0.0"))         shouldBe false
      NetUtil.isPrivateAddress(InetAddress.getByName("199.59.148.13"))   shouldBe false
      NetUtil.isPrivateAddress(InetAddress.getByName("10.0.0.0"))        shouldBe true
      NetUtil.isPrivateAddress(InetAddress.getByName("10.255.255.255"))  shouldBe true
      NetUtil.isPrivateAddress(InetAddress.getByName("172.16.0.0"))      shouldBe true
      NetUtil.isPrivateAddress(InetAddress.getByName("172.31.255.255"))  shouldBe true
      NetUtil.isPrivateAddress(InetAddress.getByName("192.168.0.0"))     shouldBe true
      NetUtil.isPrivateAddress(InetAddress.getByName("192.168.255.255")) shouldBe true
    }

    "ipToInt" in {
      NetUtil.ipToInt("0.0.0.0")         shouldEqual 0
      NetUtil.ipToInt("255.255.255.255") shouldEqual 0xFFFFFFFF
      NetUtil.ipToInt("255.255.255.0")   shouldEqual 0xFFFFFF00
      NetUtil.ipToInt("255.0.255.0")     shouldEqual 0xFF00FF00
      NetUtil.ipToInt("61.197.253.56")   shouldEqual 0x3dc5fd38
      intercept[IllegalArgumentException] {
        NetUtil.ipToInt("256.0.255.0")
      }
    }

    "inetAddressToInt" in {
      NetUtil.inetAddressToInt(InetAddress.getByName("0.0.0.0"))         shouldEqual 0
      NetUtil.inetAddressToInt(InetAddress.getByName("255.255.255.255")) shouldEqual 0xFFFFFFFF
      NetUtil.inetAddressToInt(InetAddress.getByName("255.255.255.0"))   shouldEqual 0xFFFFFF00
      NetUtil.inetAddressToInt(InetAddress.getByName("255.0.255.0"))     shouldEqual 0xFF00FF00
      NetUtil.inetAddressToInt(InetAddress.getByName("61.197.253.56"))   shouldEqual 0x3dc5fd38
      intercept[IllegalArgumentException] {
      NetUtil.inetAddressToInt(InetAddress.getByName("::1"))
      }
    }

    "cidrToIpBlock" in {
      NetUtil.cidrToIpBlock("127")          shouldEqual (0x7F000000, 0xFF000000)
      NetUtil.cidrToIpBlock("127.0.0")      shouldEqual (0x7F000000, 0xFFFFFF00)
      NetUtil.cidrToIpBlock("127.0.0.1")    shouldEqual (0x7F000001, 0xFFFFFFFF)
      NetUtil.cidrToIpBlock("127.0.0.1/1")  shouldEqual (0x7F000001, 0x80000000)
      NetUtil.cidrToIpBlock("127.0.0.1/4")  shouldEqual (0x7F000001, 0xF0000000)
      NetUtil.cidrToIpBlock("127.0.0.1/32") shouldEqual (0x7F000001, 0xFFFFFFFF)
      NetUtil.cidrToIpBlock("127/24")       shouldEqual (0x7F000000, 0xFFFFFF00)
    }

    "isIpInBlocks" in {
      val blocks = Seq(NetUtil.cidrToIpBlock("127"),
                       NetUtil.cidrToIpBlock("10.1.1.0/24"),
                       NetUtil.cidrToIpBlock("192.168.0.0/16"),
                       NetUtil.cidrToIpBlock("200.1.1.1"),
                       NetUtil.cidrToIpBlock("200.1.1.2/32"))

      NetUtil.isIpInBlocks("127.0.0.1", blocks)       shouldBe true
      NetUtil.isIpInBlocks("128.0.0.1", blocks)       shouldBe false
      NetUtil.isIpInBlocks("127.255.255.255", blocks) shouldBe true

      NetUtil.isIpInBlocks("10.1.1.1", blocks)        shouldBe true
      NetUtil.isIpInBlocks("10.1.1.255", blocks)      shouldBe true
      NetUtil.isIpInBlocks("10.1.0.255", blocks)      shouldBe false
      NetUtil.isIpInBlocks("10.1.2.0", blocks)        shouldBe false

      NetUtil.isIpInBlocks("192.168.0.1", blocks)     shouldBe true
      NetUtil.isIpInBlocks("192.168.255.255", blocks) shouldBe true
      NetUtil.isIpInBlocks("192.167.255.255", blocks) shouldBe false
      NetUtil.isIpInBlocks("192.169.0.0", blocks)     shouldBe false
      NetUtil.isIpInBlocks("200.168.0.0", blocks)     shouldBe false

      NetUtil.isIpInBlocks("200.1.1.1", blocks)       shouldBe true
      NetUtil.isIpInBlocks("200.1.3.1", blocks)       shouldBe false
      NetUtil.isIpInBlocks("200.1.1.2", blocks)       shouldBe true
      NetUtil.isIpInBlocks("200.1.3.2", blocks)       shouldBe false

      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("no", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("::127.0.0.1", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("-1.0.0.0", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("256.0.0.0", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("0.256.0.0", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("0.0.256.0", blocks)
      }
      intercept[IllegalArgumentException] {
        NetUtil.isIpInBlocks("0.0.0.256", blocks)
      }
    }
  }
}
