package com.twitter.concurrent

import com.twitter.conversions.DurationOps._
import com.twitter.util._
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException, CountDownLatch}
import org.scalatest.fixture.FunSpec
import scala.collection.mutable
import org.scalatest.Outcome

class AsyncSemaphoreTest extends FunSpec {
  class AsyncSemaphoreHelper(
    val sem: AsyncSemaphore,
    var count: Int,
    val permits: ConcurrentLinkedQueue[Permit]) {
    def copy(
      sem: AsyncSemaphore = this.sem,
      count: Int = this.count,
      permits: ConcurrentLinkedQueue[Permit] = this.permits
    ): AsyncSemaphoreHelper =
      new AsyncSemaphoreHelper(sem, count, permits)
  }

  type FixtureParam = AsyncSemaphoreHelper

  private def await[T](t: Awaitable[T]): T = Await.result(t, 15.seconds)

  override def withFixture(test: OneArgTest): Outcome = {
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

    it("shouldn't deadlock on releases") { _ =>
      val semaphore1 = new AsyncSemaphore(1)
      val semaphore2 = new AsyncSemaphore(1)

      val latch1 = new CountDownLatch(1)
      val latch2 = new CountDownLatch(1)

      val permit1 = await(semaphore1.acquire())
      val permit2 = await(semaphore2.acquire())

      val futurePermit1 = semaphore1.acquire()
      val futurePermit2 = semaphore2.acquire()

      assert(!futurePermit1.isDefined)
      assert(!futurePermit2.isDefined)

      // Add continuations that acquire each others lock
      futurePermit1.map { permit =>
        latch1.countDown()
        latch2.await()
        semaphore2.numWaiters // poor synchronization causes a deadlock
        permit.release()
      }

      futurePermit2.map { permit =>
        latch2.countDown()
        latch1.await()
        semaphore1.numWaiters // poor synchronization causes a deadlock
        permit.release()
      }

      val thread1 = new Thread {
        override def run(): Unit = {
          permit1.release()
        }
      }

      val thread2 = new Thread {
        override def run(): Unit = {
          permit2.release()
        }
      }

      thread1.start()
      thread2.start()

      thread1.join(15 * 1000)
      thread2.join(15 * 1000)

      // If they didn't deadlock, they shouldn't be alive.
      assert(!thread1.isAlive)
      assert(!thread2.isAlive)

      assert(futurePermit1.isDefined)
      assert(futurePermit2.isDefined)
    }

    it("shouldn't deadlock on interrupts") { _ =>
      val semaphore1 = new AsyncSemaphore(1)
      val semaphore2 = new AsyncSemaphore(1)

      val latch1 = new CountDownLatch(1)
      val latch2 = new CountDownLatch(1)

      val permit1 = await(semaphore1.acquire())
      val unused = await(semaphore2.acquire())

      val futurePermit1 = semaphore1.acquire()
      val futurePermit2 = semaphore2.acquire()

      assert(!futurePermit1.isDefined)
      assert(!futurePermit2.isDefined)

      // Add continuations that acquire each others lock
      futurePermit1.ensure {
        latch1.countDown()
        latch2.await()
        semaphore2.numWaiters // poor synchronization causes a deadlock
      }

      futurePermit2.ensure {
        latch2.countDown()
        latch1.await()
        semaphore1.numWaiters // poor synchronization causes a deadlock
      }

      val thread1 = new Thread {
        override def run(): Unit = {
          permit1.release()
        }
      }

      val ex = new Exception("boom")

      val thread2 = new Thread {
        override def run(): Unit = {
          futurePermit2.raise(ex)
        }
      }

      thread1.start()
      thread2.start()

      thread1.join(15 * 1000)
      thread2.join(15 * 1000)

      // If they didn't deadlock, they shouldn't be alive.
      assert(!thread1.isAlive)
      assert(!thread2.isAlive)

      assert(futurePermit1.isDefined)
      assert(futurePermit2.poll == Some(Throw(ex)))
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

    it("drains waiters when failed") { semHelper =>
      val as = new AsyncSemaphore(1)
      val (r1, r2, r3) = (as.acquire(), as.acquire(), as.acquire())

      assert(r1.isDefined)
      assert(!r2.isDefined)
      assert(!r3.isDefined)
      assert(as.numWaiters == 2)

      as.fail(new Exception("woop"))

      assert(as.numWaiters == 0)

      // new acquisitions fail
      Await.result(r1, 2.seconds).release()
      val (r4, r5) = (as.acquire(), as.acquire())
      assert(as.numWaiters == 0)

      val results = Seq(r2.poll, r3.poll, r4.poll, r5.poll)
      val msgs = results.collect { case Some(Throw(e)) => e.getMessage }
      assert(msgs.forall(_ == "woop"))
    }
  }
}
