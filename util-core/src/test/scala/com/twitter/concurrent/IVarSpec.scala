package com.twitter.concurrent

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ArrayBuffer, SynchronizedBuffer}

import org.specs.Specification

object IVarSpec extends Specification {
  "IVar" should {
    val iv = new IVar[Int]
    "invoke gets after value is set" in {
      var value: Option[Int] = None
      iv.get { v => value = Some(v) }
      value must beNone

      iv.set(123) must beTrue
      value must beSome(123)
    }

   "set value once" in {
     iv.set(123) must beTrue
     iv() must be_==(123)
     iv.set(333) must beFalse
     iv() must be_==(123)
   }

   "execute get blocks in order" in {
     val order = new ArrayBuffer[Int]
     iv.get { _ => order += 1 }
     iv.get { _ => order += 2 }
     iv.set(123)
     iv.get { _ => order += 3 }
     order.toSeq must be_==(Seq(1,2,3))
   }

   "execute blocks in order when gets are interleaved with a set" in {
     val startGet, continueGet, finishGet = new CountDownLatch(1)
     val dispatches = new ArrayBuffer[(Int, Long)] with SynchronizedBuffer[(Int, Long)]
     Thread.currentThread.getId()

     iv.get { _ => startGet.countDown(); continueGet.await(); dispatches += ((1, Thread.currentThread.getId())) }
     iv.get { _ => finishGet.countDown(); dispatches += ((2, Thread.currentThread.getId())) }
     val t = new Thread {
       override def run() {
         iv.set(123)
       }
     }

     t.start()
     startGet.await()
     iv.get { _ => finishGet.await(); dispatches += ((3, Thread.currentThread.getId())) }
     continueGet.countDown()
     val id = t.getId
     t.join()

     dispatches.toSeq must be_==(Seq((1, id), (2, id), (3, id)))
   }
  }
}
