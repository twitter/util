package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException}

class AsyncSemaphoreSpec extends SpecificationWithJUnit with Mockito {
  "AsyncSemaphore" should {
    val f = mock[() => Unit]
    val s = new AsyncSemaphore(2)
    val permits = new ConcurrentLinkedQueue[Permit]
    def acquire() {
      s.acquire() onSuccess { permit =>
        f()
        permits add permit
      }
    }

    "execute immediately while permits are available" in {
      acquire()
      there was one(f)()

      acquire()
      there were two(f)()

      acquire()
      there were two(f)()
    }

    "execute deferred computations when permits are released" in {
      acquire()
      acquire()
      acquire()
      acquire()

      there were two(f)()

      permits.poll().release()
      there were three(f)()

      permits.poll().release()
      there were 4.times(f)()

      permits.poll().release()
      there were 4.times(f)()
    }

    "bound the number of waiters" in {
      val s2 = new AsyncSemaphore(2, 3)
      def acquire2() = {
        s2.acquire() onSuccess { permit =>
          f()
          permits add permit
        }
      }

      // The first two acquires obtain a permit.
      acquire2()
      acquire2()

      there were two(f)()

      // The next three acquires wait.
      acquire2()
      acquire2()
      acquire2()

      there were two(f)()
      s2.numWaiters mustEqual(3)

      // The next acquire should be rejected.
      val futurePermit = acquire2()
      s2.numWaiters mustEqual(3)
      futurePermit.get() must throwA[RejectedExecutionException]

      // Waiting tasks should still execute once permits are available.
      permits.poll().release()
      permits.poll().release()
      permits.poll().release()
      there were 5.times(f)()
    }
  }
}
