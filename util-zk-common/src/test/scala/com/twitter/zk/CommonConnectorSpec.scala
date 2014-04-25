package com.twitter.zk

import com.twitter.common.net.InetSocketAddressHelper
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.common.quantity._
import com.twitter.conversions.common.zookeeper._
import com.twitter.conversions.time._
import com.twitter.util.{Await, FuturePool, RandomSocket}

import java.net.InetSocketAddress
import scala.collection.JavaConverters._
import org.scalatest.{WordSpec, Matchers, BeforeAndAfter}

class CommonConnectorSpec extends WordSpec with Matchers with BeforeAndAfter {
  val timeout = 2.seconds
  val port = RandomSocket.nextPort()
  val addresses = new InetSocketAddress("localhost", port) :: Nil

  "CommonConnector" should  {
    "initialize" should {
      "with addresses" in {
        implicit val pool = FuturePool.immediatePool
        CommonConnector(addresses, timeout) should not be (null)
      }

      "with a ZooKeeperClient instance" in {
        implicit val pool = FuturePool.immediatePool
        val zookeeper = new ZooKeeperClient(timeout.toIntAmount, addresses.asJava)
        val connector = CommonConnector(zookeeper, timeout)
        connector.underlying shouldBe zookeeper
      }
    }
  }

  // A simple live test
  Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
    val address = InetSocketAddressHelper.parse(connectString)

    "A live server @ %s".format(connectString) should  {
      val commonClient: ZooKeeperClient = new ZooKeeperClient(timeout.toIntAmount, address)
      val zkClient = commonClient.toZkClient(timeout)(FuturePool.immediatePool)

      after {
        Await.ready(zkClient.release())
      }

      "have 'zookeeper' in '/'" in {
        Await.result(zkClient("/").getChildren(), timeout).children map { _.name } should contain("zookeeper")
      }
    }
  }
}
