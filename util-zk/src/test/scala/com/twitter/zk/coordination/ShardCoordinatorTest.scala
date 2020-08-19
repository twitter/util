package com.twitter.zk.coordination

import scala.jdk.CollectionConverters._

import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.scalatestplus.mockito.MockitoSugar

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future, JavaTimer}
import com.twitter.zk.{NativeConnector, RetryPolicy, ZkClient}
import org.scalatest.wordspec.AnyWordSpec

class ShardCoordinatorTest extends AnyWordSpec with MockitoSugar {

  "ShardCoordinator" should {

    val path = "/testing/twitter/service/charm/shards/test"

    Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
      def withClient(f: (ZkClient) => Unit) = {
        implicit val timer = new JavaTimer(true)
        val connector = NativeConnector(connectString, 5.seconds, 10.minutes)
        val zk = ZkClient(connector)
          .withRetryPolicy(RetryPolicy.Basic(3))
          .withAcl(OPEN_ACL_UNSAFE.asScala.toSeq)

        Await.result(Future { f(zk) } ensure { zk.release })
      }

      def acquire(coord: ShardCoordinator) = {
        coord.acquire within (new JavaTimer(true), 1.second)
      }

      "provide shards" in {
        withClient { zk =>
          val coord = new ShardCoordinator(zk, path, 5)

          val shard0 = Await.result(acquire(coord))
          assert(shard0.id == 0)

          val shard1 = Await.result(acquire(coord))
          assert(shard1.id == 1)

          val shard2 = Await.result(acquire(coord))
          assert(shard2.id == 2)

          val shard3 = Await.result(acquire(coord))
          assert(shard3.id == 3)

          val shard4 = Await.result(acquire(coord))
          assert(shard4.id == 4)

          val fshard5 = acquire(coord)
          assert(fshard5.isDefined == (false))
          shard3.release
          val shard5 = Await.result(fshard5)
          assert(shard5.id == 3)

          shard0.release
          shard1.release
          shard2.release
          shard4.release
          shard5.release
        }
      }

    }
  }
}
