package com.twitter.util

import java.net.{InetAddress, Inet4Address}

object NetUtil {
  val Ipv4Digit = """(?:0|1\d{0,2}|2(?:|[0-4]\d?|5[0-5]?|[6-9])|[3-9]\d?)"""
  val Ipv4Regex = Seq(Ipv4Digit, Ipv4Digit, Ipv4Digit, Ipv4Digit).mkString("""\.""").r
  def isIpv4Address(ip: String): Boolean =
    Ipv4Regex.pattern.matcher(ip).matches

  def isPrivateAddress(ip: InetAddress): Boolean =
    ip match {
      case ip: Inet4Address =>
        val addr = ip.getAddress
        if (addr(0) == 10.toByte) // 10/8
          true
        else if (addr(0) == 172.toByte && (addr(1) & 0xf0) == 16.toByte) // 172/12
          true
        else if (addr(0) == 192.toByte && addr(1) == 168.toByte) // 192.168/16
          true
        else
            false
      case _ =>
        false
    }

  def ipToInt(ip: String): Int = {
    require(isIpv4Address(ip))
    ip.split('.').foldLeft(0) { case (acc, byteStr) =>
      (acc << 8) | byteStr.toInt
    }
  }

  def inetAddressToInt(inetAddress: InetAddress): Int = {
    inetAddress match {
      case inetAddress: Inet4Address =>
        val addr = inetAddress.getAddress
        ((addr(0) & 0xff) << 24) |
        ((addr(1) & 0xff) << 16) |
        ((addr(2) & 0xff) <<  8) |
         (addr(3) & 0xff)
      case _ =>
        throw new IllegalArgumentException("non-Inet4Address cannot be converted to an Int")
    }
  }

  // Converts either a full or partial ip, (e.g.127.0.0.1, 127.0)
  // to it's integer equivalent with mask specified by prefixlen.
  // Assume missing bits are 0s for a partial ip. Result returned as
  // (ip, netMask)
  def ipToIpBlock(ip: String, prefixLen: Option[Int]): (Int, Int) = {
    val arr = ip.split('.')
    val pLen = prefixLen match {
      case None if (arr.size != 4) => arr.size * 8
      case t => t.getOrElse(32)
    }

    val netIp = ipToInt(arr.padTo(4, "0").mkString("."))
    val mask = (1 << 31) >> (pLen - 1)
    (netIp, mask)
  }

  // Get the ip block from CIDR notation, returned as (subnet, subnetMask)
  def cidrToIpBlock(cidr: String): (Int, Int) = cidr.split('/') match {
    case Array(ip, prefixLen) => ipToIpBlock(ip, Some(prefixLen.toInt))
    case Array(ip) => ipToIpBlock(ip, None)
  }

  def isIpInBlock(ip: Int, ipBlock: (Int, Int)): Boolean = ipBlock match {
    case (netIp, mask) => (mask & ip) == netIp
  }

  def isInetAddressInBlock(inetAddress: InetAddress, ipBlock: (Int, Int)): Boolean =
    isInetAddressInBlock(inetAddress, ipBlock)

  def isIpInBlocks(ip: Int, ipBlocks: Iterable[(Int, Int)]): Boolean = {
    ipBlocks exists { ipBlock => isIpInBlock(ip, ipBlock) }
  }

  def isIpInBlocks(ip: String, ipBlocks: Iterable[(Int, Int)]): Boolean = {
    isIpInBlocks(ipToInt(ip), ipBlocks)
  }

  def isInetAddressInBlocks(inetAddress: InetAddress, ipBlocks: Iterable[(Int, Int)]): Boolean =
    isIpInBlocks(inetAddressToInt(inetAddress), ipBlocks)
}
