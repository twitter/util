package com.twitter.conversions.common

import com.twitter.conversions.common.quantity.COMMON_FOREVER
import com.twitter.zk.{CommonConnector, ZkClient}
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.util.{FuturePool, Duration}

/** Adapters for common's ZooKeeperClient (and, later, serversets, etc) */
object zookeeper {
  class CommonZkClientAdapter(zkc: ZooKeeperClient) {
    def toConnector(timeout: Duration = COMMON_FOREVER)
                   (implicit pool: FuturePool): CommonConnector = {
      new CommonConnector(zkc, timeout)
    }

    def toZkClient(timeout: Duration = COMMON_FOREVER)(implicit pool: FuturePool): ZkClient = {
      ZkClient(toConnector(timeout))
    }
  }

  /** Implicit conversion of ZooKeeperClient to CommonZkClient */
  implicit def commonZkClient(zkc: ZooKeeperClient) = new CommonZkClientAdapter(zkc)
}
