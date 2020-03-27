package com.twitter.util

import java.net.{Inet4Address, InetAddress, UnknownHostException}

object NetUtil {
  def isIpv4Address(ip: String): Boolean =
    // This is 4x faster than using a regular expression, even for invalid input.
    ipToOptionInt(ip).isDefined

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
    ipToOptionInt(ip) getOrElse {
      throw new IllegalArgumentException("invalid IPv4 address: " + ip)
    }
  }

  def ipToOptionInt(ip: String): Option[Int] = {
    // Fast IPv4 address to integer.  This is fast because it avoids split,
    // regular expressions, and String.toInt, which can throw an exception.
    val dot1 = ip.indexOf('.')
    if (dot1 <= 0) {
      return None
    }
    val dot2 = ip.indexOf('.', dot1 + 1)
    if (dot2 == -1) {
      return None
    }
    val dot3 = ip.indexOf('.', dot2 + 1)
    if (dot3 == -1) {
      return None
    }
    val num1 = ipv4DecimalToInt(ip.substring(0, dot1))
    if (num1 < 0) {
      return None
    }
    val num2 = ipv4DecimalToInt(ip.substring(dot1 + 1, dot2))
    if (num2 < 0) {
      return None
    }
    val num3 = ipv4DecimalToInt(ip.substring(dot2 + 1, dot3))
    if (num3 < 0) {
      return None
    }
    val num4 = ipv4DecimalToInt(ip.substring(dot3 + 1))
    if (num4 < 0) {
      return None
    }
    Some((num1 << 24) | (num2 << 16) | (num3 << 8) | num4)
  }

  /**
   * Fast IPv4 decimal to int.  String.toInt is fast, but can throw an
   * exception on invalid strings, which is expensive.
   */
  private[this] def ipv4DecimalToInt(s: String): Int = {
    if (s.isEmpty || s.length > 3) {
      return -1
    }
    var i = 0
    var num = 0
    while (i < s.length) {
      val c = s.charAt(i).toInt
      if (c < '0' || c > '9') {
        return -1
      }
      num = (num * 10) + (c - '0')
      i += 1
    }
    if (num >= 0 && num <= 255) {
      num
    } else {
      -1
    }
  }

  def inetAddressToInt(inetAddress: InetAddress): Int = {
    inetAddress match {
      case inetAddress: Inet4Address =>
        val addr = inetAddress.getAddress
        ((addr(0) & 0xff) << 24) |
          ((addr(1) & 0xff) << 16) |
          ((addr(2) & 0xff) << 8) |
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
      case None if arr.length != 4 => arr.length * 8
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
    isIpInBlock(inetAddressToInt(inetAddress), ipBlock)

  def isIpInBlocks(ip: Int, ipBlocks: Iterable[(Int, Int)]): Boolean = {
    ipBlocks exists { ipBlock => isIpInBlock(ip, ipBlock) }
  }

  def isIpInBlocks(ip: String, ipBlocks: Iterable[(Int, Int)]): Boolean = {
    isIpInBlocks(ipToInt(ip), ipBlocks)
  }

  def isInetAddressInBlocks(inetAddress: InetAddress, ipBlocks: Iterable[(Int, Int)]): Boolean =
    isIpInBlocks(inetAddressToInt(inetAddress), ipBlocks)

  def getLocalHostName(): String = {
    try {
      InetAddress.getLocalHost().getHostName()
    } catch {
      case uhe: UnknownHostException =>
        Option(uhe.getMessage) match {
          case Some(host) =>
            host.split(":") match {
              case Array(hostName, _) => hostName
              case _ => "unknown_host"
            }
          case None => "unknown_host"
        }
    }
  }
}
