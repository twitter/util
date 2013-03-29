package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException}
import com.twitter.util.Await

class AsyncSemaphoreSpec extends SpecificationWithJUnit with Mockito {
  "AsyncSemaphore" should {
    var count = 0
    val s = new AsyncSemaphore(2)
    val permits = new ConcurrentLinkedQueue[Permit]
    def acquire() {
      s.acquire() onSuccess { permit =>
        count += 1
        permits add permit
      }
    }

    "execute immediately while permits are available" in {
      acquire()
      count must be_==(1)

      acquire()
      count must be_==(2)

      acquire()
      count must be_==(2)
    }

    "execute deferred computations when permits are released" in {
      acquire()
      acquire()
      acquire()
      acquire()
      
      count must be_==(2)

      permits.poll().release()
      count must be_==(3)

      permits.poll().release()
      count must be_==(4)

      permits.poll().release()
      count must be_==(4)
    }

    "bound the number of waiters" in {
      val s2 = new AsyncSemaphore(2, 3)
      def acquire2() = {
        s2.acquire() onSuccess { permit =>
          count += 1
          permits add permit
        }
      }

      // The first two acquires obtain a permit.
      acquire2()
      acquire2()

      count must be_==(2)

      // The next three acquires wait.
      acquire2()
      acquire2()
      acquire2()

      count must be_==(2)
      s2.numWaiters mustEqual(3)

      // The next acquire should be rejected.
      val futurePermit = acquire2()
      s2.numWaiters mustEqual(3)
      Await.result(futurePermit) must throwA[RejectedExecutionException]

      // Waiting tasks should still execute once permits are available.
      permits.poll().release()
      permits.poll().release()
      permits.poll().release()
      count must be_==(5)
    }
  }
}
