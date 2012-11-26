package com.twitter.util

import java.net.{InetAddress, InetSocketAddress, Socket}
import java.io.IOException
import java.util.Random

object RandomSocket {
  private[this] val rng = new Random
  private[this] def localSocketOnPort(port: Int) =
    new InetSocketAddress(port)
  private[this] val ephemeralSocketAddress = localSocketOnPort(0)

  def apply() = nextAddress()

  def nextAddress(): InetSocketAddress =
    localSocketOnPort(nextPort())

  def nextPort(): Int = {
    val s = new Socket
    s.setReuseAddress(true)
    try {
      s.bind(ephemeralSocketAddress)
      s.getLocalPort
    } catch {
      case NonFatal(e) =>
        if (e.getClass == classOf[IOException] || e.getClass == classOf[IllegalArgumentException])
          throw new Exception("Couldn't find an open port: %s".format(e.getMessage))
        else
          throw e
    } finally {
      s.close()
    }
  }
}

