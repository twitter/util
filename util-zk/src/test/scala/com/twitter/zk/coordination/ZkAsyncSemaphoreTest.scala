package com.twitter.zk.coordination

import com.twitter.concurrent.Permit
import com.twitter.conversions.DurationOps._
import com.twitter.util._
import com.twitter.zk.{NativeConnector, RetryPolicy, ZkClient, ZNode}
import java.util.concurrent.ConcurrentLinkedQueue
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.scalatest.concurrent.Waiters
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import scala.jdk.CollectionConverters._
import org.scalatest.wordspec.AnyWordSpec

class ZkAsyncSemaphoreTest extends AnyWordSpec with MockitoSugar with Waiters {

  "ZkAsyncSemaphore" should {

    val path = "/testing/twitter/service/charm/semaphore/test"
    val permits = new ConcurrentLinkedQueue[Permit]

    Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
      def withClient(f: (ZkClient) => Unit) = {
        implicit val timer = new JavaTimer(true)
        val connector = NativeConnector(connectString, 5.seconds, 10.minutes)
        val zk = ZkClient(connector)
          .withRetryPolicy(RetryPolicy.Basic(3))
          .withAcl(OPEN_ACL_UNSAFE.asScala.toSeq)

        Await.result(Future { f(zk) } ensure { zk.release })
      }

      def acquire(sem: ZkAsyncSemaphore) = {
        sem.acquire() onSuccess { permit => permits add permit }
      }

      "provide a shared 2-permit semaphore and" should {
        withClient { zk =>
          val sem1 = new ZkAsyncSemaphore(zk, path, 2)
          val sem2 = new ZkAsyncSemaphore(zk, path, 2)

          "have correct initial values" in {
            assert(sem1.numPermitsAvailable == 2)
            assert(sem1.numWaiters == 0)
            assert(sem2.numPermitsAvailable == 2)
            assert(sem2.numWaiters == 0)
          }

          "execute immediately while permits are available" in {
            Await.result(acquire(sem1) within (new JavaTimer(true), 2.second))
            assert(sem1.numPermitsAvailable == 1)
            assert(sem1.numWaiters == 0)
            assert(sem2.numPermitsAvailable == 1)
            assert(sem2.numWaiters == 0)

            Await.result(acquire(sem2) within (new JavaTimer(true), 2.second))
            assert(sem1.numPermitsAvailable == 0)
            assert(sem1.numWaiters == 0)
            assert(sem2.numPermitsAvailable == 0)
            assert(sem2.numWaiters == 0)
          }

          var awaiting1: Future[Permit] = null
          var awaiting2: Future[Permit] = null

          "queue waiters when no permits are available" in {
            implicit val config =
              PatienceConfig(
                timeout = scaled(Span(1, Seconds)),
                interval = scaled(Span(100, Millis))
              )

            awaiting1 = acquire(sem1)
            assert(awaiting1.poll == (None))
            assert(awaiting1.isDefined == (false))
            eventually {
              assert(sem1.numPermitsAvailable == 0)
              assert(sem1.numWaiters == 1)
              assert(sem2.numPermitsAvailable == 0)
              assert(sem2.numWaiters == 1)
            }

            awaiting2 = acquire(sem2)
            assert(awaiting2.poll == (None))
            assert(awaiting2.isDefined == (false))
            eventually {
              assert(sem1.numPermitsAvailable == 0)
              assert(sem1.numWaiters == 2)
              assert(sem2.numPermitsAvailable == 0)
              assert(sem2.numWaiters == 2)
            }
          }

          "have correct gauges as permit holders release" in {
            implicit val config =
              PatienceConfig(
                timeout = scaled(Span(1, Seconds)),
                interval = scaled(Span(100, Millis))
              )

            assert(permits.size == (2))

            permits.poll().release()
            Await.result(awaiting1 within (new JavaTimer(true), 2.second))

            eventually {
              assert(sem1.numPermitsAvailable == 0)
              assert(sem1.numWaiters == 1)
              assert(sem2.numPermitsAvailable == 0)
              assert(sem2.numWaiters == 1)
            }

            permits.poll().release()
            Await.result(awaiting2 within (new JavaTimer(true), 2.second))

            eventually {
              assert(sem1.numPermitsAvailable == 0)
              assert(sem1.numWaiters == 0)
              assert(sem2.numPermitsAvailable == 0)
              assert(sem2.numWaiters == 0)
            }

            permits.poll().release()

            eventually {
              assert(sem1.numPermitsAvailable == 1)
              assert(sem1.numWaiters == 0)
              assert(sem2.numPermitsAvailable == 1)
              assert(sem2.numWaiters == 0)
            }

            permits.poll().release()

            eventually {
              assert(sem1.numPermitsAvailable == 2)
              assert(sem1.numWaiters == 0)
              assert(sem2.numPermitsAvailable == 2)
              assert(sem2.numWaiters == 0)
            }
          }
        }
      }

      "numPermitsOf" should {
        "get a node's value" in {
          withClient { zk =>
            val sem = new ZkAsyncSemaphore(zk, "/aoeu/aoeu", 2)
            val znode = ZNode(zk, "/testing/twitter/node_with_data_7")
            Await.result(
              znode.delete().rescue { case e: NoNodeException => Future.value(0) },
              5.seconds
            )
            Await.result(znode.create("7".getBytes), 5.seconds)
            val permits: Future[Int] = sem.numPermitsOf(znode)
            assert(Await.result(permits, 5.seconds) == 7)
          }
        }

        "not error on NoNode" in {
          withClient { zk =>
            val sem = new ZkAsyncSemaphore(zk, "/aoeu/aoeu", 2)
            val permits: Future[Int] =
              sem.numPermitsOf(ZNode(zk, "/aoeu/aoeu/node_that_does_not_exist"))
            Await.result(permits, 5.seconds)
          }
        }
      }
    }
  }
}
