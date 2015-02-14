package com.twitter.zk.coordination

import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._

import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.concurrent.Eventually._
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.PrivateMethodTester
import org.scalatest.time.{Millis, Seconds, Span}

import com.twitter.concurrent.Permit
import com.twitter.conversions.time._
import com.twitter.util._
import com.twitter.zk.{NativeConnector, RetryPolicy, ZkClient, ZNode}

@RunWith(classOf[JUnitRunner])
class ZkAsyncSemaphoreTest extends WordSpec with MockitoSugar with AsyncAssertions with PrivateMethodTester {

  "ZkAsyncSemaphore" should {

    val path = "/testing/twitter/service/charm/semaphore/test"
    val permits = new ConcurrentLinkedQueue[Permit]

    Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
      def withClient(f: (ZkClient) => Unit) = {
        implicit val timer = new JavaTimer(true)
        val connector = NativeConnector(connectString, 5.seconds, 10.minutes)
        val zk = ZkClient(connector)
            .withRetryPolicy(RetryPolicy.Basic(3))
            .withAcl(OPEN_ACL_UNSAFE.asScala)

        Await.result( Future { f(zk) } ensure { zk.release } )
      }

      def acquire(sem: ZkAsyncSemaphore) = {
        sem.acquire() onSuccess { permit =>
          permits add permit
        }
      }

      "provide a shared 2-permit semaphore and" should {
        withClient { zk =>
          val sem1 = new ZkAsyncSemaphore(zk, path, 2)
          val sem2 = new ZkAsyncSemaphore(zk, path, 2)

          "have correct initial values" in {
            assert(sem1.numPermitsAvailable === 2)
            assert(sem1.numWaiters === 0)
            assert(sem2.numPermitsAvailable === 2)
            assert(sem2.numWaiters === 0)
          }

          "execute immediately while permits are available" in {
            Await.result(acquire(sem1) within(new JavaTimer(true), 2.second))
            assert(sem1.numPermitsAvailable === 1)
            assert(sem1.numWaiters === 0)
            assert(sem2.numPermitsAvailable === 1)
            assert(sem2.numWaiters === 0)

            Await.result(acquire(sem2) within(new JavaTimer(true), 2.second))
            assert(sem1.numPermitsAvailable === 0)
            assert(sem1.numWaiters === 0)
            assert(sem2.numPermitsAvailable === 0)
            assert(sem2.numWaiters === 0)
          }

          var awaiting1: Future[Permit] = null
          var awaiting2: Future[Permit] = null

          "queue waiters when no permits are available" in {
            implicit val config =
              PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

            awaiting1 = acquire(sem1)
            assert(awaiting1.poll === (None))
            assert(awaiting1.isDefined === (false))
            eventually {
              assert(sem1.numPermitsAvailable === 0)
              assert(sem1.numWaiters === 1)
              assert(sem2.numPermitsAvailable === 0)
              assert(sem2.numWaiters === 1)
            }

            awaiting2 = acquire(sem2)
            assert(awaiting2.poll === (None))
            assert(awaiting2.isDefined === (false))
            eventually {
              assert(sem1.numPermitsAvailable === 0)
              assert(sem1.numWaiters === 2)
              assert(sem2.numPermitsAvailable === 0)
              assert(sem2.numWaiters === 2)
            }
          }

          "have correct gauges as permit holders release" in {
            implicit val config =
              PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

            assert(permits.size === (2))

            permits.poll().release()
            Await.result(awaiting1 within(new JavaTimer(true), 2.second))

            eventually {
              assert(sem1.numPermitsAvailable === 0)
              assert(sem1.numWaiters === 1)
              assert(sem2.numPermitsAvailable === 0)
              assert(sem2.numWaiters === 1)
            }

            permits.poll().release()
            Await.result(awaiting2 within(new JavaTimer(true), 2.second))

            eventually {
              assert(sem1.numPermitsAvailable === 0)
              assert(sem1.numWaiters === 0)
              assert(sem2.numPermitsAvailable === 0)
              assert(sem2.numWaiters === 0)
            }

            permits.poll().release()

            eventually {
              assert(sem1.numPermitsAvailable === 1)
              assert(sem1.numWaiters === 0)
              assert(sem2.numPermitsAvailable === 1)
              assert(sem2.numWaiters === 0)
            }

            permits.poll().release()

            eventually {
              assert(sem1.numPermitsAvailable === 2)
              assert(sem1.numWaiters === 0)
              assert(sem2.numPermitsAvailable === 2)
              assert(sem2.numWaiters === 0)
            }
          }
        }
      }

      "numPermitsOf" should {
        "get a node's value" in {
          val numPermitsOfMethod = PrivateMethod[Future[Int]]('numPermitsOf)
          withClient { zk =>
            val sem = new ZkAsyncSemaphore(zk, "/aoeu/aoeu", 2)
            val znode = ZNode(zk, "/testing/twitter/uuu")
            znode.create("7".getBytes)
            val permits: Future[Int] = sem invokePrivate numPermitsOfMethod(znode)
            assert(permits.get() == 7)
          }
        }

        "not error on NoNode" in {
          val numPermitsOfMethod = PrivateMethod[Future[Int]]('numPermitsOf)
          withClient { zk =>
            val sem = new ZkAsyncSemaphore(zk, "/aoeu/aoeu", 2)
            val permits: Future[Int] = sem invokePrivate numPermitsOfMethod(ZNode(zk, "/aoeu/aoeu/aoeu"))
            Await.result(permits)
          }
        }
      }
    }
 }
}
