package com.twitter.concurrent

import java.util.concurrent.CountDownLatch

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
@RunWith(classOf[JUnitRunner])
class SerializedTest extends WordSpec with Serialized {
  "Serialized" should {
    "runs blocks, one at a time, in the order received" in {
      val t1CallsSerializedFirst = new CountDownLatch(1)
      val t1FinishesWork = new CountDownLatch(1)
      val orderOfExecution = new collection.mutable.ListBuffer[Thread]

      val t1 = new Thread {
        override def run: Unit = {
          serialized {
            t1CallsSerializedFirst.countDown()
            t1FinishesWork.await()
            orderOfExecution += this
            ()
          }
        }
      }

      val t2 = new Thread {
        override def run: Unit = {
          t1CallsSerializedFirst.await()
          serialized {
            orderOfExecution += this
            ()
          }
          t1FinishesWork.countDown()
        }
      }

      t1.start()
      t2.start()
      t1.join()
      t2.join()

      assert(orderOfExecution.toList == List(t1, t2))
    }
  }
}
