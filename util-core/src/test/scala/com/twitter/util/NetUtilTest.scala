package com.twitter.util

import java.net.InetAddress

import org.scalatest.wordspec.AnyWordSpec

class NetUtilTest extends AnyWordSpec {
  "NetUtil" should {
    "isIpv4Address" in {
      for (i <- 0.to(255)) {
        assert(NetUtil.isIpv4Address("%d.0.0.0".format(i)) == true)
        assert(NetUtil.isIpv4Address("0.%d.0.0".format(i)) == true)
        assert(NetUtil.isIpv4Address("0.0.%d.0".format(i)) == true)
        assert(NetUtil.isIpv4Address("0.0.0.%d".format(i)) == true)
        assert(NetUtil.isIpv4Address("%d.%d.%d.%d".format(i, i, i, i)) == true)
      }

      assert(NetUtil.isIpv4Address("") == false)
      assert(NetUtil.isIpv4Address("no") == false)
      assert(NetUtil.isIpv4Address("::127.0.0.1") == false)
      assert(NetUtil.isIpv4Address("-1.0.0.0") == false)
      assert(NetUtil.isIpv4Address("256.0.0.0") == false)
      assert(NetUtil.isIpv4Address("0.256.0.0") == false)
      assert(NetUtil.isIpv4Address("0.0.256.0") == false)
      assert(NetUtil.isIpv4Address("0.0.0.256") == false)
      assert(NetUtil.isIpv4Address("x1.2.3.4") == false)
      assert(NetUtil.isIpv4Address("1.x2.3.4") == false)
      assert(NetUtil.isIpv4Address("1.2.x3.4") == false)
      assert(NetUtil.isIpv4Address("1.2.3.x4") == false)
      assert(NetUtil.isIpv4Address("1.2.3.4x") == false)
      assert(NetUtil.isIpv4Address(" 1.2.3.4") == false)
      assert(NetUtil.isIpv4Address("1.2.3.4 ") == false)
      assert(NetUtil.isIpv4Address(".") == false)
      assert(NetUtil.isIpv4Address("....") == false)
      assert(NetUtil.isIpv4Address("1....") == false)
      assert(NetUtil.isIpv4Address("1.2...") == false)
      assert(NetUtil.isIpv4Address("1.2.3.") == false)
      assert(NetUtil.isIpv4Address(".2.3.4") == false)
    }

    "isPrivate" in {
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("0.0.0.0")) == false)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("199.59.148.13")) == false)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("10.0.0.0")) == true)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("10.255.255.255")) == true)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("172.16.0.0")) == true)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("172.31.255.255")) == true)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("192.168.0.0")) == true)
      assert(NetUtil.isPrivateAddress(InetAddress.getByName("192.168.255.255")) == true)
    }

    "ipToInt" in {
      assert(NetUtil.ipToInt("0.0.0.0") == 0)
      assert(NetUtil.ipToInt("255.255.255.255") == 0xffffffff)
      assert(NetUtil.ipToInt("255.255.255.0") == 0xffffff00)
      assert(NetUtil.ipToInt("255.0.255.0") == 0xff00ff00)
      assert(NetUtil.ipToInt("61.197.253.56") == 0x3dc5fd38)
      intercept[IllegalArgumentException] {
        NetUtil.ipToInt("256.0.255.0")
      }
    }

    "inetAddressToInt" in {
      assert(NetUtil.inetAddressToInt(InetAddress.getByName("0.0.0.0")) == 0)
      assert(NetUtil.inetAddressToInt(InetAddress.getByName("255.255.255.255")) == 0xffffffff)
      assert(NetUtil.inetAddressToInt(InetAddress.getByName("255.255.255.0")) == 0xffffff00)
      assert(NetUtil.inetAddressToInt(InetAddress.getByName("255.0.255.0")) == 0xff00ff00)
      assert(NetUtil.inetAddressToInt(InetAddress.getByName("61.197.253.56")) == 0x3dc5fd38)
      intercept[IllegalArgumentException] {
        NetUtil.inetAddressToInt(InetAddress.getByName("::1"))
      }
    }

    "cidrToIpBlock" in {
      assert(NetUtil.cidrToIpBlock("127") == ((0x7f000000, 0xff000000)))
      assert(NetUtil.cidrToIpBlock("127.0.0") == ((0x7f000000, 0xffffff00)))
      assert(NetUtil.cidrToIpBlock("127.0.0.1") == ((0x7f000001, 0xffffffff)))
      assert(NetUtil.cidrToIpBlock("127.0.0.1/1") == ((0x7f000001, 0x80000000)))
      assert(NetUtil.cidrToIpBlock("127.0.0.1/4") == ((0x7f000001, 0xf0000000)))
      assert(NetUtil.cidrToIpBlock("127.0.0.1/32") == ((0x7f000001, 0xffffffff)))
      assert(NetUtil.cidrToIpBlock("127/24") == ((0x7f000000, 0xffffff00)))
    }

    "isInetAddressInBlock" in {
      val block = NetUtil.cidrToIpBlock("192.168.0.0/16")

      assert(NetUtil.isInetAddressInBlock(InetAddress.getByName("192.168.0.1"), block) == true)
      assert(NetUtil.isInetAddressInBlock(InetAddress.getByName("192.168.255.254"), block) == true)
      assert(NetUtil.isInetAddressInBlock(InetAddress.getByName("192.169.0.1"), block) == false)
    }

    "isIpInBlocks" in {
      val blocks = Seq(
        NetUtil.cidrToIpBlock("127"),
        NetUtil.cidrToIpBlock("10.1.1.0/24"),
        NetUtil.cidrToIpBlock("192.168.0.0/16"),
        NetUtil.cidrToIpBlock("200.1.1.1"),
        NetUtil.cidrToIpBlock("200.1.1.2/32")
      )

      assert(NetUtil.isIpInBlocks("127.0.0.1", blocks) == true)
      assert(NetUtil.isIpInBlocks("128.0.0.1", blocks) == false)
      assert(NetUtil.isIpInBlocks("127.255.255.255", blocks) == true)

      assert(NetUtil.isIpInBlocks("10.1.1.1", blocks) == true)
      assert(NetUtil.isIpInBlocks("10.1.1.255", blocks) == true)
      assert(NetUtil.isIpInBlocks("10.1.0.255", blocks) == false)
      assert(NetUtil.isIpInBlocks("10.1.2.0", blocks) == false)

      assert(NetUtil.isIpInBlocks("192.168.0.1", blocks) == true)
      assert(NetUtil.isIpInBlocks("192.168.255.255", blocks) == true)
      assert(NetUtil.isIpInBlocks("192.167.255.255", blocks) == false)
      assert(NetUtil.isIpInBlocks("192.169.0.0", blocks) == false)
      assert(NetUtil.isIpInBlocks("200.168.0.0", blocks) == false)

      assert(NetUtil.isIpInBlocks("200.1.1.1", blocks) == true)
      assert(NetUtil.isIpInBlocks("200.1.3.1", blocks) == false)
      assert(NetUtil.isIpInBlocks("200.1.1.2", blocks) == true)
      assert(NetUtil.isIpInBlocks("200.1.3.2", blocks) == false)

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
