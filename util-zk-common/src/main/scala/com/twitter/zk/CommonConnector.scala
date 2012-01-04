package com.twitter.zk

import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.common.quantity._
import scala.collection.JavaConverters._
import com.twitter.util.{Duration, FuturePool}

/**
 * Adapts a common ZooKeeperClient for use as a Connector.
 * This connector blocks, but it doesn't seem worth a FuturePool only for this connector.
 */
class CommonConnector(
    val underlying: ZooKeeperClient,
    timeout: Duration = COMMON_FOREVER)
    (implicit pool: FuturePool)
extends Connector {
  underlying.register(sessionBroker)

  def apply() = pool { underlying.get(timeout.toLongAmount) }
  def release() = pool { underlying.close() }
}

object CommonConnector {
  def apply(underlying: ZooKeeperClient,
            connectTimeout: Duration = COMMON_FOREVER)
           (implicit pool: FuturePool): CommonConnector = {
    new CommonConnector(underlying, connectTimeout)(pool)
  }

  def apply(addrs: Seq[java.net.InetSocketAddress],
            sessionTimeout: Duration)
           (implicit pool: FuturePool): CommonConnector = {
    apply(new ZooKeeperClient(sessionTimeout.toIntAmount, addrs.asJava))(pool)
  }

  def apply(addrs: Seq[java.net.InetSocketAddress],
            sessionTimeout: Duration,
            connectTimeout: Duration)
           (implicit pool: FuturePool): CommonConnector = {
    apply(new ZooKeeperClient(sessionTimeout.toIntAmount, addrs.asJava), connectTimeout)(pool)
  }
}
