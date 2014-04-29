package com.twitter.util

import scala.collection.mutable
import org.scalatest.{WordSpec, Matchers}

import com.twitter.conversions.time._

class PoolSpec extends WordSpec with Matchers {
  "SimplePool" should {
    "with a simple queue of items" should {
      "it reseves items in FIFO order" in {
        val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
        val pool = new SimplePool(queue)
        Await.result(pool.reserve()) shouldEqual 1
        Await.result(pool.reserve()) shouldEqual 2
        pool.release(2)
        Await.result(pool.reserve()) shouldEqual 3
        Await.result(pool.reserve()) shouldEqual 2
        pool.release(1)
        pool.release(2)
        pool.release(3)
      }
    }

    "with an object factory and a health check" should {
      class PoolSpecHelper {
        var count = 0
        val pool = new FactoryPool[Int](4) {
          def makeItem() = { count += 1; Future(count) }
          def isHealthy(i: Int) = i % 2 == 0
        }
      }

      "reserve & release" in {
        val h = new PoolSpecHelper
        import h._

        Await.result(pool.reserve()) shouldEqual 2
        Await.result(pool.reserve()) shouldEqual 4
        Await.result(pool.reserve()) shouldEqual 6
        Await.result(pool.reserve()) shouldEqual 8
        val promise = pool.reserve()
        intercept[TimeoutException] {
          Await.result(promise, 1.millisecond)
        }
        pool.release(8)
        pool.release(6)
        Await.result(promise) shouldEqual 8
        Await.result(pool.reserve()) shouldEqual 6
        intercept[TimeoutException] {
          Await.result(pool.reserve, 1.millisecond)
        }
      }

      "reserve & dispose" in {
        val h = new PoolSpecHelper
        import h._

        Await.result(pool.reserve()) shouldEqual 2
        Await.result(pool.reserve()) shouldEqual 4
        Await.result(pool.reserve()) shouldEqual 6
        Await.result(pool.reserve()) shouldEqual 8
        intercept[TimeoutException] {
          Await.result(pool.reserve(), 1.millisecond)
        }
        pool.dispose(2)
        Await.result(pool.reserve(), 1.millisecond) shouldEqual 10
      }
    }
  }
}
