package com.twitter.concurrent

import com.twitter.util.{Await, Future, Promise, Try}
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException}
import org.junit.runner.RunWith
import org.scalatest.fixture.FunSpec
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class AsyncSemaphoreTest extends FunSpec {
  class AsyncSemaphoreHelper(val sem: AsyncSemaphore, var count: Int, val permits: ConcurrentLinkedQueue[Permit]) {
    def copy(sem: AsyncSemaphore = this.sem, count: Int = this.count, permits: ConcurrentLinkedQueue[Permit] = this.permits) =
      new AsyncSemaphoreHelper(sem, count, permits)
  }

  type FixtureParam = AsyncSemaphoreHelper

  override def withFixture(test: OneArgTest) = {
    val sem = new AsyncSemaphore(2)
    val helper = new AsyncSemaphoreHelper(sem, 0, new ConcurrentLinkedQueue[Permit])
    withFixture(test.toNoArgTest(helper))
  }

  describe("AsyncSemaphore") {
    def acquire(s: AsyncSemaphoreHelper) = {
      val fPermit = s.sem.acquire()
      fPermit onSuccess { permit =>
        s.count += 1
        s.permits add permit
      }
      fPermit
    }

    it("should validate constructor parameters") { _ =>
      intercept[IllegalArgumentException] {
        new AsyncSemaphore(0)
      }

      intercept[IllegalArgumentException] {
        new AsyncSemaphore(1, -1)
      }
    }

    it("should execute immediately while permits are available") { semHelper =>
      assert(semHelper.sem.numPermitsAvailable == (2))
      acquire(semHelper)
      assert(semHelper.count == (1))
      assert(semHelper.sem.numPermitsAvailable == (1))

      acquire(semHelper)
      assert(semHelper.count == (2))
      assert(semHelper.sem.numPermitsAvailable == (0))

      acquire(semHelper)
      assert(semHelper.count == (2))
      assert(semHelper.sem.numPermitsAvailable == (0))
    }

    it("should execute deferred computations when permits are released") { semHelper =>
      acquire(semHelper)
      acquire(semHelper)
      acquire(semHelper)
      acquire(semHelper)

      assert(semHelper.count == (2))
      assert(semHelper.sem.numPermitsAvailable == (0))

      semHelper.permits.poll().release()
      assert(semHelper.count == (3))

      semHelper.permits.poll().release()
      assert(semHelper.count == (4))

      semHelper.permits.poll().release()
      assert(semHelper.count == (4))
    }

    it("should bound the number of waiters") { semHelper =>
      val semHelper2 = semHelper.copy(sem = new AsyncSemaphore(2, 3))

      // The first two acquires obtain a permit.
      acquire(semHelper2)
      acquire(semHelper2)

      assert(semHelper2.count == (2))

      // The next three acquires wait.
      acquire(semHelper2)
      acquire(semHelper2)
      acquire(semHelper2)

      assert(semHelper2.count == (2))
      assert(semHelper2.sem.numWaiters == (3))

      // The next acquire should be rejected.
      val permit = acquire(semHelper2)
      assert(semHelper2.sem.numWaiters == (3))
      intercept[RejectedExecutionException] {
        Await.result(permit)
      }

      // Waiting tasks should still execute once permits are available.
      semHelper2.permits.poll().release()
      semHelper2.permits.poll().release()
      semHelper2.permits.poll().release()
      assert(semHelper2.count == (5))
    }
    it("should satisfy futures with exceptions if they are interrupted") { semHelper =>
      val p1 = acquire(semHelper)
      val p2 = acquire(semHelper)
      val p3 = acquire(semHelper)

      p3.raise(new Exception("OK"))
      val e = intercept[Exception] {
        Await.result(p3)
      }
      assert(e.getMessage == ("OK"))

      Await.result(p2).release()
      Await.result(p1).release()
    }

    it("should execute queued up async functions as permits become available") { semHelper =>
      var counter = 0
      val queue = new mutable.Queue[Promise[Unit]]()
      val func = new (() => Future[Unit]) {
        def apply(): Future[Unit] = {
          counter = counter + 1
          val promise = new Promise[Unit]()
          queue.enqueue(promise)
          promise
        }
      }
      assert(semHelper.sem.numPermitsAvailable == 2)

      semHelper.sem.acquireAndRun(func())
      assert(counter == 1)
      assert(semHelper.sem.numPermitsAvailable == 1)

      semHelper.sem.acquireAndRun(func())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)

      semHelper.sem.acquireAndRun(func())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)

      queue.dequeue().setValue(Unit)
      assert(counter == 3)
      assert(semHelper.sem.numPermitsAvailable == 0)

      queue.dequeue().setValue(Unit)
      assert(semHelper.sem.numPermitsAvailable == 1)

      queue.dequeue().setException(new RuntimeException("test"))
      assert(semHelper.sem.numPermitsAvailable == 2)
    }

    it("should release permit even if queued up function throws an exception") { semHelper =>
      val badFunc = new Function0[Future[Unit]] {
        def apply(): Future[Unit] = throw new RuntimeException("bad func calling")
      }
      semHelper.sem.acquireAndRun(badFunc())
      assert(semHelper.sem.numPermitsAvailable == 2)
    }

    it("should execute queued up sync functions as permits become available") { semHelper =>
      var counter = 0
      val queue = new mutable.Queue[Promise[Unit]]()
      val funcFuture = new (() => Future[Unit]) {
        def apply(): Future[Unit] = {
          counter = counter + 1
          val promise = new Promise[Unit]()
          queue.enqueue(promise)
          promise
        }
      }
      val func = new (() => Int) {
        def apply(): Int = {
          counter = counter + 1
          counter
        }
      }
      assert(semHelper.sem.numPermitsAvailable == 2)

      semHelper.sem.acquireAndRun(funcFuture())
      assert(counter == 1)
      assert(semHelper.sem.numPermitsAvailable == 1)

      semHelper.sem.acquireAndRun(funcFuture())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)

      val future = semHelper.sem.acquireAndRunSync(func())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)
      // sync func is blocked at this point.
      // But it should be executed as soon as one of the queued up future functions finish

      queue.dequeue().setValue(Unit)
      assert(counter == 3)
      val result = Await.result(future)
      assert(result == 3)
      assert(semHelper.sem.numPermitsAvailable == 1)
    }

    it("should handle queued up sync functions which throw exception") { semHelper =>
      var counter = 0
      val queue = new mutable.Queue[Promise[Unit]]()
      val funcFuture = new (() => Future[Unit]) {
        def apply(): Future[Unit] = {
          counter = counter + 1
          val promise = new Promise[Unit]()
          queue.enqueue(promise)
          promise
        }
      }
      val badFunc = new (() => Int) {
        def apply(): Int = {
          throw new Exception("error!")
        }
      }
      assert(semHelper.sem.numPermitsAvailable == 2)

      semHelper.sem.acquireAndRun(funcFuture())
      assert(counter == 1)
      assert(semHelper.sem.numPermitsAvailable == 1)

      semHelper.sem.acquireAndRun(funcFuture())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)

      val future = semHelper.sem.acquireAndRunSync(badFunc())
      assert(counter == 2)
      assert(semHelper.sem.numPermitsAvailable == 0)
      // sync func is blocked at this point.
      // But it should be executed as soon as one of the queued up future functions finish

      queue.dequeue().setValue(Unit)
      assert(counter == 2)
      assert(Try(Await.result(future)).isThrow)
      assert(semHelper.sem.numPermitsAvailable == 1)
    }
  }
}
