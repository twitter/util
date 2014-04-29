package com.twitter.zk.coordination

import com.twitter.concurrent.Permit
import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.util._
import org.junit.runner.RunWith
import com.twitter.conversions.time._
import scala.collection.JavaConverters._
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import com.twitter.zk.{RetryPolicy, NativeConnector, ZkClient}
import org.scalatest.junit.JUnitRunner
import org.scalatest.{WordSpec, Matchers}
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.concurrent.Eventually._

@RunWith(classOf[JUnitRunner])
class ZkAsyncSemaphoreSpec extends WordSpec with Matchers with MockitoSugar with AsyncAssertions {

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
            sem1.numPermitsAvailable should equal(2)
            sem1.numWaiters should equal(0)
            sem2.numPermitsAvailable should equal(2)
            sem2.numWaiters should equal(0)
          }

          "execute immediately while permits are available" in {
            Await.result(acquire(sem1) within(new JavaTimer(true), 2.second))
            sem1.numPermitsAvailable should equal(1)
            sem1.numWaiters should equal(0)
            sem2.numPermitsAvailable should equal(1)
            sem2.numWaiters should equal(0)

            Await.result(acquire(sem2) within(new JavaTimer(true), 2.second))
            sem1.numPermitsAvailable should equal(0)
            sem1.numWaiters should equal(0)
            sem2.numPermitsAvailable should equal(0)
            sem2.numWaiters should equal(0)
          }

          var awaiting1: Future[Permit] = null
          var awaiting2: Future[Permit] = null

          "queue waiters when no permits are available" in {
            implicit val config =
              PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

            awaiting1 = acquire(sem1)
            awaiting1.poll should be (None)
            awaiting1.isDefined should be (false)
            eventually {
              sem1.numPermitsAvailable should equal(0)
              sem1.numWaiters should equal(1)
              sem2.numPermitsAvailable should equal(0)
              sem2.numWaiters should equal(1)
            }

            awaiting2 = acquire(sem2)
            awaiting2.poll should be (None)
            awaiting2.isDefined should be (false)
            eventually {
              sem1.numPermitsAvailable should equal(0)
              sem1.numWaiters should equal(2)
              sem2.numPermitsAvailable should equal(0)
              sem2.numWaiters should equal(2)
            }
          }

          "have correct gauges as permit holders release" in {
            implicit val config =
              PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

            permits.size should equal (2)

            permits.poll().release()
            Await.result(awaiting1 within(new JavaTimer(true), 2.second))

            eventually {
              sem1.numPermitsAvailable should equal(0)
              sem1.numWaiters should equal(1)
              sem2.numPermitsAvailable should equal(0)
              sem2.numWaiters should equal(1)
            }

            permits.poll().release()
            Await.result(awaiting2 within(new JavaTimer(true), 2.second))

            eventually {
              sem1.numPermitsAvailable should equal(0)
              sem1.numWaiters should equal(0)
              sem2.numPermitsAvailable should equal(0)
              sem2.numWaiters should equal(0)
            }

            permits.poll().release()

            eventually {
              sem1.numPermitsAvailable should equal(1)
              sem1.numWaiters should equal(0)
              sem2.numPermitsAvailable should equal(1)
              sem2.numWaiters should equal(0)
            }

            permits.poll().release()

            eventually {
              sem1.numPermitsAvailable should equal(2)
              sem1.numWaiters should equal(0)
              sem2.numPermitsAvailable should equal(2)
              sem2.numWaiters should equal(0)
            }
          }
        }
      }
    }
 }
}
