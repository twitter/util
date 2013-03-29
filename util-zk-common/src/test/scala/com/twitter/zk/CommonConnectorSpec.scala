package com.twitter.zk

import com.twitter.common.net.InetSocketAddressHelper
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.common.quantity._
import com.twitter.conversions.common.zookeeper._
import com.twitter.conversions.time._
import com.twitter.util.{Await, FuturePool}
import org.specs.SpecificationWithJUnit
import java.net.InetSocketAddress
import scala.collection.JavaConverters._

class CommonConnectorSpec extends SpecificationWithJUnit {
  val timeout = 2.seconds
  val addresses = new InetSocketAddress("localhost", 2181) :: Nil

  "CommonConnector" should {
    "initialize" in {
      "with addresses" in {
        implicit val pool = FuturePool.immediatePool
        CommonConnector(addresses, timeout) must notBeNull
      }

      "with a ZooKeeperClient instance" in {
        implicit val pool = FuturePool.immediatePool
        val zookeeper = new ZooKeeperClient(timeout.toIntAmount, addresses.asJava)
        val connector = CommonConnector(zookeeper, timeout)
        connector.underlying mustBe zookeeper
      }
    }
  }

  // A simple live test
  Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
    val address = InetSocketAddressHelper.parse(connectString)

    "A live server @ %s".format(connectString) should {
      val commonClient: ZooKeeperClient = new ZooKeeperClient(timeout.toIntAmount, address)
      val zkClient = commonClient.toZkClient(timeout)(FuturePool.immediatePool)

      doAfter {
        Await.ready(zkClient.release())
      }

      "have 'zookeeper' in '/'" in {
        Await.result(zkClient("/").getChildren(), timeout).children map { _.name } mustContain("zookeeper")
      }
    }
  }
}
