package com.twitter.zk

import java.net.{InetAddress, InetSocketAddress}
import org.apache.zookeeper.server.ZooKeeperServer

sealed trait ServerCnxnFactory {
  def getLocalPort: Int
  def startup(server: ZooKeeperServer): Unit
  def shutdown(): Unit
}

object ServerCnxnFactory {
  private[this] val UnlimitedClients: java.lang.Integer = -1

  /**
   * Create a ServerCnxnFactory
   *
   * Required for creating a in-process ZooKeeperServer for integration
   * testing. Given an InetAddress, bind to an available port and accept
   * unlimited clients.
   *
   * @param addr InetAddress to bind to with an available port
   *
   * @return ServerCnxnFactory
   */
  def apply(addr: InetAddress): ServerCnxnFactory =
    this(new InetSocketAddress(addr, 0), UnlimitedClients)

  /**
   * Create a ServerCnxnFactory
   *
   * Required for creating a in-process ZooKeeperServer for integration
   * testing. Given an InetSocketAddress, bind to an available port and
   * accept unlimited clients.
   *
   * @param sockAddr InetSocketAddress to bind to
   *
   * @return ServerCnxnFactory
   */
  def apply(sockAddr: InetSocketAddress): ServerCnxnFactory =
    this(sockAddr, UnlimitedClients)

  /**
   * Create a ServerCnxnFactory
   *
   * Required for creating a in-process ZooKeeperServer for integration
   * testing.
   *
   * @param sockAddr InetSocketAddress to bind to
   * @param maxClients maximum number of clients to accept
   *
   * @return ServerCnxnFactory
   */
  def apply(sockAddr: InetSocketAddress, maxClients: java.lang.Integer): ServerCnxnFactory = {
    val factory = {
      try {
        val inst = java.lang.Class.forName("org.apache.zookeeper.server.NIOServerCnxnFactory").newInstance
        val call = inst.getClass.getMethod("configure", sockAddr.getClass, Integer.TYPE)
        call.invoke(inst, sockAddr, UnlimitedClients)
        inst
      } catch {
        case t: ClassNotFoundException =>
          val constructor = java.lang.Class.forName("org.apache.zookeeper.server.NIOServerCnxn$Factory")
            .getConstructor(sockAddr.getClass)
          constructor.newInstance(sockAddr)
      }
    }

    new ServerCnxnFactory {
      def getLocalPort: Int = {
        val call = factory.getClass.getMethod("getLocalPort")
        call.invoke(factory).asInstanceOf[Int]
      }

      def startup(server: ZooKeeperServer): Unit = {
        val call = factory.getClass.getMethod("startup", server.getClass)
        call.invoke(factory, server)
      }

      def shutdown(): Unit = {
        val call = factory.getClass.getMethod("shutdown")
        call.invoke(factory)
      }
    }
  }
}
