package com.twitter.util

import com.twitter.conversions.DurationOps._
import org.scalatest.WordSpec
import scala.collection.mutable

class PoolTest extends WordSpec {

  private[this] def await[T](f: Awaitable[T]): T =
    Await.result(f, 2.seconds)

  "SimplePool" should {
    "with a simple queue of items" should {
      "it reseves items in FIFO order" in {
        val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
        val pool = new SimplePool(queue)
        assert(await(pool.reserve()) == 1)
        assert(await(pool.reserve()) == 2)
        pool.release(2)
        assert(await(pool.reserve()) == 3)
        assert(await(pool.reserve()) == 2)
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

        assert(await(pool.reserve()) == 2)
        assert(await(pool.reserve()) == 4)
        assert(await(pool.reserve()) == 6)
        assert(await(pool.reserve()) == 8)
        val promise = pool.reserve()
        intercept[TimeoutException] {
          Await.result(promise, 1.millisecond)
        }
        pool.release(8)
        pool.release(6)
        assert(await(promise) == 8)
        assert(await(pool.reserve()) == 6)
        intercept[TimeoutException] {
          Await.result(pool.reserve(), 1.millisecond)
        }
      }

      "reserve & dispose" in {
        val h = new PoolSpecHelper
        import h._

        assert(await(pool.reserve()) == 2)
        assert(await(pool.reserve()) == 4)
        assert(await(pool.reserve()) == 6)
        assert(await(pool.reserve()) == 8)
        intercept[TimeoutException] {
          Await.result(pool.reserve(), 1.millisecond)
        }
        pool.dispose(2)
        assert(Await.result(pool.reserve(), 1.millisecond) == 10)
      }
    }
  }
}
